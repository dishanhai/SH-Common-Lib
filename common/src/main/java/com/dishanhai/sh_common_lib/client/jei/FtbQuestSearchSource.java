// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_common_lib.api.client.ulk.SearchSources;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/** Optional FTB Quests adapter; consumes only visible, synced client quest data. */
final class FtbQuestSearchSource {
    private static final long REFRESH = TimeUnit.SECONDS.toNanos(5);
    private final Class<?> questClass, itemTaskClass;
    private final Method exists, getInstance, objects, visible, completed, dependencies, tasks, item;
    private final Field instance, selfData;
    private Object file, data;
    private Iterator<?> iterator;
    private final ArrayList<String> pending = new ArrayList<>();
    private long nextScan;

    static FtbQuestSearchSource create() {
        try { return new FtbQuestSearchSource(); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError absent) { return null; }
    }

    private FtbQuestSearchSource() throws ReflectiveOperationException {
        Class<?> client = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
        Class<?> base = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
        Class<?> team = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
        Class<?> object = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObject");
        questClass = Class.forName("dev.ftb.mods.ftbquests.quest.Quest");
        itemTaskClass = Class.forName("dev.ftb.mods.ftbquests.quest.task.ItemTask");
        exists = client.getMethod("exists");
        Method getter = null; Field field = null;
        try { getter = client.getMethod("getInstance"); }
        catch (NoSuchMethodException missing) { field = client.getField("INSTANCE"); }
        getInstance = getter; instance = field;
        selfData = client.getField("selfTeamData");
        objects = base.getMethod("getAllObjects");
        visible = questClass.getMethod("isVisible", team);
        dependencies = questClass.getMethod("areDependenciesComplete", team);
        completed = team.getMethod("isCompleted", object);
        tasks = questClass.getMethod("getTasks");
        item = itemTaskClass.getMethod("getItemStack");
    }

    void reset(SearchSources sources) {
        file = data = null; iterator = null; pending.clear(); nextScan = 0;
        sources.replaceQuest(java.util.List.of());
    }

    void tick(SearchSources sources) {
        try {
            if (!Boolean.TRUE.equals(exists.invoke(null))) { reset(sources); return; }
            Object nextFile = getInstance == null ? instance.get(null) : getInstance.invoke(null);
            Object nextData = nextFile == null ? null : selfData.get(nextFile);
            if (nextFile == null || nextData == null) { reset(sources); return; }
            if (file != nextFile || data != nextData) { reset(sources); file = nextFile; data = nextData; }
            long now = System.nanoTime();
            if (iterator == null && now >= nextScan) {
                iterator = ((Collection<?>) objects.invoke(file)).iterator();
                pending.clear();
            }
            if (iterator == null) return;
            long deadline = now + TimeUnit.MILLISECONDS.toNanos(1);
            for (int count = 0; count < 128 && System.nanoTime() < deadline && iterator.hasNext(); count++) {
                Object quest = iterator.next();
                if (!questClass.isInstance(quest) || !Boolean.TRUE.equals(visible.invoke(quest, data))
                        || !Boolean.TRUE.equals(dependencies.invoke(quest, data))
                        || Boolean.TRUE.equals(completed.invoke(data, quest))) continue;
                for (Object task : (Collection<?>) tasks.invoke(quest)) {
                    if (!itemTaskClass.isInstance(task) || Boolean.TRUE.equals(completed.invoke(data, task))) continue;
                    Object value = item.invoke(task);
                    if (value instanceof ItemStack stack && !stack.isEmpty()) pending.add(stack.getHoverName().getString());
                    if (pending.size() >= 512) break;
                }
                if (pending.size() >= 512) break;
            }
            if (pending.size() >= 512 || !iterator.hasNext()) {
                sources.replaceQuest(pending);
                iterator = null;
                nextScan = System.nanoTime() + REFRESH;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            reset(sources);
            nextScan = System.nanoTime() + REFRESH;
        }
    }
}
