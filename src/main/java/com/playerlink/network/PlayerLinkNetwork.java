package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestWhitelistPacket() implements CustomPacketPayload {

    // 1. This creates the unique ID for your network packet
    public static final CustomPacketPayload.Type<RequestWhitelistPacket> TYPE = 
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "request_whitelist"));

    // 2. This creates the reader/writer codec NeoForge needs to send the packet
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWhitelistPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {}, // This writes your packet to the network (currently empty)
            buf -> new RequestWhitelistPacket() // This reads your packet from the network
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}