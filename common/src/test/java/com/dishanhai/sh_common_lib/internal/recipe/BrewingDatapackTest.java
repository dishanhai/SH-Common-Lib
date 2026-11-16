// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.recipe;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Exercises the real data-pack reader with a resource-manager boundary, without a game instance. */
public final class BrewingDatapackTest {
    public static void main(String[] args) throws Exception {
        Map<Object, Resource> resources = new LinkedHashMap<>();
        resources.put(id("example:sh_common_lib/brewing/nested/good.json"), resource("""
                {"id":"example:nested/good","input":"minecraft:water",
                 "ingredient":"minecraft:diamond","output":"minecraft:awkward"}
                """));
        resources.put(id("example:sh_common_lib/brewing/broken.json"), resource("{broken"));
        resources.put(id("example:sh_common_lib/brewing/large.json"), resource("x".repeat(65537)));
        resources.put(id("example:sh_common_lib/brewing/large_utf8.json"), resource("水".repeat(30000)));
        resources.put(id("example:other/ignored.json"), resource("{}"));
        boolean[] fail = {false};
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, arguments) -> {
                    if (!method.getName().equals("listResources")) throw new UnsupportedOperationException(method.getName());
                    if (fail[0]) throw new IllegalStateException("resource manager failed");
                    @SuppressWarnings("unchecked") var filter = (Predicate<Object>) arguments[1];
                    Map<Object, Resource> matching = new LinkedHashMap<>();
                    for (var entry : resources.entrySet()) {
                        if (entry.getKey().toString().contains(":sh_common_lib/brewing/") && filter.test(entry.getKey())) {
                            matching.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return matching;
                });
        var errors = new ArrayList<String>();
        var original = BrewingDatapack.load(manager, errors::add);
        check(original.size() == 1 && original.get(0).id().equals("example:nested/good"), "nested id and broken-file isolation");
        check(errors.size() == 3, "malformed and oversized byte streams are reported");

        var folder = Files.createTempDirectory("brewing-config-test");
        try {
            Files.writeString(folder.resolve("fallback.json"), """
                    {"id":"example:nested/good","input":"minecraft:water",
                     "ingredient":"minecraft:sugar","output":"minecraft:awkward"}
                    """);
            var combined = BrewingDatapack.loadWithConfig(manager, folder, errors::add);
            check(combined.size() == 1 && combined.get(0).ingredient().equals("minecraft:diamond"),
                    "pack replaces legacy config with matching id");
            resources.remove(id("example:sh_common_lib/brewing/nested/good.json"));
            combined = BrewingDatapack.loadWithConfig(manager, folder, errors::add);
            check(combined.size() == 1 && combined.get(0).ingredient().equals("minecraft:sugar"),
                    "reload removes deleted pack rules and restores config fallback");
            check(original.get(0).ingredient().equals("minecraft:diamond"), "previous snapshot is immutable");
            fail[0] = true;
            try {
                BrewingDatapack.load(manager, errors::add);
                throw new AssertionError("resource manager failures must abort preparation");
            } catch (IllegalStateException expected) {
                check(expected.getMessage().equals("resource manager failed"), "unexpected failure");
            }
        } finally {
            Files.deleteIfExists(folder.resolve("fallback.json"));
            Files.deleteIfExists(folder);
        }
    }

    private static Object id(String value) throws Exception {
        Class<?> type;
        try { type = Class.forName("net.minecraft.resources.Identifier"); }
        catch (ClassNotFoundException olderVersion) { type = Class.forName("net.minecraft.resources.ResourceLocation"); }
        try { return type.getMethod("parse", String.class).invoke(null, value); }
        catch (NoSuchMethodException olderVersion) { return type.getMethod("tryParse", String.class).invoke(null, value); }
    }

    private static Resource resource(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new Resource(null, () -> new ByteArrayInputStream(bytes));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
