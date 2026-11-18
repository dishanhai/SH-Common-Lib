// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets;
import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets.*;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanAcceleration.Platform;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Server-thread state. All owners and dimensions share the same execution budget. */
public final class AccelerationManager {
    private static final Gson GSON = new Gson();
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final int DURATION = 600;
    private static final int CALL_LIMIT = 2048;
    private static final long TIME_LIMIT_NANOS = 2_000_000L;
    private static final int MAX_TARGETS = 128;
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(AccelerationManager.class.getName());
    private static final String AI_MARK = "sh_common_lib.acceleration.ai_original";
    private static final MobAiLeases.Adapter<Mob> AI_ADAPTER = new MobAiLeases.Adapter<>() {
        @Override public boolean marked(Mob mob) { return mob.getPersistentData().contains(AI_MARK); }
        @Override public boolean original(Mob mob) {
            var value = mob.getPersistentData().get(AI_MARK);
            return value != null && Codec.BOOL.parse(NbtOps.INSTANCE, value).result().orElse(false);
        }
        @Override public void mark(Mob mob, boolean original) { mob.getPersistentData().putBoolean(AI_MARK, original); }
        @Override public void clear(Mob mob) { mob.getPersistentData().remove(AI_MARK); }
        @Override public boolean disabled(Mob mob) { return mob.isNoAi(); }
        @Override public void disable(Mob mob, boolean disabled) { mob.setNoAi(disabled); }
    };

    private AccelerationManager() {}

    public static boolean startBlock(ServerPlayer player, BlockPos pos, BlockState block, Platform platform) {
        var state = state(((ServerLevel) player.level()).getServer());
        var owner = owner(state, player);
        if (!enabled(player, owner, platform) || owner.rate == 0 || !((ServerLevel) player.level()).mayInteract(player, pos)) return false;
        var level = (ServerLevel) player.level();
        var be = level.getBlockEntity(pos);
        if (!block.is(Blocks.LIGHTNING_ROD) && !block.isRandomlyTicking() && be == null) return false;
        var target = new Target();
        target.kind = block.is(Blocks.LIGHTNING_ROD) ? 3 : 0;
        target.dimension = dimension(level);
        target.pos = pos.asLong();
        target.block = BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
        target.rate = owner.rate;
        target.mode = owner.mode;
        target.until = level.getGameTime() + DURATION;
        owner.targets.removeIf(old -> old.kind == target.kind && old.pos == target.pos && old.dimension.equals(target.dimension));
        if (owner.targets.size() >= MAX_TARGETS) owner.targets.remove(0);
        owner.targets.add(target);
        hint(player, owner, 2, platform);
        save(((ServerLevel) player.level()).getServer(), state);
        return true;
    }

    public static boolean startEntity(ServerPlayer player, Entity entity, Platform platform) {
        var state = state(((ServerLevel) player.level()).getServer());
        var owner = owner(state, player);
        if (!enabled(player, owner, platform) || owner.rate == 0 || entity instanceof Player || entity.isRemoved()
                || entity.level() != player.level()) return false;
        var target = new Target();
        target.kind = 1;
        target.dimension = dimension((ServerLevel) player.level());
        target.entity = entity.getUUID().toString();
        target.rate = owner.rate;
        target.mode = owner.mode;
        target.noAi = owner.disableAi;
        target.until = ((ServerLevel) player.level()).getGameTime() + DURATION;
        owner.targets.removeIf(old -> old.kind == 1 && old.entity.equals(target.entity));
        if (owner.targets.size() >= MAX_TARGETS) owner.targets.remove(0);
        owner.targets.add(target);
        hint(player, owner, 2, platform);
        save(((ServerLevel) player.level()).getServer(), state);
        reconcileAi(((ServerLevel) player.level()).getServer(), state);
        return true;
    }

    public static boolean startWorld(ServerPlayer player, Platform platform) {
        var level = (ServerLevel) player.level();
        if (!AccelerationEnvironment.aimsAtCelestial(player)) return false;
        var state = state(((ServerLevel) player.level()).getServer());
        var owner = owner(state, player);
        if (!enabled(player, owner, platform) || owner.rate == 0) return false;
        var target = new Target();
        target.kind = 2;
        target.dimension = dimension(level);
        target.rate = owner.rate;
        target.mode = owner.mode;
        target.until = level.getGameTime() + DURATION;
        owner.targets.removeIf(old -> old.kind == 2 && old.dimension.equals(target.dimension));
        owner.targets.add(target);
        hint(player, owner, 2, platform);
        save(((ServerLevel) player.level()).getServer(), state);
        return true;
    }

    public static boolean handleBlock(ServerPlayer player, BlockPos pos, Platform platform) {
        var state = state(((ServerLevel) player.level()).getServer());
        var owner = owner(state, player);
        if (!platform.masterEnabled(player)) return false;
        if (owner.editing != 0) return true; // A validated client request carries the Ctrl modifier.
        if (!owner.placing) return false;
        var level = (ServerLevel) player.level();
        if (!enabled(player, owner, platform) || !level.mayInteract(player, pos)
                || owner.ranges.size() >= AccelerationPackets.MAX_RANGES) return true;
        var range = new Range();
        range.id = UUID.randomUUID().toString();
        range.dimension = dimension(level);
        range.pos = pos.asLong();
        range.created = System.currentTimeMillis();
        range.x = owner.x; range.y = owner.y; range.z = owner.z;
        range.rate = owner.rate; range.mode = owner.mode;
        range.accelerationWhitelist = owner.accelerationWhitelist;
        range.sleepWhitelist = owner.sleepWhitelist;
        owner.ranges.add(range);
        owner.selected = range.id;
        owner.placing = false;
        save(((ServerLevel) player.level()).getServer(), state);
        return true;
    }

    public static boolean isEditing(ServerPlayer player) {
        var owner = owner(state(((ServerLevel) player.level()).getServer()), player);
        return owner.master && owner.editing != 0;
    }

    public static void receive(ServerPlayer player, Request request, Consumer<Reply> reply, Platform platform) {
        if (player.isSpectator() || !CosmicArtisanModes.isActive(player.getMainHandItem())) return;
        var server = ((ServerLevel) player.level()).getServer();
        var state = state(server);
        var throttle = state.requests.computeIfAbsent(player, ignored -> new RequestLimit());
        int now = server.getTickCount();
        if (throttle.tick != now) { throttle.tick = now; throttle.count = 0; }
        if (++throttle.count > 8) return;
        if (request.action() == AccelerationPackets.SYNC) {
            if (now - throttle.lastSync < 5) return;
            throttle.lastSync = now;
        }
        if (request.action() == AccelerationPackets.MARK) {
            if (throttle.lastMark == now) return;
            throttle.lastMark = now;
        }
        var owner = owner(state, player);
        var selected = selected(owner);
        boolean changed = true;
        switch (request.action()) {
            case AccelerationPackets.SYNC -> changed = false;
            case AccelerationPackets.SET_RATE -> {
                if (AccelerationPolicy.validRate(request.a())) {
                    owner.rate = request.a();
                    if (selected != null) selected.rate = owner.rate;
                    for (var target : owner.targets) target.rate = owner.rate;
                } else changed = false;
            }
            case AccelerationPackets.SCROLL -> {
                owner.rate = AccelerationPolicy.shift(owner.rate, request.a());
                if (selected != null) selected.rate = owner.rate;
                for (var target : owner.targets) target.rate = owner.rate;
                hint(player, owner, 4, platform);
                AccelerationEnvironment.feedback(player, owner.rate == 0 ? Component.translatable("screen.sh_common_lib.acceleration.rate_off")
                        : Component.translatable("screen.sh_common_lib.acceleration.rate", owner.rate));
            }
            case AccelerationPackets.SET_MODE -> {
                if (request.a() >= 0 && request.a() <= 2) {
                    owner.mode = request.a();
                    if (selected != null) selected.mode = owner.mode;
                    for (var target : owner.targets) {
                        if (target.mode != 0 && owner.mode == 0) {
                            var targetLevel = level(server, target.dimension);
                            if (targetLevel != null) target.until = targetLevel.getGameTime() + DURATION;
                        }
                        target.mode = owner.mode;
                    }
                } else changed = false;
            }
            case AccelerationPackets.SET_NO_AI -> {
                owner.disableAi = request.a() != 0;
                for (var target : owner.targets) if (target.kind == 1) target.noAi = owner.disableAi;
            }
            case AccelerationPackets.SET_SIZE -> {
                int value = Math.max(1, Math.min(15, request.b()));
                switch (request.a()) {
                    case 0 -> owner.x = value;
                    case 1 -> owner.y = value;
                    case 2 -> owner.z = value;
                    default -> changed = false;
                }
                if (selected != null && changed) {
                    selected.x = owner.x; selected.y = owner.y; selected.z = owner.z;
                    selected.invalidate();
                }
            }
            case AccelerationPackets.SET_PLACING -> {
                if (request.a() != 0 && !platform.masterEnabled(player)) changed = false;
                else { owner.placing = request.a() != 0; if (owner.placing) owner.editing = 0; }
            }
            case AccelerationPackets.SET_EDITING -> {
                if (request.a() >= 0 && request.a() <= 2 && (request.a() == 0 || platform.masterEnabled(player))) {
                    owner.editing = request.a();
                    if (owner.editing != 0) owner.placing = false;
                } else changed = false;
            }
            case AccelerationPackets.SET_FILTER -> {
                if (request.a() == 1) {
                    owner.accelerationWhitelist = request.b() != 0;
                    if (selected != null) selected.accelerationWhitelist = owner.accelerationWhitelist;
                } else if (request.a() == 2) {
                    owner.sleepWhitelist = request.b() != 0;
                    if (selected != null) selected.sleepWhitelist = owner.sleepWhitelist;
                } else changed = false;
            }
            case AccelerationPackets.SET_RANGE_ENABLED -> {
                var range = find(owner, request.id());
                if (range != null) {
                    boolean active = request.a() != 0;
                    if (active && !range.enabled) range.failed.clear();
                    range.enabled = active;
                } else changed = false;
            }
            case AccelerationPackets.RECLAIM -> {
                changed = owner.ranges.removeIf(range -> range.id.equals(request.id().toString()));
                if (owner.selected.equals(request.id().toString())) owner.selected = "";
            }
            case AccelerationPackets.SET_MASTER -> {
                platform.setMaster(player, request.a() != 0);
                owner.master = platform.masterEnabled(player);
                if (!owner.master) { owner.editing = 0; owner.placing = false; }
            }
            case AccelerationPackets.SELECT_RANGE -> {
                var range = find(owner, request.id());
                if (range == null) changed = false;
                else {
                    select(owner, range);
                }
            }
            case AccelerationPackets.OPENED -> owner.opened = true;
            case AccelerationPackets.MARK -> changed = platform.masterEnabled(player)
                    && mark(player, owner, request.pos(), request.a() != 0);
            default -> changed = false;
        }
        if (changed) { save(server, state); reconcileAi(server, state); }
        reply.accept(snapshot(player, state, owner, platform));
    }

    public static void tickPlayer(ServerPlayer player, Platform platform) {
        if (!CosmicArtisanModes.isActive(player.getMainHandItem())) return;
        var state = state(((ServerLevel) player.level()).getServer());
        var owner = owner(state, player);
        boolean master = platform.masterEnabled(player);
        boolean changed = owner.master != master;
        owner.master = master;
        if (!master && (owner.editing != 0 || owner.placing)) {
            owner.editing = 0; owner.placing = false; changed = true;
        }
        if ((owner.hints & 1) == 0 && !owner.opened) { hint(player, owner, 1, platform); changed = true; }
        if (changed) {
            var server = ((ServerLevel) player.level()).getServer();
            save(server, state);
            reconcileAi(server, state);
        }
    }

    public static void tickServer(MinecraftServer server, Platform platform) {
        long start = System.nanoTime();
        var state = state(server);
        reconcileAi(server, state);
        var units = new ArrayList<Unit>();
        var scans = new ArrayList<RangeScan>();
        boolean expired = false;
        for (var owner : state.owners.values()) {
            if (!ArtisanConfig.current(true).enabled() || !owner.master || owner.editing != 0) continue;
            Iterator<Target> targets = owner.targets.iterator();
            while (targets.hasNext()) {
                var target = targets.next();
                var level = level(server, target.dimension);
                if (level == null) continue;
                if (target.mode == 0 && level.getGameTime() >= target.until) {
                    targets.remove(); expired = true; continue;
                }
                if (target.rate > 1 && !target.failed) {
                    target.pending = addPending(target.pending, target.rate - 1);
                    units.add(new Unit(owner, target, null, 0, null));
                }
            }
            for (var range : owner.ranges) {
                var level = level(server, range.dimension);
                if (!range.enabled || range.rate <= 1 || level == null) continue;
                range.activeTicks++;
                scans.add(new RangeScan(level, range));
            }
        }
        state.external.removeIf(work -> work.remaining <= 0);
        for (var work : state.external) units.add(new Unit(null, null, null, 0, work));
        if (!scans.isEmpty()) {
            int index = Math.floorMod(state.scanCursor, scans.size());
            for (int i = 0; i < 64 && System.nanoTime() < start + TIME_LIMIT_NANOS / 2; i++) {
                var scan = scans.get(index);
                refreshCache(scan.level, scan.range, 1);
                index = (index + 1) % scans.size();
            }
            state.scanCursor = index;
            index = Math.floorMod(state.rangeCursor, scans.size());
            for (int i = 0; i < 64 && System.nanoTime() < start + TIME_LIMIT_NANOS * 3 / 4; i++) {
                var range = scans.get(index).range;
                index = (index + 1) % scans.size();
                if (range.workKeys.isEmpty()) continue;
                range.workCursor = Math.floorMod(range.workCursor, range.workKeys.size());
                long pos = range.workKeys.get(range.workCursor++);
                if (range.failed.contains(pos) || !AccelerationPolicy.allowed(range.accelerationWhitelist,
                        range.accelerationMarks.contains(pos))) {
                    range.lastAccrual.put(pos, range.activeTicks);
                    continue;
                }
                long delta = Math.max(0, range.activeTicks - range.lastAccrual.getOrDefault(pos, range.activeTicks - 1));
                range.lastAccrual.put(pos, range.activeTicks);
                long ticks = delta > Long.MAX_VALUE / (range.rate - 1) ? Long.MAX_VALUE : delta * (range.rate - 1);
                range.pending.merge(pos, ticks, AccelerationManager::addPending);
                units.add(new Unit(null, null, range, pos, null));
            }
            state.rangeCursor = index;
        }
        if (expired) save(server, state);
        if (server.getTickCount() % 20 == 0) {
            for (var level : server.getAllLevels()) platform.syncWorld(level, state.worldTicks.getOrDefault(dimension(level), 0L));
            if (state.worldDirty) { save(server, state); state.worldDirty = false; }
        }
        if (units.isEmpty()) return;
        long end = start + TIME_LIMIT_NANOS;
        state.cursor = TickBudget.drain(units, state.cursor, CALL_LIMIT, System::nanoTime, end, unit -> {
            try {
                return execute(server, state, unit, platform);
            } catch (RuntimeException | LinkageError exception) {
                if (unit.external != null) unit.external.remaining = 0;
                else if (unit.target != null) { unit.target.failed = true; unit.target.pending = 0; }
                else { unit.range.failed.add(unit.pos); unit.range.pending.remove(unit.pos); }
                LOG.log(java.util.logging.Level.WARNING, "Suspended a failing accelerated tick", exception);
                return false;
            }
        });
    }

    private static boolean execute(MinecraftServer server, State state, Unit unit, Platform platform) {
        if (unit.external != null) {
            if (unit.external.remaining <= 0) return false;
            unit.external.tick.run();
            unit.external.remaining--;
            return true;
        }
        var target = unit.target;
        var range = unit.range;
        if (target != null && target.pending <= 0) return false;
        if (range != null && range.pending.getOrDefault(unit.pos, 0L) <= 0) return false;
        var level = level(server, target != null ? target.dimension : range.dimension);
        if (level == null) return false;
        if (target != null) {
            if (target.kind == 2) {
                platform.tickWorld(level);
                state.worldTicks.merge(target.dimension, 1L, AccelerationManager::addPending);
                state.worldDirty = true;
            } else if (target.kind == 1) {
                Entity entity;
                try { entity = level.getEntity(UUID.fromString(target.entity)); }
                catch (IllegalArgumentException ex) { target.pending = 0; return false; }
                if (entity == null || entity.isRemoved() || entity instanceof Player) return false;
                platform.tickEntity(level, entity);
            } else {
                var pos = BlockPos.of(target.pos);
                if (!level.hasChunkAt(pos)) return false;
                var block = level.getBlockState(pos);
                if (!BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString().equals(target.block)) {
                    target.pending = 0; return false;
                }
                target.sleeping = target.kind == 0 && target.mode != 2 && inactive(block);
                if (target.sleeping && level.getGameTime() % 20 != 0) {
                    target.pending = 0; return false;
                }
                if (target.kind == 3) {
                    if (level.isThundering() && level.canSeeSky(pos.above()) && level.getRandom().nextInt(100000) == 0) {
                        platform.strikeLightning(level, pos);
                    }
                } else if (!tickBlock(level, pos, block, platform)) return false;
            }
            target.pending--;
            return true;
        }
        var pos = BlockPos.of(unit.pos);
        if (!level.hasChunkAt(pos)) return false;
        var block = level.getBlockState(pos);
        boolean sleep = range.mode != 2 && AccelerationPolicy.allowed(range.sleepWhitelist, range.sleepMarks.contains(unit.pos))
                && inactive(block);
        range.sleeping.put(unit.pos, sleep);
        if (sleep && level.getGameTime() % 20 != 0) {
            range.pending.put(unit.pos, 0L);
            return false;
        }
        if (!tickBlock(level, pos, block, platform)) return false;
        range.pending.put(unit.pos, range.pending.get(unit.pos) - 1);
        return true;
    }

    private static boolean tickBlock(ServerLevel level, BlockPos pos, BlockState state, Platform platform) {
        var entity = level.getBlockEntity(pos);
        if (entity != null) return tickEntityBlock(level, pos, state, entity, platform);
        if (state.isRandomlyTicking()) { state.randomTick(level, pos, level.getRandom()); return true; }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> boolean tickEntityBlock(ServerLevel level, BlockPos pos, BlockState state,
                                                                    T entity, Platform platform) {
        if (entity.isRemoved()) return false;
        var ticker = state.getTicker(level, (BlockEntityType<T>) entity.getType());
        if (ticker != null) { ticker.tick(level, pos, state, entity); platform.tickAe(level, pos, entity); return true; }
        return platform.tickAe(level, pos, entity);
    }

    private static boolean inactive(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof BooleanProperty active && (active.getName().equals("active") || active.getName().equals("lit")))
                return !state.getValue(active);
        }
        return false;
    }

    private static void refreshCache(ServerLevel level, Range range, int budget) {
        int volume = range.x * range.y * range.z;
        int steps = Math.min(budget, volume);
        if (range.scan == 0) range.seen.clear();
        for (int i = 0; i < steps; i++) {
            int index = range.scan++;
            var pos = position(range, index);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) != null) {
                if (range.cache.put(pos.asLong(), Boolean.TRUE) == null) range.workKeys.add(pos.asLong());
                range.seen.add(pos.asLong());
            }
            if (range.scan >= volume) {
                range.scan = 0;
                range.cache.keySet().retainAll(range.seen);
                range.pending.keySet().retainAll(range.seen);
                range.sleeping.keySet().retainAll(range.seen);
                range.lastAccrual.keySet().retainAll(range.seen);
                range.workKeys.removeIf(point -> !range.cache.containsKey(point));
                break;
            }
        }
    }

    private static BlockPos position(Range range, int index) {
        var center = BlockPos.of(range.pos);
        int x = index % range.x, y = index / range.x % range.y, z = index / (range.x * range.y);
        return center.offset(x - (range.x - 1) / 2, y - (range.y - 1) / 2, z - (range.z - 1) / 2);
    }

    private static boolean contains(Range range, BlockPos pos) {
        var start = position(range, 0);
        return pos.getX() >= start.getX() && pos.getX() < start.getX() + range.x
                && pos.getY() >= start.getY() && pos.getY() < start.getY() + range.y
                && pos.getZ() >= start.getZ() && pos.getZ() < start.getZ() + range.z;
    }

    private static boolean mark(ServerPlayer player, Owner owner, BlockPos pos, boolean all) {
        if (owner.editing == 0 || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36) return false;
        var level = (ServerLevel) player.level();
        var range = selected(owner);
        if (range == null || !range.enabled || !range.dimension.equals(dimension(level)) || !contains(range, pos)) {
            range = null;
            for (var candidate : owner.ranges) {
                if (candidate.enabled && candidate.dimension.equals(dimension(level)) && contains(candidate, pos)) {
                    range = candidate; break;
                }
            }
        }
        if (range == null || !level.hasChunkAt(pos) || !level.mayInteract(player, pos) || level.getBlockEntity(pos) == null) return false;
        select(owner, range);
        var marks = owner.editing == 1 ? range.accelerationMarks : range.sleepMarks;
        boolean add = !marks.contains(pos.asLong());
        var type = level.getBlockState(pos).getBlock();
        int volume = range.x * range.y * range.z;
        for (int i = 0; i < (all ? volume : 1); i++) {
            var point = all ? position(range, i) : pos;
            if (!level.hasChunkAt(point) || !level.mayInteract(player, point) || level.getBlockEntity(point) == null
                    || all && level.getBlockState(point).getBlock() != type) continue;
            if (add) marks.add(point.asLong()); else marks.remove(point.asLong());
        }
        return true;
    }

    private static Reply snapshot(ServerPlayer player, State state, Owner owner, Platform platform) {
        var ranges = new ArrayList<RangeView>();
        for (var range : owner.ranges) {
            if (ranges.size() >= AccelerationPackets.MAX_RANGES) break;
            UUID id;
            try { id = UUID.fromString(range.id); } catch (IllegalArgumentException ex) { continue; }
            ranges.add(new RangeView(id, range.dimension, BlockPos.of(range.pos), range.created, range.enabled,
                    range.x, range.y, range.z, range.rate, range.accelerationWhitelist, range.sleepWhitelist,
                    range.accelerationMarks.size(), range.sleepMarks.size()));
        }
        var visuals = new ArrayList<Visual>();
        String current = dimension((ServerLevel) player.level());
        for (var other : state.owners.values()) {
            for (var range : other.ranges) {
                if (range.dimension.equals(current) && visuals.size() < AccelerationPackets.MAX_VISUALS)
                    visuals.add(new Visual(current, BlockPos.of(range.pos), -1, 2, -1, DURATION, false));
            }
            if (!other.master) continue;
            for (var target : other.targets) {
                if (!target.dimension.equals(current) || target.kind == 2 || visuals.size() >= AccelerationPackets.MAX_VISUALS) continue;
                int remaining = target.mode == 0 ? (int) Math.max(0, Math.min(Integer.MAX_VALUE,
                        target.until - ((ServerLevel) player.level()).getGameTime())) : -1;
                if (target.kind == 1) {
                    try {
                        var entity = ((ServerLevel) player.level()).getEntity(UUID.fromString(target.entity));
                        if (entity != null) visuals.add(new Visual(current, entity.blockPosition(), entity.getId(), 1,
                                remaining, DURATION, false));
                    } catch (IllegalArgumentException ignored) {}
                } else visuals.add(new Visual(current, BlockPos.of(target.pos), -1, 0, remaining, DURATION, target.sleeping));
            }
            for (var range : other.ranges) {
                if (!range.enabled || range.rate == 0 || !range.dimension.equals(current)) continue;
                for (long point : range.cache.keySet()) {
                    if (visuals.size() >= AccelerationPackets.MAX_VISUALS) break;
                    if (AccelerationPolicy.allowed(range.accelerationWhitelist, range.accelerationMarks.contains(point)))
                        visuals.add(new Visual(current, BlockPos.of(point), -1, 0, -1, DURATION,
                                range.sleeping.getOrDefault(point, false)));
                }
            }
        }
        if (owner.editing != 0) {
            var range = selected(owner);
            if (range != null && range.enabled && range.dimension.equals(current)) {
                var marks = owner.editing == 1 ? range.accelerationMarks : range.sleepMarks;
                int kind = owner.editing == 1 ? range.accelerationWhitelist ? 4 : 3 : range.sleepWhitelist ? 6 : 5;
                for (long pos : marks) {
                    if (visuals.size() >= AccelerationPackets.MAX_VISUALS) break;
                    visuals.add(new Visual(current, BlockPos.of(pos), -1, kind, -1, DURATION, false));
                }
            }
        }
        UUID selected;
        try { selected = UUID.fromString(owner.selected); } catch (IllegalArgumentException ex) { selected = AccelerationPackets.NO_ID; }
        var settings = new Settings(owner.rate, owner.mode, owner.disableAi, owner.x, owner.y, owner.z,
                owner.placing, owner.editing, owner.accelerationWhitelist, owner.sleepWhitelist,
                platform.masterEnabled(player), selected);
        return new Reply(settings, ranges, visuals, state.worldTicks.getOrDefault(current, 0L));
    }

    private static void hint(ServerPlayer player, Owner owner, int flag, Platform platform) {
        if (!owner.opened && (owner.hints & flag) == 0) {
            owner.hints |= flag;
            platform.showHint(player);
        }
    }

    private static boolean enabled(ServerPlayer player, Owner owner, Platform platform) {
        owner.master = platform.masterEnabled(player);
        return owner.master && !player.isSpectator();
    }

    private static long addPending(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static String dimension(ServerLevel level) { return AccelerationEnvironment.dimension(level); }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        for (var level : server.getAllLevels()) if (dimension(level).equals(dimension)) return level;
        return null;
    }

    private static Range selected(Owner owner) {
        for (var range : owner.ranges) if (range.id.equals(owner.selected)) return range;
        return null;
    }

    private static void select(Owner owner, Range range) {
        owner.selected = range.id; owner.x = range.x; owner.y = range.y; owner.z = range.z;
        owner.rate = range.rate; owner.mode = range.mode;
        owner.accelerationWhitelist = range.accelerationWhitelist; owner.sleepWhitelist = range.sleepWhitelist;
    }

    private static Range find(Owner owner, UUID id) {
        for (var range : owner.ranges) if (range.id.equals(id.toString())) return range;
        return null;
    }

    private static State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, key -> {
            var tag = AccelerationEnvironment.read(key).get("json");
            if (tag != null) {
                var json = Codec.STRING.parse(NbtOps.INSTANCE, tag).result().orElse("");
                try {
                    var loaded = GSON.fromJson(json, State.class);
                    if (loaded != null && loaded.owners != null && loaded.worldTicks != null) return loaded;
                } catch (RuntimeException ignored) { }
            }
            return new State();
        });
    }

    private static Owner owner(State state, ServerPlayer player) {
        return state.owners.computeIfAbsent(player.getUUID().toString(), ignored -> new Owner());
    }

    private static void save(MinecraftServer server, State state) {
        var tag = new CompoundTag();
        tag.putString("json", GSON.toJson(state));
        AccelerationEnvironment.write(server, tag);
    }

    /** Enqueue reusable third-party work on the same server-wide budget. */
    public static boolean submit(MinecraftServer server, Runnable tick, long count) {
        if (!server.isSameThread()) throw new IllegalStateException("Acceleration work must be submitted on the server thread");
        if (tick == null || count <= 0) throw new IllegalArgumentException("A tick callback and positive count are required");
        var state = state(server);
        if (state.external.size() >= 1024) return false;
        state.external.add(new External(tick, count));
        return true;
    }

    public static void shutdown(MinecraftServer server) {
        var state = STATES.remove(server);
        if (state != null) {
            state.ai.close();
            if (state.worldDirty) save(server, state);
        }
    }

    public static void entityJoined(ServerLevel level, Entity entity) {
        if (!(entity instanceof Mob mob)) return;
        var state = STATES.get(level.getServer());
        if (state == null) new MobAiLeases<UUID, Mob>(AI_ADAPTER).recover(mob.getUUID(), mob);
        else state.ai.recover(mob.getUUID(), mob);
    }

    private static void reconcileAi(MinecraftServer server, State state) {
        var desired = new HashMap<UUID, Mob>();
        if (ArtisanConfig.current(true).enabled()) {
            for (var owner : state.owners.values()) {
                if (!owner.master || owner.editing != 0) continue;
                for (var target : owner.targets) {
                    if (target.kind != 1 || !target.noAi || target.rate <= 1 || target.failed) continue;
                    var level = level(server, target.dimension);
                    if (level == null || target.mode == 0 && level.getGameTime() >= target.until) continue;
                    try {
                        var uuid = UUID.fromString(target.entity);
                        var entity = level.getEntity(uuid);
                        if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) desired.put(uuid, mob);
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        }
        state.ai.reconcile(desired);
    }

    private record Unit(Owner owner, Target target, Range range, long pos, External external) {}
    private record RangeScan(ServerLevel level, Range range) {}
    private static final class External {
        final Runnable tick;
        long remaining;
        External(Runnable tick, long remaining) { this.tick = tick; this.remaining = remaining; }
    }
    private static final class RequestLimit {
        int tick = Integer.MIN_VALUE, count, lastSync = -10, lastMark = Integer.MIN_VALUE;
    }

    static final class State {
        Map<String, Owner> owners = new LinkedHashMap<>();
        Map<String, Long> worldTicks = new HashMap<>();
        transient List<External> external = new ArrayList<>();
        transient Map<ServerPlayer, RequestLimit> requests = new WeakHashMap<>();
        transient MobAiLeases<UUID, Mob> ai = new MobAiLeases<>(AI_ADAPTER);
        transient int cursor, scanCursor, rangeCursor;
        transient boolean worldDirty;
    }

    static final class Owner {
        int rate = 2, mode, x = 1, y = 1, z = 1, editing, hints;
        boolean disableAi, placing, accelerationWhitelist, sleepWhitelist, master, opened;
        String selected = "";
        List<Target> targets = new ArrayList<>();
        List<Range> ranges = new ArrayList<>();
    }

    static final class Target {
        int kind, rate, mode;
        boolean noAi;
        String dimension = "", entity = "", block = "";
        long pos, until;
        transient long pending;
        transient boolean failed, sleeping;
    }

    static final class Range {
        String id = "", dimension = "";
        long pos, created;
        int x = 1, y = 1, z = 1, rate = 2, mode;
        boolean enabled = true, accelerationWhitelist, sleepWhitelist;
        Set<Long> accelerationMarks = new java.util.LinkedHashSet<>(), sleepMarks = new java.util.LinkedHashSet<>();
        transient Map<Long, Boolean> cache = new LinkedHashMap<>();
        transient Map<Long, Long> pending = new HashMap<>();
        transient Map<Long, Boolean> sleeping = new HashMap<>();
        transient Map<Long, Long> lastAccrual = new HashMap<>();
        transient List<Long> workKeys = new ArrayList<>();
        transient Set<Long> seen = new java.util.HashSet<>();
        transient Set<Long> failed = new java.util.HashSet<>();
        transient int scan, workCursor;
        transient long activeTicks;

        void invalidate() { cache.clear(); pending.clear(); sleeping.clear(); lastAccrual.clear(); workKeys.clear(); seen.clear(); failed.clear(); scan = workCursor = 0; }
    }
}
