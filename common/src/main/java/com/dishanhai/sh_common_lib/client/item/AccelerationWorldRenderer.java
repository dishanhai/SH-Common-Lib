// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class AccelerationWorldRenderer {
    private AccelerationWorldRenderer() {}
    public static String dimension() {
        var level = Minecraft.getInstance().level;
        return level == null ? "" : level.dimension().location().toString();
    }

    public static void renderHud(GuiGraphics graphics) {
        var frame = AccelerationWorldMarkers.frame();
        for (var stroke : frame.strokes()) {
            var line = stroke.line();
            int dx = line.b().x() - line.a().x(), dy = line.b().y() - line.a().y();
            graphics.pose().pushPose();
            graphics.pose().translate(line.a().x(), line.a().y(), 0);
            graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
            graphics.fill(0, 0, Math.max(1, (int) Math.ceil(Math.hypot(dx, dy))), 1, stroke.color());
            graphics.pose().popPose();
        }
        var client = Minecraft.getInstance();
        for (var label : frame.labels()) {
            int x = label.point().x(), y = label.point().y();
            int textWidth = client.font.width(label.text());
            graphics.fill(x - textWidth / 2 - 2, y - 1, x + textWidth / 2 + 3, y + 10, 0xC0101420);
            graphics.drawString(client.font, label.text(), x - textWidth / 2, y, label.color(), true);
            if (label.progress() >= 0) {
                graphics.fill(x - 19, y + 11, x + 19, y + 15, 0xE0101420);
                graphics.fill(x - 18, y + 12, x - 18 + Math.round(36 * label.progress()), y + 14, 0xFF7EE4E8);
            }
        }
        if (frame.editing() != null) {
            int x = client.getWindow().getGuiScaledWidth() / 2, y = client.getWindow().getGuiScaledHeight() - 101;
            int width = client.font.width(frame.editing());
            graphics.fill(x - width / 2 - 5, y - 3, x + width / 2 + 5, y + 12, 0xEB101420);
            graphics.drawString(client.font, frame.editing(), x - width / 2, y, 0xFFFFFFAA, true);
        }
    }
}
