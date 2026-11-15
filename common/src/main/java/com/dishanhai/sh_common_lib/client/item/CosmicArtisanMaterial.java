// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;

/** SHCLIB liquid glass presets for the Artisan controls. */
enum CosmicArtisanMaterial {
    PANEL(GlassMaterial.preset(GlassVariant.REGULAR), 0xC9212738, 0xF0212738, 0xCF7C9EBB, 0x80202535),
    WHEEL(GlassMaterial.preset(GlassVariant.CLEAR), 0xB31B2639, 0xEC202A3E, 0xE88CBDE4, 0x43202535);

    private final GlassMaterial glass;
    private final int tint;
    private final int fallback;
    private final int edge;
    private final int scrim;

    CosmicArtisanMaterial(GlassMaterial glass, int tint, int fallback, int edge, int scrim) {
        this.glass = glass;
        this.tint = tint;
        this.fallback = fallback;
        this.edge = edge;
        this.scrim = scrim;
    }

    GlassMaterial glass() { return glass; }
    int tint() { return tint; }
    int fallback() { return fallback; }
    int edge() { return edge; }
    int scrim() { return scrim; }
}
