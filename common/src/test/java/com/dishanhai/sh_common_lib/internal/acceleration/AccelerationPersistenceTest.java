// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

import com.google.gson.Gson;

/** Exercises the actual save model, including cache reinitialization and independent lists. */
public final class AccelerationPersistenceTest {
    public static void main(String[] args) {
        var state = new AccelerationManager.State();
        var owner = new AccelerationManager.Owner();
        owner.opened = true;
        owner.hints = 7;
        owner.master = false;
        state.owners.put("owner", owner);
        var a = new AccelerationManager.Range();
        a.id = "first";
        a.x = 15; a.y = 7; a.z = 1;
        a.rate = 1024;
        a.enabled = false;
        a.accelerationWhitelist = true;
        a.accelerationMarks.add(10L);
        a.sleepMarks.add(11L);
        a.cache.put(10L, true);
        a.pending.put(10L, 999L);
        var b = new AccelerationManager.Range();
        b.id = "second";
        b.rate = 4;
        b.accelerationMarks.add(12L);
        owner.ranges.add(a); owner.ranges.add(b);
        var gson = new Gson();
        var saved = gson.toJson(state);
        require(!saved.contains("pending") && !saved.contains("cache"), "live caches must not enter world saves");
        var restored = gson.fromJson(saved, AccelerationManager.State.class);
        var player = restored.owners.get("owner");
        require(player.opened && player.hints == 7 && !player.master, "per-world hints and paused master must survive");
        require(player.ranges.size() == 2, "pausing must retain both range records");
        var first = player.ranges.get(0);
        var second = player.ranges.get(1);
        require(!first.enabled && first.rate == 1024 && first.x == 15 && first.y == 7 && first.z == 1,
                "disabled range configuration must survive");
        require(first.cache.isEmpty() && first.pending.isEmpty() && first.sleeping.isEmpty(), "live caches must restart empty");
        require(first.accelerationMarks.contains(10L) && !first.accelerationMarks.contains(11L)
                && first.sleepMarks.contains(11L), "acceleration and sleep marks must remain independent");
        first.accelerationMarks.clear();
        require(second.accelerationMarks.contains(12L) && second.rate == 4, "ranges must not share marks or rate state");
        player.ranges.removeIf(range -> range.id.equals("first"));
        var reclaimed = gson.fromJson(gson.toJson(restored), AccelerationManager.State.class);
        require(reclaimed.owners.get("owner").ranges.size() == 1
                && reclaimed.owners.get("owner").ranges.get(0).id.equals("second"), "reclamation must survive reload");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
