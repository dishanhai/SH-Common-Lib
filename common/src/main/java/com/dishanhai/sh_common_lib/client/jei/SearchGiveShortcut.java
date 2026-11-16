// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_common_lib.api.client.ulk.ClientIngredientGive.Mode;
import org.lwjgl.glfw.GLFW;

final class SearchGiveShortcut {
    private SearchGiveShortcut() {}

    static Mode mode(int key, int modifiers) {
        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) return null;
        return switch (modifiers) {
            case GLFW.GLFW_MOD_CONTROL -> Mode.ONE;
            case GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT -> Mode.STACK;
            case GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT -> Mode.INFINITY_CELL;
            default -> null;
        };
    }
}
