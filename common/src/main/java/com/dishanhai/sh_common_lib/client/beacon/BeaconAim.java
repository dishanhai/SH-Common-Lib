// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

final class BeaconAim {
    static final double LABEL_HEIGHT = 1.35;
    private static final int PADDING = 8;
    private static final float MIN_SCALE = 0.5F;
    private static final double SCALE_START_DISTANCE = 24;
    private static final double SCALE_END_DISTANCE = 120;

    private BeaconAim() {}

    static float scale(double distance) {
        double progress = Math.clamp((distance - SCALE_START_DISTANCE) / (SCALE_END_DISTANCE - SCALE_START_DISTANCE), 0, 1);
        return (float) (1 - (1 - MIN_SCALE) * progress);
    }

    record Bounds(int x, int y, int width, int height, float scale) {
        boolean hits(int crosshairX, int crosshairY) {
            int padding = Math.round(PADDING * scale);
            return crosshairX >= x - padding && crosshairX < x + width + padding
                    && crosshairY >= y - padding && crosshairY < y + height + padding;
        }

        double score(int crosshairX, int crosshairY) {
            if (!hits(crosshairX, crosshairY)) return Double.POSITIVE_INFINITY;
            double dx = crosshairX - (x + width * 0.5);
            double dy = crosshairY - (y + height * 0.5);
            double centerDistance = dx * dx + dy * dy;
            // Rank hits on the glass ahead of hits in its padding.
            return crosshairX >= x && crosshairX < x + width && crosshairY >= y && crosshairY < y + height
                    ? centerDistance / (1 + centerDistance) : 1 + centerDistance;
        }
    }
}
