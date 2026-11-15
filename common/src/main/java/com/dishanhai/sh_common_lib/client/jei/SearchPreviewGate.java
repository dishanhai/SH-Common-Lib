// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import java.util.Objects;

/** Wait for a stable selection before resolving potentially expensive recipes. */
final class SearchPreviewGate {
    private final long delay;
    private String key;
    private long since;

    SearchPreviewGate(long delay) { this.delay = delay; }

    boolean ready(String next, long now) {
        if (!Objects.equals(key, next)) {
            key = next;
            since = now;
        }
        return next != null && now - since >= delay;
    }

    void reset() { key = null; }
}
