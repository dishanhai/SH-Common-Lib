// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import net.minecraft.client.renderer.item.TrackingItemStackRenderState;

/** Gives animated JEI icons a short-lived key in Minecraft's GUI item atlas. */
public final class JeiAnimatedIconRendering {
    private static final long REFRESH_NANOS = 50_000_000L;
    private static boolean extracting;
    private static long extractionTime;

    private JeiAnimatedIconRendering() {}

    public static void extractBatch(Runnable extract) {
        boolean previousExtracting = extracting;
        long previousTime = extractionTime;
        extracting = true;
        extractionTime = System.nanoTime();
        try {
            extract.run();
        } finally {
            extracting = previousExtracting;
            extractionTime = previousTime;
        }
    }

    public static boolean useAtlasCache(TrackingItemStackRenderState item) {
        if (!extracting || !item.isAnimated() || item.isOversizedInGui()) return false;
        int modelHash = item.getModelIdentity().hashCode();
        long phase = Math.floorMod(modelHash * 0x9E3779B9L, REFRESH_NANOS);
        long interval = Math.floorDiv(extractionTime + phase, REFRESH_NANOS);
        item.appendModelIdentityElement(new RefreshInterval(interval));
        return true;
    }

    private record RefreshInterval(long number) {}
}
