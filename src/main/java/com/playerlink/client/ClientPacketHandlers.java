package com.playerlink.client;

import com.playerlink.PlayerLinkMod;
import com.playerlink.network.WhitelistResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void handleWhitelistResponse(final WhitelistResponsePacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                PlayerLinkMod.LOGGER.info("[PlayerLink] Got WhitelistResponse: {} entries, currentOwner={}",
                        pkt.entries().size(), pkt.currentOwner().orElse(null));

                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("[PlayerLink] Got " + pkt.entries().size() + " players - opening picker..."),
                            false);
                }

                PlayerSelectScreen screen = new PlayerSelectScreen(
                        pkt.blockPos(),
                        pkt.currentOwner().orElse(null),
                        pkt.entries()
                );
                mc.setScreen(screen);
                PlayerLinkMod.LOGGER.info("[PlayerLink] PlayerSelectScreen set as active screen.");
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.error("[PlayerLink] handleWhitelistResponse crashed", t);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("[PlayerLink] ERROR opening GUI: " + t.getMessage()),
                            false);
                }
            }
        });
    }
}