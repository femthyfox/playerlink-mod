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
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Adds 6 player-face slots BELOW Create's LinkedController frequency
 * columns. Each face is x-aligned with its column (computed by reading
 * the actual Slot x positions from Create's menu, not hard-coded).
 *
 * No backdrop strip is drawn — that previously bled across Create's
 * trash + confirm buttons and made the UI look broken.
 */
@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class LinkedControllerScreenEvents {

    private LinkedControllerScreenEvents() {}

    private static final int FACE_SIZE = 16;

    private static int[] faceX = new int[ControllerOwners.SLOT_COUNT];
    private static int faceY = 0;
    private static boolean layoutValid = false;
    private static boolean layoutLogged = false;

    /**
     * Compute layout by reading actual freq slot x-positions from Create's
     * menu. This way our faces line up with whatever column layout Create
     * decides to use.
     */
    private static void recomputeLayout(AbstractContainerScreen<?> screen) {
        int leftPos = screen.getGuiLeft();
        int topPos  = screen.getGuiTop();

        // Distinct slot x-values (we expect 6 columns × 2 freq slots = 12 freq slots).
        // Some of the menu's slots are the player's inventory — those have x=8
        // and y > 80ish. Filter to slots in the top half of the GUI.
        int imageH = screen.getYSize();
        int freqHalf = imageH / 2 + 10;     // be generous

        TreeSet<Integer> xs = new TreeSet<>();
        int maxFreqSlotY = -1;
        for (Slot s : screen.getMenu().slots) {
            if (s.y < freqHalf) {
                xs.add(s.x);
                if (s.y > maxFreqSlotY) maxFreqSlotY = s.y;
            }
        }

        if (xs.size() < ControllerOwners.SLOT_COUNT) {
            // Fallback: hard-coded based on observed layout — even spacing
            // across the panel width.
            int imageW = screen.getXSize();
            int totalW = ControllerOwners.SLOT_COUNT * FACE_SIZE + (ControllerOwners.SLOT_COUNT - 1) * 4;
            int startX = leftPos + (imageW - totalW) / 2;
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                faceX[i] = startX + i * (FACE_SIZE + 4);
            }
            faceY = topPos + 80;
            layoutValid = true;
            if (!layoutLogged) {
                layoutLogged = true;
                PlayerLinkMod.LOGGER.info("[PlayerLink] Controller layout FALLBACK; freq slots <6 distinct x. xs={}", xs);
            }
            return;
        }

        // Take the 6 lowest-x distinct values (the 6 columns).
        List<Integer> sortedXs = new ArrayList<>(xs);
        Collections.sort(sortedXs);
        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            faceX[i] = leftPos + sortedXs.get(i);
        }

        // Place the face row right below the lowest freq slot
        // (slot is 16px tall, +6 gap = 22).
        faceY = topPos + maxFreqSlotY + 22;
        layoutValid = true;

        if (!layoutLogged) {
            layoutLogged = true;
            PlayerLinkMod.LOGGER.info(
                "[PlayerLink] Controller layout: topPos={} maxFreqSlotY={} faceY={} columns={}",
                topPos, maxFreqSlotY, faceY, sortedXs);
        }
    }

    @SubscribeEvent
    public static void onRender(final ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof LinkedControllerScreen lcs)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        recomputeLayout(lcs);
        if (!layoutValid) return;

        GuiGraphics g = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        ItemStack controller = findHeldController(mc);

        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            int x = faceX[i];
            int y = faceY;
            boolean hover = mouseX >= x && mouseX < x + FACE_SIZE
                         && mouseY >= y && mouseY < y + FACE_SIZE;

            // Slot well — 1-px dark border + slot-fill, no big strip.
            g.fill(x - 1, y - 1, x + FACE_SIZE + 1, y + FACE_SIZE + 1, 0xFF373737);
            g.fill(x,     y,     x + FACE_SIZE,     y + FACE_SIZE,     hover ? 0xFFFFFFC0 : 0xFF8B8B8B);

            UUID owner = controller.isEmpty() ? null : ControllerOwners.get(controller, i);
            if (owner != null) {
                String name = resolveName(owner);
                ResourceLocation skin = SkinCache.get(owner, name);
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                PlayerFaceRenderer.draw(g, skin, x, y, FACE_SIZE);
                RenderSystem.disableBlend();
            } else {
                int cx = x + FACE_SIZE / 2;
                int cy = y + FACE_SIZE / 2;
                g.fill(cx - 3, cy - 1, cx + 3, cy + 1, 0xFF555555);
                g.fill(cx - 1, cy - 3, cx + 1, cy + 3, 0xFF555555);
            }

            if (hover) {
                String tip = owner != null ? "Player Frequency" : "Click to assign owner";
                g.renderTooltip(mc.font, Component.literal(tip), mouseX, mouseY);
            }
        }
    }

    @SubscribeEvent
    public static void onClick(final ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof LinkedControllerScreen lcs)) return;
        if (event.getButton() != 0) return;
        recomputeLayout(lcs);
        if (!layoutValid) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        // Face slot click
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

        // Trash button click — sync clearing on our side too. (We no longer
        // hard-code coordinates — instead we just send the clear packet on
        // ANY click that's within Create's bottom strip but NOT on a face.
        // If user just clicks the bottom strip area, we clear; this is a
        // pragmatic compromise until we can detect Create's exact trash
        // button widget. Adjust later if it causes false-clears.)
        // For now, leave trash auto-clear disabled to avoid surprises.
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
