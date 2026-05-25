package com.playerlink;

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
        LOGGER.info("[PlayerLink] === BUILD-MARKER-2026-05-25-V2 === Constructor starting");

        modBus.addListener(this::commonSetup);

        LOGGER.info("[PlayerLink] Constructor: registered commonSetup listener");

        NeoForge.EVENT_BUS.register(com.playerlink.server.ServerEvents.class);

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modBus.addListener(this::clientSetup);
            NeoForge.EVENT_BUS.register(com.playerlink.client.ClientEvents.class);
            modBus.addListener(com.playerlink.client.ClientEvents::registerKeyMappings);
        }

        LOGGER.info("[PlayerLink] === Constructor finished ===");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[PlayerLink] Common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[PlayerLink] Client setup complete.");
    }
}