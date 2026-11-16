// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.dishanhai.sh_common_lib.internal.beacon.BeaconPackets;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeacon;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeaconBlock;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.ToIntFunction;

public final class BeaconClient {
    @FunctionalInterface
    public interface Transport {
        boolean send(Connection connection, BeaconPackets.Request request);
    }

    private static Transport transport;
    private static Connection connection;
    private static Object level;
    private static long nextRequest;
    private static ToIntFunction<ItemStack> readFlags = stack -> 0;
    private static List<CosmicBeacon.View> directory = List.of();

    private BeaconClient() {}

    public static void tick(Minecraft client, Transport sender, ToIntFunction<ItemStack> flags) {
        transport = sender;
        readFlags = flags;
        var listener = client.getConnection();
        var connected = listener == null ? null : listener.getConnection();
        if (connection != connected || level != client.level) {
            connection = connected;
            level = client.level;
            directory = List.of();
            BeaconWorldMarkers.reset();
            nextRequest = 0;
        }
        long now = System.nanoTime() / 1_000_000L;
        if ((showHud(client) || client.screen instanceof BeaconDestinationScreen) && now >= nextRequest) {
            nextRequest = now + 1500;
            send(new BeaconPackets.DirectoryRequest());
        }
    }

    private static ItemStack heldTool(Minecraft client) {
        var player = client.player;
        if (player == null || player.isSpectator()) return ItemStack.EMPTY;
        var main = player.getMainHandItem();
        if (CosmicArtisanModes.isActive(main)) return main;
        var off = player.getOffhandItem();
        return CosmicArtisanModes.isActive(off) ? off : ItemStack.EMPTY;
    }

    public static void receive(Connection source, BeaconPackets.Reply reply) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || client.getConnection().getConnection() != source || client.player == null) return;
        if (reply instanceof BeaconPackets.Editor editor) {
            if (editor.beacon().point().dimension().equals(client.player.level().dimension()))
                client.setScreen(new BeaconEditorScreen(editor));
        } else if (reply instanceof BeaconPackets.Directory entries) {
            directory = entries.entries();
        }
    }

    static boolean send(BeaconPackets.Request request) {
        return transport != null && connection != null && transport.send(connection, request);
    }

    public static void openDestinations(Screen parent) {
        Minecraft.getInstance().setScreen(new BeaconDestinationScreen(parent));
        if (send(new BeaconPackets.DirectoryRequest())) nextRequest = System.nanoTime() / 1_000_000L + 1500;
    }

    static List<CosmicBeacon.View> directory() { return directory; }

    static boolean showHud(Minecraft client) {
        if (level == null || level != client.level) return false;
        var tool = heldTool(client);
        return !tool.isEmpty() && Mode.BEACON_HUD.enabled(readFlags.applyAsInt(tool));
    }

    static boolean canAim(Minecraft client) {
        return showHud(client) && client.screen == null && !client.options.hideGui
                && client.getCameraEntity() == client.player && client.gameRenderer.getMainCamera().isInitialized();
    }

    static Component warpHint(Minecraft client) {
        return canWarp(client) ? text("world_warp_hint", client.options.keyUse.getTranslatedKeyMessage()) : null;
    }

    public static boolean warpAimed(Minecraft client) {
        if (!canWarp(client) || !canAim(client)) return false;
        var beacon = BeaconWorldMarkers.focusedBeacon();
        return beacon != null && directory.contains(beacon) && send(new BeaconPackets.Warp(beacon.point(), beacon.id()));
    }

    private static boolean canWarp(Minecraft client) {
        if (client.level == null || client.player == null || client.player.isShiftKeyDown()) return false;
        if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && client.level.getBlockState(hit.getBlockPos()).getBlock() instanceof CosmicBeaconBlock) return false;
        var tool = heldTool(client);
        return !tool.isEmpty() && Mode.TELEPORT.enabled(readFlags.applyAsInt(tool));
    }

    static Component name(CosmicBeacon.View beacon) {
        return beacon.name().isEmpty() ? Component.translatable("block.sh_common_lib.cosmic_beacon")
                : Component.literal(beacon.name());
    }

    static Component destination(CosmicBeacon.View beacon) {
        var point = beacon.point();
        var player = Minecraft.getInstance().player;
        if (player != null && point.dimension().equals(player.level().dimension())) {
            long blocks = Math.round(Vec3.atCenterOf(point.pos()).distanceTo(player.position()));
            return text("distance", blocks);
        }
        var dimension = point.dimension().location();
        return Component.translatableWithFallback("dimension." + dimension.getNamespace() + "." + dimension.getPath(),
                dimension.toString());
    }

    static Component text(String key, Object... args) {
        return Component.translatable("screen.sh_common_lib.cosmic_beacon." + key, args);
    }
}
