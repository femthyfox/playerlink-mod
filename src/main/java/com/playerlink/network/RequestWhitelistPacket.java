package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the player opens the Set Owner screen for
 * a specific Redstone Link block. Server responds with a
 * {@link WhitelistResponsePacket} containing the same blockPos so the
 * client knows which BE to operate on.
 */
public record RequestWhitelistPacket(BlockPos blockPos) implements CustomPacketPayload {

    public static final Type<RequestWhitelistPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "request_whitelist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWhitelistPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestWhitelistPacket::blockPos,
                    RequestWhitelistPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
