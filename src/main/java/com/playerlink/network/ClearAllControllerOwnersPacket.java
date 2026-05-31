package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClearAllControllerOwnersPacket() implements CustomPacketPayload {

    public static final ClearAllControllerOwnersPacket INSTANCE = new ClearAllControllerOwnersPacket();

    public static final Type<ClearAllControllerOwnersPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "clear_all_controller_owners"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearAllControllerOwnersPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}