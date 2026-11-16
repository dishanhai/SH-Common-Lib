// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import java.nio.file.Path;

/** Version-specific desktop integration for the recipe workspace. */
final class RecipeEditorPlatform {
    private RecipeEditorPlatform() {}
    static void openDirectory(Path directory) { net.minecraft.Util.getPlatform().openFile(directory.toFile()); }
}
