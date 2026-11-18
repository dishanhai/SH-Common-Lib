package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.client.beacon.BeaconWorldRenderer;
import net.minecraft.resources.Identifier;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanShortcut;
import com.dishanhai.sh_common_lib.client.item.AccelerationClient;
import com.dishanhai.sh_common_lib.client.item.AccelerationInput;
import com.dishanhai.sh_common_lib.client.item.AccelerationWorldRenderer;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanAutoClick;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.world.InteractionHand;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.glfw.GLFW;
import com.dishanhai.sh_common_lib.client.item.ArtisanAppearance;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

final class NeoForgeArtisanClient {
    private NeoForgeArtisanClient() {}

    static void registerBeaconMarkers(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(BeaconWorldRenderer::extract);
        NeoForge.EVENT_BUS.addListener(AccelerationWorldRenderer::extract);
        NeoForge.EVENT_BUS.addListener(BeaconWorldRenderer::submit);
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerBelowAll(
                Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "beacon_markers"),
                (graphics, delta) -> BeaconWorldRenderer.renderHud(graphics)));
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerBelowAll(
                Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "acceleration_markers"),
                (graphics, delta) -> AccelerationWorldRenderer.renderHud(graphics)));
    }

    static void registerQuickConfig(IEventBus modBus) {
        var key = new KeyMapping("key.sh_common_lib.cosmic_artisan", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, NeoForgeKeyMappings.CATEGORY);
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        var accelerationKey = new KeyMapping("key.sh_common_lib.acceleration", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, NeoForgeKeyMappings.CATEGORY);
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(accelerationKey));
        var shortcut = new CosmicArtisanShortcut(key);
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            if (event.isUseItem() && AccelerationInput.mark(Minecraft.getInstance(),
                    GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)) {
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
                InputConstants.getKey(event.getKeyEvent()), event.getAction()));
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> shortcut.input(
                InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            var client = Minecraft.getInstance();
            BeaconClient.tick(client, NeoForgeBeaconNetwork::send, NeoForgeCosmicModes::flags);
            CosmicArtisanAutoClick.tick(client, hit -> client.gameMode.interact(client.player, hit.getEntity(),
                    hit, InteractionHand.MAIN_HAND));
            var listener = client.getConnection();
            var connection = listener == null ? null : listener.getConnection();
            AccelerationClient.tick(client, connection != null && NeoForgeAccelerationNetwork.supported(connection),
                    request -> NeoForgeAccelerationNetwork.send(connection, request), NeoForgeCosmicModes::flags,
                    mode -> NeoForgeCosmicModesNetwork.send(connection, mode.bit()));
            while (accelerationKey.consumeClick()) AccelerationClient.open(client);
            shortcut.tick(client, client.getWindow().handle(),
                    connection != null && NeoForgeCosmicModesNetwork.supported(connection),
                    stack -> CosmicArtisanAutoClick.flags(NeoForgeCosmicModes.flags(stack)), mode -> {
                        if (mode == Mode.AUTO_CLICK) CosmicArtisanAutoClick.toggle();
                        else NeoForgeCosmicModesNetwork.send(connection, mode.bit());
                    }, NeoForgeCosmicStorage::available, NeoForgeCosmicModes::bossPolicy,
                    policy -> NeoForgeCosmicModesNetwork.send(connection, policy.request()));
        });
    }

    static void registerModel(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, ArtisanAppearance.PROPERTY), ModeProperty.CODEC);
    }

    private record ModeProperty() implements RangeSelectItemModelProperty {
        private static final MapCodec<ModeProperty> CODEC = MapCodec.unit(new ModeProperty());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            return ArtisanAppearance.modelValue(stack, level, owner == null ? null : owner.asLivingEntity(),
                    (mode, block, context) -> NeoForgeArtisanActions.canUse(mode, block, context,
                            offhand -> offhand.has(DataComponents.BLOCKS_ATTACKS)));
        }

        @Override
        public MapCodec<ModeProperty> type() {
            return CODEC;
        }
    }
}
