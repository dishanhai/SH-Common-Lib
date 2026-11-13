// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.internal.performance.ClientTickMonitor;
import com.dishanhai.sh_common_lib.internal.performance.ServerTickSample;
import com.dishanhai.sh_common_lib.internal.performance.ClientWeatherMonitor;
import com.dishanhai.sh_common_lib.internal.performance.WeatherForecast;
import com.dishanhai.sh_common_lib.internal.performance.Ae2Snapshot;
import com.dishanhai.sh_common_lib.internal.performance.ClientAe2Monitor;
import com.dishanhai.sh_common_lib.internal.performance.InventoryHudSnapshot;
import com.dishanhai.sh_common_lib.internal.performance.ClientInventoryHudMonitor;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class ForgeTickNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ShCommonLib.MOD_ID, "server_ticks"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    private static final SimpleChannel WEATHER = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ShCommonLib.MOD_ID, "weather_forecast"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    private static final SimpleChannel AE2 = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ShCommonLib.MOD_ID, "ae2_hud"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    private static final SimpleChannel INVENTORY_HUD = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ShCommonLib.MOD_ID, "inventory_hud"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private ForgeTickNetwork() {}

    static void register() {
        INVENTORY_HUD.messageBuilder(InventoryRequest.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((request, buffer) -> buffer.writeVarInt(request.flags()))
                .decoder(buffer -> new InventoryRequest(buffer.readVarInt()))
                .consumerMainThread((request, supplied) -> {
                    var context = supplied.get();
                    var player = context.getSender();
                    if (player == null || request.flags() < 1 || request.flags() > 3 || !InventoryHudSnapshot.allow(player)) return;
                    var sources = java.util.Map.<com.dishanhai.sh_common_lib.api.ulk.InventorySource, InventoryHudSnapshot.Source>of();
                    boolean scanned = false;
                    if ((request.flags() & 1) != 0) {
                        try { sources = InventoryHudSnapshot.inspect(player); scanned = true; }
                        catch (RuntimeException | LinkageError ignored) { }
                    }
                    INVENTORY_HUD.sendTo(new InventoryHudSnapshot(player.level().dimension().location().toString(),
                            request.flags(), scanned, sources, InventoryHudSnapshot.ECO_NOT_INSTALLED, 0, 0, false),
                            context.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
                }).add();
        INVENTORY_HUD.messageBuilder(InventoryHudSnapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(InventoryHudSnapshot::encode).decoder(InventoryHudSnapshot::decode)
                .consumerMainThread((snapshot, context) -> ClientInventoryHudMonitor.INSTANCE.accept(
                        context.get().getNetworkManager(), snapshot)).add();
        AE2.messageBuilder(Ae2Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((request, buffer) -> {}).decoder(buffer -> Ae2Request.INSTANCE)
                .consumerMainThread((request, supplied) -> {
                    var context = supplied.get();
                    var player = context.getSender();
                    if (player == null || !Ae2Snapshot.allow(player)) return;
                    String dimension = player.level().dimension().location().toString();
                    Ae2Snapshot snapshot = Ae2Snapshot.empty(dimension, Ae2Snapshot.NO_MOD);
                    if (net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
                        try { snapshot = ForgeAe2Hud.sample(player, dimension); }
                        catch (RuntimeException | LinkageError ignored) { snapshot = Ae2Snapshot.empty(dimension, Ae2Snapshot.ERROR); }
                    }
                    AE2.sendTo(snapshot, context.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
                }).add();
        AE2.messageBuilder(Ae2Snapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Ae2Snapshot::encode).decoder(Ae2Snapshot::decode)
                .consumerMainThread((snapshot, context) -> ClientAe2Monitor.INSTANCE.accept(
                        context.get().getNetworkManager(), snapshot)).add();
        WEATHER.messageBuilder(WeatherRequest.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((request, buffer) -> {}).decoder(buffer -> WeatherRequest.INSTANCE)
                .consumerMainThread((request, supplied) -> {
                    var context = supplied.get();
                    var player = context.getSender();
                    if (player == null || !WeatherForecast.allow(player)) return;
                    var level = player.serverLevel();
                    var data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
                    var forecast = WeatherForecast.of(level.dimension().location().toString(),
                            level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE),
                            data.getClearWeatherTime(), data.getRainTime(), data.getThunderTime(),
                            data.isRaining(), data.isThundering());
                    WEATHER.sendTo(forecast, context.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
                }).add();
        WEATHER.messageBuilder(WeatherForecast.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WeatherForecast::encode).decoder(WeatherForecast::decode)
                .consumerMainThread((forecast, context) -> ClientWeatherMonitor.INSTANCE.accept(
                        context.get().getNetworkManager(), forecast)).add();
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((request, buffer) -> {})
                .decoder(buffer -> Request.INSTANCE)
                .consumerMainThread((request, supplied) -> {
                    var context = supplied.get();
                    var player = context.getSender();
                    var connection = context.getNetworkManager();
                    if (player != null && supported(connection)) {
                        ServerTickSample.reply(player, MinecraftServer::getAverageTickTime,
                                sample -> CHANNEL.sendTo(sample, connection, NetworkDirection.PLAY_TO_CLIENT));
                    }
                }).add();
        CHANNEL.messageBuilder(ServerTickSample.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerTickSample::encode)
                .decoder(ServerTickSample::decode)
                .consumerMainThread((sample, context) ->
                        ClientTickMonitor.INSTANCE.accept(context.get().getNetworkManager(), sample)).add();
    }

    static void request(Connection connection) {
        if (supported(connection)) CHANNEL.sendTo(Request.INSTANCE, connection, NetworkDirection.PLAY_TO_SERVER);
    }

    static void requestWeather(Connection connection) {
        if (connection.isConnected() && WEATHER.isRemotePresent(connection))
            WEATHER.sendTo(WeatherRequest.INSTANCE, connection, NetworkDirection.PLAY_TO_SERVER);
    }

    static void requestAe2(Connection connection) {
        if (connection.isConnected() && AE2.isRemotePresent(connection))
            AE2.sendTo(Ae2Request.INSTANCE, connection, NetworkDirection.PLAY_TO_SERVER);
    }
    static void requestInventoryHud(Connection connection, int flags) {
        if (connection.isConnected() && INVENTORY_HUD.isRemotePresent(connection))
            INVENTORY_HUD.sendTo(new InventoryRequest(flags), connection, NetworkDirection.PLAY_TO_SERVER);
    }

    private static boolean supported(Connection connection) {
        return connection.isConnected() && CHANNEL.isRemotePresent(connection);
    }

    private enum Request { INSTANCE }
    private enum WeatherRequest { INSTANCE }
    private enum Ae2Request { INSTANCE }
    private record InventoryRequest(int flags) {}
}
