// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

final class CosmicArtisanGesture {
    private static final long HOLD_MS = 300;

    enum Action { NONE, OPEN_CONFIG, OPEN_WHEEL, CLOSE_WHEEL }

    private boolean held;
    private boolean wheel;
    private long pressedAt;
    private Action pending = Action.NONE;

    void press(long now) {
        if (held) return;
        held = true;
        pressedAt = now;
    }

    void release(long now) {
        if (!held) return;
        held = false;
        if (wheel) pending = Action.CLOSE_WHEEL;
        else if (now - pressedAt < HOLD_MS) pending = Action.OPEN_CONFIG;
        wheel = false;
    }

    Action poll(long now) {
        if (pending != Action.NONE) {
            Action action = pending;
            pending = Action.NONE;
            return action;
        }
        if (held && !wheel && now - pressedAt >= HOLD_MS) {
            wheel = true;
            return Action.OPEN_WHEEL;
        }
        return Action.NONE;
    }

    void cancel() {
        held = false;
        wheel = false;
        pending = Action.NONE;
    }
}
