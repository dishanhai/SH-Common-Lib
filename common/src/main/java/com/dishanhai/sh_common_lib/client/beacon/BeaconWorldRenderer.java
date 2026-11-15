// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconAim.LABEL_HEIGHT;
import static com.dishanhai.sh_common_lib.client.beacon.BeaconWorldMarkers.*;

public final class BeaconWorldRenderer {
    @FunctionalInterface
    public interface Beam {
        void draw(PoseStack pose, MultiBufferSource buffers, long gameTime, int height, int color,
                  float radius, float glowRadius);
    }

    private BeaconWorldRenderer() {}

    public static void render(PoseStack pose, Camera camera, float projectionY, Frustum frustum,
                              MultiBufferSource.BufferSource buffers, Beam beam) {
        var client = Minecraft.getInstance();
        var cameraPosition = camera.getPosition();
        var markers = BeaconWorldMarkers.visible(client, cameraPosition, camera.getLookVector(), projectionY, frustum);
        if (markers.isEmpty()) return;

        long gameTime = client.level.getGameTime();
        for (var marker : markers) {
            var pos = marker.pos();
            pose.pushPose();
            pose.translate(pos.getX() - cameraPosition.x, pos.getY() - cameraPosition.y, pos.getZ() - cameraPosition.z);
            beam.draw(pose, buffers, gameTime, BEAM_HEIGHT, BEAM_COLOR, BEAM_RADIUS, BEAM_GLOW_RADIUS);
            pose.translate(0.5, LABEL_HEIGHT, 0.5);
            pose.mulPose(camera.rotation());
            float scale = marker.labelScale();
            if (!marker.rows().isEmpty()) {
                pose.scale(scale, -scale, scale);
                for (var row : marker.rows()) line(client.font, buffers, pose, row);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void line(Font font, MultiBufferSource buffers, PoseStack pose, Row row) {
        pose.pushPose();
        pose.translate(0, row.y(), 0);
        pose.scale(row.scale(), row.scale(), row.scale());
        font.drawInBatch(row.text(), -row.width() / 2.0F, 0, row.color(), true, pose.last().pose(),
                buffers, Font.DisplayMode.SEE_THROUGH, row.background(), LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
