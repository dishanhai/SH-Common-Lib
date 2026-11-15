// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import com.dishanhai.sh_common_lib.internal.beacon.BeaconPackets;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeacon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconClient.text;

final class BeaconDestinationScreen extends LayeredScreen {
    private final Screen parent;
    private List<CosmicBeacon.View> shown = List.of();
    private int left, top, panelWidth, panelHeight;
    private int page, pages;
    private boolean unavailable;

    BeaconDestinationScreen(Screen parent) {
        super(text("destinations"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shown = BeaconClient.directory();
        int rows = Math.max(1, Math.min(7, (height - 52) / 22));
        pages = Math.max(1, (shown.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        panelWidth = Math.min(320, width - 12);
        panelHeight = 52 + rows * 22;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        for (int i = 0; i < rows && page * rows + i < shown.size(); i++) {
            var beacon = shown.get(page * rows + i);
            var message = text("destination", BeaconClient.name(beacon), BeaconClient.destination(beacon));
            var button = new UiButton(left + 8, top + 25 + i * 22, panelWidth - 16, 19, message, pressed -> {
                if (BeaconClient.send(new BeaconPackets.Warp(beacon.point(), beacon.id()))) minecraft.setScreen(null);
                else unavailable = true;
            });
            var pos = beacon.point().pos();
            button.setPreview(text("location", message, pos.getX(), pos.getY(), pos.getZ()));
            addRenderableWidget(button);
        }
        int y = top + panelHeight - 23;
        addRenderableWidget(new UiButton(left + 8, y, 56, 18, text("close"), ignored -> onClose()));
        var previous = addRenderableWidget(new UiButton(left + panelWidth - 92, y, 20, 18, text("previous"), ignored -> change(-1)));
        var next = addRenderableWidget(new UiButton(left + panelWidth - 28, y, 20, 18, text("next"), ignored -> change(1)));
        previous.active = page > 0;
        next.active = page + 1 < pages;
    }

    private void change(int delta) {
        page += delta;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (!shown.equals(BeaconClient.directory())) rebuildWidgets();
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics, int x, int y, float partialTick) {
        LiquidGlassTheme.popover(graphics, left, top, panelWidth, panelHeight, UiPreferences.configured());
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.drawCenteredString(font, unavailable ? text("unavailable") : title, width / 2, top + 9, 0xFFFFFFFF);
        if (shown.isEmpty()) graphics.drawCenteredString(font, text("empty"), width / 2, top + 29, 0xFFC3CCD8);
        graphics.drawCenteredString(font, text("pages", page + 1, pages), left + panelWidth - 50,
                top + panelHeight - 18, 0xFFFFFFFF);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
