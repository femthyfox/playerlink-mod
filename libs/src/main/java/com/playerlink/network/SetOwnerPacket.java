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
 * Sent client -> server when the player picks an owner (or "clear")
 * inside the Set Owner screen.
 */
public record SetOwnerPacket(BlockPos blockPos, Optional<UUID> newOwner) implements CustomPacketPayload {

    public static final Type<SetOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "set_owner"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetOwnerPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetOwnerPacket::blockPos,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC), SetOwnerPacket::newOwner,
                    SetOwnerPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
