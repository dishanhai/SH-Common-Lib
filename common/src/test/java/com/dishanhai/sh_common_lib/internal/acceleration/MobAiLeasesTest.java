// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

import java.util.HashMap;
import java.util.Map;

public final class MobAiLeasesTest {
    public static void main(String[] args) {
        var leases = new MobAiLeases<String, Mob>(new Adapter());
        var normal = new Mob(false);
        var alreadyDisabled = new Mob(true);
        var requests = new HashMap<String, Mob>();
        requests.put("normal", normal);
        requests.put("disabled", alreadyDisabled);
        leases.reconcile(requests);
        check(normal.disabled && Boolean.FALSE.equals(normal.original), "normal mob enters lease");
        check(alreadyDisabled.disabled && Boolean.TRUE.equals(alreadyDisabled.original), "original disabled recorded");

        // Two players requesting one mob collapse to the same key. Removing one player's
        // request leaves the mob in the desired set until the other request ends.
        boolean first = true, second = true;
        first = false;
        requests.clear();
        if (first || second) requests.put("normal", normal);
        leases.reconcile(requests);
        check(normal.disabled && normal.original != null, "second owner retains lease");
        check(alreadyDisabled.disabled && alreadyDisabled.original == null, "original disabled remains disabled");
        second = false;
        requests.clear();
        if (first || second) requests.put("normal", normal);
        leases.reconcile(requests);
        check(!normal.disabled && normal.original == null, "last release restores original state");

        requests.put("normal", normal);
        leases.reconcile(requests);
        var reloaded = new Mob(true);
        reloaded.original = normal.original;
        var newSession = new MobAiLeases<String, Mob>(new Adapter());
        newSession.recover("normal", reloaded);
        check(!reloaded.disabled && reloaded.original == null, "stale lease restored after reload");
        var reloadedDisabled = new Mob(true);
        reloadedDisabled.original = true;
        newSession.recover("disabled", reloadedDisabled);
        check(reloadedDisabled.disabled && reloadedDisabled.original == null, "reload preserves original disabled AI");
        leases.recover("normal", reloaded);
        check(!normal.disabled && normal.original == null, "old instance released when UUID reloads");
        requests.put("normal", reloaded);
        leases.reconcile(requests);
        check(reloaded.disabled && Boolean.FALSE.equals(reloaded.original), "reloaded active mob gets a fresh lease");
        leases.close();
        check(!reloaded.disabled && reloaded.original == null, "shutdown releases live lease");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static final class Mob {
        boolean disabled;
        Boolean original;
        Mob(boolean disabled) { this.disabled = disabled; }
    }

    private static final class Adapter implements MobAiLeases.Adapter<Mob> {
        public boolean marked(Mob mob) { return mob.original != null; }
        public boolean original(Mob mob) { return mob.original; }
        public void mark(Mob mob, boolean original) { mob.original = original; }
        public void clear(Mob mob) { mob.original = null; }
        public boolean disabled(Mob mob) { return mob.disabled; }
        public void disable(Mob mob, boolean value) { mob.disabled = value; }
    }
}
