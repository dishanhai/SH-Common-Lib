// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.nio.file.Path;
import java.util.Objects;

/** Client-selected root directory for opt-in JSON exports. */
public record RecipeExportSettings(boolean customJsonExport, String jsonExportRoot) {
    public static final RecipeExportSettings DEFAULT = new RecipeExportSettings(false, RecipeWorkspace.DEFAULT_DIRECTORY);

    public RecipeExportSettings {
        jsonExportRoot = Objects.requireNonNull(jsonExportRoot).strip();
    }

    public Path resolveRoot(Path gameDirectory) {
        if (jsonExportRoot.isEmpty()) throw new IllegalArgumentException("Missing JSON export directory");
        Path path = Path.of(jsonExportRoot);
        if (!path.isAbsolute() && path.getRoot() != null) throw new IllegalArgumentException("Ambiguous JSON export directory");
        return gameDirectory.toAbsolutePath().resolve(path).normalize();
    }
}
