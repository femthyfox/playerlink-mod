package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sent server -> client. Contains the list of whitelisted players
 * (name + UUID), the block position of the link being edited, and the
 * currently assigned owner UUID (if any).
 */
public record WhitelistResponsePacket(BlockPos blockPos,
                                      Optional<UUID> currentOwner,
                                      List<Entry> entries) implements CustomPacketPayload {

    public record Entry(UUID uuid, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        net.minecraft.core.UUIDUtil.STREAM_CODEC, Entry::uuid,
                        ByteBufCodecs.STRING_UTF8, Entry::name,
                        Entry::new
                );
    }

    public static final Type<WhitelistResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "whitelist_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WhitelistResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, WhitelistResponsePacket::blockPos,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC), WhitelistResponsePacket::currentOwner,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), WhitelistResponsePacket::entries,
                    WhitelistResponsePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
