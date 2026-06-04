package com.playerlink.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playerlink.PlayerLinkMod;
import com.playerlink.network.CopyLinkFrequencyPacket;
import com.playerlink.network.RequestTypewriterWhitelistPacket;
import com.playerlink.network.RequestWhitelistPacket;
import com.playerlink.util.ControllerOwners;
import com.playerlink.util.SlotMath;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    public static final KeyMapping OPEN_OWNER_GUI = new KeyMapping(
            "key.playerlink.open_owner_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.playerlink"
    );

    public static void registerKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_OWNER_GUI);
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            LinkFaceRenderer.HOVERED_FACE_SLOT_POS = null;
            return;
        }

        BlockPos hovered = null;
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
            if (be instanceof RedstoneLinkBlockEntity) {
                BlockState state = be.getBlockState();
                Direction facing = state.getValue(RedstoneLinkBlock.FACING);
                if (SlotMath.isFaceSlotHit(bhr.getBlockPos(), facing, bhr.getLocation())) {
                    hovered = bhr.getBlockPos();
                }
            }
        }
        LinkFaceRenderer.HOVERED_FACE_SLOT_POS = hovered;

        if (mc.screen != null) return;
        while (OPEN_OWNER_GUI.consumeClick()) {
            if (hovered != null) {
                PacketDistributor.sendToServer(new RequestWhitelistPacket(hovered));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderHud(final RenderGuiEvent.Post event) {
        BlockPos hovered = LinkFaceRenderer.HOVERED_FACE_SLOT_POS;
        if (hovered == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.screen != null) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;

        Component title = Component.translatable("playerlink.face_slot.title")
                .withStyle(ChatFormatting.WHITE);
        Component sub = Component.translatable("playerlink.face_slot.sub")
                .withStyle(ChatFormatting.GRAY);

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int titleY = sh / 2 + 16;
        int subY = titleY + font.lineHeight + 2;

        g.drawString(font, title, sw / 2 - font.width(title) / 2, titleY, 0xFFFFFFFF, true);
        g.drawString(font, sub,   sw / 2 - font.width(sub)   / 2, subY,   0xFFAAAAAA, true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getEntity().isShiftKeyDown()) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof RedstoneLinkBlockEntity)) return;

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(RedstoneLinkBlock.FACING);
        Vec3 hitVec = event.getHitVec().getLocation();
        if (!SlotMath.isFaceSlotHit(event.getPos(), facing, hitVec)) return;

        PlayerLinkMod.LOGGER.info("[PlayerLink] Face slot clicked at {}, opening GUI", event.getPos());
        PacketDistributor.sendToServer(new RequestWhitelistPacket(event.getPos()));
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * Right-click a Redstone Link while holding a Link Controller:
     * copies the link's player-frequency onto the currently active
     * controller slot (determined by which slot the controller GUI
     * last highlighted, defaulting to slot 0).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlockWithController(final PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Only fire when holding a controller
        net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof LinkedControllerItem)) {
            held = mc.player.getOffhandItem();
            if (!(held.getItem() instanceof LinkedControllerItem)) return;
        }

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof RedstoneLinkBlockEntity)) return;

        // Find which slot to copy into: pick the first empty one, or slot 0
        int targetSlot = 0;
        for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
            if (ControllerOwners.get(held, i) == null) { targetSlot = i; break; }
        }

        PacketDistributor.sendToServer(new CopyLinkFrequencyPacket(
                event.getPos(), targetSlot, net.minecraft.core.BlockPos.ZERO));
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * Shift + right-click on a Linked Typewriter opens the owner-select GUI.
     * Normal right-click on a Redstone Link while a Typewriter is selected
     * in the hotbar copies the link's frequency to the typewriter.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseTypewriter(final PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());

        // Shift + right-click ON the typewriter → open owner GUI
        if (com.playerlink.compat.TypewriterCompat.isTypewriter(be)
                && event.getEntity().isShiftKeyDown()) {
            PacketDistributor.sendToServer(new RequestTypewriterWhitelistPacket(event.getPos()));
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        // Right-click a Redstone Link while the player's selected hotbar item
        // is a Typewriter-linked item → not applicable client side; handled
        // via the normal typewriter interaction on the block itself.
        // Instead: right-click any Redstone Link while sneaking near a typewriter
        // is handled by the server via CopyLinkFrequencyPacket with typewriterPos set.
        // We detect this client side: if the player is holding nothing special but
        // a typewriter exists at the last-right-clicked typewriter position, copy.
        // (This interaction path is intentionally left to the server handler since
        // we cannot know the typewriter position from the client without extra state.)
    }
}
