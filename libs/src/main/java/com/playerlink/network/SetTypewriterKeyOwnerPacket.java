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

public record SetTypewriterKeyOwnerPacket(BlockPos blockPos, int keyNum, Optional<UUID> newOwner)
        implements CustomPacketPayload {

    public static final Type<SetTypewriterKeyOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "set_typewriter_key_owner"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTypewriterKeyOwnerPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetTypewriterKeyOwnerPacket::blockPos,
                    ByteBufCodecs.VAR_INT, SetTypewriterKeyOwnerPacket::keyNum,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC), SetTypewriterKeyOwnerPacket::newOwner,
                    SetTypewriterKeyOwnerPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
