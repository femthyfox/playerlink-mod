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

@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class LinkedControllerScreenEvents {

    private LinkedControllerScreenEvents() {}

    private static final int FACE_SIZE = 16;
    private static final int FACE_GAP  = 4;

    private static int[] faceX     = new int[ControllerOwners.SLOT_COUNT];
    private static int   faceY     = 0;
    private static boolean layoutValid = false;

    /**
     * Place the six player-face slots ABOVE the frequency item slots,
     * leaving a 4px gap so they sit just above Create's slot row and
     * never overlap the trash or tick buttons at the bottom.
     */
    private static void recomputeLayout(AbstractContainerScreen<?> screen) {
        int leftPos = screen.getGuiLeft();
        int topPos  = screen.getGuiTop();
        int imageH  = screen.getYSize();

        // Collect all slot X positions that are in the upper half of the GUI
        // (frequency slots, not the inventory rows at the bottom).
        int freqHalf = imageH / 2 + 10;
        TreeSet<Integer> xs = new TreeSet<>();
        int minFreqSlotY = Integer.MAX_VALUE;

        for (Slot s : screen.getMenu().slots) {
            if (s.y < freqHalf) {
                xs.add(s.x);
                if (s.y < minFreqSlotY) minFreqSlotY = s.y;
            }
        }

        if (xs.size() >= ControllerOwners.SLOT_COUNT) {
            List<Integer> sortedXs = new ArrayList<>(xs);
            Collections.sort(sortedXs);
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                faceX[i] = leftPos + sortedXs.get(i);
            }
            // Place ABOVE the topmost frequency slot row, with a small gap
            faceY = topPos + minFreqSlotY - FACE_SIZE - FACE_GAP;
        } else {
            // Fallback: spread evenly across GUI width above centre
            int imageW = screen.getXSize();
            int totalW = ControllerOwners.SLOT_COUNT * FACE_SIZE
                       + (ControllerOwners.SLOT_COUNT - 1) * FACE_GAP;
            int startX = leftPos + (imageW - totalW) / 2;
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                faceX[i] = startX + i * (FACE_SIZE + FACE_GAP);
            }
            faceY = topPos + 14;
        }
        layoutValid = true;
    }

    @SubscribeEvent
    public static void onRender(final ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof LinkedControllerScreen lcs)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        recomputeLayout(lcs);
        if (!layoutValid) return;

        GuiGraphics g     = event.getGuiGraphics();
        int mouseX        = event.getMouseX();
        int mouseY        = event.getMouseY();
        ItemStack ctrl    = findHeldController(mc);

        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            int x     = faceX[i];
            int y     = faceY;
            boolean hover = mouseX >= x && mouseX < x + FACE_SIZE
                         && mouseY >= y && mouseY < y + FACE_SIZE;

            // Border + background
            g.fill(x - 1, y - 1, x + FACE_SIZE + 1, y + FACE_SIZE + 1, 0xFF373737);
            g.fill(x,     y,     x + FACE_SIZE,     y + FACE_SIZE,
                   hover ? 0xFFFFFFC0 : 0xFF8B8B8B);

            UUID owner = ctrl.isEmpty() ? null : ControllerOwners.get(ctrl, i);
            if (owner != null) {
                String name = resolveName(owner);
                ResourceLocation skin = SkinCache.get(owner, name);
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                PlayerFaceRenderer.draw(g, skin, x, y, FACE_SIZE);
                RenderSystem.disableBlend();
            } else {
                // Draw a small "+" placeholder
                int cx = x + FACE_SIZE / 2;
                int cy = y + FACE_SIZE / 2;
                g.fill(cx - 3, cy - 1, cx + 3, cy + 1, 0xFF555555);
                g.fill(cx - 1, cy - 3, cx + 1, cy + 3, 0xFF555555);
            }

            if (hover) {
                String tip = owner != null
                        ? "Player Frequency (click to change)"
                        : "Click to assign player frequency";
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

        if (my >= faceY && my < faceY + FACE_SIZE) {
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                if (mx >= faceX[i] && mx < faceX[i] + FACE_SIZE) {
                    PacketDistributor.sendToServer(new RequestControllerWhitelistPacket(i));
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // Trash button → clear all face owners too
        if (isTrashButtonClick(lcs, mx, my)) {
            PacketDistributor.sendToServer(ClearAllControllerOwnersPacket.INSTANCE);
        }
    }

    private static boolean isTrashButtonClick(LinkedControllerScreen screen,
                                               double mx, double my) {
        int leftPos = screen.getGuiLeft();
        int topPos  = screen.getGuiTop();
        int imageW  = screen.getXSize();
        int imageH  = screen.getYSize();
        int trashX  = leftPos + imageW - 44;
        int trashY  = topPos  + imageH + 4;
        return mx >= trashX && mx < trashX + 18
            && my >= trashY && my < trashY + 18;
    }

    private static ItemStack findHeldController(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof LinkedControllerItem) return main;
        ItemStack off  = mc.player.getOffhandItem();
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
