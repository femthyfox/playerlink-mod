package com.playerlink.client;
import com.mojang.blaze3d.platform.InputConstants;
import com.playerlink.PlayerLinkMod;
import com.playerlink.network.RequestWhitelistPacket;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    public static final KeyMapping OPEN_OWNER_GUI = new KeyMapping(
            "key.playerlink.open_owner_gui",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.playerlink");

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_OWNER_GUI);
    }

    /**
     * Alternative way to open the picker: sneak + right-click the link
     * with an EMPTY main hand. Doesn't conflict with Create's normal
     * slot-setting (which requires a held item).
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) return;

        BlockPos pos = event.getPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof RedstoneLinkBlockEntity)) return;

        PlayerLinkMod.LOGGER.info("[PlayerLink] Shift+RClick on link@{} - sending RequestWhitelist", pos);
        PacketDistributor.sendToServer(new RequestWhitelistPacket(pos));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        while (OPEN_OWNER_GUI.consumeClick()) {
            tryOpenOwnerGui(mc);
        }
    }

    private static void tryOpenOwnerGui(Minecraft mc) {
        HitResult hit = mc.hitResult;

        if (hit == null) {
            chat(mc, "[PlayerLink] DBG: hitResult is NULL");
            return;
        }
        chat(mc, "[PlayerLink] DBG: hit type = " + hit.getType());

        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            chat(mc, "[PlayerLink] DBG: not looking at a block (type=" + hit.getType() + ")");
            return;
        }

        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);

        if (be == null) {
            chat(mc, "[PlayerLink] DBG: no BlockEntity at " + pos + " (block is "
                    + mc.level.getBlockState(pos).getBlock().getClass().getSimpleName() + ")");
            return;
        }

        chat(mc, "[PlayerLink] DBG: BE class = " + be.getClass().getName());

        if (!(be instanceof RedstoneLinkBlockEntity)) {
            chat(mc, "[PlayerLink] DBG: BE is not RedstoneLinkBlockEntity (was "
                    + be.getClass().getSimpleName() + ")");
            return;
        }

        chat(mc, "[PlayerLink] OK - sending RequestWhitelistPacket...");
        PlayerLinkMod.LOGGER.info("[PlayerLink] K pressed on link@{} - sending packet", pos);
        PacketDistributor.sendToServer(new RequestWhitelistPacket(pos));
    }

    private static void chat(Minecraft mc, String msg) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg), false);
        PlayerLinkMod.LOGGER.info(msg);
    }
}