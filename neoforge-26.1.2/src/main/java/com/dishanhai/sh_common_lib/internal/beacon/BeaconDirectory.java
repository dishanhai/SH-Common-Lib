// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.beacon;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class BeaconDirectory {
    private static final Identifier KEY = Identifier.fromNamespaceAndPath("sh_common_lib", "cosmic_beacons");
    private static final UUID SYSTEM = new UUID(0, 0);
    private static final Map<MinecraftServer, Map<GlobalPos, CosmicBeacon>> WORLDS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> REQUESTS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> WARPS = new WeakHashMap<>();

    private BeaconDirectory() {}

    static void placed(ServerLevel level, BlockPos pos, UUID owner) {
        var point = GlobalPos.of(level.dimension(), pos.immutable());
        update(level.getServer(), point, new CosmicBeacon(point, UUID.randomUUID(), owner, "", false, false, true));
    }

    static void removed(ServerLevel level, BlockPos pos) {
        update(level.getServer(), GlobalPos.of(level.dimension(), pos), null);
    }

    public static boolean isBeacon(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CosmicBeaconBlock;
    }

    static BeaconPackets.Editor editor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var point = GlobalPos.of(level.dimension(), pos);
        var beacon = records(level.getServer()).get(point);
        if (beacon == null) {
            placed(level, pos, SYSTEM);
            beacon = records(level.getServer()).get(point);
        }
        return new BeaconPackets.Editor(CosmicBeacon.View.of(beacon), editable(beacon, player, level),
                beacon.owner().equals(SYSTEM) || beacon.owner().equals(player.getUUID()) || operator(player));
    }

    public static boolean holding(ServerPlayer player) {
        return !player.isSpectator() && (CosmicArtisanModes.isActive(player.getMainHandItem())
                || CosmicArtisanModes.isActive(player.getOffhandItem()));
    }

    public static void receive(ServerPlayer player, BeaconPackets.Request request, Consumer<BeaconPackets.Reply> reply) {
        if (player.isSpectator()) return;
        var level = (ServerLevel) player.level();
        if (request instanceof BeaconPackets.Edit edit) {
            saveEdit(player, level, edit.beacon());
            return;
        }
        if (!holding(player)) return;
        if (request instanceof BeaconPackets.DirectoryRequest) {
            long now = level.getGameTime();
            if (now < REQUESTS.getOrDefault(player, Long.MIN_VALUE)) return;
            REQUESTS.put(player, now + 10);
            reply.accept(new BeaconPackets.Directory(records(level.getServer()).values().stream()
                    .filter(beacon -> visible(beacon, level))
                    .sorted(Comparator.comparingDouble(beacon -> beacon.point().dimension().equals(level.dimension())
                            ? Vec3.atCenterOf(beacon.point().pos()).distanceToSqr(player.position()) : Double.MAX_VALUE))
                    .limit(CosmicBeacon.DIRECTORY_LIMIT).map(CosmicBeacon.View::of).toList()));
        } else if (request instanceof BeaconPackets.Warp warp) {
            warp(player, level, warp);
        }
    }

    private static void saveEdit(ServerPlayer player, ServerLevel level, CosmicBeacon.View draft) {
        var point = draft.point();
        if (!point.dimension().equals(level.dimension()) || !level.hasChunkAt(point.pos())
                || Vec3.atCenterOf(point.pos()).distanceToSqr(player.getEyePosition()) > 36 || !isBeacon(level, point.pos())) return;
        var old = records(level.getServer()).get(point);
        if (old == null || !old.id().equals(draft.id()) || !editable(old, player, level)) {
            player.sendSystemMessage(Component.translatable("screen.sh_common_lib.cosmic_beacon.edit_denied"));
            return;
        }
        var owner = old.owner().equals(SYSTEM) ? player.getUUID() : old.owner();
        boolean locked = owner.equals(player.getUUID()) || operator(player) ? draft.locked() : old.locked();
        var beacon = new CosmicBeacon(point, old.id(), owner, CosmicBeacon.cleanName(draft.name()), locked,
                draft.crossDimension(), draft.hud());
        update(level.getServer(), point, beacon);
        player.sendSystemMessage(Component.translatable("screen.sh_common_lib.cosmic_beacon.saved"));
    }

    private static boolean editable(CosmicBeacon beacon, ServerPlayer player, ServerLevel level) {
        return level.mayInteract(player, beacon.point().pos())
                && (!beacon.locked() || beacon.owner().equals(player.getUUID()) || operator(player));
    }

    private static boolean operator(ServerPlayer player) {
        return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    private static boolean visible(CosmicBeacon beacon, ServerLevel level) {
        return beacon.point().dimension().equals(level.dimension()) || beacon.crossDimension();
    }

    private static void warp(ServerPlayer player, ServerLevel from, BeaconPackets.Warp request) {
        long now = from.getGameTime();
        if (now < WARPS.getOrDefault(player, Long.MIN_VALUE)) return;
        WARPS.put(player, now + 20);
        var server = from.getServer();
        var point = request.point();
        var beacon = records(server).get(point);
        if (beacon != null && beacon.id().equals(request.id()) && visible(beacon, from)) {
            var target = server.getLevel(point.dimension());
            if (target != null && target.getWorldBorder().isWithinBounds(point.pos())) {
                target.getChunkAt(point.pos());
                if (!isBeacon(target, point.pos())) {
                    update(server, point, null);
                } else {
                    var feet = Vec3.atBottomCenterOf(point.pos().above());
                    if (BeaconLanding.safe(target, player, feet, true)
                            && player.teleportTo(target, feet.x, feet.y, feet.z, Set.<Relative>of(),
                                    player.getYRot(), player.getXRot(), false)) {
                        player.resetFallDistance();
                        return;
                    }
                }
            }
        }
        player.sendSystemMessage(Component.translatable("screen.sh_common_lib.cosmic_beacon.unavailable"));
    }

    private static Map<GlobalPos, CosmicBeacon> records(MinecraftServer server) {
        return WORLDS.computeIfAbsent(server, key -> {
            var result = new LinkedHashMap<GlobalPos, CosmicBeacon>();
            var list = server.getCommandStorage().get(KEY).get("entries");
            if (list != null) CosmicBeacon.CODEC.listOf().parse(NbtOps.INSTANCE, list).result()
                    .ifPresent(entries -> entries.forEach(entry -> result.put(entry.point(), entry)));
            return result;
        });
    }

    private static void update(MinecraftServer server, GlobalPos point, CosmicBeacon beacon) {
        var entries = records(server);
        if (beacon == null && entries.remove(point) == null) return;
        if (beacon != null && beacon.equals(entries.put(point, beacon))) return;
        var storage = new CompoundTag();
        CosmicBeacon.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(entries.values())).result()
                .ifPresent(tag -> storage.put("entries", tag));
        server.getCommandStorage().set(KEY, storage);
    }
}
