package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: open owner-select GUI for a specific Typewriter key. */
public record RequestTypewriterKeyWhitelistPacket(BlockPos blockPos,
                                                   int glfwKey)
        implements CustomPacketPayload {

    public static final Type<RequestTypewriterKeyWhitelistPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    PlayerLinkMod.MODID, "request_typewriter_key_whitelist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTypewriterKeyWhitelistPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestTypewriterKeyWhitelistPacket::blockPos,
                    ByteBufCodecs.INT, RequestTypewriterKeyWhitelistPacket::glfwKey,
                    RequestTypewriterKeyWhitelistPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
