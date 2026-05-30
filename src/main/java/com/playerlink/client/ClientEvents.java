package com.playerlink.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playerlink.PlayerLinkMod;
import com.playerlink.network.RequestWhitelistPacket;
import com.playerlink.util.SlotMath;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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

        if (!event.getItemStack().isEmpty()) return;

        PlayerLinkMod.LOGGER.info("[PlayerLink] Face slot clicked at {}, opening GUI", event.getPos());
        PacketDistributor.sendToServer(new RequestWhitelistPacket(event.getPos()));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}