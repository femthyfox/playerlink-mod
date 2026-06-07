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

    private static int[] faceX = new int[ControllerOwners.SLOT_COUNT];
    private static int faceY = 0;
    private static boolean layoutValid = false;
    private static boolean layoutLogged = false;

    private static void recomputeLayout(AbstractContainerScreen<?> screen) {
        int leftPos = screen.getGuiLeft();
        int topPos  = screen.getGuiTop();
        int imageH  = screen.getYSize();
        int imageW  = screen.getXSize();

        // Try to find column x positions from the container's slot list.
        // Create ghost slots ARE in the menu slot list — collect unique x values
        // that appear in the upper half of the GUI (the frequency rows).
        TreeSet<Integer> xs = new TreeSet<>();
        for (Slot s : screen.getMenu().slots) {
            if (s.y > 0 && s.y < imageH / 2) {
                xs.add(s.x);
            }
        }

        if (xs.size() >= ControllerOwners.SLOT_COUNT) {
            // Found the column x positions from the slot list
            List<Integer> sortedXs = new ArrayList<>(xs);
            Collections.sort(sortedXs);
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                faceX[i] = leftPos + sortedXs.get(i) + 1;
            }
        } else {
            // Fallback: evenly space across the GUI width matching Create's known column spacing
            // LinkedControllerScreen is 222px wide with 6 columns of 18px each starting at x=8
            int firstColX = leftPos + 8;
            int colStride = (imageW - 16) / ControllerOwners.SLOT_COUNT;
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                faceX[i] = firstColX + i * colStride + 1;
            }
            if (!layoutLogged) {
                layoutLogged = true;
                PlayerLinkMod.LOGGER.info("[PlayerLink] Controller layout: using fallback column x, imageW={}", imageW);
            }
        }

        // Place face row below the second freq row (y=52), the slot height (18),
        // and the key-label strip Create draws below the freq rows (~14px).
        faceY = topPos + 52 + 18 + 14 + 2;
        layoutValid = true;

        if (!layoutLogged) {
            layoutLogged = true;
            PlayerLinkMod.LOGGER.info("[PlayerLink] Controller layout: topPos={} faceY={}", topPos, faceY);
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

        // Trash button click → server clears ALL face owners too.
        if (isTrashButtonClick(lcs, mx, my)) {
            PacketDistributor.sendToServer(ClearAllControllerOwnersPacket.INSTANCE);
        }
    }

    private static boolean isTrashButtonClick(LinkedControllerScreen screen, double mx, double my) {
        int leftPos = screen.getGuiLeft();
        int topPos = screen.getGuiTop();
        int imageW = screen.getXSize();
        int imageH = screen.getYSize();
        int trashX = leftPos + imageW - 44;
        int trashY = topPos + imageH + 4;
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