package com.playerlink.client;

import com.playerlink.network.ControllerWhitelistResponsePacket;
import com.playerlink.network.WhitelistResponsePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void handleWhitelistResponse(final WhitelistResponsePacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(PlayerSelectScreen.forBlock(
                    pkt.blockPos(),
                    pkt.currentOwner().orElse(null),
                    pkt.entries()
            ));
        });
    }

    public static void handleControllerWhitelistResponse(final ControllerWhitelistResponsePacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(PlayerSelectScreen.forControllerSlot(
                    pkt.slotIndex(),
                    pkt.currentOwner().orElse(null),
                    pkt.entries(),
                    mc.screen
            ));
        });
    }
}
