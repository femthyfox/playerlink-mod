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
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * NeoForge ScreenEvent-based overlay for Create's LinkedControllerScreen.
 *
 * Adds a row of 6 player-face slots in the gap BELOW the main controller
 * panel and ABOVE the player inventory. Clicking a face opens our
 * PlayerSelectScreen in controller-slot mode.
 *
 * Using ScreenEvent (instead of a Mixin) so we don't depend on whether
 * LinkedControllerScreen actually overrides render()/mouseClicked(); the
 * events fire for every screen regardless.
 */
@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class LinkedControllerScreenEvents {

    private LinkedControllerScreenEvents() {}

    private static final int FACE_SIZE = 16;
    private static final int FACE_GAP  = 4;
    private static boolean playerlink$layoutLogged = false;

    private static int[] computeLayout(AbstractContainerScreen<?> screen) {
        int leftPos = screen.getGuiLeft();
        int topPos  = screen.getGuiTop();
        int imageW  = screen.getXSize();
        int imageH  = screen.getYSize();

        if (!playerlink$layoutLogged) {
            playerlink$layoutLogged = true;
            PlayerLinkMod.LOGGER.info("[PlayerLink] LinkedController layout: leftPos={} topPos={} imageW={} imageH={}",
                    leftPos, topPos, imageW, imageH);
        }

        int totalW = ControllerOwners.SLOT_COUNT * FACE_SIZE + (ControllerOwners.SLOT_COUNT - 1) * FACE_GAP;
        int startX = leftPos + (imageW - totalW) / 2;
        // Place the face row just below the 2 rows of frequency slots and
        // just above (or overlapping) the textbox/save strip. 58 is a tuned
        // fixed offset; if your imageH differs, send me the log line above.
        int y      = topPos + 80;

        int[] r = new int[ControllerOwners.SLOT_COUNT + 1];
        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            r[i] = startX + i * (FACE_SIZE + FACE_GAP);
        }
        r[ControllerOwners.SLOT_COUNT] = y;
        return r;
    }

    @SubscribeEvent
    public static void onRender(final ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof LinkedControllerScreen lcs)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int[] layout = computeLayout(lcs);
        int y = layout[ControllerOwners.SLOT_COUNT];
        GuiGraphics g = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        // Strip backdrop
        int pad = 4;
        int stripX = layout[0] - pad;
        int stripY = y - pad;
        int stripW = (layout[ControllerOwners.SLOT_COUNT - 1] + FACE_SIZE) - layout[0] + pad * 2;
        int stripH = FACE_SIZE + pad * 2;
        g.fill(stripX, stripY, stripX + stripW, stripY + stripH, 0xFF8B8B8B);
        g.fill(stripX, stripY, stripX + stripW, stripY + 1, 0xFF373737);
        g.fill(stripX, stripY + stripH - 1, stripX + stripW, stripY + stripH, 0xFFFFFFFF);
        g.fill(stripX, stripY, stripX + 1, stripY + stripH, 0xFF373737);
        g.fill(stripX + stripW - 1, stripY, stripX + stripW, stripY + stripH, 0xFFFFFFFF);

        ItemStack controller = findHeldController(mc);

        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            int x = layout[i];
            boolean hover = mouseX >= x && mouseX < x + FACE_SIZE
                         && mouseY >= y && mouseY < y + FACE_SIZE;

            g.fill(x - 1, y - 1, x + FACE_SIZE + 1, y + FACE_SIZE + 1, 0xFF373737);
            g.fill(x,     y,     x + FACE_SIZE,     y + FACE_SIZE,     hover ? 0xFFFFFFC0 : 0xFFAAAAAA);

            UUID owner = controller.isEmpty() ? null : ControllerOwners.get(controller, i);
            if (owner != null) {
                String name = resolveName(owner);
                ResourceLocation skin = SkinCache.get(owner, name);
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                PlayerFaceRenderer.draw(g, skin, x, y, FACE_SIZE);
                RenderSystem.disableBlend();
            } else {
                // "+" hint for empty
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
        int[] layout = computeLayout(lcs);
        int y = layout[ControllerOwners.SLOT_COUNT];
        double mx = event.getMouseX();
        double my = event.getMouseY();

        // 1. Face slot click → open player picker.
        if (my >= y && my < y + FACE_SIZE) {
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                int x = layout[i];
                if (mx >= x && mx < x + FACE_SIZE) {
                    PacketDistributor.sendToServer(new RequestControllerWhitelistPacket(i));
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // 2. Trash button click → server clears ALL face owners too.
        //    We don't cancel — Create still gets the click and clears its
        //    own slots. We just piggy-back so owners are cleared in sync.
        if (isTrashButtonClick(lcs, mx, my)) {
            PacketDistributor.sendToServer(ClearAllControllerOwnersPacket.INSTANCE);
        }
    }

    /**
     * Heuristic: the trash button is the LEFT icon-button in the textbox
     * strip — i.e. the second-to-last widget by x position in the bottom
     * strip. Rather than couple to Create's widget classes (fragile), we
     * scan for any small icon-sized AbstractWidget below the face row,
     * positioned on the right half of the strip, that the mouse is over.
     *
     * The check button (checkmark) sits to the right of the trash button,
     * so trash = the one with the SMALLER x of the two right-side widgets.
     */
    private static boolean isTrashButtonClick(LinkedControllerScreen screen, double mx, double my) {
        int topPos = screen.getGuiTop();
        int leftPos = screen.getGuiLeft();
        int imageW = screen.getXSize();

        // Bottom strip is roughly below the face row. Constrain the search
        // to that y band so we don't false-positive on freq slot clicks.
        int faceY = topPos + 80;
        double yBandTop = faceY + FACE_SIZE - 2;
        double yBandBot = faceY + FACE_SIZE + 40;
        if (my < yBandTop || my > yBandBot) return false;

        // Trash sits in the right-third of the panel width, before the check.
        // From the reference screenshot: trash ≈ leftPos + imageW - 42,
        // check ≈ leftPos + imageW - 22. Each icon ~16x16.
        int trashX = leftPos + imageW - 42;
        int trashY = faceY + FACE_SIZE + 6;
        return mx >= trashX && mx < trashX + 18
            && my >= trashY && my < trashY + 18;
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
