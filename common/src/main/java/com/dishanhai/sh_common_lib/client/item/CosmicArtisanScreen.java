// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.PointerDrag;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.BossPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public final class CosmicArtisanScreen extends LayeredScreen {
    static { ShBetterUiApi.blur().allowScreen(CosmicArtisanScreen.class::isInstance); }

    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 24;
    private static final int CLOSE_WIDTH = 44;
    private static final List<Group> DEFAULT_GROUPS = List.of(
            new Group("tools", GraphicIcon.TOOL, List.of(Mode.SILK_TOUCH, Mode.FORTUNE)),
            new Group("combat", GraphicIcon.CRITICAL_WARNING, List.of(Mode.HOSTILE_FRIENDLY, Mode.FORCE_KILL, Mode.BEHEADING, Mode.CAPTURE, Mode.AREA_DAMAGE,
                    Mode.KILL_AURA, Mode.PROTECTION_LIST)),
            new Group("auxiliary", GraphicIcon.SETTINGS, List.of(Mode.TIME_ACCELERATION, Mode.ENTITY_ACCELERATION, Mode.PLAYER_PROTECTION,
                    Mode.ADVANCED_STEALTH, Mode.MAGNET, Mode.AUTO_CLICK)),
            new Group("travel", GraphicIcon.GOAL, List.of(Mode.TELEPORT, Mode.BEACON_HUD)),
            new Group("mining", GraphicIcon.BLOCK, List.of(Mode.CONSTRUCTION_WAND, Mode.ANGEL_CORE, Mode.DESTRUCTION_CORE,
                    Mode.CHAIN_MINING, Mode.FORCE_MINING, Mode.AUTO_SMELT, Mode.AE_STORAGE_PRIORITY, Mode.AE_LINK, Mode.WRENCH_TAG)),
            new Group("vanilla", GraphicIcon.MINECRAFT, List.of(Mode.HOE_PRIORITY, Mode.CROP_HARVEST, Mode.SHEARS, Mode.FLINT_AND_STEEL,
                    Mode.RIPEN, Mode.FORCE_GROW)));

    private static final Group QUICK_GROUP = new Group("quick", GraphicIcon.QUICK, List.of(
            Mode.FORCE_KILL, Mode.SILK_TOUCH, Mode.FORTUNE, Mode.CHAIN_MINING, Mode.AUTO_SMELT, Mode.MAGNET));
    private static final int WHEEL_CAPACITY = 6;
    private static final List<WheelSlot> WHEEL_SLOTS = List.of(
            new WheelSlot(0, -2), new WheelSlot(-1, -1), new WheelSlot(1, -1),
            new WheelSlot(-1, 1), new WheelSlot(1, 1), new WheelSlot(0, 2));

    private final ToIntFunction<ItemStack> read;
    private final Consumer<Mode> toggle;
    private final boolean wheel;
    private final List<Group> groups;
    private final Function<ItemStack, BossPolicy> readBossPolicy;
    private final Consumer<BossPolicy> toggleBossPolicy;
    private UiButton beaconDestinations;
    private BossPolicy bossPolicy = BossPolicy.NEUTRAL;
    private final EnumMap<BossPolicy, UiButton> bossButtons = new EnumMap<>(BossPolicy.class);
    private final EnumMap<Mode, UiButton> buttons = new EnumMap<>(Mode.class);
    private final EnumMap<Mode, UiToggleButton> toggles = new EnumMap<>(Mode.class);
    private final List<UiButton> navigation = new ArrayList<>();
    private final Map<UiButton, Integer> buttonPositions = new LinkedHashMap<>();
    private final List<UiButton> configPageButtons = new ArrayList<>();
    private final InterruptibleAnimation appearance =
            new InterruptibleAnimation(new Spring.Preset(0.24, 0.95, 0.001F, 0.001F));
    private final InterruptibleAnimation pageAppearance =
            new InterruptibleAnimation(new Spring.Preset(0.24, 0.95, 0.001F, 0.001F), 1.0F);
    private UiScrollableSectionBar groupTabs;
    private UiButton layoutButton;
    private final PointerDrag groupDrag = new PointerDrag();
    private final TextReadabilityPalette textPalette = new TextReadabilityPalette();
    private List<Group> undoOrder = List.of();
    private int draggedGroup = -1;
    private int dropGroup = -1;
    private boolean swallowRelease;
    private boolean layoutSaveFailed;
    private boolean sidebar;
    private int navX;
    private int navY;
    private int navStep;
    private int navButtonWidth;
    private boolean entered;
    private boolean editing;
    private int flags;
    private int activeGroup;
    private int page;
    private int pageCount;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int offset;
    private float opacity;
    private int pageOffset;
    private float pageOpacity;
    private int wheelCenterY;

    CosmicArtisanScreen(ToIntFunction<ItemStack> read, Consumer<Mode> toggle, boolean aeAvailable, boolean wheel,
                        Function<ItemStack, BossPolicy> readBossPolicy, Consumer<BossPolicy> toggleBossPolicy) {
        super(label(wheel ? "wheel_title" : "title"));
        this.read = read;
        this.toggle = toggle;
        this.wheel = wheel;
        this.readBossPolicy = readBossPolicy;
        this.toggleBossPolicy = toggleBossPolicy;
        this.groups = readGroupOrder(aeAvailable);
        if (wheel) {
            groups.add(0, QUICK_GROUP);
        } else {
            var position = ArtisanAppearancePreferences.configPosition();
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).name().equals(position.group())) {
                    activeGroup = i;
                    page = position.page();
                    break;
                }
            }
        }
    }

    @Override
    protected void init() {
        cancelDrag();
        flags = minecraft.player == null ? 0 : read.applyAsInt(minecraft.player.getMainHandItem());
        bossPolicy = minecraft.player == null ? BossPolicy.NEUTRAL : readBossPolicy.apply(minecraft.player.getMainHandItem());
        clearOptions();
        cancelTabDrag();
        groupTabs = null;
        navigation.clear();
        buttonPositions.clear();
        configPageButtons.clear();
        pageAppearance.snapTo(1.0F);
        if (wheel) layoutWheel();
        else layoutConfig();
        if (!entered) {
            entered = true;
            appearance.snapTo(0.30F);
            appearance.blendTo(1.0F, now(), 0, 180, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        }
    }

    private void layoutConfig() {
        panelWidth = Math.min(486, width - 12);
        panelHeight = Math.min(224, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        sidebar = panelWidth >= 345 && panelHeight >= 204;
        int navWidth = 90;
        navX = left + 8;
        navY = top + 32;
        navStep = sidebar ? ROW : (panelWidth - 14) / groups.size();
        navButtonWidth = sidebar ? navWidth : navStep - 2;
        contentX = sidebar ? navX + navWidth + 10 : navX;
        contentY = sidebar ? navY : navY + BUTTON_HEIGHT + 6;
        contentWidth = left + panelWidth - 8 - contentX;
        addButton(left + panelWidth - CLOSE_WIDTH - 8, top + 7, CLOSE_WIDTH, label("close"), this::onClose);
        if (!sidebar && !editing && contentWidth >= GraphicIcon.PIXELS) {
            var sections = groups.stream()
                    .map(group -> new UiSectionBar.Section(label(group.name()), group.icon())).toList();
            groupTabs = addRenderableWidget(ShBetterUiApi.controls().jellySectionBar(
                    new Bounds(navX, navY, contentWidth, BUTTON_HEIGHT), UiPreferences.configured(),
                    sections, activeGroup, this::selectGroup));
        } else {
            layoutGroupButtons();
        }
        int footer = top + panelHeight - BUTTON_HEIGHT - 8;
        layoutButton = addButton(left + 8, footer, 82, label(editing ? "confirm_layout" : "edit_layout"), () -> {
            editing = !editing;
            rebuildWidgets();
            setFocused(layoutButton);
        });
        layoutButton.setLeadingIcon(editing ? GraphicIcon.SAVE : GraphicIcon.EDIT);
        if (editing) {
            var undo = addButton(left + panelWidth - 72, footer, 64, label("undo_layout"), this::undoLayout);
            undo.setLeadingIcon(GraphicIcon.UNDO);
            undo.active = !undoOrder.isEmpty();
            addRenderableOnly((graphics, x, y, tick) -> renderDraggedGroup(graphics));
        } else {
            layoutConfigPage();
        }
    }

    private void layoutGroupButtons() {
        for (int i = 0; i < groups.size(); i++) {
            int index = i;
            var title = label(groups.get(i).name());
            var group = addButton(groupX(i), groupY(i), navButtonWidth,
                    editing ? label("drag_group", title) : title, () -> selectGroup(index));
            if (editing) group.setPreview(UiHudRichText.prepare(label("drag_group_hint", title)));
            else if (sidebar) group.setLeadingIcon(groups.get(i).icon());
            group.setSelected(i == activeGroup);
            navigation.add(group);
        }
    }

    private void layoutConfigPage() {
        int footer = top + panelHeight - BUTTON_HEIGHT - 8;
        int columns = contentWidth >= 258 ? 2 : 1;
        int firstRow = contentY + 19;
        int capacity = columns * Math.max(1, (footer - firstRow - 3) / ROW);
        var selected = groups.get(activeGroup);
        int options = selected.optionCount();
        int pages = (options + capacity - 1) / capacity;
        page = Math.min(page, pages - 1);
        int first = page * capacity;
        int slot = (contentWidth + 4) / columns;
        for (int i = first; i < Math.min(first + capacity, options); i++) {
            int cell = i - first;
            configPageButtons.add(addOption(selected, i,
                    contentX + cell % columns * slot, firstRow + cell / columns * ROW, slot - 4));
        }
        if (pages > 1) {
            int right = left + panelWidth - 8;
            var previous = addButton(right - 100, footer, 26, label("previous_page_label"), () -> changePage(-1));
            var next = addButton(right - 26, footer, 26, label("next_page_label"), () -> changePage(1));
            previous.setIcon(GraphicIcon.PREVIOUS);
            next.setIcon(GraphicIcon.NEXT);
            previous.active = page > 0;
            next.active = page + 1 < pages;
            configPageButtons.add(previous);
            configPageButtons.add(next);
            pageCount = pages;
        } else {
            pageCount = 0;
        }
    }

    private void layoutWheel() {
        int columns = width >= 430 ? groups.size() : 4;
        int navWidth = Math.min(width - 16, columns * 74);
        int slot = navWidth / columns;
        int rows = (groups.size() + columns - 1) / columns;
        int navX = (width - slot * columns) / 2;
        int navY = height - rows * ROW - 3;
        int centerX = width / 2;
        wheelCenterY = (navY - 4) / 2;
        panelWidth = Math.min(176, Math.min(width - 28, navY - 10));
        panelHeight = panelWidth;
        left = centerX - panelWidth / 2;
        top = wheelCenterY - panelHeight / 2;
        for (int i = 0; i < groups.size(); i++) {
            int index = i;
            var button = addButton(navX + i % columns * slot, navY + i / columns * ROW,
                    slot - 3, label(groups.get(i).name()), () -> selectGroup(index));
            button.setSelected(i == activeGroup);
            navigation.add(button);
        }

        var selected = groups.get(activeGroup);
        int options = selected.optionCount();
        pageCount = (options + WHEEL_CAPACITY - 1) / WHEEL_CAPACITY;
        page = Math.min(page, pageCount - 1);
        int first = page * WHEEL_CAPACITY;
        int centerGap = Math.min(102, width / 3);
        int buttonWidth = Math.min(138, (width - centerGap - 16) / 2);
        int side = (centerGap + buttonWidth) / 2;
        int end = Math.min(options, first + WHEEL_CAPACITY);
        int pitch = Math.min(65, (navY - BUTTON_HEIGHT - 10) / 2);
        int centerWidth = pitch < 39 ? Math.min(buttonWidth, centerGap - 4) : buttonWidth;
        for (int i = first; i < end; i++) {
            WheelSlot position = WHEEL_SLOTS.get(i - first);
            int y = switch (position.y()) {
                case -2 -> -pitch;
                case 2 -> pitch;
                default -> position.y() * (BUTTON_HEIGHT / 2 + 7);
            };
            int optionWidth = position.x() == 0 ? centerWidth : buttonWidth;
            addOption(selected, i, centerX + position.x() * side - optionWidth / 2,
                    wheelCenterY + y - BUTTON_HEIGHT / 2, optionWidth);
        }
        if (pageCount > 1) {
            int y = wheelCenterY - BUTTON_HEIGHT / 2;
            int arrowWidth = 20;
            int space = centerGap / 2 - 2;
            addButton(centerX - space, y, arrowWidth, label("previous_page"), () -> changePage(-1)).active = page > 0;
            addButton(centerX + space - arrowWidth, y, arrowWidth, label("next_page"), () -> changePage(1)).active =
                    page + 1 < pageCount;
        }
    }

    private UiButton addButton(int x, int y, int buttonWidth, Component title, Runnable action) {
        var button = new UiButton(x, y, buttonWidth, BUTTON_HEIGHT, UiHudRichText.prepare(title),
                ignored -> action.run(), UiPreferences.configured(), false, UiStyle.LIQUID_GLASS);
        return registerButton(button, y);
    }

    private UiButton registerButton(UiButton button, int y) {
        button.setSampleBackground(false);
        button.setY(y + offset);
        buttonPositions.put(button, y);
        return addRenderableWidget(button);
    }

    private UiButton addOption(Group group, int index, int x, int y, int buttonWidth) {
        if (index < group.modes().size()) return addMode(group.modes().get(index), x, y, buttonWidth);
        if (group.name().equals("travel")) {
            beaconDestinations = addButton(x, y, buttonWidth, label("beacon_destinations"),
                    () -> BeaconClient.openDestinations(wheel ? null : this));
            setFunctionPreview(beaconDestinations, label("beacon_destinations"), label("beacon_destinations_hint"));
            if (!wheel) beaconDestinations.setLeadingIcon(GraphicIcon.GOAL);
            return beaconDestinations;
        }
        var policy = index == group.modes().size() ? BossPolicy.FRIENDLY : BossPolicy.HOSTILE;
        var button = addButton(x, y, buttonWidth, bossLabel(policy), () -> toggleBossPolicy.accept(policy));
        bossButtons.put(policy, button);
        updateBossButton(policy, button);
        return button;
    }

    private UiButton addMode(Mode mode, int x, int y, int buttonWidth) {
        UiButton button;
        if (wheel) {
            button = addButton(x, y, buttonWidth, modeLabel(mode), () -> toggle.accept(mode));
        } else {
            var control = ShBetterUiApi.controls().toggle(new Bounds(x, y, buttonWidth, BUTTON_HEIGHT),
                    mode.enabled(flags), UiPreferences.configured(), UiStyle.LIQUID_GLASS,
                    enabled -> UiHudRichText.prepare(message(mode, enabled)), ignored -> toggle.accept(mode));
            if (mode == Mode.HOE_PRIORITY) control.setIcons(GraphicIcon.SWITCH, GraphicIcon.SWITCH);
            else control.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
            toggles.put(mode, control);
            button = registerButton(control.button(), y);
        }
        updateModeButton(mode, button);
        buttons.put(mode, button);
        return button;
    }

    private void selectGroup(int index) {
        if (activeGroup == index) return;
        activeGroup = index;
        page = 0;
        if (wheel || editing) {
            rebuildWidgets();
        } else {
            changeConfigPage();
        }
        if (groupTabs != null) {
            groupTabs.focus(index);
            setFocused(groupTabs);
        } else {
            setFocused(navigation.get(index));
        }
    }

    private void changePage(int direction) {
        page += direction;
        if (wheel) rebuildWidgets();
        else changeConfigPage();
        if (beaconDestinations != null && buttons.isEmpty()) setFocused(beaconDestinations);
        else if (!buttons.isEmpty()) setFocused(buttons.values().iterator().next());
        else if (!bossButtons.isEmpty()) setFocused(bossButtons.values().iterator().next());
    }

    private void changeConfigPage() {
        for (var button : configPageButtons) {
            if (getFocused() == button) setFocused(null);
            removeWidget(button);
            buttonPositions.remove(button);
        }
        configPageButtons.clear();
        clearOptions();
        for (int i = 0; i < navigation.size(); i++) navigation.get(i).setSelected(i == activeGroup);
        layoutConfigPage();
        pageAppearance.snapTo(0.55F);
        pageAppearance.blendTo(1.0F, now(), 0, 160, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
    }

    private void clearOptions() {
        buttons.clear();
        toggles.clear();
        bossButtons.clear();
        beaconDestinations = null;
    }

    private void moveGroup(int from, int to) {
        if (to < 0 || to >= groups.size() || from == to) return;
        var reordered = new ArrayList<>(groups);
        Group group = reordered.remove(from);
        reordered.add(to, group);
        var previous = List.copyOf(groups);
        if (saveOrder(reordered, group)) {
            undoOrder = previous;
            rebuildWidgets();
            setFocused(navigation.get(activeGroup));
        }
    }

    private void undoLayout() {
        if (!undoOrder.isEmpty() && saveOrder(undoOrder, groups.get(activeGroup))) {
            undoOrder = List.of();
            rebuildWidgets();
            setFocused(navigation.get(activeGroup));
        }
    }

    private boolean saveOrder(List<Group> order, Group selected) {
        layoutSaveFailed = !ArtisanAppearancePreferences.saveQuickGroups(
                order.stream().map(Group::name).collect(Collectors.joining(",")));
        if (layoutSaveFailed) return false;
        groups.clear();
        groups.addAll(order);
        activeGroup = groups.indexOf(selected);
        page = 0;
        return true;
    }

    private int groupX(int slot) { return navX + (sidebar ? 0 : slot * navStep); }

    private int groupY(int slot) { return navY + (sidebar ? slot * navStep : 0); }

    private int dropSlot(double x, double y) {
        var last = groups.size() - 1;
        if (x < navX - 3 || x >= groupX(last) + navButtonWidth + 3
                || y < navY + offset - 3 || y >= groupY(last) + offset + BUTTON_HEIGHT + 3) return -1;
        double along = sidebar ? y - navY - offset : x - navX;
        return Math.max(0, Math.min(last, (int) (along / navStep)));
    }

    private boolean pressGroup(double x, double y, int button) {
        if (groupDrag.active()) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        swallowRelease = false;
        if (!editing || !minecraft.isWindowActive()) return false;
        for (int i = 0; i < navigation.size(); i++) {
            if (!navigation.get(i).isMouseOver(x, y)) continue;
            if (!groupDrag.begin(button, x, y)) return false;
            draggedGroup = dropGroup = i;
            setFocused(navigation.get(i));
            return true;
        }
        return false;
    }

    private boolean dragGroup(double x, double y, int button) {
        if (!groupDrag.active() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!minecraft.isWindowActive()) cancelDrag();
        else if (groupDrag.move(button, x, y) != null) dropGroup = dropSlot(x, y);
        return true;
    }

    private boolean releaseGroup(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!groupDrag.active()) {
            boolean consumed = swallowRelease;
            swallowRelease = false;
            return consumed;
        }
        int from = draggedGroup;
        int to = minecraft.isWindowActive() && groupDrag.release(button, x, y) != null ? dropSlot(x, y) : -1;
        cancelDrag();
        swallowRelease = false;
        moveGroup(from, to);
        return true;
    }

    private void cancelDrag() {
        if (draggedGroup < 0) return;
        groupDrag.cancel();
        for (int i = 0; i < navigation.size(); i++) {
            var button = navigation.get(i);
            button.visible = true;
            button.setX(groupX(i));
            button.setY(groupY(i) + offset);
        }
        draggedGroup = dropGroup = -1;
        swallowRelease = true;
    }

    private boolean layoutKey(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && cancelTabDrag()) return true;
        if (groupDrag.active()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) cancelDrag();
            return true;
        }
        if (!editing || (modifiers & GLFW.GLFW_MOD_SHIFT) == 0) return false;
        int from = navigation.indexOf(getFocused());
        if (from < 0) return false;
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP) moveGroup(from, from - 1);
        else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN) moveGroup(from, from + 1);
        else return false;
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return pressGroup(x, y, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return dragGroup(x, y, button) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return releaseGroup(x, y, button) || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return layoutKey(key, modifiers) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void removed() {
        cancelDrag();
        cancelTabDrag();
        if (!wheel) ArtisanAppearancePreferences.rememberConfigPosition(groups.get(activeGroup).name(), page);
        super.removed();
    }

    private static List<Group> readGroupOrder(boolean aeAvailable) {
        var result = new ArrayList<Group>();
        for (String name : ArtisanAppearancePreferences.quickGroups().split(",")) {
            for (Group group : DEFAULT_GROUPS) {
                if (group.name().equals(name) && !result.contains(group)) result.add(group);
            }
        }
        for (Group group : DEFAULT_GROUPS) {
            if (!result.contains(group)) result.add(group);
        }
        if (!aeAvailable) {
            result.replaceAll(group -> new Group(group.name(), group.icon(), group.modes().stream()
                    .filter(mode -> mode != Mode.AE_STORAGE_PRIORITY && mode != Mode.AE_LINK).toList()));
        }
        return result;
    }

    @Override
    public void tick() {
        if (!minecraft.isWindowActive()) {
            cancelDrag();
            cancelTabDrag();
        }
        if (minecraft.player == null || !CosmicArtisanModes.isActive(minecraft.player.getMainHandItem())) {
            onClose();
            return;
        }
        var held = minecraft.player.getMainHandItem();
        int updated = read.applyAsInt(held);
        var policy = readBossPolicy.apply(held);
        if (updated == flags && policy == bossPolicy) return;
        flags = updated;
        bossPolicy = policy;
        buttons.forEach(this::updateModeButton);
        bossButtons.forEach(this::updateBossButton);
    }

    private Component bossLabel(BossPolicy policy) {
        var name = label(policy == BossPolicy.FRIENDLY ? "boss_friendly" : "boss_hostile");
        return label(policy == bossPolicy ? "enabled" : "disabled", name);
    }

    private void updateBossButton(BossPolicy policy, UiButton button) {
        button.active = Mode.HOSTILE_FRIENDLY.enabled(flags);
        Component status = bossLabel(policy);
        button.setMessage(UiHudRichText.prepare(status));
        button.setSelected(policy == bossPolicy && button.active);
        setFunctionPreview(button, status, label(policy == BossPolicy.FRIENDLY ? "boss_friendly_hint" : "boss_hostile_hint"));
    }

    private void updateModeButton(Mode mode, UiButton button) {
        var control = toggles.get(mode);
        if (control != null) {
            control.setValue(mode.enabled(flags));
        } else {
            button.setMessage(UiHudRichText.prepare(modeLabel(mode)));
            button.setSelected(mode.enabled(flags));
        }
        setFunctionPreview(button, message(mode), Component.translatable(mode.translationKey() + "_hint"));
    }

    private static void setFunctionPreview(UiButton button, Component status, Component hint) {
        button.setPreview(UiHudRichText.prepare(status.copy().append("\n").append(hint)));
    }

    private Component modeLabel(Mode mode) {
        return wheel && mode != Mode.HOE_PRIORITY ? Component.translatable(mode.translationKey()) : message(mode);
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var preferences = UiPreferences.configured();
        long frameTime = now();
        opacity = appearance.sample(frameTime, preferences);
        offset = Math.round((1.0F - opacity) * 10);
        pageOpacity = pageAppearance.sample(frameTime, preferences);
        pageOffset = Math.round((1.0F - pageOpacity) * 9);
        if (groupTabs != null) {
            groupTabs.setY(navY + offset);
            groupTabs.setPreferences(preferences);
        }
        for (var entry : buttonPositions.entrySet()) {
            var button = entry.getKey();
            button.setY(entry.getValue() + offset);
            button.setAlpha(opacity);
        }
        for (var button : configPageButtons) {
            button.setY(buttonPositions.get(button) + offset + pageOffset);
            button.setAlpha(opacity * pageOpacity);
        }
        if (groupDrag.active()) {
            for (int i = 0; i < navigation.size(); i++) {
                var button = navigation.get(i);
                int slot = i;
                if (i != draggedGroup && dropGroup >= 0) {
                    if (draggedGroup < i && i <= dropGroup) slot--;
                    if (dropGroup <= i && i < draggedGroup) slot++;
                }
                button.visible = i != draggedGroup;
                button.setX(groupX(slot));
                button.setY(groupY(slot) + offset);
            }
        }
        var material = wheel ? CosmicArtisanMaterial.WHEEL : CosmicArtisanMaterial.PANEL;
        graphics.fill(0, 0, width, height, withAlpha(material.scrim(), opacity));
        float radius = wheel ? panelWidth / 2f : 10;
        int y = top + offset;
        int edge = preferences.highContrast() ? 0xFFFFFFFF : material.edge();
        boolean sampled = !preferences.highContrast() && preferences.useBackdropBlur()
                && ShBetterUiApi.blur().tryBackdrop(graphics, new GuiRect(left, y, panelWidth, panelHeight),
                        opacity, wheel ? GlassStyle.LIQUID_CLEAR : GlassStyle.LIQUID_REGULAR,
                        material.glass().optics().blurRadius(), radius);
        int fill = preferences.highContrast() ? 0xFF000000 : sampled ? material.tint() : material.fallback();
        RoundedCorners.draw(graphics, left, y, panelWidth, panelHeight, radius, 1,
                withAlpha(edge, opacity), withAlpha(fill, opacity));
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var preferences = UiPreferences.configured();
        textPalette.update(null, 0xFF000000, preferences.highContrast() ? 0xFF000000 : 0xFF686879);
        int text = withAlpha(preferences.highContrast() ? 0xFFFFFFFF : 0xFFE7E8F0, opacity);
        if (wheel) {
            int centerY = wheelCenterY + offset;
            int centerX = width / 2;
            drawRich(graphics, title, centerX, centerY - (pageCount > 1 ? 22 : 13), text, preferences, true);
            drawRich(graphics, pageCount > 1 ? label("page", page + 1, pageCount)
                    : label(groups.get(activeGroup).name()), centerX, centerY - (pageCount > 1 ? 4 : -1), text,
                    preferences, true);
            if (top >= 14) drawRich(graphics, label("wheel_hint"), centerX, top - 12 + offset, text, preferences, true);
            return;
        }
        drawRich(graphics, title, left + 10, top + 13 + offset, text, preferences, false);
        if (editing) {
            if (dropGroup >= 0) {
                RoundedCorners.draw(graphics, groupX(dropGroup), groupY(dropGroup) + offset, navButtonWidth,
                        BUTTON_HEIGHT, 5, 1, preferences.highContrast() ? 0xFFFFFFFF : 0xFF8BBFF4, 0x50252F44);
            }
            int y = contentY + 3 + offset;
            var hint = UiHudRichText.prepare(label(layoutSaveFailed ? "layout_save_failed" : "drag_layout_hint"));
            for (var line : font.split(hint, contentWidth - 4)) {
                drawRich(graphics, line, contentX + 2, y, text, preferences, false);
                y += font.lineHeight;
            }
            return;
        }
        int pageText = withAlpha(text, pageOpacity);
        drawRich(graphics, label(groups.get(activeGroup).name()), contentX + 2, contentY + 3 + offset + pageOffset, pageText,
                preferences, false);
        if (pageCount > 1) {
            drawRich(graphics, label("page", page + 1, pageCount),
                    left + panelWidth - 58, top + panelHeight - 22 + offset + pageOffset, pageText, preferences, true);
        }
    }

    private void drawRich(GuiGraphics graphics, Component text, int x, int y, int color,
                          UiPreferences preferences, boolean centered) {
        drawRich(graphics, UiHudRichText.prepare(text).getVisualOrderText(), x, y, color, preferences, centered);
    }

    private void drawRich(GuiGraphics graphics, FormattedCharSequence text, int x, int y, int color,
                          UiPreferences preferences, boolean centered) {
        int left = centered ? x - font.width(text) / 2 : x;
        UiHudRichText.draw(graphics, font, text, left, y, color, preferences, textPalette);
    }

    private void renderDraggedGroup(GuiGraphics graphics) {
        if (!groupDrag.active()) return;
        int x = groupX(draggedGroup) + (int) Math.round(groupDrag.offsetX());
        int y = groupY(draggedGroup) + offset + (int) Math.round(groupDrag.offsetY());
        x = Math.max(0, Math.min(width - navButtonWidth, x));
        y = Math.max(0, Math.min(height - BUTTON_HEIGHT, y));
        int edge = UiPreferences.configured().highContrast() ? 0xFFFFFFFF
                : dropGroup >= 0 ? 0xFF8BBFF4 : 0xFF7C85A1;
        RoundedCorners.draw(graphics, x, y, navButtonWidth, BUTTON_HEIGHT, 5, 1, edge, 0xFF364861);
        var title = label(groups.get(draggedGroup).name());
        var lines = font.split(UiHudRichText.prepare(title), navButtonWidth - 6);
        if (!lines.isEmpty()) drawRich(graphics, lines.get(0), x + navButtonWidth / 2, y + 6, 0xFFFFFFFF,
                UiPreferences.configured(), true);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private Component message(Mode mode) {
        return message(mode, mode.enabled(flags));
    }

    private static Component message(Mode mode, boolean enabled) {
        if (mode == Mode.HOE_PRIORITY) return label(enabled ? "hoe_priority" : "shovel_priority");
        return label(enabled ? "enabled" : "disabled", Component.translatable(mode.translationKey()));
    }

    private boolean cancelTabDrag() {
        if (groupTabs == null || !groupTabs.cancelDrag()) return false;
        setDragging(false);
        return true;
    }

    private static int withAlpha(int color, float opacity) {
        return (color & 0xFFFFFF) | (Math.round((color >>> 24) * opacity) << 24);
    }

    private static long now() { return System.nanoTime() / 1_000_000L; }

    private static Component label(String key, Object... args) {
        return Component.translatable("screen.sh_common_lib.cosmic_artisan." + key, args);
    }

    private record WheelSlot(int x, int y) {}
    private record Group(String name, GraphicIcon icon, List<Mode> modes) {
        int optionCount() { return modes.size() + (name.equals("travel") ? 1 : name.equals("combat") ? 2 : 0); }
    }
}
