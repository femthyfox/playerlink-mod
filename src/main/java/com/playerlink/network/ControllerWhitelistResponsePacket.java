package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ControllerWhitelistResponsePacket(int slotIndex,
                                                Optional<UUID> currentOwner,
                                                List<WhitelistResponsePacket.Entry> entries)
        implements CustomPacketPayload {

    public static final Type<ControllerWhitelistResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "controller_whitelist_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControllerWhitelistResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ControllerWhitelistResponsePacket::slotIndex,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC),
                    ControllerWhitelistResponsePacket::currentOwner,
                    WhitelistResponsePacket.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ControllerWhitelistResponsePacket::entries,
                    ControllerWhitelistResponsePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}