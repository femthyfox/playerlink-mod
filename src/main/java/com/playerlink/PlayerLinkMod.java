package com.playerlink;

import com.playerlink.network.PlayerLinkNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(PlayerLinkMod.MODID)
public class PlayerLinkMod {

    public static final String MODID = "playerlink";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PlayerLinkMod(IEventBus modBus) {
        modBus.addListener(this::commonSetup);

        // Server-side event bus listeners (commands, etc.)
        NeoForge.EVENT_BUS.register(com.playerlink.server.ServerEvents.class);

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modBus.addListener(this::clientSetup);
            NeoForge.EVENT_BUS.register(com.playerlink.client.ClientEvents.class);
            modBus.addListener(com.playerlink.client.ClientEvents::registerKeyMappings);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[PlayerLink] Common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[PlayerLink] Client setup complete.");
    }
}
