// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.render.WorldOverlayGeometry;
import com.dishanhai.sh_better_ui.kernel.api.render.WorldOverlayGeometry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable projected frame shared by the legacy and extracted GUI render backends. */
public final class AccelerationWorldMarkers {
    public record Stroke(Line line, int color) {}
    public record Label(Point point, Component text, int color, float progress) {}
    public record Frame(List<Stroke> strokes, List<Label> labels, Component editing) {}
    private static Frame frame = new Frame(List.of(), List.of(), null);
    private static Object lastSnapshot;
    private static long snapshotTick;

    private AccelerationWorldMarkers() {}

    public static Frame frame() { return visible() ? frame : new Frame(List.of(), List.of(), null); }

    private static boolean visible() {
        var client = Minecraft.getInstance();
        return client.level != null && AccelerationClient.holding(client) && client.screen == null
                && !client.options.hideGui;
    }

    public static void capture(Vec3 camera, Matrix4fc projection, Matrix4fc view, String dimension) {
        var client = Minecraft.getInstance();
        var snapshot = AccelerationClient.snapshot();
        if (!visible() || snapshot == null) { frame = new Frame(List.of(), List.of(), null); return; }
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        if (lastSnapshot != snapshot) { lastSnapshot = snapshot; snapshotTick = client.level.getGameTime(); }
        int elapsed = (int) Math.max(0, Math.min(600, client.level.getGameTime() - snapshotTick));
        var matrix = new Matrix4f(projection).mul(view);
        var strokes = new ArrayList<Stroke>();
        var labels = new ArrayList<Label>();
        boolean reduced = UiPreferences.configured().reducedMotion();
        double phase = reduced ? 0 : (System.nanoTime() / 1_000_000L % 12000) / 12000.0 * Math.PI * 2;
        for (var range : snapshot.ranges()) {
            if (!dimension.equals(range.dimension()) || distance(camera, range.pos()) > 9216) continue;
            double x = range.pos().getX() - (range.x() - 1) / 2;
            double y = range.pos().getY() - (range.y() - 1) / 2;
            double z = range.pos().getZ() - (range.z() - 1) / 2;
            add(strokes, WorldOverlayGeometry.box(x - .015, y - .015, z - .015,
                    range.x() + .03, range.y() + .03, range.z() + .03), camera, matrix, width, height, phase,
                    range.enabled() && snapshot.settings().master() ? 0 : 0x998A805C);
            add(strokes, WorldOverlayGeometry.clock(range.pos().getX() + .5, range.pos().getY() + .6,
                    range.pos().getZ() + .5, .4, range.enabled() && snapshot.settings().master() ? phase : 0),
                    camera, matrix, width, height, phase, 0xB0A0F7FF);
        }
        var visuals = snapshot.visuals().stream().filter(v -> dimension.equals(v.dimension()))
                .filter(v -> snapshot.settings().editing() == 0 || v.kind() >= 2)
                .sorted(Comparator.comparingDouble(v -> distance(camera, v.pos()))).limit(160).toList();
        for (var visual : visuals) {
            var pos = visual.pos();
            if (distance(camera, pos) > 9216) continue;
            double x = pos.getX() + .5, y = pos.getY() + .85, z = pos.getZ() + .5;
            int kind = visual.kind();
            if (kind == 1) {
                var entity = client.level.getEntity(visual.entityId());
                if (entity == null || entity.isRemoved()) continue;
                x = entity.getX(); y = entity.getY() + entity.getBbHeight() + .35; z = entity.getZ();
            } else {
                int color = kind == 2 ? 0xFF57EE8B : visual.sleeping() ? 0xFFFFD65A : 0;
                add(strokes, WorldOverlayGeometry.box(pos.getX() - .012, pos.getY() - .012, pos.getZ() - .012,
                        1.024, 1.024, 1.024), camera, matrix, width, height, phase, color);
                // Move the label towards the camera-facing surface, outside the block texture.
                double dx = camera.x - x, dz = camera.z - z;
                if (Math.abs(dx) >= Math.abs(dz)) x += Math.copySign(.525, dx);
                else z += Math.copySign(.525, dz);
            }
            Point point = project(x, y, z, camera, matrix, width, height);
            if (point == null || kind == 2) continue;
            if (kind >= 3) {
                labels.add(new Label(point, AccelerationClient.text("mark_" + kind),
                        kind == 3 || kind == 5 ? 0xFFFF7171 : 0xFF78FF99, -1));
            } else {
                boolean permanent = visual.remaining() < 0;
                int remaining = Math.max(0, visual.remaining() - elapsed);
                float progress = permanent ? 1 : Math.max(0, Math.min(1, remaining / (float) Math.max(1, visual.duration())));
                labels.add(new Label(point, AccelerationClient.text(permanent ? "infinite" : "remaining",
                        (remaining + 19) / 20), 0xFFFFFFFF, progress));
            }
        }
        int editingKind = snapshot.settings().editing() == 1
                ? snapshot.settings().accelerationWhitelist() ? 4 : 3 : snapshot.settings().sleepWhitelist() ? 6 : 5;
        Component editing = snapshot.settings().editing() == 0 ? null
                : AccelerationClient.text("editing_current", AccelerationClient.text("mark_" + editingKind));
        frame = new Frame(List.copyOf(strokes), List.copyOf(labels), editing);
    }

    private static double distance(Vec3 camera, BlockPos pos) {
        return camera.distanceToSqr(Vec3.atCenterOf(pos));
    }

    private static void add(List<Stroke> strokes, List<Edge> edges, Vec3 camera, Matrix4f matrix,
                            int width, int height, double phase, int fixed) {
        int i = 0;
        for (var edge : edges) {
            var line = WorldOverlayGeometry.line(clip(edge.a().x(), edge.a().y(), edge.a().z(), camera, matrix),
                    clip(edge.b().x(), edge.b().y(), edge.b().z(), camera, matrix), width, height);
            if (line != null) strokes.add(new Stroke(line, fixed != 0 ? fixed : rainbow(phase + i * .47)));
            i++;
        }
    }

    private static Point project(double x, double y, double z, Vec3 camera, Matrix4f matrix, int width, int height) {
        return WorldOverlayGeometry.project(clip(x, y, z, camera, matrix), width, height);
    }

    private static Clip clip(double x, double y, double z, Vec3 camera, Matrix4f matrix) {
        var v = new Vector4f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1).mul(matrix);
        return new Clip(v.x, v.y, v.z, v.w);
    }

    private static int rainbow(double phase) {
        int r = (int) (175 + 80 * Math.sin(phase));
        int g = (int) (175 + 80 * Math.sin(phase + 2.094));
        int b = (int) (175 + 80 * Math.sin(phase + 4.189));
        return 0xE0000000 | r << 16 | g << 8 | b;
    }
}
