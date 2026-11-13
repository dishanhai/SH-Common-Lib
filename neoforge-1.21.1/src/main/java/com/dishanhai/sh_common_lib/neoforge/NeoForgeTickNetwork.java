// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.internal.performance.ClientTickMonitor;
import com.dishanhai.sh_common_lib.internal.performance.ServerTickSample;
import com.dishanhai.sh_common_lib.internal.performance.ClientWeatherMonitor;
import com.dishanhai.sh_common_lib.internal.performance.WeatherForecast;
import com.dishanhai.sh_common_lib.internal.performance.Ae2Snapshot;
import com.dishanhai.sh_common_lib.internal.performance.ClientAe2Monitor;
import com.dishanhai.sh_common_lib.internal.performance.InventoryHudSnapshot;
import com.dishanhai.sh_common_lib.internal.performance.ClientInventoryHudMonitor;
import com.dishanhai.sh_common_lib.internal.performance.EcoMachineSnapshot;
import com.dishanhai.sh_common_lib.internal.performance.ClientEcoMachineMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class NeoForgeTickNetwork {
    private NeoForgeTickNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(EcoMachineRequest.TYPE, EcoMachineRequest.CODEC, (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !ecoMachineSupported(context.connection())
                    || request.mask() < 1 || request.mask() > 15 || !EcoMachineSnapshot.allow(player)) return;
            String dimension = player.level().dimension().location().toString();
            EcoMachineSnapshot snapshot = new EcoMachineSnapshot(dimension, request.mask(), EcoMachineSnapshot.NOT_INSTALLED, false, java.util.List.of());
            if (net.neoforged.fml.ModList.get().isLoaded("neoecoae") && net.neoforged.fml.ModList.get().isLoaded("ae2")) {
                try { snapshot = NeoForgeEcoMachines.sample(player, dimension, request.mask()); }
                catch (RuntimeException | LinkageError ignored) {
                    snapshot = new EcoMachineSnapshot(dimension, request.mask(), EcoMachineSnapshot.ERROR, false, java.util.List.of());
                }
            }
            context.reply(new EcoMachineReply(snapshot));
        });
        registrar.playToClient(EcoMachineReply.TYPE, EcoMachineReply.CODEC,
                (reply, context) -> ClientEcoMachineMonitor.INSTANCE.accept(context.connection(), reply.snapshot()));
        registrar.playToServer(InventoryRequest.TYPE, InventoryRequest.CODEC, (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !inventorySupported(context.connection())
                    || request.flags() < 1 || request.flags() > 3 || !InventoryHudSnapshot.allow(player)) return;
            var sources = java.util.Map.<com.dishanhai.sh_common_lib.api.ulk.InventorySource, InventoryHudSnapshot.Source>of();
            boolean scanned = false;
            if ((request.flags() & 1) != 0) {
                try { sources = InventoryHudSnapshot.inspect(player); scanned = true; }
                catch (RuntimeException | LinkageError ignored) { }
            }
            int state = InventoryHudSnapshot.ECO_NOT_INSTALLED, nodes = 0, active = 0;
            boolean truncated = false;
            if ((request.flags() & 2) != 0 && net.neoforged.fml.ModList.get().isLoaded("neoecoae")
                    && net.neoforged.fml.ModList.get().isLoaded("ae2")) {
                try {
                    var result = NeoForgeEcoHud.sample(player);
                    state = result.state(); nodes = result.nodes(); active = result.active(); truncated = result.truncated();
                } catch (RuntimeException | LinkageError ignored) { state = InventoryHudSnapshot.ECO_ERROR; }
            }
            context.reply(new InventoryReply(new InventoryHudSnapshot(player.level().dimension().location().toString(),
                    request.flags(), scanned, sources, state, nodes, active, truncated)));
        });
        registrar.playToClient(InventoryReply.TYPE, InventoryReply.CODEC,
                (reply, context) -> ClientInventoryHudMonitor.INSTANCE.accept(context.connection(), reply.snapshot()));
        registrar.playToServer(Ae2Request.TYPE, StreamCodec.unit(Ae2Request.INSTANCE), (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !ae2Supported(context.connection()) || !Ae2Snapshot.allow(player)) return;
            String dimension = player.level().dimension().location().toString();
            Ae2Snapshot snapshot = Ae2Snapshot.empty(dimension, Ae2Snapshot.NO_MOD);
            if (net.neoforged.fml.ModList.get().isLoaded("ae2")) {
                try { snapshot = NeoForgeAe2Hud.sample(player, dimension); }
                catch (RuntimeException | LinkageError ignored) { snapshot = Ae2Snapshot.empty(dimension, Ae2Snapshot.ERROR); }
            }
            context.reply(new Ae2Reply(snapshot));
        });
        registrar.playToClient(Ae2Reply.TYPE, Ae2Reply.CODEC,
                (reply, context) -> ClientAe2Monitor.INSTANCE.accept(context.connection(), reply.snapshot()));
        registrar.playToServer(WeatherRequest.TYPE, StreamCodec.unit(WeatherRequest.INSTANCE), (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !WeatherForecast.allow(player)) return;
            var level = player.serverLevel();
            var data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
            context.reply(new WeatherReply(WeatherForecast.of(level.dimension().location().toString(),
                    level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE),
                    data.getClearWeatherTime(), data.getRainTime(), data.getThunderTime(),
                    data.isRaining(), data.isThundering())));
        });
        registrar.playToClient(WeatherReply.TYPE, WeatherReply.CODEC,
                (reply, context) -> ClientWeatherMonitor.INSTANCE.accept(context.connection(), reply.forecast()));
        registrar.playToServer(Request.TYPE, StreamCodec.unit(Request.INSTANCE), (request, context) -> {
            if (context.player() instanceof ServerPlayer player && supported(context.connection())) {
                ServerTickSample.reply(player, server -> server.getAverageTickTimeNanos() / 1_000_000.0,
                        sample -> context.reply(new Reply(sample)));
            }
        });
        registrar.playToClient(Reply.TYPE, Reply.CODEC,
                (reply, context) -> ClientTickMonitor.INSTANCE.accept(context.connection(), reply.sample()));
    }

    static void request(Connection connection) {
        if (supported(connection)) connection.send(new ServerboundCustomPayloadPacket(Request.INSTANCE));
    }

    static void requestWeather(Connection connection) {
        if (connection.isConnected()
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, WeatherRequest.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, WeatherReply.TYPE.id()))
            connection.send(new ServerboundCustomPayloadPacket(WeatherRequest.INSTANCE));
    }

    static void requestAe2(Connection connection) {
        if (ae2Supported(connection)) connection.send(new ServerboundCustomPayloadPacket(Ae2Request.INSTANCE));
    }
    static void requestInventoryHud(Connection connection, int flags) {
        if (inventorySupported(connection)) connection.send(new ServerboundCustomPayloadPacket(new InventoryRequest(flags)));
    }
    static void requestEcoMachines(Connection connection, int mask) {
        if (ecoMachineSupported(connection)) connection.send(new ServerboundCustomPayloadPacket(new EcoMachineRequest(mask)));
    }
    private static boolean ecoMachineSupported(Connection connection) {
        return connection.isConnected()
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, EcoMachineRequest.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, EcoMachineReply.TYPE.id());
    }
    private record EcoMachineRequest(int mask) implements CustomPacketPayload {
        private static final Type<EcoMachineRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "eco_machines_v1/request"));
        private static final StreamCodec<FriendlyByteBuf, EcoMachineRequest> CODEC = StreamCodec.of(
                (buf, request) -> buf.writeVarInt(request.mask()), buf -> new EcoMachineRequest(buf.readVarInt()));
        @Override public Type<EcoMachineRequest> type() { return TYPE; }
    }
    private record EcoMachineReply(EcoMachineSnapshot snapshot) implements CustomPacketPayload {
        private static final Type<EcoMachineReply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "eco_machines_v1/reply"));
        private static final StreamCodec<FriendlyByteBuf, EcoMachineReply> CODEC = StreamCodec.of(
                (buf, reply) -> reply.snapshot().encode(buf), buf -> new EcoMachineReply(EcoMachineSnapshot.decode(buf)));
        @Override public Type<EcoMachineReply> type() { return TYPE; }
    }
    private static boolean inventorySupported(Connection connection) {
        return connection.isConnected()
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, InventoryRequest.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, InventoryReply.TYPE.id());
    }
    private record InventoryRequest(int flags) implements CustomPacketPayload {
        private static final Type<InventoryRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "inventory_hud/request"));
        private static final StreamCodec<FriendlyByteBuf, InventoryRequest> CODEC = StreamCodec.of(
                (buf, request) -> buf.writeVarInt(request.flags()), buf -> new InventoryRequest(buf.readVarInt()));
        @Override public Type<InventoryRequest> type() { return TYPE; }
    }
    private record InventoryReply(InventoryHudSnapshot snapshot) implements CustomPacketPayload {
        private static final Type<InventoryReply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "inventory_hud/reply"));
        private static final StreamCodec<FriendlyByteBuf, InventoryReply> CODEC = StreamCodec.of(
                (buf, reply) -> reply.snapshot().encode(buf), buf -> new InventoryReply(InventoryHudSnapshot.decode(buf)));
        @Override public Type<InventoryReply> type() { return TYPE; }
    }

    private static boolean ae2Supported(Connection connection) {
        return connection.isConnected()
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Ae2Request.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Ae2Reply.TYPE.id());
    }

    private static boolean supported(Connection connection) {
        return connection.isConnected() && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Request.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Reply.TYPE.id());
    }

    private enum Ae2Request implements CustomPacketPayload {
        INSTANCE;
        private static final Type<Ae2Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShCommonLib.MOD_ID, "ae2_hud/request"));
        @Override public Type<Ae2Request> type() { return TYPE; }
    }

    private record Ae2Reply(Ae2Snapshot snapshot) implements CustomPacketPayload {
        private static final Type<Ae2Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShCommonLib.MOD_ID, "ae2_hud/reply"));
        private static final StreamCodec<FriendlyByteBuf, Ae2Reply> CODEC = StreamCodec.of(
                (buffer, reply) -> reply.snapshot.encode(buffer), buffer -> new Ae2Reply(Ae2Snapshot.decode(buffer)));
        @Override public Type<Ae2Reply> type() { return TYPE; }
    }

    private enum WeatherRequest implements CustomPacketPayload {
        INSTANCE;
        private static final Type<WeatherRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShCommonLib.MOD_ID, "weather_forecast/request"));
        @Override public Type<WeatherRequest> type() { return TYPE; }
    }

    private record WeatherReply(WeatherForecast forecast) implements CustomPacketPayload {
        private static final Type<WeatherReply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShCommonLib.MOD_ID, "weather_forecast/reply"));
        private static final StreamCodec<FriendlyByteBuf, WeatherReply> CODEC = StreamCodec.of(
                (buffer, reply) -> reply.forecast.encode(buffer), buffer -> new WeatherReply(WeatherForecast.decode(buffer)));
        @Override public Type<WeatherReply> type() { return TYPE; }
    }

    private enum Request implements CustomPacketPayload {
        INSTANCE;
        private static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShCommonLib.MOD_ID, "server_ticks/request"));

        @Override public Type<Request> type() { return TYPE; }
    }

    private record Reply(ServerTickSample sample) implements CustomPacketPayload {
        private static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShCommonLib.MOD_ID, "server_ticks/reply"));
        private static final StreamCodec<FriendlyByteBuf, Reply> CODEC = StreamCodec.of(
                (buffer, reply) -> reply.sample.encode(buffer), buffer -> new Reply(ServerTickSample.decode(buffer)));

        @Override public Type<Reply> type() { return TYPE; }
    }
}
