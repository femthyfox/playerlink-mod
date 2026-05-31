package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

public record SetControllerSlotOwnerPacket(int slotIndex,
                                           Optional<UUID> newOwner) implements CustomPacketPayload {

    public static final Type<SetControllerSlotOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerLinkMod.MODID, "set_controller_slot_owner"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetControllerSlotOwnerPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetControllerSlotOwnerPacket::slotIndex,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC), SetControllerSlotOwnerPacket::newOwner,
                    SetControllerSlotOwnerPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}