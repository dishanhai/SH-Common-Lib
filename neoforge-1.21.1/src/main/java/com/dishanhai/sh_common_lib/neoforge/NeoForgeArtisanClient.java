package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.client.beacon.BeaconWorldRenderer;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.resources.ResourceLocation;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanShortcut;
import com.dishanhai.sh_common_lib.client.item.AccelerationClient;
import com.dishanhai.sh_common_lib.client.item.PhoneScreen;
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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.glfw.GLFW;
import com.dishanhai.sh_common_lib.client.item.ArtisanAppearance;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Items;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

final class NeoForgeArtisanClient {
    private NeoForgeArtisanClient() {}

    static void registerBeaconMarkers() {
        var buffers = MultiBufferSource.immediate(new ByteBufferBuilder(4096));
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
            var level = Minecraft.getInstance().level;
            if (level != null) AccelerationWorldMarkers.capture(event.getCamera().getPosition(), event.getProjectionMatrix(),
                    event.getModelViewMatrix(), level.dimension().location().toString());
            BeaconWorldRenderer.render(event.getPoseStack(), event.getCamera(), event.getProjectionMatrix(),
                    event.getModelViewMatrix(),
                    event.getFrustum(), buffers,
                    (pose, target, time, height, color, radius, glowRadius) -> BeaconRenderer.renderBeaconBeam(
                            pose, target, BeaconRenderer.BEAM_LOCATION,
                            event.getPartialTick().getGameTimeDeltaPartialTick(false), 1.0F, time,
                            1, height, color, radius, glowRadius));
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Pre event) -> BeaconWorldRenderer.renderHud(event.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Pre event) -> AccelerationWorldRenderer.renderHud(event.getGuiGraphics()));
    }

    static void registerQuickConfig(IEventBus modBus) {
        var key = new KeyMapping("key.sh_common_lib.cosmic_artisan", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.sh_common_lib");
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        var accelerationKey = new KeyMapping("key.sh_common_lib.acceleration", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.sh_common_lib");
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(accelerationKey));
        var shortcut = new CosmicArtisanShortcut(key);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> PhoneScreen.afterFrame());
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            if (event.isUseItem() && PhoneScreen.openIfHeld(event.getHand())) {
                event.setSwingHand(false);
                event.setCanceled(true);
                return;
            }
            if (event.isUseItem() && AccelerationInput.mark(Minecraft.getInstance(),
                    net.minecraft.client.gui.screens.Screen.hasControlDown())) {
                event.setSwingHand(false); event.setCanceled(true); return;
            }
            if (event.isUseItem() && BeaconClient.warpAimed(Minecraft.getInstance())) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event) -> {
            if (AccelerationClient.scroll(Minecraft.getInstance(), event.getScrollDeltaY())) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((InputEvent.Key event) -> shortcut.input(
                InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction()));
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> shortcut.input(
                InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            var client = Minecraft.getInstance();
            BeaconClient.tick(client, NeoForgeBeaconNetwork::send, NeoForgeCosmicModes::flags);
            CosmicArtisanAutoClick.tick(client, hit -> client.gameMode.interact(client.player, hit.getEntity(),
                    InteractionHand.MAIN_HAND));
            var listener = client.getConnection();
            var connection = listener == null ? null : listener.getConnection();
            AccelerationClient.tick(client, connection != null && NeoForgeAccelerationNetwork.supported(connection),
                    request -> NeoForgeAccelerationNetwork.send(connection, request), NeoForgeCosmicModes::flags,
                    mode -> NeoForgeCosmicModesNetwork.send(connection, mode.bit()));
            while (accelerationKey.consumeClick()) AccelerationClient.open(client);
            shortcut.tick(client, client.getWindow().getWindow(),
                    connection != null && NeoForgeCosmicModesNetwork.supported(connection),
                    stack -> CosmicArtisanAutoClick.flags(NeoForgeCosmicModes.flags(stack)), mode -> {
                        if (mode == Mode.AUTO_CLICK) CosmicArtisanAutoClick.toggle();
                        else NeoForgeCosmicModesNetwork.send(connection, mode.bit());
                    }, NeoForgeCosmicStorage::available, NeoForgeCosmicModes::bossPolicy,
                    policy -> NeoForgeCosmicModesNetwork.send(connection, policy.request()),
                    () -> NeoForgeCosmicModesNetwork.send(connection, CosmicArtisanModes.EMPTY_TELEPORT_REQUEST));
        });
    }

    static void registerModel(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(NeoForgeArtisan.item(),
                ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, ArtisanAppearance.PROPERTY),
                (stack, level, holder, seed) -> ArtisanAppearance.modelValue(stack, level, holder,
                        (mode, block, context) -> NeoForgeArtisanActions.canUse(mode, block, context,
                                offhand -> offhand.is(Items.SHIELD)))));
    }
}
