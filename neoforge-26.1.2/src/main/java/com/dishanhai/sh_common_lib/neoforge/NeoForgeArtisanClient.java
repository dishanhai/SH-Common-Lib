package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.client.beacon.BeaconWorldRenderer;
import net.minecraft.resources.Identifier;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanShortcut;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanAutoClick;
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

    static void registerBeaconMarkers() {
        NeoForge.EVENT_BUS.addListener(BeaconWorldRenderer::extract);
        NeoForge.EVENT_BUS.addListener(BeaconWorldRenderer::submit);
    }

    static void registerQuickConfig(IEventBus modBus) {
        var key = new KeyMapping("key.sh_common_lib.cosmic_artisan", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, NeoForgeKeyMappings.CATEGORY);
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        var shortcut = new CosmicArtisanShortcut(key);
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            if (event.isUseItem() && BeaconClient.warpAimed(Minecraft.getInstance())) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
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
