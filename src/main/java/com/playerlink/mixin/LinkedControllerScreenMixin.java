package com.playerlink.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.playerlink.client.SkinCache;
import com.playerlink.network.RequestControllerWhitelistPacket;
import com.playerlink.util.ControllerOwners;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(value = LinkedControllerScreen.class, remap = false)
public abstract class LinkedControllerScreenMixin extends AbstractContainerScreen<AbstractContainerMenu> {

    @Unique private static final int FACE_STRIP_H = 24;
    @Unique private static final int FACE_SIZE    = 16;
    @Unique private static final int BOTTOM_STRIP_Y_THRESHOLD = 50;

    @Unique private boolean playerlink$layoutShifted = false;
    @Unique private int[] playerlink$faceX = new int[ControllerOwners.SLOT_COUNT];
    @Unique private int   playerlink$faceY = 0;

    private LinkedControllerScreenMixin(AbstractContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void playerlink$init(CallbackInfo ci) {
        if (playerlink$layoutShifted) return;
        playerlink$layoutShifted = true;

        this.imageHeight += FACE_STRIP_H;
        this.inventoryLabelY += FACE_STRIP_H;
        this.topPos = (this.height - this.imageHeight) / 2;

        int relThreshold = BOTTOM_STRIP_Y_THRESHOLD;
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget w) {
                int relY = w.getY() - this.topPos;
                if (relY >= relThreshold) {
                    w.setY(w.getY() + FACE_STRIP_H);
                }
            }
        }

        playerlink$faceY = this.topPos + BOTTOM_STRIP_Y_THRESHOLD + 4;
        int totalW = ControllerOwners.SLOT_COUNT * FACE_SIZE + (ControllerOwners.SLOT_COUNT - 1) * 4;
        int startX = this.leftPos + (this.imageWidth - totalW) / 2;
        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            playerlink$faceX[i] = startX + i * (FACE_SIZE + 4);
        }
    }

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void playerlink$renderFaces(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int stripY = playerlink$faceY - 4;
        int stripH = FACE_STRIP_H;
        int stripX = this.leftPos + 7;
        int stripW = this.imageWidth - 14;
        g.fill(stripX,     stripY,             stripX + stripW, stripY + stripH,         0xFF8B8B8B);
        g.fill(stripX,     stripY,             stripX + stripW, stripY + 1,              0xFF373737);
        g.fill(stripX,     stripY + stripH - 1, stripX + stripW, stripY + stripH,        0xFFFFFFFF);
        g.fill(stripX,     stripY,             stripX + 1,      stripY + stripH,         0xFF373737);
        g.fill(stripX + stripW - 1, stripY,    stripX + stripW, stripY + stripH,         0xFFFFFFFF);

        net.minecraft.world.item.ItemStack stack = playerlink$findHeldController(mc);

        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            int x = playerlink$faceX[i];
            int y = playerlink$faceY;
            boolean hover = mouseX >= x && mouseX < x + FACE_SIZE && mouseY >= y && mouseY < y + FACE_SIZE;

            g.fill(x - 1, y - 1, x + FACE_SIZE + 1, y + FACE_SIZE + 1, 0xFF373737);
            g.fill(x,     y,     x + FACE_SIZE,     y + FACE_SIZE,     hover ? 0xFFFFFFC0 : 0xFFAAAAAA);

            UUID owner = stack.isEmpty() ? null : ControllerOwners.get(stack, i);
            if (owner != null) {
                String name = playerlink$resolveName(owner);
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
                g.renderTooltip(this.font, Component.literal(tip), mouseX, mouseY);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void playerlink$onMouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        int idx = playerlink$faceAt(mx, my);
        if (idx < 0) return;
        PacketDistributor.sendToServer(new RequestControllerWhitelistPacket(idx));
        cir.setReturnValue(true);
    }

    @Unique
    private int playerlink$faceAt(double mx, double my) {
        if (my < playerlink$faceY || my >= playerlink$faceY + FACE_SIZE) return -1;
        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            int x = playerlink$faceX[i];
            if (mx >= x && mx < x + FACE_SIZE) return i;
        }
        return -1;
    }

    @Unique
    private static net.minecraft.world.item.ItemStack playerlink$findHeldController(Minecraft mc) {
        if (mc.player == null) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.world.item.ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof com.simibubi.create.content.redstone.link.controller.LinkedControllerItem) return main;
        net.minecraft.world.item.ItemStack off = mc.player.getOffhandItem();
        if (off.getItem() instanceof com.simibubi.create.content.redstone.link.controller.LinkedControllerItem) return off;
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Unique
    @Nullable
    private static String playerlink$resolveName(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var p = mc.level.getPlayerByUUID(id);
        return p == null ? null : p.getGameProfile().getName();
    }
}