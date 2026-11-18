// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.kernel.api.layout.SettingsPanelLayout;
import com.dishanhai.sh_better_ui.kernel.api.layout.SettingsPanelLayout.Rect;
import com.dishanhai.sh_better_ui.kernel.api.layout.RadialMenuLayout;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanMenu.Group;
import com.dishanhai.sh_common_lib.client.item.CosmicArtisanMenu.Option;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.BossPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** Apple-style grouped settings and a six-position wheel share one option catalog. */
public final class CosmicArtisanScreen extends LayeredScreen {
    static { ShBetterUiApi.blur().allowScreen(CosmicArtisanScreen.class::isInstance); }
    private static final long CONFIRM_MS = 3000;
    private final ToIntFunction<ItemStack> read;
    private final Consumer<Mode> toggle;
    private final Function<ItemStack, BossPolicy> readBossPolicy;
    private final Consumer<BossPolicy> toggleBossPolicy;
    private final Runnable toggleEmptyTeleport;
    private final boolean wheel;
    private final List<Group> groups;
    private final Map<Option, UiSwitchRow> switches = new LinkedHashMap<>();
    private final Map<Option, UiButton> actionButtons = new LinkedHashMap<>();
    private final Map<Option, Pending> pending = new LinkedHashMap<>();
    private final Map<UiButton, Integer> sorting = new LinkedHashMap<>();
    private final Map<UiButton, Rect> sortBounds = new LinkedHashMap<>();
    private final PointerDrag groupDrag = new PointerDrag();
    private final InterruptibleAnimation appearance =
            new InterruptibleAnimation(new Spring.Preset(.24, 1, .001f, .001f), .6f);
    private UiNavigationRail navigation;
    private UiScrollableSectionBar tabs;
    private SettingsPanelLayout layout;
    private RadialMenuLayout radial;
    private Rect wheelArea;
    private List<Group> undoOrder = List.of();
    private int activeGroup, page, pageCount = 1, editPage, editCapacity;
    private int flags, draggedGroup = -1, dropGroup = -1;
    private int sortFocusIndex = -1, pageFocusDirection;
    private BossPolicy bossPolicy = BossPolicy.NEUTRAL;
    private boolean allowEmpty, editing, tiny, entered, restoreNavigationFocus, saveFailed;
    private String result = "";
    private long resultUntil;
    private record Pending(boolean expected, long deadline) {}

    CosmicArtisanScreen(ToIntFunction<ItemStack> read, Consumer<Mode> toggle, boolean aeAvailable, boolean wheel,
                        Function<ItemStack, BossPolicy> readBossPolicy, Consumer<BossPolicy> toggleBossPolicy,
                        Runnable toggleEmptyTeleport) {
        super(label(wheel ? "wheel_title" : "title"));
        this.read = read; this.toggle = toggle; this.wheel = wheel;
        this.readBossPolicy = readBossPolicy; this.toggleBossPolicy = toggleBossPolicy;
        this.toggleEmptyTeleport = toggleEmptyTeleport;
        groups = CosmicArtisanMenu.groups(aeAvailable, wheel);
        if (!wheel) {
            var saved = ArtisanAppearancePreferences.configPosition();
            for (int i = 0; i < groups.size(); i++) if (groups.get(i).name().equals(saved.group())) {
                activeGroup = i; page = saved.page(); break;
            }
        }
    }

    @Override protected void init() {
        cancelInteractions();
        if (navigation != null) navigation.close();
        navigation = null; tabs = null;
        switches.clear(); actionButtons.clear(); sorting.clear(); sortBounds.clear();
        readState();
        tiny = width < 180 || height < 180;
        if (tiny) { button(new Rect(8, Math.max(8, height - 30), Math.max(20, width - 16), 22), label("close"), this::onClose); return; }
        if (wheel) layoutWheel(); else layoutSettings();
        if (!entered) { entered = true; appearance.animateTo(1, now()); }
    }

    private void layoutSettings() {
        layout = SettingsPanelLayout.of(width, height, groups.size());
        var panel = layout.panel();
        var close = button(new Rect(panel.right() - 34, panel.y() + 10, 22, 22), label("close"), this::onClose);
        close.setIcon(GraphicIcon.CLOSE);
        if (editing) layoutSorting();
        else {
            layoutNavigation(layout.navigation(), layout.sidebar());
            int capacity = layout.capacity();
            var options = group().options();
            pageCount = Math.max(1, (options.size() + capacity - 1) / capacity);
            page = Math.min(page, pageCount - 1);
            for (int i = page * capacity; i < Math.min(options.size(), (page + 1) * capacity); i++) {
                var rect = layout.row(i - page * capacity);
                addSetting(options.get(i), rect, i + 1 == Math.min(options.size(), (page + 1) * capacity));
            }
        }
        var footer = layout.footer();
        button(new Rect(footer.x(), footer.y(), 82, 22), label(editing ? "confirm_layout" : "edit_layout"), () -> {
            editing = !editing; editPage = 0; rebuildWidgets();
        }).setLeadingIcon(editing ? GraphicIcon.SAVE : GraphicIcon.EDIT);
        if (editing) button(new Rect(footer.x() + 88, footer.y(), 58, 22), label("undo_layout"), this::undoLayout).active = !undoOrder.isEmpty();
        pageButtons(footer.right() - 92, footer.y());
        updateControls();
    }

    private void layoutNavigation(Rect area, boolean sidebar) {
        if (sidebar) {
            navigation = addRenderableWidget(ShBetterUiApi.controls().navigationRail(font, bounds(area), label("title"),
                    groups.stream().map(group -> new UiNavigationRail.Item(label(group.name()), group.icon())).toList(),
                    activeGroup, UiNavigationRail.Orientation.VERTICAL, UiPreferences.configured(), this::selectGroup));
            if (restoreNavigationFocus) setFocused(navigation);
        } else {
            tabs = addRenderableWidget(ShBetterUiApi.controls().jellySectionBar(bounds(area), UiPreferences.configured(),
                    groups.stream().map(group -> new UiSectionBar.Section(label(group.name()), group.icon())).toList(),
                    activeGroup, this::selectGroup));
            if (restoreNavigationFocus) { tabs.focus(activeGroup); setFocused(tabs); }
        }
        restoreNavigationFocus = false;
    }

    private void layoutWheel() {
        wheelArea = new Rect(8, 32, width - 16, height - 72);
        int count = group().options().size();
        pageCount = Math.max(1, (count + 5) / 6);
        page = Math.min(page, pageCount - 1);
        int first = page * 6, end = Math.min(first + 6, count);
        radial = RadialMenuLayout.of(wheelArea.width(), wheelArea.height(), end - first);
        for (int i = first; i < end; i++) {
            var local = radial.slots().get(i - first);
            var area = new Rect(wheelArea.x() + local.x(), wheelArea.y() + local.y(), local.width(), local.height());
            var option = group().options().get(i);
            var control = button(area, optionTitle(option), () -> activate(option));
            control.setLeadingIcon(group().icon());
            actionButtons.put(option, control);
        }
        layoutNavigation(new Rect(12, height - 29, width - 24, 22), false);
        if (pageCount > 1) pageButtons(width - 102, 7);
        else button(new Rect(width - 30, 7, 22, 22), label("close"), this::onClose).setIcon(GraphicIcon.CLOSE);
        updateControls();
    }

    private void addSetting(Option option, Rect rect, boolean last) {
        if (option.toggle()) {
            var control = addRenderableWidget(new UiSwitchRow(bounds(rect), optionTitle(option), optionHint(option),
                    enabled(option), UiPreferences.configured(), ignored -> activate(option)));
            control.setDivider(!last);
            switches.put(option, control);
        } else {
            var control = button(new Rect(rect.x() + 4, rect.y() + 4, rect.width() - 8, rect.height() - 8),
                    optionTitle(option), () -> activate(option));
            control.setLeadingIcon(option.kind() == CosmicArtisanMenu.Kind.BEACONS ? GraphicIcon.GOAL : GraphicIcon.SETTINGS);
            control.setPreview(UiHudRichText.prepare(optionTitle(option).copy().append("\n").append(optionHint(option))));
            actionButtons.put(option, control);
        }
    }

    private void pageButtons(int x, int y) {
        var previous = button(new Rect(x, y, 24, 22), label("previous_page"), () -> changePage(-1));
        var next = button(new Rect(x + 68, y, 24, 22), label("next_page"), () -> changePage(1));
        previous.setIcon(GraphicIcon.PREVIOUS); next.setIcon(GraphicIcon.NEXT);
        int current = editing ? editPage : page;
        previous.active = current > 0; next.active = current + 1 < pageCount;
        if (pageFocusDirection != 0) {
            setFocused(pageFocusDirection > 0 && next.active ? next : previous.active ? previous : next);
            pageFocusDirection = 0;
        }
    }

    private UiButton button(Rect rect, Component label, Runnable action) {
        var control = ShBetterUiApi.controls().button(bounds(rect), UiHudRichText.prepare(label),
                ignored -> action.run(), UiPreferences.configured(), false, UiStyle.LIQUID_GLASS);
        control.setSampleBackground(false);
        return addRenderableWidget(control);
    }

    private void selectGroup(int index) {
        if (index == activeGroup) return;
        activeGroup = index; page = 0; restoreNavigationFocus = true; rememberPosition(); rebuildWidgets();
    }
    private void changePage(int direction) {
        int current = editing ? editPage : page;
        int next = Math.max(0, Math.min(pageCount - 1, current + direction));
        if (next == current) return;
        cancelInteractions();
        pageFocusDirection = direction;
        if (editing) editPage = next; else { page = next; rememberPosition(); }
        rebuildWidgets();
    }

    private void activate(Option option) {
        if (!available(option) || pending.containsKey(option)) return;
        if (!option.toggle()) {
            if (option.kind() == CosmicArtisanMenu.Kind.BEACONS) BeaconClient.openDestinations(wheel ? null : this);
            else AccelerationClient.open(minecraft);
            return;
        }
        pending.put(option, new Pending(!enabled(option), now() + CONFIRM_MS));
        updateControls();
        try {
            switch (option.kind()) {
                case MODE -> toggle.accept(option.mode());
                case BOSS -> toggleBossPolicy.accept(option.policy());
                case EMPTY_TELEPORT -> toggleEmptyTeleport.run();
                default -> throw new IllegalStateException("Not a toggle");
            }
        } catch (RuntimeException | LinkageError failure) {
            pending.remove(option); result = "sync_failed"; resultUntil = now() + 4000;
            System.getLogger(CosmicArtisanScreen.class.getName()).log(System.Logger.Level.WARNING,
                    "Could not update Artisan setting " + option.key(), failure);
            updateControls();
        }
    }

    private boolean enabled(Option option) {
        return switch (option.kind()) {
            case MODE -> option.mode().enabled(flags);
            case BOSS -> bossPolicy == option.policy();
            case EMPTY_TELEPORT -> allowEmpty;
            default -> false;
        };
    }
    private boolean available(Option option) {
        return option.kind() != CosmicArtisanMenu.Kind.BOSS || Mode.HOSTILE_FRIENDLY.enabled(flags);
    }
    private Component optionTitle(Option option) {
        if (option.mode() == Mode.HOE_PRIORITY) return label(Mode.HOE_PRIORITY.enabled(flags) ? "hoe_priority" : "shovel_priority");
        return Component.translatable(option.key());
    }
    private Component optionHint(Option option) {
        return Component.translatable(option.key() + "_hint");
    }
    private Component status(Option option) {
        if (pending.containsKey(option)) return label("sync_waiting");
        if (!available(option)) return label("requires_friendship");
        return option.toggle() ? Component.translatable(enabled(option) ? "options.on" : "options.off") : label("open_action");
    }
    private void readState() {
        if (minecraft.player == null) return;
        var tool = minecraft.player.getMainHandItem();
        flags = read.applyAsInt(tool); bossPolicy = readBossPolicy.apply(tool);
        allowEmpty = CosmicArtisanModes.emptyTeleport(tool);
    }
    private void updateControls() {
        var preferences = UiPreferences.configured();
        switches.forEach((option, control) -> {
            control.active = available(option);
            control.setValue(enabled(option));
            control.setPending(pending.containsKey(option));
            control.setMessage(UiHudRichText.prepare(optionTitle(option)));
            control.setDescription(pending.containsKey(option) ? label("sync_waiting") : optionHint(option));
            control.setPreferences(preferences);
        });
        actionButtons.forEach((option, control) -> {
            control.active = available(option) && !pending.containsKey(option);
            control.setPreferences(preferences);
            control.setMessage(UiHudRichText.prepare(optionTitle(option)));
            control.setSelected(option.toggle() && enabled(option));
            if (option.toggle()) control.setModeIndicators(2, enabled(option) ? 1 : 0);
            control.setPreview(UiHudRichText.prepare(optionTitle(option).copy().append(" · ").append(status(option))
                    .append("\n").append(optionHint(option))));
        });
        if (navigation != null) navigation.setPreferences(preferences);
        if (tabs != null) tabs.setPreferences(preferences);
    }

    @Override public void tick() {
        if (!minecraft.isWindowActive()) cancelInteractions();
        if (minecraft.player == null || !CosmicArtisanModes.isActive(minecraft.player.getMainHandItem())) { onClose(); return; }
        readState();
        long now = now();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var change = iterator.next();
            if (enabled(change.getKey()) == change.getValue().expected()) {
                iterator.remove(); result = "sync_saved"; resultUntil = now + 2200;
            } else if (now >= change.getValue().deadline()) {
                iterator.remove(); result = "sync_failed"; resultUntil = now + 4000;
            }
        }
        updateControls();
    }

    private void layoutSorting() {
        var content = layout.content();
        int x = layout.panel().x() + 12, width = layout.panel().width() - 24;
        editCapacity = Math.max(1, (content.height() - 22) / 28);
        pageCount = Math.max(1, (groups.size() + editCapacity - 1) / editCapacity);
        editPage = Math.min(editPage, pageCount - 1);
        for (int i = editPage * editCapacity; i < Math.min(groups.size(), (editPage + 1) * editCapacity); i++) {
            int index = i;
            var rect = new Rect(x, content.y() + 22 + (i % editCapacity) * 28, width, 24);
            var control = button(rect, label("drag_group", label(groups.get(i).name())), () -> {});
            control.setLeadingIcon(GraphicIcon.EDIT);
            control.setPreview(UiHudRichText.prepare(label("drag_group_hint", label(groups.get(i).name()))));
            sorting.put(control, index); sortBounds.put(control, rect);
            if (index == sortFocusIndex) setFocused(control);
        }
        sortFocusIndex = -1;
    }

    private boolean pressGroup(double x, double y, int button) {
        if (!editing || button != 0 || !minecraft.isWindowActive()) return false;
        for (var entry : sorting.entrySet()) if (entry.getKey().isMouseOver(x, y)) {
            if (!groupDrag.begin(button, x, y)) return false;
            draggedGroup = dropGroup = entry.getValue(); setFocused(entry.getKey()); return true;
        }
        return false;
    }
    private int dropSlot(double x, double y) {
        for (var entry : sorting.entrySet()) {
            var rect = sortBounds.get(entry.getKey());
            if (x >= rect.x() && x < rect.right() && y >= rect.y() - 2 && y < rect.bottom() + 2) return entry.getValue();
        }
        return -1;
    }
    private boolean dragGroup(double x, double y, int button) {
        if (!groupDrag.active() || button != 0) return false;
        if (!minecraft.isWindowActive()) cancelInteractions();
        else if (groupDrag.move(button, x, y) != null) dropGroup = dropSlot(x, y);
        return true;
    }
    private boolean releaseGroup(double x, double y, int button) {
        if (!groupDrag.active() || button != 0) return false;
        int from = draggedGroup;
        int target = minecraft.isWindowActive() && groupDrag.release(button, x, y) != null ? dropSlot(x, y) : -1;
        cancelInteractions(); moveGroup(from, target); return true;
    }
    private void moveGroup(int from, int target) {
        if (from < 0 || target < 0 || from == target || target >= groups.size()) return;
        var previous = List.copyOf(groups); var selected = group();
        var next = new ArrayList<>(groups); next.add(target, next.remove(from));
        if (saveOrder(next, selected)) {
            undoOrder = previous; editPage = target / editCapacity; sortFocusIndex = target; rebuildWidgets();
        }
    }
    private boolean saveOrder(List<Group> next, Group selected) {
        saveFailed = !ArtisanAppearancePreferences.saveQuickGroups(next.stream().map(Group::name).collect(Collectors.joining(",")));
        if (saveFailed) return false;
        groups.clear(); groups.addAll(next); activeGroup = groups.indexOf(selected); return true;
    }
    private void undoLayout() {
        if (!undoOrder.isEmpty() && saveOrder(undoOrder, group())) { undoOrder = List.of(); rebuildWidgets(); }
    }
    private void cancelInteractions() {
        groupDrag.cancel(); draggedGroup = dropGroup = -1;
        switches.values().forEach(UiSwitchRow::cancelInteraction);
        if (navigation != null) navigation.cancelInteraction();
        if (tabs != null) tabs.cancelDrag();
        sorting.keySet().forEach(control -> { control.visible = true; var rect = sortBounds.get(control); control.setX(rect.x()); control.setY(rect.y()); });
        setDragging(false);
    }

    private boolean layoutKey(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && (groupDrag.active() || editing)) {
            if (groupDrag.active()) cancelInteractions(); else { editing = false; rebuildWidgets(); }
            return true;
        }
        if (editing && key == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { undoLayout(); return true; }
        if (editing && (modifiers & GLFW.GLFW_MOD_ALT) != 0 && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            for (var entry : sorting.entrySet()) if (entry.getKey() == getFocused()) {
                moveGroup(entry.getValue(), entry.getValue() + (key == GLFW.GLFW_KEY_UP ? -1 : 1)); return true;
            }
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) { changePage(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1); return true; }
        return false;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return pressGroup(x, y, button) || super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return dragGroup(x, y, button) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (releaseGroup(x, y, button)) return true;
        for (var control : switches.values()) if (control.releasePointer(x, y, button)) { setDragging(false); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        return layoutKey(key, modifiers) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override protected void renderBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var preferences = UiPreferences.configured();
        var theme = UiTheme.current();
        float opacity = appearance.sample(now(), preferences);
        graphics.fill(0, 0, width, height, withAlpha(theme.background(), wheel ? .22f : .42f));
        if (tiny) return;
        if (wheel) {
            if (!radial.compact()) {
                var center = radial.center();
                paintPanel(graphics, new Rect(wheelArea.x() + center.x(), wheelArea.y() + center.y(),
                        center.width(), center.height()), center.width() / 2f, opacity);
            }
            return;
        }
        paintPanel(graphics, layout.panel(), 14, opacity);
        if (!editing) {
            var content = layout.content();
            int rows = Math.min(layout.capacity(), group().options().size() - page * layout.capacity());
            if (rows > 0) RoundedCorners.draw(graphics, content.x(), content.y() + 22,
                    content.width(), rows * layout.rowHeight(), 9, preferences.highContrast() ? 1 : 0,
                    theme.border(), withAlpha(theme.background(), preferences.highContrast() ? 1 : .32f));
        } else if (groupDrag.active()) renderSorting(graphics);
    }

    private void paintPanel(GuiGraphics graphics, Rect rect, float radius, float opacity) {
        var preferences = UiPreferences.configured();
        var theme = UiTheme.current();
        var material = wheel ? CosmicArtisanMaterial.WHEEL : CosmicArtisanMaterial.PANEL;
        int edge = preferences.highContrast() ? theme.text() : withAlpha(theme.border(), .65f);
        boolean sampled = !preferences.highContrast() && preferences.useBackdropBlur()
                && ShBetterUiApi.blur().tryBackdrop(graphics, new GuiRect(rect.x(), rect.y(), rect.width(), rect.height()),
                        opacity, wheel ? GlassStyle.LIQUID_CLEAR : GlassStyle.LIQUID_REGULAR,
                        material.glass().optics().blurRadius(), radius);
        int fill = withAlpha(theme.surface(), preferences.highContrast() ? 1 : sampled ? .85f : .97f);
        RoundedCorners.draw(graphics, rect.x(), rect.y(), rect.width(), rect.height(), radius, 1, edge, fill);
    }

    private void renderSorting(GuiGraphics graphics) {
        UiButton dragged = null;
        for (var entry : sorting.entrySet()) {
            var control = entry.getKey(); var rect = sortBounds.get(control);
            int slot = entry.getValue();
            if (slot == draggedGroup) { dragged = control; continue; }
            if (dropGroup >= 0) {
                if (draggedGroup < slot && slot <= dropGroup) slot--;
                if (dropGroup <= slot && slot < draggedGroup) slot++;
            }
            control.setY(rect.y() + (slot - entry.getValue()) * 28);
        }
        if (dragged == null) return;
        var origin = sortBounds.get(dragged);
        dragged.setX(Math.max(layout.panel().x() + 6, Math.min(layout.panel().right() - dragged.getWidth() - 6,
                origin.x() + (int)Math.round(groupDrag.offsetX()))));
        dragged.setY(Math.max(layout.content().y(), Math.min(layout.footer().y() - 26,
                origin.y() + (int)Math.round(groupDrag.offsetY()))));
        if (dropGroup >= 0) {
            int y = layout.content().y() + 22 + (dropGroup % editCapacity) * 28;
            RoundedCorners.draw(graphics, origin.x(), y, origin.width(), 24, 7, 1, UiTheme.current().accent(), 0);
        }
    }

    @Override protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var theme = UiTheme.current();
        if (tiny) { draw(graphics, label("larger_gui"), 8, 14, Math.max(1, width - 16), theme.text(), 3); return; }
        if (wheel) { renderWheelText(graphics); return; }
        var panel = layout.panel(); var content = layout.content();
        draw(graphics, title, panel.x() + 14, panel.y() + 15, panel.width() - 60, theme.text(), 1);
        if (editing) {
            draw(graphics, label(saveFailed ? "layout_save_failed" : "drag_layout_hint"), panel.x() + 14,
                    content.y() + 3, panel.width() - 28, theme.secondaryText(theme.text(), .2f), 1);
        } else {
            draw(graphics, label("group_" + group().name() + "_hint"), content.x() + 2, content.y() + 3,
                    content.width() - 4, theme.secondaryText(theme.text(), .18f), 1);
        }
        var footer = layout.footer();
        if (pageCount > 1) centered(graphics, label("page", (editing ? editPage : page) + 1, pageCount),
                footer.right() - 46, footer.y() + 7, 40, theme.text(), 1);
        if (!editing) {
            Component state = !pending.isEmpty() ? label("sync_waiting") : now() < resultUntil ? label(result) : label("instant_save");
            draw(graphics, state, footer.x() + 90, footer.y() + 7, Math.max(1, footer.width() - 188),
                    theme.secondaryText(theme.text(), .18f), 1);
        }
    }

    private void renderWheelText(GuiGraphics graphics) {
        var theme = UiTheme.current();
        draw(graphics, title, 14, 12, Math.max(20, width - 124), theme.text(), 1);
        if (pageCount > 1) centered(graphics, label("page", page + 1, pageCount), width - 56, 14, 40, theme.text(), 1);
        if (radial.compact()) return;
        var center = radial.center();
        int cx = wheelArea.x() + center.x() + center.width() / 2;
        int cy = wheelArea.y() + center.y() + center.height() / 2;
        Option preview = null;
        for (var entry : actionButtons.entrySet()) if (entry.getValue().isHovered() || entry.getValue() == getFocused()) { preview = entry.getKey(); break; }
        int available = Math.max(16, center.width() - 12);
        centered(graphics, preview == null ? label(group().name()) : optionTitle(preview), cx, cy - 15, available, theme.text(), 2);
        long enabled = group().options().stream().filter(option -> option.toggle() && enabled(option)).count();
        centered(graphics, preview == null ? label("enabled_count", enabled) : status(preview), cx, cy + 10, available,
                theme.secondaryText(theme.text(), .16f), 1);
    }

    private void draw(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxLines) {
        var lines = font.split(UiHudRichText.prepare(text), Math.max(1, width));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) graphics.drawString(font, lines.get(i), x, y + i * font.lineHeight, color, false);
    }
    private void centered(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxLines) {
        var lines = font.split(UiHudRichText.prepare(text), Math.max(1, width));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.drawString(font, line, x - font.width(line) / 2, y + i * font.lineHeight, color, false);
        }
    }
    private void rememberPosition() {
        if (!wheel) ArtisanAppearancePreferences.rememberConfigPosition(group().name(), page);
    }
    @Override public void onClose() { rememberPosition(); cancelInteractions(); super.onClose(); }
    @Override public void removed() {
        cancelInteractions(); if (navigation != null) navigation.close(); super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
    private Group group() { return groups.get(activeGroup); }
    private static Bounds bounds(Rect rect) { return new Bounds(rect.x(), rect.y(), rect.width(), rect.height()); }
    private static Component label(String key, Object... args) { return Component.translatable(CosmicArtisanMenu.prefix(key), args); }
    private static int withAlpha(int color, float opacity) { return color & 0xFFFFFF | Math.round((color >>> 24) * opacity) << 24; }
    private static long now() { return System.nanoTime() / 1_000_000L; }
}
