// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.settings;

import com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Explicitly selected client settings shared through the existing UUK extension point. */
public final class AssistantSettingsProvider implements Supplier<List<Map<String, Object>>> {
    @Override
    public List<Map<String, Object>> get() {
        return snapshot();
    }

    /** Narrow optional bridge for UUK versions without the extension registry. */
    public static List<Map<String, Object>> snapshot() {
        return List.of(
                option("loading_animation", ClientLoading::animation, ClientLoading::setAnimation),
                option("jei_enabled", () -> JeiSearchPreferences.current().enabled(),
                        value -> JeiSearchPreferences.save(JeiSearchPreferences.current().withEnabled(value))),
                option("jei_ae2_orderable", () -> JeiSearchPreferences.current().ae2Orderable(),
                        value -> JeiSearchPreferences.save(JeiSearchPreferences.current().withAe2Orderable(value))),
                option("jei_render_optimization", () -> JeiSearchPreferences.current().optimizeRendering(),
                        value -> JeiSearchPreferences.save(JeiSearchPreferences.current().withOptimizeRendering(value))));
    }

    private static Map<String, Object> option(String id, BooleanSupplier read, Predicate<Boolean> write) {
        String key = "sh_common_lib.assistant.setting." + id;
        return Map.of("id", "sh_common_lib:" + id, "nameKey", key, "descriptionKey", key + ".description",
                "read", read, "write", write);
    }
}
