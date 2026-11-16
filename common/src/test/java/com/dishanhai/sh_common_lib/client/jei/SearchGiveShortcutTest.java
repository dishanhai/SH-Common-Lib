// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_common_lib.api.client.ulk.ClientIngredientGive;
import com.dishanhai.sh_common_lib.api.client.ulk.ClientIngredientGive.Mode;

public final class SearchGiveShortcutTest {
    private static final int ENTER = 257, KEYPAD_ENTER = 335, KEY_A = 65;
    private static final int CTRL = 2, SHIFT = 1, ALT = 4, SUPER = 8;
    public static void main(String[] args) {
        // GLFW protocol codes keep this contract independent of the native window runtime.
        for (int key : new int[] {ENTER, KEYPAD_ENTER}) {
            require(SearchGiveShortcut.mode(key, CTRL) == Mode.ONE, "Ctrl+Enter gives one");
            require(SearchGiveShortcut.mode(key, CTRL | SHIFT) == Mode.STACK,
                    "Ctrl+Shift+Enter gives a stack");
            require(SearchGiveShortcut.mode(key, CTRL | SHIFT | ALT) == Mode.INFINITY_CELL,
                    "Ctrl+Shift+Alt+Enter gives a bound cell");
            for (int modifiers : new int[] {0, SHIFT, ALT,
                    CTRL | ALT, CTRL | SUPER}) {
                require(SearchGiveShortcut.mode(key, modifiers) == null, "unrelated Enter modifiers remain available");
            }
        }
        require(SearchGiveShortcut.mode(KEY_A, CTRL) == null, "unrelated keys remain available");
        require(Mode.ONE.amount(false, 64) == 1 && Mode.ONE.amount(true, 1) == 1, "single item or bucket");
        require(Mode.STACK.amount(false, 64) == 64 && Mode.STACK.amount(false, 16) == 16
                && Mode.STACK.amount(false, 1) == 1, "items respect their own stack limit");
        require(Mode.STACK.amount(true, 1) == 16, "bulk fluid gives sixteen buckets regardless of bucket stack limit");
        require(Mode.INFINITY_CELL.amount(true, 1) == 1, "one bound cell");
        require(!ClientIngredientGive.request("item", Mode.ONE), "missing backend does not give");
        boolean[] enabled = {false};
        int[] requests = {0};
        ClientIngredientGive.install(new ClientIngredientGive.Backend() {
            public boolean enabled() { return enabled[0]; }
            public boolean infinityCellsAvailable() { return true; }
            public boolean give(Object ingredient, Mode mode) { requests[0]++; return true; }
        });
        require(!ClientIngredientGive.request("item", Mode.ONE) && requests[0] == 0
                && !ClientIngredientGive.infinityCellsAvailable(), "cheat mode gates all give operations");
        enabled[0] = true;
        require(ClientIngredientGive.request("item", Mode.STACK) && requests[0] == 1,
                "enabled request reaches the permission-checked backend once");
        require(!ClientIngredientGive.request(null, Mode.ONE) && !ClientIngredientGive.request("item", null)
                && requests[0] == 1, "invalid selections do not send requests");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
