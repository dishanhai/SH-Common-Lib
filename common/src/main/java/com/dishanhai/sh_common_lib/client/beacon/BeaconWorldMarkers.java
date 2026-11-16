// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeacon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconAim.LABEL_HEIGHT;

final class BeaconWorldMarkers {
    static final int BEAM_COLOR = 0xFF80FF40;
    static final int BEAM_HEIGHT = 2048;
    static final float BEAM_RADIUS = 0.12F;
    static final float BEAM_GLOW_RADIUS = 0.18F;
    private static final int MAX_MARKERS = 3;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 3;
    private static final int MARGIN = 4;
    private static final long REVEAL_MILLIS = 180L;
    private static final Map<UUID, InterruptibleAnimation> REVEALS = new HashMap<>();
    private static List<Panel> pendingPanels = List.of();
    private static Focus focus;

    private BeaconWorldMarkers() {}

    static void reset() {
        REVEALS.clear();
        pendingPanels = List.of();
        focus = null;
    }

    private static boolean showMarkers(Minecraft client) {
        return BeaconClient.showHud(client) && client.level != null && client.screen == null && !client.options.hideGui;
    }

    static List<Panel> takePanels(Minecraft client) {
        var panels = pendingPanels;
        pendingPanels = List.of();
        return showMarkers(client) ? panels : List.of();
    }

    static CosmicBeacon.View focusedBeacon() {
        return focus == null ? null : focus.beacon();
    }

    static List<BlockPos> capture(Minecraft client, Vec3 camera, Matrix4fc projection, Matrix4fc view, Frustum frustum) {
        if (!showMarkers(client)) {
            reset();
            return List.of();
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        if (width < 2 * (MARGIN + PAD_X) + 1 || height < client.font.lineHeight + 2 * (MARGIN + PAD_Y)) {
            reset();
            return List.of();
        }
        var dimension = client.level.dimension();
        boolean canAim = BeaconClient.canAim(client);
        int crosshairX = width / 2;
        int crosshairY = height / 2;
        var viewProjection = new Matrix4f(projection).mul(view);
        boolean showBeams = BeaconBeamPreference.enabled(client);
        var projected = new ArrayList<Projected>(MAX_MARKERS);
        var beams = new ArrayList<BlockPos>(showBeams ? MAX_MARKERS : 0);
        var font = client.font;
        Projected aimed = null;
        double bestScore = Double.POSITIVE_INFINITY;
        var clip = new Vector4f();
        for (var beacon : BeaconClient.directory()) {
            if (!beacon.hud() || !beacon.point().dimension().equals(dimension)) continue;
            var pos = beacon.point().pos();
            if (showBeams && beams.size() < MAX_MARKERS && beamVisible(frustum, pos)) beams.add(pos);
            if (projected.size() >= MAX_MARKERS && (!canAim || bestScore < 0)) {
                if (!showBeams || beams.size() >= MAX_MARKERS) break;
                continue;
            }
            double dx = pos.getX() + 0.5 - camera.x;
            double dy = pos.getY() + LABEL_HEIGHT - camera.y;
            double dz = pos.getZ() + 0.5 - camera.z;
            clip.set((float) dx, (float) dy, (float) dz, 1).mul(viewProjection);
            if (!(clip.w > 0 && Math.abs(clip.x) <= clip.w && Math.abs(clip.y) <= clip.w && clip.z >= -clip.w)) continue;

            int x = Math.round((0.5F + 0.5F * clip.x / clip.w) * width);
            int y = Math.round((0.5F - 0.5F * clip.y / clip.w) * height);
            var name = BeaconClient.name(beacon);
            int compactWidth = Math.min(Math.min(96, width - 2 * MARGIN), Math.max(24, font.width(name) + 2 * PAD_X));
            float scale = BeaconAim.scale(Math.sqrt(dx * dx + dy * dy + dz * dz));
            var entry = new Projected(beacon, name, x, y, compactWidth, scale);
            if (projected.size() < MAX_MARKERS) projected.add(entry);
            if (canAim) {
                var compact = bounds(font, entry, compactWidth, compactHeight(font), width, height);
                double score = compact.score(crosshairX, crosshairY);
                if (focus != null && focus.beacon().id().equals(beacon.id())) {
                    var expanded = bounds(font, entry, focus.panelWidth(), focus.panelHeight(), width, height);
                    if (expanded.hits(crosshairX, crosshairY)) score = -1;
                }
                if (score < bestScore) {
                    aimed = entry;
                    bestScore = score;
                }
            }
        }
        if (aimed != null) {
            projected.remove(aimed);
            if (projected.size() == MAX_MARKERS) projected.removeLast();
            projected.add(aimed);
            var pos = aimed.beacon().point().pos();
            if (showBeams && (beams.remove(pos) || beamVisible(frustum, pos))) {
                if (beams.size() == MAX_MARKERS) beams.removeLast();
                beams.addFirst(pos);
            }
        }
        focus = null;
        var visibleLabels = new HashSet<UUID>();
        var panels = new ArrayList<Panel>(projected.size());
        var warpHint = aimed != null || !REVEALS.isEmpty() ? BeaconClient.warpHint(client) : null;
        long now = System.nanoTime() / 1_000_000L;
        boolean reducedMotion = UiPreferences.configured().reducedMotion();
        for (var entry : projected) {
            var beacon = entry.beacon();
            var pos = beacon.point().pos();
            boolean focused = entry == aimed;
            visibleLabels.add(beacon.id());
            float progress = reveal(beacon.id(), focused, now, reducedMotion);
            var text = new ArrayList<Component>(4);
            text.add(entry.name());
            if (progress > 0) {
                text.add(BeaconClient.text("world_distance", Math.round(Vec3.atCenterOf(pos).distanceTo(client.player.position()))));
                text.add(BeaconClient.text("world_coordinates", pos.getX(), pos.getY(), pos.getZ()));
                if (warpHint != null) text.add(warpHint);
            }
            var panel = layout(font, entry, text, progress, warpHint != null && text.size() == 4, width, height);
            if (focused) focus = new Focus(beacon, panel.contentWidth(), panel.contentHeight());
            panels.add(panel);
        }
        REVEALS.keySet().retainAll(visibleLabels);
        pendingPanels = panels;
        return beams;
    }

    private static boolean beamVisible(Frustum frustum, BlockPos pos) {
        return frustum.isVisible(new AABB(pos).expandTowards(0, BEAM_HEIGHT, 0));
    }

    private static float reveal(UUID id, boolean focused, long now, boolean reducedMotion) {
        var animation = focused ? REVEALS.computeIfAbsent(id, key -> new InterruptibleAnimation(Spring.FLUID_MORPH))
                : REVEALS.get(id);
        if (animation == null) return 0;
        float target = focused ? 1 : 0;
        if (animation.target() != target) {
            animation.blendTo(target, now, 0, REVEAL_MILLIS, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        }
        float progress = animation.sample(now, reducedMotion);
        if (!focused && progress == 0) REVEALS.remove(id);
        return progress;
    }

    private static Panel layout(Font font, Projected entry, List<Component> lines, float progress, boolean hint,
                                int screenWidth, int screenHeight) {
        int widthLimit = Math.min(screenWidth - 2 * MARGIN, Math.max(96, Math.min(168, screenWidth / 2)));
        int fullWidth = entry.compactWidth();
        for (var line : lines) fullWidth = Math.max(fullWidth, Math.min(widthLimit, font.width(line) + 2 * PAD_X));
        int rowHeight = font.lineHeight + 2;
        int compactHeight = compactHeight(font);
        int fullHeight = compactHeight + (lines.size() - 1) * rowHeight;
        int contentWidth = Math.round(GuiMotion.lerp(entry.compactWidth(), fullWidth, progress));
        int contentHeight = Math.round(GuiMotion.lerp(compactHeight, fullHeight, progress));
        var bounds = bounds(font, entry, contentWidth, contentHeight, screenWidth, screenHeight);
        var rows = new ArrayList<Row>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            int rowY = PAD_Y + index * rowHeight;
            if (Math.round((rowY + font.lineHeight + PAD_Y) * entry.scale()) > bounds.height()) break;
            var line = lines.get(index);
            int maxTextWidth = contentWidth - 2 * PAD_X;
            var text = fitText(font, line, maxTextWidth);
            int color = index == 0 ? GuiMotion.lerpColor(0xFFF5F7FA, 0xFFFFE57A, progress)
                    : GuiMotion.lerpColor(0x00FFFFFF, hint && index == 3 ? 0xFFFFE57A : 0xFFFFFFFF, progress);
            rows.add(new Row(text, (contentWidth - font.width(text)) / 2, rowY, color));
        }
        return new Panel(bounds, entry.scale(), contentWidth, contentHeight, rows);
    }

    private static int compactHeight(Font font) {
        return font.lineHeight + 2 * PAD_Y;
    }

    private static BeaconAim.Bounds bounds(Font font, Projected entry, int contentWidth, int contentHeight,
                                           int screenWidth, int screenHeight) {
        int width = Math.min(screenWidth - 2 * MARGIN, Math.round(contentWidth * entry.scale()));
        int height = Math.min(screenHeight - 2 * MARGIN, Math.round(contentHeight * entry.scale()));
        int x = Math.clamp(entry.x() - width / 2, MARGIN, screenWidth - width - MARGIN);
        int anchorGap = (int) (7 * entry.scale());
        int y = entry.y() + anchorGap;
        int expandedHeight = Math.round((compactHeight(font) + 3 * (font.lineHeight + 2)) * entry.scale());
        if (y + expandedHeight > screenHeight - MARGIN && entry.y() - anchorGap >= expandedHeight + MARGIN) {
            y = entry.y() - anchorGap - height;
        }
        y = Math.clamp(y, MARGIN, screenHeight - height - MARGIN);
        return new BeaconAim.Bounds(x, y, width, height, entry.scale());
    }

    private static FormattedCharSequence fitText(Font font, Component line, int width) {
        if (font.width(line) <= width) return line.getVisualOrderText();
        var ellipsis = BeaconClient.text("world_truncated");
        int remaining = width - font.width(ellipsis);
        if (remaining < 0) return FormattedCharSequence.EMPTY;
        return Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(line, remaining), ellipsis));
    }

    private record Projected(CosmicBeacon.View beacon, Component name, int x, int y, int compactWidth, float scale) {}

    private record Focus(CosmicBeacon.View beacon, int panelWidth, int panelHeight) {}

    record Panel(BeaconAim.Bounds bounds, float scale, int contentWidth, int contentHeight, List<Row> rows) {}

    record Row(FormattedCharSequence text, int x, int y, int color) {}
}
