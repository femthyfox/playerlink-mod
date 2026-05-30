package com.playerlink.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playerlink.PlayerLinkMod;
import com.playerlink.network.RequestWhitelistPacket;
import com.playerlink.util.SlotMath;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
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

    /**
     * Each tick: figure out whether the player's crosshair is on a face slot
     * of a redstone link, store that on {@link LinkFaceRenderer#HOVERED_FACE_SLOT_POS}
     * so the renderer can show a highlight, and let the K-key open the GUI.
     */
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

    /**
     * Draw the "Player Frequency / click to open" hint on the HUD when the
     * player is hovering over the face slot. Styled to mirror Create's
     * value-box hover labels.
     */
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

    /**
     * Open the GUI when the player right-clicks the face slot. Held item
     * doesn't matter — we cancel the event so it doesn't fall through to
     * Create's frequency-setting logic.
     *
     * High priority so we run before Create's LinkHandler.
     */
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
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
