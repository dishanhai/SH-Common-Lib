// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4fc;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconWorldMarkers.*;

public final class BeaconWorldRenderer {
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
        for (var panel : panels) {
            var bounds = panel.bounds();
            LiquidGlassTheme.popover(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), preferences);
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
}
