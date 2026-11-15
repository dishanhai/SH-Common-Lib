// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconAim.LABEL_HEIGHT;

final class BeaconWorldMarkers {
    static final int BEAM_COLOR = 0xFF80FF40;
    static final int BEAM_HEIGHT = 2048;
    static final float BEAM_RADIUS = 0.12F;
    static final float BEAM_GLOW_RADIUS = 0.18F;
    private static final int TEXT_BACKGROUND = 0xA8000000;
    private static final int FOCUSED_COLOR = 0xFFFFE57A;
    private static final float ICON_SCALE = 2.0F;
    private static final int LINE_GAP = 3;
    private static final FormattedCharSequence DIAMOND = FormattedCharSequence.codepoint(0x25C6, Style.EMPTY);
    private static final int MAX_MARKERS = 3;

    private BeaconWorldMarkers() {}

    static List<Marker> visible(Minecraft client, Vec3 camera, Vector3fc forward, float projectionY, Frustum frustum) {
        BeaconAim.highlight(null);
        var player = client.player;
        if (!BeaconClient.showHud(client) || player == null || player.isSpectator()
                || client.level == null || client.screen != null || client.options.hideGui) return List.of();
        var dimension = client.level.dimension();
        var aimed = BeaconClient.aimedBeacon(client);
        var warpHint = aimed == null ? null : BeaconClient.warpHint(client);
        int guiHeight = client.getWindow().getGuiScaledHeight();
        int guiWidth = client.getWindow().getGuiScaledWidth();
        return BeaconClient.directory().stream()
                .filter(beacon -> beacon.hud() && beacon.point().dimension().equals(dimension))
                .filter(beacon -> frustum.isVisible(new AABB(beacon.point().pos()).expandTowards(0, BEAM_HEIGHT, 0)))
                .sorted(Comparator.comparing(beacon -> !beacon.equals(aimed)))
                .limit(MAX_MARKERS)
                .sorted(Comparator.comparing(beacon -> beacon.equals(aimed)))
                .map(beacon -> {
                    var pos = beacon.point().pos();
                    var center = Vec3.atCenterOf(pos);
                    var anchor = center.add(0, LABEL_HEIGHT - 0.5, 0).subtract(camera);
                    double depth = anchor.x * forward.x() + anchor.y * forward.y() + anchor.z * forward.z();
                    if (depth <= 0 || projectionY <= 0 || guiHeight <= 0 || guiWidth <= 0) {
                        return new Marker(pos, 0, List.of());
                    }

                    var font = client.font;
                    boolean focused = beacon.equals(aimed) && frustum.isVisible(new AABB(
                            pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0));
                    if (focused) BeaconAim.highlight(beacon);
                    long distance = Math.round(center.distanceTo(player.position()));
                    float linePixels = Math.max(9.0F, Math.min(13.0F, guiHeight / 30.0F));
                    if (focused) linePixels *= 1.35F;
                    float guiPixelsPerUnit = linePixels / font.lineHeight;
                    var rows = rows(font, BeaconClient.name(beacon).getVisualOrderText(), distance, pos, focused,
                            focused ? warpHint : null, guiWidth * 0.75F / guiPixelsPerUnit);
                    // A camera-facing plane loses height in proportion to its camera-space depth.
                    float labelScale = (float) (2.0 * depth * guiPixelsPerUnit / (guiHeight * projectionY));
                    return new Marker(pos, labelScale, rows);
                }).toList();
    }

    private static List<Row> rows(Font font, FormattedCharSequence name, long distance, BlockPos pos, boolean focused,
                                  Component warpHint, float maxWidth) {
        int iconHalfHeight = font.lineHeight;
        int detailTop = iconHalfHeight + LINE_GAP;
        int nextLine = font.lineHeight + LINE_GAP;
        int accent = focused ? FOCUSED_COLOR : BEAM_COLOR;
        var rows = new ArrayList<Row>(5);
        rows.add(row(font, name, -detailTop - font.lineHeight, 1, focused ? accent : -1, TEXT_BACKGROUND, maxWidth));
        rows.add(row(font, DIAMOND, -iconHalfHeight, ICON_SCALE, accent, 0, maxWidth));
        rows.add(row(font, BeaconClient.text("world_distance", distance).getVisualOrderText(),
                detailTop, 1, -1, TEXT_BACKGROUND, maxWidth));
        rows.add(row(font, BeaconClient.text("world_coordinates", pos.getX(), pos.getY(), pos.getZ()).getVisualOrderText(),
                detailTop + nextLine, 1, -1, TEXT_BACKGROUND, maxWidth));
        if (warpHint != null) {
            rows.add(row(font, warpHint.getVisualOrderText(), detailTop + 2 * nextLine, 1, accent, TEXT_BACKGROUND, maxWidth));
        }
        return rows;
    }

    private static Row row(Font font, FormattedCharSequence text, int y, float scale, int color, int background,
                           float maxWidth) {
        int width = font.width(text);
        return new Row(text, width, y, Math.min(scale, maxWidth / Math.max(1, width)), color, background);
    }

    record Marker(BlockPos pos, float labelScale, List<Row> rows) {}

    record Row(FormattedCharSequence text, int width, int y, float scale, int color, int background) {}
}
