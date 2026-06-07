package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestTypewriterWhitelistPacket(BlockPos blockPos, int keyNum) implements CustomPacketPayload {

    public static final Type<RequestTypewriterWhitelistPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "request_typewriter_whitelist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTypewriterWhitelistPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestTypewriterWhitelistPacket::blockPos,
                    ByteBufCodecs.VAR_INT, RequestTypewriterWhitelistPacket::keyNum,
                    RequestTypewriterWhitelistPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
