// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

public final class AccelerationPolicy {
    private static final int[] RATES = {0, 2, 4, 16, 32, 64, 128, 256, 512, 1024};

    private AccelerationPolicy() {}

    public static boolean validRate(int rate) {
        for (int value : RATES) if (value == rate) return true;
        return false;
    }

    public static int shift(int rate, int direction) {
        int index = 0;
        for (int i = 0; i < RATES.length; i++) if (RATES[i] == rate) index = i;
        return RATES[Math.max(0, Math.min(RATES.length - 1, index + Integer.signum(direction)))];
    }

    public static boolean allowed(boolean whitelist, boolean marked) {
        return whitelist == marked;
    }
}
