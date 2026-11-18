// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import com.dishanhai.sh_common_lib.api.ulk.recipe.*;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Client convenience facade; server consumers use RecipeReferences and MinecraftRecipeLookup. */
public final class RecipeRegistryValidation {
    private RecipeRegistryValidation() {}

    public static List<RecipeIssue> validate(RecipeDocument doc) {
        var level = Minecraft.getInstance().level;
        return RecipeReferences.validate(doc, RecipeTarget.NEOFORGE_1_21_1,
                level == null ? MinecraftRecipeLookup.builtInOnly() : MinecraftRecipeLookup.of(level.registryAccess()));
    }
}
