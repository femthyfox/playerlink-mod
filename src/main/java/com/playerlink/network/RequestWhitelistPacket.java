package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestWhitelistPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestWhitelistPacket> TYPE = 
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "request_whitelist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWhitelistPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {}, 
            buf -> new RequestWhitelistPacket() 
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}