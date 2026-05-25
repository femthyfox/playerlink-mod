package com.playerlink.client;

import com.playerlink.network.WhitelistResponsePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void handleWhitelistResponse(final WhitelistResponsePacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new PlayerSelectScreen(
                    pkt.blockPos(),
                    pkt.currentOwner().orElse(null),
                    pkt.entries()
            ));
        });
    }
}