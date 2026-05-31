package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Adding "BlockPos blockPos" inside the brackets tells the game this packet carries coordinates!
public record RequestWhitelistPacket(BlockPos blockPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestWhitelistPacket> TYPE = 
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "request_whitelist"));

    // This updated codec tells the network how to read and write the BlockPos data
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWhitelistPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> BlockPos.STREAM_CODEC.encode(buf, packet.blockPos()), 
            buf -> new RequestWhitelistPacket(BlockPos.STREAM_CODEC.decode(buf)) 
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}