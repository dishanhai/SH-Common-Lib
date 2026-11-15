// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

/** Prevents a handled recipe key from also inserting a character in the same screen. */
public final class RecipeKeyGuard {
    private Object screen;
    private int key = -1;
    private long time;

    public void arm(Object screen, int key, long now) {
        this.screen = screen;
        this.key = key;
        this.time = now;
    }

    public int pendingKey() { return key; }

    public boolean take(Object current, int pressedKey, boolean held, long now) {
        boolean consume = screen != null && screen == current && key == pressedKey && held
                && now - time >= 0 && now - time < 250;
        clear();
        return consume;
    }

    public void clear() { screen = null; key = -1; }
}
