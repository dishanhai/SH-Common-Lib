// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.List;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconAim.LABEL_HEIGHT;
import static com.dishanhai.sh_common_lib.client.beacon.BeaconWorldMarkers.*;

public final class BeaconWorldRenderer {
    private static final ContextKey<RenderData> MARKERS =
            new ContextKey<>(Identifier.fromNamespaceAndPath("sh_common_lib", "cosmic_beacon_markers"));

    private BeaconWorldRenderer() {}

    public static void extract(ExtractLevelRenderStateEvent event) {
        var client = Minecraft.getInstance();
        var camera = event.getCamera();
        float projectionY = event.getRenderState().cameraRenderState.projectionMatrix.m11();
        var markers = BeaconWorldMarkers.visible(client, camera.position(), camera.forwardVector(), projectionY,
                event.getFrustum());
        if (markers.isEmpty()) return;
        float time = Math.floorMod(event.getLevel().getGameTime(), 40)
                + event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        event.getRenderState().setRenderData(MARKERS, new RenderData(markers, time));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        var level = event.getLevelRenderState();
        var data = level.getRenderData(MARKERS);
        if (data == null) return;
        var camera = level.cameraRenderState;
        var pose = event.getPoseStack();
        var nodes = event.getSubmitNodeCollector();
        for (var marker : data.markers()) {
            var pos = marker.pos();
            pose.pushPose();
            pose.translate(pos.getX() - camera.pos.x, pos.getY() - camera.pos.y, pos.getZ() - camera.pos.z);
            BeaconRenderer.submitBeaconBeam(pose, nodes, BeaconRenderer.BEAM_LOCATION, 1.0F,
                    data.time(), 1, BEAM_HEIGHT, BEAM_COLOR, BEAM_RADIUS, BEAM_GLOW_RADIUS);
            pose.translate(0.5, LABEL_HEIGHT, 0.5);
            pose.mulPose(camera.orientation);
            float scale = marker.labelScale();
            if (!marker.rows().isEmpty()) {
                pose.scale(scale, -scale, scale);
                for (var row : marker.rows()) line(nodes, pose, row);
            }
            pose.popPose();
        }
    }

    private static void line(SubmitNodeCollector nodes, PoseStack pose, Row row) {
        pose.pushPose();
        pose.translate(0, row.y(), 0);
        pose.scale(row.scale(), row.scale(), row.scale());
        nodes.submitText(pose, -row.width() / 2.0F, 0, row.text(), true, Font.DisplayMode.SEE_THROUGH,
                LightCoordsUtil.FULL_BRIGHT, row.color(), row.background(), 0);
        pose.popPose();
    }

    private record RenderData(List<Marker> markers, float time) {}
}
