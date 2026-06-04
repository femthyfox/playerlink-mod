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

/** Server → client: whitelist + current owner for a specific Typewriter key. */
public record TypewriterKeyWhitelistResponsePacket(BlockPos blockPos,
                                                    int glfwKey,
                                                    Optional<UUID> currentOwner,
                                                    List<WhitelistResponsePacket.Entry> entries)
        implements CustomPacketPayload {

    public static final Type<TypewriterKeyWhitelistResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    PlayerLinkMod.MODID, "typewriter_key_whitelist_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TypewriterKeyWhitelistResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TypewriterKeyWhitelistResponsePacket::blockPos,
                    ByteBufCodecs.INT, TypewriterKeyWhitelistResponsePacket::glfwKey,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC),
                    TypewriterKeyWhitelistResponsePacket::currentOwner,
                    WhitelistResponsePacket.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    TypewriterKeyWhitelistResponsePacket::entries,
                    TypewriterKeyWhitelistResponsePacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
