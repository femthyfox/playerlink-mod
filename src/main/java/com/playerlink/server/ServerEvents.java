package com.playerlink.server;

import com.mojang.brigadier.CommandDispatcher;
import com.playerlink.PlayerLinkMod;
import com.playerlink.api.PlayerLinkApi;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
                            java.util.UUID owner = PlayerLinkApi.readBlockOwner(be);
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
                                    PlayerLinkApi.writeBlockOwner(be, null);
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
}
