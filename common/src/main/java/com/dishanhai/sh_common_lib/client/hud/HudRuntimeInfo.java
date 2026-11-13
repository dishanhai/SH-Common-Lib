// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud.LANG;
import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem.*;

public final class HudRuntimeInfo {
    private static final String SHBUI_PANEL_KEY = "key.sh_better_ui.control_panel";
    private static final String SYSTEM_NAME = detectSystem();
    private final PlayerInfoItem loaderVersionItem;
    private final Supplier<Mods> inspectMods;
    private final Predicate<String> modLoaded;
    private Mods mods;

    public HudRuntimeInfo(PlayerInfoItem loaderVersionItem, Supplier<Mods> inspectMods) {
        this(loaderVersionItem, inspectMods, ignored -> false);
    }

    public HudRuntimeInfo(PlayerInfoItem loaderVersionItem, Supplier<Mods> inspectMods, Predicate<String> modLoaded) {
        this.loaderVersionItem = loaderVersionItem;
        this.inspectMods = inspectMods;
        this.modLoaded = java.util.Objects.requireNonNull(modLoaded);
    }

    boolean modLoaded(String modId) { return modLoaded.test(modId); }

    public PlayerInfoItem loaderVersionItem() {
        return loaderVersionItem;
    }

    void append(Predicate<PlayerInfoItem> shows, Function<Object, Component> value,
                BiConsumer<PlayerInfoItem, Component> add) {
        if (shows.test(JAVA_VERSION)) {
            add.accept(JAVA_VERSION, line(JAVA_VERSION, value.apply(Runtime.version().toString())));
        }
        if (shows.test(SYSTEM_VERSION)) {
            add.accept(SYSTEM_VERSION, line(SYSTEM_VERSION, value.apply(version(SYSTEM_NAME))));
        }
        if (shows.test(MINECRAFT_VERSION)) {
            add.accept(MINECRAFT_VERSION, line(MINECRAFT_VERSION, value.apply(version(mods().minecraftVersion()))));
        }
        if (shows.test(loaderVersionItem)) {
            add.accept(loaderVersionItem, line(loaderVersionItem, value.apply(version(mods().loaderVersion()))));
        }
        if (shows.test(SHADER_PACK)) {
            add.accept(SHADER_PACK, line(SHADER_PACK, value.apply(ShaderPack.current())));
        }
        if (shows.test(MOD_STATUS)) {
            var status = mods();
            var state = Component.translatable(LANG + (status.loaded() == status.total() ? "mods_ready" : "mods_partial"));
            add.accept(MOD_STATUS, line(MOD_STATUS, value.apply(status.loaded()), value.apply(status.total()), state));
        }
    }

    private Mods mods() {
        // Forge and NeoForge have finished constructing the client mod list before the HUD can draw.
        if (mods == null) mods = inspectMods.get();
        return mods;
    }

    private static String detectSystem() {
        try {
            String name = System.getProperty("os.name", "");
            String runtime = System.getProperty("java.runtime.name", "");
            String version = System.getProperty("os.version", "");
            if (runtime.toLowerCase(java.util.Locale.ROOT).contains("android")) {
                name = "Android";
                try {
                    Object release = Class.forName("android.os.Build$VERSION").getField("RELEASE").get(null);
                    if (release instanceof String text && !text.isBlank()) version = text;
                } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
                    // Some launchers expose only the JVM's operating-system version.
                }
            }
            String result = (name + " " + version).trim();
            return result.isEmpty() ? null : result;
        } catch (SecurityException ignored) {
            return null;
        }
    }

    static Component shbuiShortcut(Minecraft client) {
        for (var mapping : client.options.keyMappings) {
            if (SHBUI_PANEL_KEY.equals(mapping.getName())) return keyLabel(mapping);
        }
        return Component.translatable(LANG + "unavailable");
    }

    static Component keyLabel(KeyMapping mapping) {
        return mapping.isUnbound() ? Component.translatable(LANG + "key_unbound") : mapping.getTranslatedKeyMessage();
    }

    private static Component line(PlayerInfoItem item, Object... arguments) {
        return Component.translatable(LANG + item.id(), arguments);
    }

    private static Component version(String version) {
        return version == null ? Component.translatable(LANG + "unavailable") : Component.literal(version);
    }

    public record Mods(String minecraftVersion, String loaderVersion, long loaded, int total) {}

    private record ShaderPack(Object api, Method active, Method name) {
        private static final ShaderPack PROBE = find();

        static Component current() {
            if (PROBE == null) return Component.translatable(LANG + "shader_unknown");
            try {
                if (!Boolean.TRUE.equals(PROBE.active.invoke(PROBE.api))) {
                    return Component.translatable(LANG + "shader_off");
                }
                if (PROBE.name != null && PROBE.name.invoke(null) instanceof String name && !name.isBlank()) {
                    return Component.literal(name);
                }
                return Component.translatable(LANG + "shader_active");
            } catch (ReflectiveOperationException | LinkageError error) {
                return Component.translatable(LANG + "shader_unknown");
            }
        }

        private static ShaderPack find() {
            for (String root : new String[]{"net.irisshaders.iris", "net.coderbot.iris"}) {
                try {
                    var type = Class.forName(root + ".api.v0.IrisApi");
                    var api = type.getMethod("getInstance").invoke(null);
                    return new ShaderPack(api, type.getMethod("isShaderPackInUse"), packName(root));
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // Iris and Oculus are optional and use different package names across releases.
                }
            }
            return null;
        }

        private static Method packName(String root) {
            try {
                return Class.forName(root + ".Iris").getMethod("getCurrentPackName");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
