package com.playerlink.server;

import com.mojang.brigadier.CommandDispatcher;
import com.playerlink.PlayerLinkMod;
import com.playerlink.api.IOwnedLink;
import com.playerlink.util.ControllerBindContext;
import com.playerlink.util.SlotMath;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

public final class ServerEvents {

    private ServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("playerlink")
                .then(Commands.literal("owner")
                        .executes(ctx -> {
                            // Show current owner of looked-at link
                            CommandSourceStack src = ctx.getSource();
                            if (!(src.getEntity() instanceof ServerPlayer sp)) {
                                src.sendFailure(Component.literal("Must be run by a player"));
                                return 0;
                            }
                            BlockPos pos = rayTraceLink(sp);
                            if (pos == null) {
                                src.sendFailure(Component.translatable("playerlink.message.no_link"));
                                return 0;
                            }
                            BlockEntity be = sp.level().getBlockEntity(pos);
                            if (!(be instanceof RedstoneLinkBlockEntity)) {
                                src.sendFailure(Component.translatable("playerlink.message.no_link"));
                                return 0;
                            }
                            java.util.UUID owner = ((IOwnedLink) be).playerlink$getOwner();
                            String name = owner == null ? "(none)" : sp.getServer().getProfileCache()
                                    .get(owner).map(p -> p.getName()).orElse(owner.toString());
                            src.sendSuccess(() -> Component.translatable(
                                    owner == null
                                            ? "playerlink.gui.select_owner.current.none"
                                            : "playerlink.gui.select_owner.current", name), false);
                            return 1;
                        })
                        .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    if (!(src.getEntity() instanceof ServerPlayer sp)) return 0;
                                    BlockPos pos = rayTraceLink(sp);
                                    if (pos == null) {
                                        src.sendFailure(Component.translatable("playerlink.message.no_link"));
                                        return 0;
                                    }
                                    BlockEntity be = sp.level().getBlockEntity(pos);
                                    if (!(be instanceof RedstoneLinkBlockEntity)) {
                                        src.sendFailure(Component.translatable("playerlink.message.no_link"));
                                        return 0;
                                    }
                                    ((IOwnedLink) be).playerlink$setOwner(null);
                                    src.sendSuccess(() -> Component.translatable("playerlink.message.owner_cleared"), false);
                                    return 1;
                                }))));
        PlayerLinkMod.LOGGER.info("[PlayerLink] /playerlink command registered.");
    }

    /** Ray-traces from the player's eyes to find a Redstone Link block. */
    public static BlockPos rayTraceLink(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F);
        double reach = 6.0;
        Vec3 end = eye.add(look.scale(reach));
        BlockHitResult hit = sp.level().clip(new ClipContext(
                eye, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                sp));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockEntity be = sp.level().getBlockEntity(hit.getBlockPos());
        if (be instanceof RedstoneLinkBlockEntity) return hit.getBlockPos();
        return null;
    }

    /**
     * Server-side mirror of ClientEvents#onUseBlock. The client cancels its own
     * event, but vanilla still ships a ServerboundUseItemOnPacket; the server
     * then fires its own RightClickBlock event, and if we don't cancel it here
     * too, BlockItem#useOn runs and places whatever block is in hand.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlockServer(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getEntity().isShiftKeyDown()) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof RedstoneLinkBlockEntity)) return;

        // Never intercept when the player holds a bound item — Create needs the
        // right-click to run its own bind mechanic (red outline → press button).
        net.minecraft.world.item.ItemStack held =
                event.getEntity().getItemInHand(event.getHand());
        if (isBoundItem(held)) {
            // Player is starting a bind — capture the link's owner now so
            // LinkedControllerServerHandlerMixin can auto-copy it to the slot.
            if (be instanceof IOwnedLink owned) {
                UUID linkOwner = owned.playerlink$getOwner();
                if (linkOwner != null) {
                    ControllerBindContext.set(event.getEntity().getUUID(), linkOwner);
                }
            }
            return;
        }

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(RedstoneLinkBlock.FACING);
        Vec3 hitVec = event.getHitVec().getLocation();
        if (!SlotMath.isFaceSlotHit(event.getPos(), facing, hitVec)) return;

        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Returns true for any item whose right-click-on-link must reach Create unmodified. */
    public static boolean isBoundItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof com.simibubi.create.content.redstone.link.controller.LinkedControllerItem)
            return true;
        // Typewriter item check via class name so we don't need the Simulated JAR
        String cls = stack.getItem().getClass().getName();
        return cls.contains("LinkedTypewriter") || cls.contains("Typewriter");
    }
}
