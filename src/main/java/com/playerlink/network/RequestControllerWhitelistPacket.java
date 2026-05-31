package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestControllerWhitelistPacket(int slotIndex) implements CustomPacketPayload {

    public static final Type<RequestControllerWhitelistPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "request_controller_whitelist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestControllerWhitelistPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RequestControllerWhitelistPacket::slotIndex,
                    RequestControllerWhitelistPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}