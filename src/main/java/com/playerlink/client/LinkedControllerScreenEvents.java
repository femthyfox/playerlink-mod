package com.playerlink.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.playerlink.PlayerLinkMod;
import com.playerlink.network.ClearAllControllerOwnersPacket;
import com.playerlink.network.RequestControllerWhitelistPacket;
import com.playerlink.util.ControllerOwners;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Hooks into Create's LinkedControllerScreen to:
 *
 *   1. Add a row of 6 player-face slots tucked DIRECTLY below the existing
 *      frequency slot columns (one face per column).
 *   2. Relocate Create's bottom-strip widgets (trash button, confirm
 *      button, any auxiliary text input) to a vertical stack ON THE
 *      RIGHT SIDE of the screen panel, so they never overlap our faces.
 *   3. Detect clicks on the relocated trash widget so we can also wipe
 *      our owner data when the user clicks Create's "clear all" button.
 *
 * State is recomputed on every {@link ScreenEvent.Init.Post} — this fires
 * on first open and after every resize / screen swap, keeping us in sync
 * with whatever positions Create produces.
 */
@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class LinkedControllerScreenEvents {

    private LinkedControllerScreenEvents() {}

    // ── Visual / layout constants ──────────────────────────────────────────
    private static final int FACE_SIZE      = 16; // matches frequency slot interior
    private static final int FACE_WELL_PAD  = 1;  // 1-px dark border around each face
    private static final int FACE_ROW_GAP   = 4;  // gap between freq row and face row
    private static final int SLOT_INTERIOR  = 16; // vanilla slot interior (16x16)
    private static final int SIDEBAR_OFFSET = 6;  // gap from main panel to side stack
    private static final int SIDEBAR_TOP_PAD = 6; // pad from top of GUI for stack
    private static final int SIDEBAR_SPACING = 4; // vertical gap between stacked widgets

    // ── Per-render-frame layout cache (recomputed on Init.Post) ────────────
    private static final int[] faceX = new int[ControllerOwners.SLOT_COUNT];
    private static int   faceY       = 0;
    private static boolean layoutValid = false;

    /** Reference to Create's trash widget after relocation — used to detect clicks. */
    @Nullable private static AbstractWidget trashWidgetRef = null;

    // ════════════════════════════════════════════════════════════════════
    //                              INIT
    // ════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onInit(final ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof LinkedControllerScreen lcs)) return;

        final int leftPos = lcs.getGuiLeft();
        final int topPos  = lcs.getGuiTop();
        final int imageW  = lcs.getXSize();
        final int imageH  = lcs.getYSize();

        // ─── (1) Inspect freq slots to derive column x's + bottom-of-row Y ──
        // LinkedControllerMenu has 12 frequency slots (6 cols × 2 rows) plus
        // the standard 36 player-inventory slots appended after them. We do
        // NOT rely on a hard-coded "player inv starts at imageH-82" — instead
        // we sort all slots by y and treat the lowest 12 as freq slots and
        // anything below that as player inventory.
        List<Slot> allSlots = new ArrayList<>(lcs.getMenu().slots);
        allSlots.sort(Comparator.comparingInt(s -> s.y));

        final int FREQ_SLOT_COUNT = ControllerOwners.SLOT_COUNT * 2; // 12
        List<Integer> colXs = new ArrayList<>();
        int freqRowBottomRel = 0; // y of bottom edge of lowest freq slot (relative)
        int countedFreq = 0;
        for (Slot s : allSlots) {
            if (countedFreq >= FREQ_SLOT_COUNT) break;
            if (!colXs.contains(s.x)) colXs.add(s.x);
            int slotBottom = s.y + SLOT_INTERIOR;
            if (slotBottom > freqRowBottomRel) freqRowBottomRel = slotBottom;
            countedFreq++;
        }
        Collections.sort(colXs);

        // Player-inv top = lowest y of the 13th slot onward, or fallback if
        // the screen has fewer slots than expected (unit-test/edge case).
        int playerInvTopRel = (allSlots.size() > FREQ_SLOT_COUNT)
                ? allSlots.get(FREQ_SLOT_COUNT).y
                : imageH - 82;

        // ─── (2) Compute face row positions ─────────────────────────────────
        if (colXs.size() >= ControllerOwners.SLOT_COUNT && freqRowBottomRel > 0) {
            // Align each face with the corresponding freq column.
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                faceX[i] = leftPos + colXs.get(i);
            }
            faceY = topPos + freqRowBottomRel + FACE_ROW_GAP;
        } else {
            // Defensive fallback if we couldn't read 6 columns: distribute evenly.
            int totalW = ControllerOwners.SLOT_COUNT * FACE_SIZE
                       + (ControllerOwners.SLOT_COUNT - 1) * 2;
            int startX = leftPos + (imageW - totalW) / 2;
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                faceX[i] = startX + i * (FACE_SIZE + 2);
            }
            faceY = topPos + (freqRowBottomRel > 0 ? freqRowBottomRel : 60) + FACE_ROW_GAP;
        }
        layoutValid = true;

        // ─── (3) Find & relocate Create's bottom-strip widgets ──────────────
        // Anything sitting INSIDE the panel horizontally and between the
        // freq row and the player inventory vertically is part of the
        // bottom strip (trash, confirm, maybe a text input). Slide them
        // out to a vertical stack on the right side.
        final int stripTopRel    = freqRowBottomRel;
        final int stripBottomRel = playerInvTopRel;
        final int panelLeft      = leftPos;
        final int panelRight     = leftPos + imageW;

        List<AbstractWidget> bottomStrip = new ArrayList<>();
        for (GuiEventListener child : lcs.children()) {
            if (!(child instanceof AbstractWidget w)) continue;
            int wxAbs = w.getX();
            int wyAbs = w.getY();
            int wyRel = wyAbs - topPos;
            boolean insidePanelX = wxAbs >= panelLeft - 2 && wxAbs <= panelRight + 2;
            boolean insideStripY = wyRel >= stripTopRel && wyRel <  stripBottomRel;
            if (insidePanelX && insideStripY) {
                bottomStrip.add(w);
            }
        }

        // Sort left-to-right by ORIGINAL X so we can heuristically identify
        // the trash button (always the leftmost icon in Create's bottom row).
        bottomStrip.sort(Comparator.comparingInt(AbstractWidget::getX));
        trashWidgetRef = bottomStrip.isEmpty() ? null : bottomStrip.get(0);

        // Stack them on the right side of the panel.
        int sideX = leftPos + imageW + SIDEBAR_OFFSET;
        int sideY = topPos + SIDEBAR_TOP_PAD;
        for (AbstractWidget w : bottomStrip) {
            w.setPosition(sideX, sideY);
            sideY += w.getHeight() + SIDEBAR_SPACING;
        }

        PlayerLinkMod.LOGGER.info(
            "[PlayerLink] LinkedController layout — leftPos={} topPos={} faceY={} "
          + "faceX={} cols={} freqRowBottom(rel)={} relocatedWidgets={} trash?={}",
            leftPos, topPos, faceY,
            Arrays.toString(faceX), colXs, freqRowBottomRel,
            bottomStrip.size(),
            trashWidgetRef == null ? "none" : trashWidgetRef.getClass().getSimpleName());
    }

    // ════════════════════════════════════════════════════════════════════
    //                              RENDER
    // ════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onRender(final ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof LinkedControllerScreen)) return;
        if (!layoutValid) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        ItemStack controller = findHeldController(mc);

        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            final int x = faceX[i];
            final int y = faceY;
            boolean hover = mouseX >= x && mouseX < x + FACE_SIZE
                         && mouseY >= y && mouseY < y + FACE_SIZE;

            // Slot well — 1-px dark border + slot-fill (matches vanilla slot look).
            g.fill(x - FACE_WELL_PAD, y - FACE_WELL_PAD,
                   x + FACE_SIZE + FACE_WELL_PAD, y + FACE_SIZE + FACE_WELL_PAD,
                   0xFF373737);
            g.fill(x, y, x + FACE_SIZE, y + FACE_SIZE,
                   hover ? 0xFFFFFFC0 : 0xFF8B8B8B);

            UUID owner = controller.isEmpty() ? null : ControllerOwners.get(controller, i);
            if (owner != null) {
                ResourceLocation skin = SkinCache.get(owner, resolveName(owner));
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                PlayerFaceRenderer.draw(g, skin, x, y, FACE_SIZE);
                RenderSystem.disableBlend();
            } else {
                // Empty-slot "+" hint
                int cx = x + FACE_SIZE / 2;
                int cy = y + FACE_SIZE / 2;
                g.fill(cx - 3, cy - 1, cx + 3, cy + 1, 0xFF555555);
                g.fill(cx - 1, cy - 3, cx + 1, cy + 3, 0xFF555555);
            }

            if (hover) {
                Component tip = Component.literal(
                        owner != null ? "Player Frequency" : "Click to assign owner");
                g.renderTooltip(mc.font, tip, mouseX, mouseY);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //                              CLICK
    // ════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onClick(final ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof LinkedControllerScreen)) return;
        if (event.getButton() != 0) return;
        if (!layoutValid) return;

        final double mx = event.getMouseX();
        final double my = event.getMouseY();

        // Face-slot click → open the player-picker for that column. We
        // CANCEL so Create's slot-handling code never sees the click.
        if (my >= faceY && my < faceY + FACE_SIZE) {
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                int x = faceX[i];
                if (mx >= x && mx < x + FACE_SIZE) {
                    PacketDistributor.sendToServer(new RequestControllerWhitelistPacket(i));
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // Trash-widget click → also wipe all our slot owners. We do NOT
        // cancel — Create's own trash widget still fires and clears its
        // own frequency items as usual.
        if (trashWidgetRef != null && isInside(trashWidgetRef, mx, my)) {
            PacketDistributor.sendToServer(ClearAllControllerOwnersPacket.INSTANCE);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //                              HELPERS
    // ════════════════════════════════════════════════════════════════════

    private static boolean isInside(AbstractWidget w, double mx, double my) {
        return mx >= w.getX() && mx < w.getX() + w.getWidth()
            && my >= w.getY() && my < w.getY() + w.getHeight();
    }

    private static ItemStack findHeldController(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof LinkedControllerItem) return main;
        ItemStack off = mc.player.getOffhandItem();
        if (off.getItem() instanceof LinkedControllerItem) return off;
        return ItemStack.EMPTY;
    }

    @Nullable
    private static String resolveName(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var p = mc.level.getPlayerByUUID(id);
        return p == null ? null : p.getGameProfile().getName();
    }
}
