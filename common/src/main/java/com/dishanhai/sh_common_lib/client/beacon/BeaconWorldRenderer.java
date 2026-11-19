// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4fc;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconWorldMarkers.*;

public final class BeaconWorldRenderer {
    private static final GlassMaterial HUD_GLASS = new GlassMaterial(GlassVariant.CLEAR,
            GlassMaterial.preset(GlassVariant.CLEAR).optics(), GlassQuality.PERFORMANCE, GlassMaterial.Foreground.LIGHT);

    @FunctionalInterface
    public interface Beam {
        void draw(PoseStack pose, MultiBufferSource buffers, long gameTime, int height, int color,
                  float radius, float glowRadius);
    }

    private BeaconWorldRenderer() {}

    public static void render(PoseStack pose, Camera camera, Matrix4fc projection, Matrix4fc view, Frustum frustum,
                              MultiBufferSource.BufferSource buffers, Beam beam) {
        var client = Minecraft.getInstance();
        var cameraPosition = camera.getPosition();
        var beams = BeaconWorldMarkers.capture(client, cameraPosition, projection, view, frustum);
        if (beams.isEmpty()) return;

        long gameTime = client.level.getGameTime();
        for (var pos : beams) {
            pose.pushPose();
            pose.translate(pos.getX() - cameraPosition.x, pos.getY() - cameraPosition.y, pos.getZ() - cameraPosition.z);
            beam.draw(pose, buffers, gameTime, BEAM_HEIGHT, BEAM_COLOR, BEAM_RADIUS, BEAM_GLOW_RADIUS);
            pose.popPose();
        }
        buffers.endBatch();
    }

    public static void renderHud(GuiGraphics graphics) {
        var client = Minecraft.getInstance();
        var panels = BeaconWorldMarkers.takePanels(client);
        if (panels.isEmpty()) return;
        var preferences = UiPreferences.configured();
        var settings = ClientUiSettings.current();
        boolean glass = preferences.useBackdropBlur() && settings.backdrop().enabled()
                && !settings.dynamicTheme().lowPerformance()
                && ClientUiKernel.paintHudGlassBatch(graphics, panels.stream().map(BeaconWorldRenderer::hudGlass).toList());
        for (var panel : panels) {
            var bounds = panel.bounds();
            if (!glass) {
                LiquidGlassTheme.popover(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), preferences);
            }
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(bounds.x(), bounds.y(), 0);
                pose.scale(panel.scale(), panel.scale(), 1);
                for (var row : panel.rows()) {
                    graphics.drawString(client.font, row.text(), row.x(), row.y(), row.color(), true);
                }
            } finally {
                pose.popPose();
            }
        }
    }

    private static GlassDraw hudGlass(Panel panel) {
        var bounds = panel.bounds();
        var region = GlassRegion.rounded(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 5);
        float inset = Math.min(4, Math.min(region.width(), region.height()) * 0.25f);
        var text = new GlassDraw.ForegroundBounds(region.x() + inset, region.y() + inset,
                region.width() - 2 * inset, region.height() - 2 * inset);
        return new GlassDraw(region, HUD_GLASS, GlassDraw.Profile.SNELL, text);
    }
}
