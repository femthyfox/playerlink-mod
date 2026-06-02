package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client → server when the player opens the owner-select GUI
 * for a Linked Typewriter block.
 */
public record RequestTypewriterWhitelistPacket(BlockPos blockPos) implements CustomPacketPayload {

    public static final Type<RequestTypewriterWhitelistPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "request_typewriter_whitelist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTypewriterWhitelistPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.blockPos()),
                    buf -> new RequestTypewriterWhitelistPacket(BlockPos.STREAM_CODEC.decode(buf))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
