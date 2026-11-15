// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import com.dishanhai.sh_common_lib.internal.beacon.BeaconPackets;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeacon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static com.dishanhai.sh_common_lib.client.beacon.BeaconClient.text;

final class BeaconEditorScreen extends LayeredScreen {
    private static final int HEIGHT = 142;
    private final BeaconPackets.Editor editor;
    private String name;
    private boolean locked;
    private boolean crossDimension;
    private boolean hud;
    private boolean unavailable;
    private int left;
    private int top;
    private int panelWidth;

    BeaconEditorScreen(BeaconPackets.Editor editor) {
        super(text("title"));
        this.editor = editor;
        var value = editor.beacon();
        name = value.name();
        locked = value.locked();
        crossDimension = value.crossDimension();
        hud = value.hud();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(300, width - 12);
        left = (width - panelWidth) / 2;
        top = Math.max(0, (height - HEIGHT) / 2);
        int x = left + 8;
        int full = panelWidth - 16;
        var field = new UiTextField(font, new Bounds(x, top + 27, full, 19), text("name"),
                CosmicBeacon.NAME_LIMIT * 2, UiPreferences.configured());
        field.setHint(text("name"));
        field.setValue(name);
        field.setOnChange(value -> name = value);
        field.active = editor.editable();
        addRenderableWidget(field);
        addSetting(x, top + 50, full, "lock", locked, value -> locked = value);
        addSetting(x, top + 72, full, "cross_dimension", crossDimension, value -> crossDimension = value);
        addSetting(x, top + 94, full, "show_hud", hud, value -> hud = value);
        int half = full / 2;
        addRenderableWidget(new UiButton(x, top + 118, half - 3, 18, text("close"), button -> onClose()));
        var save = new UiButton(x + half + 3, top + 118, full - half - 3, 18, text("save"), button -> save());
        save.active = editor.editable();
        addRenderableWidget(save);
    }

    private void addSetting(int x, int y, int width, String key, boolean selected,
                            java.util.function.Consumer<Boolean> change) {
        var button = new UiButton(x, y, width, 19, stateLabel(key, selected), pressed -> {
            var control = (UiButton) pressed;
            boolean value = !control.isSelected();
            change.accept(value);
            control.setSelected(value);
            control.setMessage(stateLabel(key, value));
        });
        button.setSelected(selected);
        button.active = editor.editable() && (!key.equals("lock") || editor.canLock());
        addRenderableWidget(button);
    }

    private static Component stateLabel(String key, boolean selected) {
        return text(selected ? "on" : "off", text(key));
    }

    private void save() {
        var value = editor.beacon();
        if (BeaconClient.send(new BeaconPackets.Edit(new CosmicBeacon.View(value.point(), value.id(), name,
                locked, crossDimension, hud)))) onClose();
        else unavailable = true;
    }

    @Override
    protected void renderBackdrop(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        ClientPanels.strongPopover(graphics, left, top, panelWidth, HEIGHT);
    }

    @Override
    protected void renderForeground(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        graphics.centeredText(font, unavailable ? text("unavailable") : editor.editable() ? title : text("read_only"),
                width / 2, top + 10, 0xFFFFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }
}
