package com.playerlink.client;

import com.playerlink.network.ControllerWhitelistResponsePacket;
import com.playerlink.network.TypewriterWhitelistResponsePacket;
import com.playerlink.network.TypewriterKeyWhitelistResponsePacket;
import com.playerlink.network.WhitelistResponsePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    /** Opens the owner-select GUI for a Redstone Link block. */
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

    /** Opens the owner-select GUI for a single Link Controller slot. */
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

    /** Opens the owner-select GUI for a Linked Typewriter block. */
    public static void handleTypewriterWhitelistResponse(final TypewriterWhitelistResponsePacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(PlayerSelectScreen.forTypewriter(
                    pkt.blockPos(),
                    pkt.currentOwner().orElse(null),
                    pkt.entries()
            ));
        });
    }

    /** Opens the owner-select GUI for a specific Linked Typewriter key. */
    public static void handleTypewriterKeyWhitelistResponse(
            final TypewriterKeyWhitelistResponsePacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(PlayerSelectScreen.forTypewriterKey(
                    pkt.blockPos(),
                    pkt.glfwKey(),
                    pkt.currentOwner().orElse(null),
                    pkt.entries()
            ));
        });
    }
}
