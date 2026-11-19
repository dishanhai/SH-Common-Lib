// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.List;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconWorldMarkers.*;

public final class BeaconWorldRenderer {
    private static final ContextKey<RenderData> MARKERS =
            new ContextKey<>(Identifier.fromNamespaceAndPath("sh_common_lib", "cosmic_beacon_markers"));

    private BeaconWorldRenderer() {}

    public static void extract(ExtractLevelRenderStateEvent event) {
        var client = Minecraft.getInstance();
        var camera = event.getRenderState().cameraRenderState;
        var beams = BeaconWorldMarkers.capture(client, camera.pos, camera.projectionMatrix, camera.viewRotationMatrix,
                event.getFrustum());
        if (beams.isEmpty()) return;
        float time = Math.floorMod(event.getLevel().getGameTime(), 40)
                + event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        event.getRenderState().setRenderData(MARKERS, new RenderData(beams, time));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        var level = event.getLevelRenderState();
        var data = level.getRenderData(MARKERS);
        if (data == null) return;
        var camera = level.cameraRenderState;
        var pose = event.getPoseStack();
        var nodes = event.getSubmitNodeCollector();
        for (var pos : data.beams()) {
            pose.pushPose();
            pose.translate(pos.getX() - camera.pos.x, pos.getY() - camera.pos.y, pos.getZ() - camera.pos.z);
            BeaconRenderer.submitBeaconBeam(pose, nodes, BeaconRenderer.BEAM_LOCATION, 1.0F,
                    data.time(), 1, BEAM_HEIGHT, BEAM_COLOR, BEAM_RADIUS, BEAM_GLOW_RADIUS);
            pose.popPose();
        }
    }

    public static void renderHud(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        for (var panel : BeaconWorldMarkers.takePanels(client)) {
            var bounds = panel.bounds();
            ClientPanels.hud(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
            var pose = graphics.pose();
            pose.pushMatrix();
            try {
                pose.translate(bounds.x(), bounds.y()).scale(panel.scale());
                for (var row : panel.rows()) {
                    graphics.text(client.font, row.text(), row.x(), row.y(), row.color(), true);
                }
            } finally {
                pose.popMatrix();
            }
        }
    }

    private record RenderData(List<BlockPos> beams, float time) {}
}
