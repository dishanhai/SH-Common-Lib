// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import java.util.Locale;

/** Keeps large numeric HUD values compact without changing non-numeric text. */
final class HudNumberFormatter {
    private static final double SCIENTIFIC_THRESHOLD = 100_000d;

    private HudNumberFormatter() {}

    static String format(Number value) {
        double number = value.doubleValue();
        return useScientific(number) ? scientific(number) : value.toString();
    }

    static String format(double value, String ordinaryFormat) {
        if (!useScientific(value)) return String.format(Locale.ROOT, ordinaryFormat, value);
        return scientific(value) + (ordinaryFormat.endsWith("%%") ? "%" : "");
    }

    private static boolean useScientific(double value) {
        return Double.isFinite(value) && Math.abs(value) >= SCIENTIFIC_THRESHOLD;
    }

    private static String scientific(double value) {
        String formatted = String.format(Locale.ROOT, "%.2E", value);
        int exponent = formatted.indexOf('E');
        return formatted.substring(0, exponent + 1) + Integer.parseInt(formatted.substring(exponent + 1));
    }
}
