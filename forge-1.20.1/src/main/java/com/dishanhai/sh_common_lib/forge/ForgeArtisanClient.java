package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.client.beacon.BeaconWorldRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraft.resources.ResourceLocation;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanShortcut;
import com.dishanhai.sh_common_lib.client.item.AccelerationClient;
import com.dishanhai.sh_common_lib.client.item.AccelerationInput;
import com.dishanhai.sh_common_lib.client.item.AccelerationWorldMarkers;
import com.dishanhai.sh_common_lib.client.item.AccelerationWorldRenderer;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanAutoClick;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.world.InteractionHand;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;
import com.dishanhai.sh_common_lib.client.item.ArtisanAppearance;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class ForgeArtisanClient {
    private ForgeArtisanClient() {}

    static void registerBeaconMarkers() {
        var buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
            var level = Minecraft.getInstance().level;
            if (level != null) AccelerationWorldMarkers.capture(event.getCamera().getPosition(), event.getProjectionMatrix(),
                    event.getPoseStack().last().pose(), level.dimension().location().toString());
            BeaconWorldRenderer.render(event.getPoseStack(), event.getCamera(), event.getProjectionMatrix(),
                    event.getPoseStack().last().pose(),
                    event.getFrustum(), buffers,
                    (pose, target, time, height, color, radius, glowRadius) -> BeaconRenderer.renderBeaconBeam(
                            pose, target, BeaconRenderer.BEAM_LOCATION, event.getPartialTick(), 1.0F, time, 1,
                            height, new float[]{(color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                                (color & 255) / 255F}, radius, glowRadius));
        });
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Pre event) -> BeaconWorldRenderer.renderHud(event.getGuiGraphics()));
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Pre event) -> AccelerationWorldRenderer.renderHud(event.getGuiGraphics()));
    }

    static void registerQuickConfig(IEventBus modBus) {
        var key = new KeyMapping("key.sh_common_lib.cosmic_artisan", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.sh_common_lib");
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        var accelerationKey = new KeyMapping("key.sh_common_lib.acceleration", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.sh_common_lib");
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(accelerationKey));
        var shortcut = new CosmicArtisanShortcut(key);
        MinecraftForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            if (event.isUseItem() && AccelerationInput.mark(Minecraft.getInstance(),
                    net.minecraft.client.gui.screens.Screen.hasControlDown())) {
                event.setSwingHand(false); event.setCanceled(true); return;
            }
            if (event.isUseItem() && BeaconClient.warpAimed(Minecraft.getInstance())) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event) -> {
            if (AccelerationClient.scroll(Minecraft.getInstance(), event.getScrollDelta())) event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key event) -> shortcut.input(
                InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction()));
        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> shortcut.input(
                InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            var client = Minecraft.getInstance();
            BeaconClient.tick(client, ForgeBeaconNetwork::send, ForgeCosmicModes::flags);
            CosmicArtisanAutoClick.tick(client, hit -> client.gameMode.interact(client.player, hit.getEntity(),
                    InteractionHand.MAIN_HAND));
            var listener = client.getConnection();
            var connection = listener == null ? null : listener.getConnection();
            AccelerationClient.tick(client, connection != null && ForgeAccelerationNetwork.supported(connection),
                    request -> ForgeAccelerationNetwork.send(connection, request), ForgeCosmicModes::flags,
                    mode -> ForgeCosmicModesNetwork.send(connection, mode.bit()));
            while (accelerationKey.consumeClick()) AccelerationClient.open(client);
            shortcut.tick(client, client.getWindow().getWindow(),
                    connection != null && ForgeCosmicModesNetwork.supported(connection),
                    stack -> CosmicArtisanAutoClick.flags(ForgeCosmicModes.flags(stack)), mode -> {
                        if (mode == Mode.AUTO_CLICK) CosmicArtisanAutoClick.toggle();
                        else ForgeCosmicModesNetwork.send(connection, mode.bit());
                    }, ForgeCosmicStorage::available, ForgeCosmicModes::bossPolicy,
                    policy -> ForgeCosmicModesNetwork.send(connection, policy.request()),
                    () -> ForgeCosmicModesNetwork.send(connection, CosmicArtisanModes.EMPTY_TELEPORT_REQUEST));
        });
    }

    static void registerModel(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ForgeArtisan.item(),
                ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, ArtisanAppearance.PROPERTY),
                (stack, level, holder, seed) -> ArtisanAppearance.modelValue(stack, level, holder, ForgeArtisanClient::canUse)));
    }

    private static boolean canUse(ArtisanAppearance.Mode mode, BlockState block, UseOnContext context) {
        return switch (mode) {
            case AXE -> block.getToolModifiedState(context, ToolActions.AXE_STRIP, true) != null
                    || block.getToolModifiedState(context, ToolActions.AXE_SCRAPE, true) != null
                    || block.getToolModifiedState(context, ToolActions.AXE_WAX_OFF, true) != null;
            case SHOVEL -> context.getClickedFace() != Direction.DOWN
                    && ((context.getLevel().getBlockState(context.getClickedPos().above()).isAir()
                        && block.getToolModifiedState(context, ToolActions.SHOVEL_FLATTEN, true) != null)
                        || (block.getBlock() instanceof CampfireBlock && block.getValue(CampfireBlock.LIT)));
            case HOE -> block.getToolModifiedState(context, ToolActions.HOE_TILL, true) != null;
            default -> false;
        };
    }
}
