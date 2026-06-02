package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * Sent client → server to assign (or clear) the owner UUID on a Linked Typewriter.
 */
public record SetTypewriterOwnerPacket(BlockPos blockPos,
                                       Optional<UUID> newOwner) implements CustomPacketPayload {

    public static final Type<SetTypewriterOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "set_typewriter_owner"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTypewriterOwnerPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetTypewriterOwnerPacket::blockPos,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC), SetTypewriterOwnerPacket::newOwner,
                    SetTypewriterOwnerPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
