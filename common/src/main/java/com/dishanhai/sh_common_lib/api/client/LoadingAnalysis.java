// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client;

import com.dishanhai.sh_common_lib.api.performance.PerformanceSession;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;

/** Client-only access to the built-in SHCLIB loading analysis. */
public final class LoadingAnalysis {
    private LoadingAnalysis() {}

    /**
     * Returns the active startup or world-entry profile, or null when none is collecting.
     * Capture the returned session before scheduling work so it stays attributed to that load.
     * SHCLIB owns its lifecycle and calls {@link PerformanceSession#finish(String)} when loading ends.
     */
    public static PerformanceSession currentSession() {
        return ClientLoading.currentProfile();
    }
}
