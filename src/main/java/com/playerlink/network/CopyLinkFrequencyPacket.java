package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client → server when a player right-clicks a Redstone Link block
 * while holding a Link Controller or standing near a Linked Typewriter.
 *
 * The server reads the player frequency from the clicked Redstone Link
 * and copies it onto:
 *   • the controller slot (if heldItem == controller, slotIndex >= 0)
 *   • the typewriter at typewriterPos (if typewriterPos != BlockPos.ZERO)
 */
public record CopyLinkFrequencyPacket(BlockPos linkPos,
                                       int controllerSlot,
                                       BlockPos typewriterPos)
        implements CustomPacketPayload {

    public static final Type<CopyLinkFrequencyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "copy_link_frequency"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CopyLinkFrequencyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CopyLinkFrequencyPacket::linkPos,
                    net.minecraft.network.codec.ByteBufCodecs.INT, CopyLinkFrequencyPacket::controllerSlot,
                    BlockPos.STREAM_CODEC, CopyLinkFrequencyPacket::typewriterPos,
                    CopyLinkFrequencyPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
