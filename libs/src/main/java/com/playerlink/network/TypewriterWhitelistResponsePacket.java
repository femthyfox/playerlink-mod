package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record TypewriterWhitelistResponsePacket(
        BlockPos blockPos,
        int keyNum,
        Optional<UUID> currentOwner,
        List<WhitelistResponsePacket.Entry> entries
) implements CustomPacketPayload {

    public static final Type<TypewriterWhitelistResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "typewriter_whitelist_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TypewriterWhitelistResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TypewriterWhitelistResponsePacket::blockPos,
                    ByteBufCodecs.VAR_INT, TypewriterWhitelistResponsePacket::keyNum,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC), TypewriterWhitelistResponsePacket::currentOwner,
                    WhitelistResponsePacket.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), TypewriterWhitelistResponsePacket::entries,
                    TypewriterWhitelistResponsePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
