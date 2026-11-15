// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_common_lib.api.client.ulk.SearchModNames;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Creative-tab captions supply optional localized namespace labels. */
final class JeiModNames {
    private JeiModNames() {}
    static Map<String, List<String>> snapshot() {
        var labels = new ArrayList<SearchModNames.Label>();
        var tabs = BuiltInRegistries.CREATIVE_MODE_TAB;
        for (var tab : tabs) {
            try {
                var id = tabs.getKey(tab);
                if (id == null || id.getNamespace().equals("minecraft")) continue;
                String text = tab.getDisplayName().getString();
                if (!text.isBlank()) labels.add(new SearchModNames.Label(id.getNamespace(), text));
            } catch (RuntimeException | LinkageError ignored) { /* A broken optional tab must not disable JEI search. */ }
        }
        return SearchModNames.index(labels);
    }
}
