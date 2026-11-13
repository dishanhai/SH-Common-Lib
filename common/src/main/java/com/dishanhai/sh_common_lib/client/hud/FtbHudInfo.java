// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Optional FTB client integrations. Only synced client data is read; no requests or disk scans are made. */
final class FtbHudInfo {
    private static final String PREFIX = PlayerInfoHud.LANG;
    private static final long REFRESH = TimeUnit.SECONDS.toNanos(5);
    private static final long SLICE = TimeUnit.MILLISECONDS.toNanos(1);
    private static final int MAX_PER_SLICE = 128;
    private final QuestProbe quests = QuestProbe.create();
    private final TeamProbe teams = TeamProbe.create();
    private final ChunkProbe chunks = ChunkProbe.create();
    private Object level;
    private Component teamText, quotaText, chunkText;
    private long nextTeam, nextQuota, nextChunk;
    private int chunkX, chunkZ;

    void reset() {
        level = null;
        teamText = quotaText = chunkText = null;
        nextTeam = nextQuota = nextChunk = 0;
        if (quests != null) quests.reset();
    }

    Component quests(Level current) {
        enter(current);
        return quests == null ? line("ftb_unavailable") : quests.read();
    }

    Component team(Level current) {
        enter(current);
        long now = System.nanoTime();
        if (teamText == null || now >= nextTeam) {
            nextTeam = now + REFRESH;
            teamText = teams == null ? line("ftb_unavailable") : teams.read();
        }
        return teamText;
    }

    Component quota(Level current) {
        enter(current);
        long now = System.nanoTime();
        if (quotaText == null || now >= nextQuota) {
            nextQuota = now + REFRESH;
            quotaText = chunks == null ? line("ftb_unavailable") : chunks.quota();
        }
        return quotaText;
    }

    Component chunk(Level current, ChunkPos pos, int x, int z) {
        enter(current);
        long now = System.nanoTime();
        if (chunkText == null || x != chunkX || z != chunkZ || now >= nextChunk) {
            chunkX = x;
            chunkZ = z;
            nextChunk = now + REFRESH;
            chunkText = chunks == null ? line("ftb_unavailable") : chunks.chunk(current, pos);
        }
        return chunkText;
    }

    private void enter(Level current) {
        if (level != current) {
            reset();
            level = current;
        }
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    private static Object unwrap(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static final class QuestProbe {
        private final Class<?> questClass;
        private final Method exists, getInstance, allObjects, visible, completed;
        private final Field instanceField, selfData;
        private Object file, data;
        private Iterator<?> iterator;
        private int total, done;
        private Component cached;
        private long nextScan;

        static QuestProbe create() {
            try { return new QuestProbe(); }
            catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return null; }
        }

        private QuestProbe() throws ReflectiveOperationException {
            Class<?> client = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
            Class<?> base = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
            Class<?> dataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            Class<?> object = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObject");
            questClass = Class.forName("dev.ftb.mods.ftbquests.quest.Quest");
            exists = client.getMethod("exists");
            Method getter = null;
            Field instance = null;
            try { getter = client.getMethod("getInstance"); }
            catch (NoSuchMethodException ignored) { instance = client.getField("INSTANCE"); }
            getInstance = getter;
            instanceField = instance;
            selfData = client.getField("selfTeamData");
            allObjects = base.getMethod("getAllObjects");
            visible = questClass.getMethod("isVisible", dataClass);
            completed = dataClass.getMethod("isCompleted", object);
        }

        void reset() { file = data = null; iterator = null; cached = null; nextScan = 0; }

        Component read() {
            try {
                if (!Boolean.TRUE.equals(exists.invoke(null))) { reset(); return line("ftb_syncing"); }
                Object currentFile = getInstance == null ? instanceField.get(null) : getInstance.invoke(null);
                Object currentData = currentFile == null ? null : selfData.get(currentFile);
                if (currentFile == null || currentData == null) { reset(); return line("ftb_syncing"); }
                if (file != currentFile || data != currentData) { reset(); file = currentFile; data = currentData; }
                long now = System.nanoTime();
                if (iterator == null && now >= nextScan) {
                    iterator = ((Collection<?>) allObjects.invoke(file)).iterator();
                    done = total = 0;
                }
                if (iterator != null) {
                    long deadline = now + SLICE;
                    int count = 0;
                    while (count++ < MAX_PER_SLICE && System.nanoTime() < deadline && iterator.hasNext()) {
                        Object item = iterator.next();
                        if (questClass.isInstance(item) && Boolean.TRUE.equals(visible.invoke(item, data))) {
                            total++;
                            if (Boolean.TRUE.equals(completed.invoke(data, item))) done++;
                        }
                    }
                    if (!iterator.hasNext()) {
                        cached = line("ftb_quests_progress", HudNumberFormatter.format(done), HudNumberFormatter.format(total));
                        iterator = null;
                        nextScan = System.nanoTime() + REFRESH;
                    }
                }
                return cached == null ? line("ftb_syncing") : cached;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                reset();
                return line("ftb_unavailable");
            }
        }
    }

    private static final class TeamProbe {
        private final Method api, loaded, manager, self, name, members;

        static TeamProbe create() {
            try { return new TeamProbe(); }
            catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return null; }
        }

        private TeamProbe() throws ReflectiveOperationException {
            Class<?> root = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            Class<?> managerClass = Class.forName("dev.ftb.mods.ftbteams.api.client.ClientTeamManager");
            Class<?> teamClass = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            api = root.getMethod("api");
            loaded = apiClass.getMethod("isClientManagerLoaded");
            manager = apiClass.getMethod("getClientManager");
            self = managerClass.getMethod("selfTeam");
            name = teamClass.getMethod("getName");
            members = teamClass.getMethod("getMembers");
        }

        Component read() {
            try {
                Object instance = api.invoke(null);
                if (!Boolean.TRUE.equals(loaded.invoke(instance))) return line("ftb_syncing");
                Object team = self.invoke(manager.invoke(instance));
                if (team == null) return line("ftb_syncing");
                return line("ftb_team_info", name.invoke(team), HudNumberFormatter.format(((Collection<?>) members.invoke(team)).size()));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return line("ftb_unavailable"); }
        }
    }

    private static final class ChunkProbe {
        private final Object client;
        private final Method summary, general, api, owner, force, teamName;

        static ChunkProbe create() {
            try { return new ChunkProbe(); }
            catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return null; }
        }

        private ChunkProbe() throws ReflectiveOperationException {
            Class<?> clientClass = Class.forName("dev.ftb.mods.ftbchunks.client.FTBChunksClient");
            client = clientClass.getField("INSTANCE").get(null);
            Method oldSummary = null, newGeneral = null;
            try { oldSummary = clientClass.getMethod("getChunkSummary"); }
            catch (NoSuchMethodException ignored) {
                try { newGeneral = clientClass.getMethod("getGeneralChunkData"); }
                catch (NoSuchMethodException absent) { /* Optional on some releases. */ }
            }
            summary = oldSummary;
            general = newGeneral;
            Class<?> root = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI$API");
            api = root.getMethod("api");
            Method getOwner = null, getForce = null;
            try {
                getOwner = apiClass.getMethod("getOwningTeam", Level.class, ChunkPos.class);
                getForce = apiClass.getMethod("isForceLoadingRequested", Level.class, ChunkPos.class);
            } catch (NoSuchMethodException ignored) { /* Older versions expose quota but not this client API. */ }
            owner = getOwner;
            force = getForce;
            teamName = Class.forName("dev.ftb.mods.ftbteams.api.Team").getMethod("getName");
        }

        Component quota() {
            try {
                if (summary != null) {
                    List<?> values = (List<?>) summary.invoke(client);
                    if (values.size() < 4) return line("ftb_syncing");
                    return line("ftb_chunks_summary", values.get(0), values.get(1), values.get(2), values.get(3));
                }
                if (general != null) {
                    Object data = general.invoke(client);
                    if (data == null) return line("ftb_syncing");
                    Class<?> type = data.getClass();
                    try {
                        if (type.getField("NONE").get(null) == data) return line("ftb_syncing");
                    } catch (NoSuchFieldException ignored) { /* Older versions have no sentinel. */ }
                    return line("ftb_chunks_quota", HudNumberFormatter.format((Number) type.getMethod("claimed").invoke(data)),
                            HudNumberFormatter.format((Number) type.getMethod("maxClaimChunks").invoke(data)),
                            HudNumberFormatter.format((Number) type.getMethod("loaded").invoke(data)),
                            HudNumberFormatter.format((Number) type.getMethod("maxForceLoadChunks").invoke(data)));
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return line("ftb_unavailable"); }
            return line("ftb_unavailable");
        }

        Component chunk(Level level, ChunkPos pos) {
            if (owner == null || force == null) return line("ftb_chunk_unsupported");
            try {
                Object instance = api.invoke(null);
                Object team = unwrap(owner.invoke(instance, level, pos));
                boolean forced = Boolean.TRUE.equals(force.invoke(instance, level, pos));
                return line("ftb_chunk_info", team == null ? line("ftb_chunk_unclaimed_or_unknown") : teamName.invoke(team),
                        line(forced ? "ftb_chunk_force_requested" : "ftb_chunk_force_unknown"));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return line("ftb_unavailable"); }
        }
    }
}
