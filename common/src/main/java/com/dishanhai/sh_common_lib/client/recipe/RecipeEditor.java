// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHistory;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiNavigationRail;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_common_lib.api.ulk.fumo.FumoRewardDefinition;
import com.dishanhai.sh_common_lib.api.ulk.recipe.*;
import com.dishanhai.sh_common_lib.api.client.ulk.RecipeRegistryValidation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Client composition only. Recipe data, validation and generation belong to ULK. */
public final class RecipeEditor {
    public static Component title() { return text("title"); }
    private final RecipeTarget target;
    private final Screen parent;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private final Path gameDirectory;
    private final RecipeWorkspace workspace;
    private final UiHistory<RecipeDocument> undo = new UiHistory<>(128);
    private final UiHistory<RecipeDocument> redo = new UiHistory<>(128);
    private final Map<String, ItemStack> icons = new HashMap<>();
    private RecipeDocument document = RecipeDocument.empty();
    private RecipeDocument saved = document;
    private String savedId;
    private RecipeFormat format = RecipeFormat.JSON;
    private UiScreen screen;
    private UiIngredientSlots inputs, output;
    private UiButton copy, export, undoButton, redoButton;
    private UiButton previousPage, nextPage, fixIssue;
    private UiNavigationRail navigation;
    private boolean focusNavigation;
    private final java.util.Set<String> invalidNumbers = new java.util.HashSet<>();
    private final Map<String, String> numberText = new HashMap<>();
    private RecipeDocument generatedDocument;
    private RecipeFormat generatedFormat;
    private RecipeArtifact generatedArtifact;
    private String[] previewLines = {};
    private List<RecipeIssue> issues = List.of();
    private final List<CanvasLabel> canvasLabels = new ArrayList<>();
    private record CanvasLabel(Component label, int x, int y, int width, boolean strong) {}
    private long registryValidationTime;
    private Component status = text("intro");
    private Component validationStatus;
    private int view, codePage, rewardPage;
    private RecipeEditorLayout.Layout layout;
    private RecipeEditorLayout.Slots slotLayout;
    private boolean busy;

    private RecipeEditor(Screen parent, Supplier<? extends GuiEventListener> returnFocus, RecipeTarget target) {
        this.parent = parent;
        this.returnFocus = returnFocus;
        this.target = target;
        gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        workspace = new RecipeWorkspace(gameDirectory.resolve(RecipeWorkspace.DEFAULT_DIRECTORY));
    }

    public static void open(Screen parent, Supplier<? extends GuiEventListener> returnFocus, RecipeTarget target) {
        new RecipeEditor(parent, returnFocus, target).open();
    }

    public static void open(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        RecipeTarget target = present("net.minecraft.resources.Identifier") ? RecipeTarget.NEOFORGE_26_1_2
                : present("net.minecraftforge.fml.ModList") ? RecipeTarget.FORGE_1_20_1 : RecipeTarget.NEOFORGE_1_21_1;
        open(parent, returnFocus, target);
    }

    private static boolean present(String name) {
        try { Class.forName(name, false, RecipeEditor.class.getClassLoader()); return true; }
        catch (ClassNotFoundException ignored) { return false; }
    }

    static UiScreenBuilder builder(Component title, Screen parent) {
        return UiWorkbench.builder(title, parent).onRender(RecipeEditor::drawBackdrop);
    }

    static void drawBackdrop(UiScreen owner, UiFrame frame) {
        var theme = UiTheme.current();
        int background = frame.preferences().highContrast() ? theme.background() : theme.light() ? 0xFFEDEDF0 : 0xFF151517;
        RoundedCorners.draw(frame.graphics(), 0, 0, owner.width, owner.height, 0, 0, 0, background);
    }

    private void open() {
        screen = builder(text("title"), exitGuard()).onInit(this::init).onRender(this::drawSurfaces)
                .onForeground(this::draw).onRemoved(owner -> closeNavigation()).build();
        screen.open();
    }

    private Screen exitGuard() {
        return builder(text("unsaved"), new Screen(text("title")) {
                    @Override protected void init() { refresh(); }
                })
                .onInit(guard -> {
                    if (!dirty()) { leave(); return; }
                    int x = 12, y = guard.viewport().y() + 18, w = Math.max(40, guard.width - 24);
                    button(guard, x, y, w, 22, text("save_return"), () -> save(false, this::leave));
                    button(guard, x, y + 28, w, 22, text("discard"), this::leave);
                    button(guard, x, y + 56, w, 22, text("continue"), this::refresh);
                }).onForeground((owner, frame) -> line(frame, status, 12, owner.height - 20, owner.width - 24, UiWorkbench.accentColor())).build();
    }

    private void leave() {
        Minecraft.getInstance().setScreen(parent);
        if (parent != null) parent.setFocused(returnFocus.get());
    }

    private void refresh() { open(); }

    static UiButton button(UiScreen owner, int x, int y, int width, int height, Component label, Runnable action) {
        var control = UiWorkbench.button(owner, x, y, width, height, label, action);
        control.setCornerRadius(Math.min(8, height / 2f));
        return control;
    }

    private void init(UiScreen owner) {
        generatedDocument = null;
        inputs = null; output = null; copy = null; export = null; slotLayout = null;
        canvasLabels.clear();
        layout = RecipeEditorLayout.compute(owner.width, owner.height);
        var toolbar = layout.toolbar();
        int x = toolbar.x(), y = toolbar.y() + (toolbar.height() - 24) / 2, right = toolbar.right();
        button(owner, x, y, 26, 24, text("back"), owner::onClose).setIcon(GraphicIcon.BACK);
        if (!layout.sidebar()) {
            button(owner, x + 30, y, 26, 24, text("drafts"), () -> openDrafts(0)).setIcon(GraphicIcon.FOLDER);
            button(owner, x + 60, y, 26, 24, text("actions"), this::openActions).setIcon(GraphicIcon.TOOLBAR);
        }
        undoButton = button(owner, right - 124, y, 26, 24, text("undo"), () -> restore(false));
        undoButton.setIcon(GraphicIcon.UNDO);
        redoButton = button(owner, right - 94, y, 26, 24, text("redo"), () -> restore(true));
        redoButton.setIcon(GraphicIcon.FORWARD);
        var saveButton = button(owner, right - 62, y, 62, 24, text("save"), this::save);
        saveButton.setLeadingIcon(GraphicIcon.SAVE);
        saveButton.setSelected(true);
        closeNavigation();
        var nav = layout.navigationControls();
        var orientation = layout.sidebar() ? UiNavigationRail.Orientation.VERTICAL : UiNavigationRail.Orientation.HORIZONTAL;
        navigation = owner.add(ShBetterUiApi.controls().navigationRail(Minecraft.getInstance().font,
                new Bounds(nav.x(), nav.y(), nav.width(), nav.height()), text("workspace"),
                List.of(new UiNavigationRail.Item(text("view.0"), GraphicIcon.RECIPE),
                        new UiNavigationRail.Item(text("view.1"), GraphicIcon.SETTINGS),
                        new UiNavigationRail.Item(text("view.2"), GraphicIcon.CODE)),
                view, orientation, UiWorkbench.preferences(owner), next -> {
                    view = next;
                    focusNavigation = true;
                    refresh();
                }));
        if (focusNavigation) { owner.setFocused(navigation); focusNavigation = false; }
        if (layout.sidebar()) {
            button(owner, nav.x(), nav.bottom() + 10, nav.width(), 22, text("drafts"), () -> openDrafts(0))
                    .setLeadingIcon(GraphicIcon.FOLDER);
            button(owner, nav.x(), nav.bottom() + 36, nav.width(), 22, text("actions"), this::openActions)
                    .setLeadingIcon(GraphicIcon.TOOLBAR);
        }
        var editor = layout.body(layout.editor());
        if (view == 0) {
            if (document.type() != RecipeType.CUSTOM && icons.isEmpty()) icons.putAll(UiWorkbench.itemIcons());
            canvas(owner, editor.x(), editor.y(), editor.width());
        } else if (view == 1) {
            properties(owner, editor.x(), editor.y(), editor.width());
        }
        if (layout.split() || view == 2) {
            var area = layout.body(layout.result(view));
            result(owner, area);
        }
    }

    private void closeNavigation() {
        if (navigation != null) { navigation.close(); navigation = null; }
    }

    /** Secondary document actions stay out of the editing toolbar. */
    private void openActions() {
        builder(text("actions"), screen).onInit(owner -> {
            back(owner);
            int x = Math.max(12, (owner.width - 320) / 2), width = Math.min(320, owner.width - 24);
            int y = Math.max(38, owner.viewport().y() + 8);
            button(owner, x, y, width, 22, text("new"), () -> confirmReplace(() -> {
                clearPendingNumbers(); change(RecipeDocument.empty()); saved = document; savedId = null; refresh();
            }));
            button(owner, x, y + 28, width, 22, text("import"), () -> confirmReplace(this::importClipboard));
            button(owner, x, y + 56, width, 22, text("fill_sequence"), this::openSequence).active =
                    document.type() == RecipeType.SHAPED || document.type() == RecipeType.SHAPELESS;
            button(owner, x, y + 84, width, 22, text("help"), this::help);
        }).open();
    }

    private void drawSurfaces(UiScreen owner, UiFrame frame) {
        drawBackdrop(owner, frame);
        var theme = UiTheme.current();
        boolean contrast = frame.preferences().highContrast();
        int edge = contrast ? theme.text() : 0x20888890;
        int fill = contrast ? theme.surface() : theme.light() ? 0xFFFCFCFD : 0xFF28282C;
        int inset = contrast ? theme.background() : theme.light() ? 0xFFF4F4F7 : 0xFF202023;
        var bar = layout.toolbar();
        var status = layout.status();
        RoundedCorners.draw(frame.graphics(), bar.x() - 7, bar.y() - 5, bar.width() + 14,
                status.bottom() - bar.y() + 7, 12, 1, edge, inset);
        RoundedCorners.draw(frame.graphics(), bar.x(), bar.bottom() + 3, bar.width(), 1, 0, 0, 0, edge);
        if (layout.sidebar()) surface(frame, layout.navigation(), 0, inset);
        if (layout.split() && view != 2) {
            surface(frame, layout.editor(), edge, fill);
            surface(frame, layout.preview(), edge, fill);
        } else {
            surface(frame, layout.content(), edge, fill);
        }
        if (layout.split() || view == 2) {
            var area = layout.body(layout.result(view));
            if (generatedArtifact != null) {
                int height = Math.min(area.height() - 52, previewLines.length * 11 + 6);
                RoundedCorners.draw(frame.graphics(), area.x() - 2, area.y() + 25, area.width() + 4, height,
                        5, 0, 0, inset);
            }
        }
    }

    private static void surface(UiFrame frame, RecipeEditorLayout.Rect area, int edge, int fill) {
        RoundedCorners.draw(frame.graphics(), area.x() - 4, area.y() - 4,
                area.width() + 8, area.height() + 8, 9, 1, edge, fill);
    }

    private boolean structuredRecipe() {
        return document.type() == RecipeType.CUSTOM || document.type() == RecipeType.GTCEU;
    }

    private void canvas(UiScreen owner, int x, int y, int width) {
        int typeWidth = Math.min(width * 55 / 100, 185);
        button(owner, x, y, typeWidth, 22, typeLabel(document.type()), this::openTypes).setLeadingIcon(GraphicIcon.RECIPE_TYPE);
        if (structuredRecipe()) {
            boolean machine = document.type() == RecipeType.GTCEU;
            button(owner, x + typeWidth + 5, y, Math.min(148, width - typeWidth - 5), 22,
                    text(machine ? "gtceu_edit" : "generic_edit"), () -> {
                if (machine) GtceuRecipeEditor.open(screen, document.rawJson(), icons, this::applyCustom);
                else GenericRecipeEditor.open(screen, document.rawJson(), this::applyCustom);
            }).setLeadingIcon(machine ? GraphicIcon.GREGTECH : GraphicIcon.EDIT);
            var pane = layout.body(layout.editor());
            if (machine) machineCanvas(owner, pane);
            else genericCanvas(owner, pane);
            return;
        }
        if (isFumo()) {
            rewards(owner, x, width);
            return;
        }
        if (document.type() == RecipeType.SHAPED || document.type() == RecipeType.SHAPELESS) {
            int space = Math.min(136, width - typeWidth - 4);
            var sequence = button(owner, x + typeWidth + 4, y, space, 22, text("fill_sequence"), this::openSequence);
            if (space < 75) sequence.setIcon(GraphicIcon.FILL); else sequence.setLeadingIcon(GraphicIcon.FILL);
        }
        int count = inputCount();
        var values = new ArrayList<ItemStack>();
        var names = new ArrayList<Component>();
        for (int i = 0; i < count; i++) { values.add(icon(document.inputs().get(i), isPotion(i))); names.add(slotName(i)); }
        boolean hasOutput = document.type() != RecipeType.SMITHING_TRIM || target == RecipeTarget.NEOFORGE_26_1_2;
        var pane = layout.body(layout.editor());
        var slotArea = RecipeEditorLayout.slotArea(pane, owner.height >= 220);
        slotLayout = RecipeEditorLayout.slots(slotArea, count, hasOutput);
        var grid = slotLayout.inputs();
        inputs = owner.add(new UiIngredientSlots(Minecraft.getInstance().font,
                new Bounds(grid.x(), grid.y(), grid.width(), grid.height()), text("inputs"), slotLayout.columns(), values,
                UiWorkbench.preferences(owner), this::openSlot, i -> setSlot(i, ""), this::moveSlot));
        inputs.setSlotNames(names);
        inputs.setAllowDrag(document.type() == RecipeType.SHAPED || document.type() == RecipeType.SHAPELESS);
        if (hasOutput) {
            var result = slotLayout.output();
            output = owner.add(new UiIngredientSlots(Minecraft.getInstance().font,
                    new Bounds(result.x(), result.y(), result.width(), result.height()), text("output"), 1,
                    List.of(resultIcon()), UiWorkbench.preferences(owner), i -> openSlot(9), i -> setSlot(9, ""), (a,b) -> {}));
            output.setSlotNames(List.of(slotName(9))); output.setAllowDrag(false);
        }
        int idY = slotArea.bottom() + 9;
        if (idY + 32 <= pane.bottom()) {
            canvasLabels.add(new CanvasLabel(text("field.id"), x, idY, width, false));
            field(owner, x, idY, width, "id", document.id(), next -> edit(next, document.result(), document.count(),
                    document.experience(), document.cookingTime(), document.group()));
            int groupY = idY + 38;
            if (groupY + 32 <= pane.bottom() && !isPotion(0)
                    && document.type() != RecipeType.BREWING_CONTAINER && document.type() != RecipeType.SMITHING_TRIM
                    && document.type() != RecipeType.SMITHING_TRANSFORM) {
                canvasLabels.add(new CanvasLabel(text("group"), x, groupY, width, false));
                field(owner, x, groupY, width, "group", document.group(), next -> edit(document.id(), document.result(),
                        document.count(), document.experience(), document.cookingTime(), next));
            }
        }
    }

    private void genericCanvas(UiScreen owner, RecipeEditorLayout.Rect pane) {
        int x = pane.x(), y = pane.y() + 30, width = pane.width();
        try {
            var tree = RecipeJsonTree.parse(document.rawJson().isBlank() ? "{\"type\":\"\"}" : document.rawJson());
            var keys = tree.keys(List.of());
            canvasLabels.add(new CanvasLabel(text("root_fields", keys.size()), x + 2, y, width - 4, false));
            int rows = Math.min(6, (pane.bottom() - y - 13) / 23);
            for (int i = 0; i < Math.min(rows, keys.size()); i++) {
                String key = keys.get(i);
                var path = List.of(key);
                String value = tree.isContainer(path) ? (tree.isArray(path) ? "[…]" : "{…}") : tree.value(path);
                if (value.length() > 100) value = value.substring(0, 99) + "…";
                button(owner, x, y + 13 + i * 23, width, 21, text("generic.row", key, value),
                        () -> GenericRecipeEditor.openField(screen, document.rawJson(), key, this::applyCustom))
                        .setTrailingIcon(GraphicIcon.FORWARD);
            }
        } catch (RuntimeException invalidDraft) {
            canvasLabels.add(new CanvasLabel(text("error.invalid_json"), x, y, width, false));
        }
    }

    private void machineCanvas(UiScreen owner, RecipeEditorLayout.Rect pane) {
        int x = pane.x(), y = pane.y() + 29, width = pane.width();
        GtMachineRecipe model;
        try {
            model = GtMachineRecipe.parse(document.rawJson());
        } catch (IllegalArgumentException invalidDraft) {
            canvasLabels.add(new CanvasLabel(text("error.invalid_json"), x, y, width, false));
            return;
        }
        int half = (width - 10) / 2;
        canvasLabels.add(new CanvasLabel(Component.literal(model.machine()), x + 2, y, half, true));
        canvasLabels.add(new CanvasLabel(text("gtceu_parameters", model.duration(), model.eut()), x + half + 10, y, half, false));
        machineLane(owner, x, y + 15, half, pane.bottom(), false, model.itemInputs(), model.fluidInputs());
        machineLane(owner, x + half + 10, y + 15, half, pane.bottom(), true, model.itemOutputs(), model.fluidOutputs());
    }

    private void machineLane(UiScreen owner, int x, int y, int width, int bottom, boolean outputLane,
                             List<GtMachineRecipe.Entry> items, List<GtMachineRecipe.Entry> fluids) {
        button(owner, x, y, width, 20, text(outputLane ? "gt.outputs" : "gt.inputs"),
                () -> GtceuRecipeEditor.openLane(screen, document.rawJson(), outputLane, false, -1, icons, this::applyCustom))
                .setTrailingIcon(GraphicIcon.ADD);
        int max = Math.min(5, (bottom - y - 24) / 23);
        for (int i = 0; i < Math.min(max, items.size() + fluids.size()); i++) {
            boolean fluid = i >= items.size();
            int row = fluid ? i - items.size() : i;
            var entry = (fluid ? fluids : items).get(row);
            String id = entry.id(), shortId = id.substring(id.indexOf(':') + 1);
            var name = text(fluid ? "flow_fluid" : "flow_item", entry.amount(), id.startsWith("#") ? "#" + shortId : shortId);
            var label = !fluid && icons.containsKey(id) ? UiRichText.withItem(id, name) : name;
            var control = button(owner, x, y + 24 + i * 23, width, 21, label,
                    () -> GtceuRecipeEditor.openLane(screen, document.rawJson(), outputLane, fluid, row, icons, this::applyCustom));
            control.setPreview(Component.literal(id));
        }
        if (max > 0 && items.isEmpty() && fluids.isEmpty())
            button(owner, x, y + 24, width, 21, text("add_fluid"),
                    () -> GtceuRecipeEditor.openLane(screen, document.rawJson(), outputLane, true, -1, icons, this::applyCustom))
                    .setLeadingIcon(GraphicIcon.FILL);
    }

    private int inputCount() {
        return switch (document.type()) {
            case SHAPED, SHAPELESS -> 9;
            case SMITHING_TRANSFORM, SMITHING_TRIM -> 3;
            case BREWING, BREWING_CONTAINER -> 2;
            default -> 1;
        };
    }

    private boolean isFumo() { return document.type() == RecipeType.FUMO_REWARDS; }

    private void rewards(UiScreen owner, int x, int width) {
        var area = RecipeEditorLayout.rewards(layout.body(layout.editor()));
        var rewards = document.rewards();
        if (rewards.isEmpty()) canvasLabels.add(new CanvasLabel(text("fumo.empty"), x, area.top(), width, false));
        int pages = Math.max(1, (rewards.size() + area.rows() - 1) / area.rows());
        rewardPage = Math.max(0, Math.min(rewardPage, pages - 1));
        int from = rewardPage * area.rows();
        for (int i = from; i < Math.min(rewards.size(), from + area.rows()); i++) {
            int index = i;
            var reward = rewards.get(i);
            var item = icons.get(reward.item());
            Component name = item == null ? Component.literal(reward.item())
                    : UiRichText.withItem(reward.item(), item.getHoverName());
            button(owner, x, area.rowY(i - from), width, 22, text("fumo.row", reward.weight(), name),
                    () -> openReward(index));
        }
        int y = area.footer();
        if (pages == 1) {
            button(owner, x, y, width, 20, text("fumo.add"), () -> openReward(rewards.size()));
            return;
        }
        int nav = Math.min(60, width / 4);
        button(owner, x, y, nav, 20, text("previous"), () -> { rewardPage--; refresh(); }).active = rewardPage > 0;
        button(owner, x + nav + 4, y, width - nav * 2 - 8, 20, text("fumo.add"), () -> openReward(rewards.size()));
        button(owner, x + width - nav, y, nav, 20, text("next"), () -> { rewardPage++; refresh(); }).active = rewardPage + 1 < pages;
    }

    private void openReward(int index) {
        var rewards = document.rewards();
        var entry = index < rewards.size() ? rewards.get(index) : new FumoRewardDefinition.Entry("", 1);
        openReward(index, entry, new RewardDraft(entry.item(), Integer.toString(entry.weight())));
    }

    private void openReward(int index, FumoRewardDefinition.Entry original, RewardDraft draft) {
        boolean existing = index < document.rewards().size();
        Component title = text(existing ? "fumo.edit" : "fumo.add");
        Runnable resume = () -> openReward(index, original, draft);
        Runnable apply = () -> applyReward(index, draft, resume);
        var currentInput = new UiTextField[1];
        builder(title, pendingGuard(() -> !draft.item.equals(original.item())
                || !draft.weight.equals(Integer.toString(original.weight())), apply, resume)).onInit(owner -> {
            back(owner);
            int x = 12, y = Math.max(38, owner.viewport().y() + 8), width = owner.width - 24;
            currentInput[0] = field(owner, x, y, width, "fumo.item", draft.item, value -> draft.item = value);
            int weightWidth = Math.min(110, width / 2);
            field(owner, x, y + 26, weightWidth, "fumo.weight", draft.weight, value -> draft.weight = value);
            button(owner, x + weightWidth + 4, y + 38, width - weightWidth - 4, 20, text("select_item"),
                    () -> UiWorkbench.selectItem(owner, text("select_item"), () -> currentInput[0],
                            icons.getOrDefault(draft.item, ItemStack.EMPTY).copy(), false, null, stack -> {
                                draft.item = itemId(stack, draft.item);
                                currentInput[0].setValue(draft.item);
                            }));
            int applyWidth = existing ? (width - 4) / 2 : width;
            button(owner, x, y + 64, applyWidth, 22, text("apply"), apply).setSelected(true);
            if (existing) button(owner, x + applyWidth + 4, y + 64, width - applyWidth - 4, 22,
                    text("fumo.remove"), () -> {
                        var updated = new ArrayList<>(document.rewards());
                        updated.remove(index);
                        change(RecipeDocument.fumo(document.id(), updated));
                        refresh();
                    });
        }).onForeground((owner, frame) -> {
            int y = Math.max(38, owner.viewport().y() + 8);
            line(frame, text("fumo.item"), 12, y, owner.width - 24, UiWorkbench.secondaryTextColor());
            line(frame, text("fumo.weight"), 12, y + 26, owner.width - 24, UiWorkbench.secondaryTextColor());
            line(frame, status, 12, owner.height - 16, owner.width - 24, UiWorkbench.accentColor());
        }).open();
    }

    private void applyReward(int index, RewardDraft draft, Runnable resume) {
        int weight;
        try { weight = Integer.parseInt(draft.weight.trim()); }
        catch (NumberFormatException invalid) {
            status = text("error.invalid_reward_weight");
            resume.run();
            return;
        }
        var updated = new ArrayList<>(document.rewards());
        var entry = new FumoRewardDefinition.Entry(draft.item.trim(), weight);
        if (index == updated.size()) updated.add(entry);
        else updated.set(index, entry);
        var problem = FumoRewardDefinition.firstProblem(updated);
        if (problem != null) {
            status = issueLabel(new RecipeIssue(problem.key(), "reward." + (problem.index() + 1)));
            resume.run();
            return;
        }
        change(RecipeDocument.fumo(document.id(), updated));
        rewardPage = index / RecipeEditorLayout.rewards(layout.body(layout.editor())).rows();
        refresh();
    }

    private static final class RewardDraft {
        private String item;
        private String weight;

        private RewardDraft(String item, String weight) {
            this.item = item;
            this.weight = weight;
        }
    }

    private boolean isPotion(int slot) { return document.type() == RecipeType.BREWING && (slot == 0 || slot == 9); }

    private Component slotName(int slot) {
        Component name = slot == 9 ? text(document.type() == RecipeType.SMITHING_TRIM ? "trim_pattern" : "output") : document.type() == RecipeType.BREWING && slot == 0
                ? text("base_potion") : document.type() == RecipeType.SMITHING_TRANSFORM || document.type() == RecipeType.SMITHING_TRIM
                ? text("smithing." + slot) : text("input", slot + 1);
        String id = slot == 9 ? document.result() : document.inputs().get(slot);
        var value = Component.literal(id).withStyle(style -> style.withColor(UiWorkbench.secondaryTextColor() & 0xFFFFFF));
        Component decorated = icons.containsKey(id) ? UiRichText.withItem(id, value) : value;
        return text("slot", name.copy().withStyle(style -> style.withBold(true)), decorated);
    }

    private ItemStack icon(String id, boolean potion) {
        if (id.isEmpty()) return ItemStack.EMPTY;
        var value = icons.get(potion ? "minecraft:potion" : id.startsWith("#") ? "minecraft:name_tag" : id);
        return value == null ? icons.getOrDefault("minecraft:barrier", ItemStack.EMPTY).copy() : value.copy();
    }

    private ItemStack resultIcon() {
        var stack = icon(document.result(), isPotion(9));
        if (!stack.isEmpty()) stack.setCount(Math.max(1, document.count()));
        return stack;
    }

    private void properties(UiScreen owner, int x, int y, int width) {
        int stride = owner.height < 220 ? 26 : 36;
        propertyField(owner, x, y, width, "id", document.id(), value -> edit(value, document.result(), document.count(),
                document.experience(), document.cookingTime(), document.group()));
        if (document.type() == RecipeType.CUSTOM) {
            var selected = document.customTarget();
            Component choice = selected == null ? text("target.current", targetName(target)) : targetName(selected);
            button(owner, x, y + stride, width, 22, text("export_target", choice), () -> {
                var targets = RecipeTarget.values();
                int next = selected == null ? 0 : selected.ordinal() + 1;
                change(document.withCustomTarget(next == targets.length ? null : targets[next]));
                refresh();
            });
        }
        if (structuredRecipe() || isFumo()) return;
        propertyField(owner, x, y + stride, width, "group", document.group(), value -> edit(document.id(), document.result(), document.count(),
                document.experience(), document.cookingTime(), value));
        if (document.type() != RecipeType.BREWING && document.type() != RecipeType.SMITHING_TRIM) {
            propertyField(owner, x, y + stride * 2, isCooking() ? width / 3 - 3 : width, "count", numberText.getOrDefault("count", Integer.toString(document.count())), value -> {
                try { int parsed = Integer.parseInt(value); invalidNumbers.remove("count"); status = text("modified"); edit(document.id(), document.result(), parsed, document.experience(), document.cookingTime(), document.group()); }
                catch (NumberFormatException ignored) { invalidNumbers.add("count"); status = text("invalid_number"); }
            });
        }
        if (isCooking()) {
            propertyField(owner, x + width / 3, y + stride * 2, width / 3 - 3, "time", numberText.getOrDefault("time", Integer.toString(document.cookingTime())), value -> {
                try { int parsed = Integer.parseInt(value); invalidNumbers.remove("time"); status = text("modified"); edit(document.id(), document.result(), document.count(), document.experience(), parsed, document.group()); }
                catch (NumberFormatException ignored) { invalidNumbers.add("time"); status = text("invalid_number"); }
            });
            propertyField(owner, x + width * 2 / 3, y + stride * 2, width / 3 - 3, "experience", numberText.getOrDefault("experience", Float.toString(document.experience())), value -> {
                try { float parsed = Float.parseFloat(value); invalidNumbers.remove("experience"); status = text("modified"); edit(document.id(), document.result(), document.count(), parsed, document.cookingTime(), document.group()); }
                catch (NumberFormatException ignored) { invalidNumbers.add("experience"); status = text("invalid_number"); }
            });
        }
    }

    private UiTextField propertyField(UiScreen owner, int x, int y, int width, String key, String value, Consumer<String> edit) {
        var row = RecipeEditorLayout.propertyRow(new RecipeEditorLayout.Rect(x, y, width, 36), 0, 0, 1, owner.height < 220);
        var bounds = row.field();
        return field(owner, bounds.x(), bounds.y() - 12, bounds.width(), key, value, edit);
    }

    private void propertyLabel(UiScreen owner, UiFrame frame, int x, int y, int width, String key) {
        var row = RecipeEditorLayout.propertyRow(new RecipeEditorLayout.Rect(x, y, width, 36), 0, 0, 1, owner.height < 220);
        var label = row.label();
        line(frame, text(key), label.x(), label.y(), label.width(), UiWorkbench.secondaryTextColor());
    }

    private boolean isCooking() {
        return document.type() == RecipeType.SMELTING || document.type() == RecipeType.BLASTING
                || document.type() == RecipeType.SMOKING || document.type() == RecipeType.CAMPFIRE;
    }

    private UiTextField field(UiScreen owner, int x, int y, int width, String key, String value, Consumer<String> edit) {
        var field = owner.add(new UiTextField(Minecraft.getInstance().font, new Bounds(x, y + 12, width, 20),
                text(key), 4096, UiWorkbench.preferences(owner)));
        field.setHint(text(key)); field.setValue(value); field.setOnChange(next -> {
            if (key.equals("count") || key.equals("time") || key.equals("experience")) numberText.put(key, next);
            edit.accept(next);
        });
        return field;
    }

    private void edit(String id, String result, int count, float experience, int time, String group) {
        // A valid numeric edit clears only its own pending error in the field callback.
        change(new RecipeDocument(id, document.type(), document.inputs(), result, count, experience, time,
                group, document.rawJson(), document.customTarget(), document.rewards()));
    }

    private boolean dirty() { return !document.equals(saved) || !invalidNumbers.isEmpty(); }

    private void clearPendingNumbers() { invalidNumbers.clear(); numberText.clear(); }

    private void result(UiScreen owner, RecipeEditorLayout.Rect area) {
        int x = area.x(), y = area.y(), width = area.width();
        boolean compact = width < 410;
        int actionWidth = compact ? 30 : 78;
        int formatWidth = width - (actionWidth + 4) * 2;
        button(owner, x, y, formatWidth, 22, text("format", text("format." + format.name().toLowerCase(Locale.ROOT))), () -> {
            format = RecipeFormat.values()[(format.ordinal() + 1) % RecipeFormat.values().length]; codePage = 0; refresh();
        });
        copy = button(owner, x + formatWidth + 4, y, actionWidth, 22, text("copy"), () -> {
            var artifact = artifact();
            if (artifact != null) { Minecraft.getInstance().keyboardHandler.setClipboard(artifact.content()); status = text("copied"); }
        });
        export = button(owner, area.right() - actionWidth, y, actionWidth, 22, text("export"), this::export);
        if (compact) { copy.setIcon(GraphicIcon.COPY); export.setIcon(GraphicIcon.EXPORT); }
        else { copy.setLeadingIcon(GraphicIcon.COPY); export.setLeadingIcon(GraphicIcon.EXPORT); }
        int bottomRow = area.bottom() - 20;
        previousPage = button(owner, area.right() - 58, bottomRow, 26, 20, text("previous"), () -> codePage--);
        previousPage.setIcon(GraphicIcon.PREVIOUS);
        nextPage = button(owner, area.right() - 28, bottomRow, 26, 20, text("next"), () -> codePage++);
        nextPage.setIcon(GraphicIcon.NEXT);
        fixIssue = button(owner, x, bottomRow, Math.min(138, width), 20, text("fix_issue"), this::editIssue);
        fixIssue.setLeadingIcon(GraphicIcon.EDIT);
        fixIssue.visible = false;
    }

    private void editIssue() {
        String field = issues.isEmpty() ? "" : issues.get(0).field();
        if (field.equals("id") || field.equals("count") || field.equals("cookingTime") || field.equals("experience")
                || !invalidNumbers.isEmpty()) {
            view = 1; refresh(); return;
        }
        if (document.type() == RecipeType.GTCEU) {
            boolean out = field.contains("output") || field.equals("result");
            if (out || field.contains("input")) {
                GtceuRecipeEditor.openLane(screen, document.rawJson(), out, field.contains("fluid"),
                        field.startsWith("gt_") ? 0 : -1, icons, this::applyCustom);
            } else GtceuRecipeEditor.open(screen, document.rawJson(), icons, this::applyCustom);
        } else if (document.type() == RecipeType.CUSTOM) {
            GenericRecipeEditor.open(screen, document.rawJson(), this::applyCustom);
        } else if (isFumo()) {
            view = 0;
            if (field.startsWith("reward.")) openReward(Integer.parseInt(field.substring("reward.".length())) - 1);
            else refresh();
        } else if (field.equals("result")) {
            openSlot(9);
        } else if (field.startsWith("input")) {
            int index = field.length() == 6 && Character.isDigit(field.charAt(5)) ? field.charAt(5) - '0' : 0;
            openSlot(index);
        } else { view = 1; refresh(); }
    }

    private static Component targetName(RecipeTarget value) {
        return text("target." + value.name().toLowerCase(Locale.ROOT));
    }

    private RecipeArtifact artifact() {
        if (!invalidNumbers.isEmpty()) { generatedDocument = null; validationStatus = text("invalid_number"); return null; }
        long now = System.nanoTime();
        boolean same = document.equals(generatedDocument) && format == generatedFormat;
        var editingTarget = document.editingTarget(target);
        boolean checkRegistry = target == editingTarget && document.type() != RecipeType.CUSTOM;
        if (same && (!checkRegistry || now < registryValidationTime)) return generatedArtifact;
        boolean hadPreview = same && generatedArtifact != null;
        validationStatus = null;
        registryValidationTime = now + 1_000_000_000L;
        generatedDocument = document; generatedFormat = format; generatedArtifact = null;
        try {
            issues = new ArrayList<>(RecipeGenerator.validate(document, editingTarget, format));
            if (issues.isEmpty() && checkRegistry) issues.addAll(RecipeRegistryValidation.validate(document, editingTarget));
            if (!issues.isEmpty()) { validationStatus = issueLabel(issues.get(0)); return null; }
            generatedArtifact = RecipeGenerator.generate(document, editingTarget, format);
            if (!hadPreview) previewLines = generatedArtifact.content().stripTrailing().split("\n");
            return generatedArtifact;
        } catch (RuntimeException exception) { issues = List.of(); validationStatus = text("failed", exception.getMessage()); return null; }
    }

    private static Component issueLabel(RecipeIssue issue) {
        String field = issue.field();
        return text("issue", field.startsWith("reward.") ? text("field.reward", field.substring("reward.".length()))
                : text("field." + field), text("error." + issue.key()));
    }

    private void draw(UiScreen owner, UiFrame frame) {
        undoButton.active = undo.canRestore(); redoButton.active = redo.canRestore();
        var artifact = artifact();
        if (copy != null) { copy.active = artifact != null && !busy; export.active = copy.active; }
        var toolbar = layout.toolbar();
        int titleX = toolbar.x() + (layout.sidebar() ? 38 : 93);
        int titleWidth = toolbar.right() - 132 - titleX;
        if (toolbar.height() >= 32) {
            var produced = icons.get(document.result());
            Component name = produced == null ? typeLabel(document.type()) : produced.getHoverName();
            line(frame, name.copy().withStyle(style -> style.withBold(true)), titleX, toolbar.y() + 4, titleWidth, UiWorkbench.textColor());
            line(frame, Component.literal(document.id()), titleX, toolbar.y() + 18, titleWidth, UiWorkbench.secondaryTextColor());
        } else line(frame, Component.literal(document.id()), titleX, toolbar.y() + 8, titleWidth, UiWorkbench.textColor());
        if (layout.sidebar()) sectionTitle(frame, layout.navigation(), "workspace");
        if (layout.split()) {
            if (view != 2) sectionTitle(frame, layout.editor(), "view." + view);
            sectionTitle(frame, layout.result(view), "view.2");
        }
        if (view == 0) for (var label : canvasLabels)
            line(frame, label.label(), label.x(), label.y(), label.width(),
                    label.strong() ? UiWorkbench.textColor() : UiWorkbench.secondaryTextColor());
        if (view == 0 && slotLayout != null) {
            var grid = slotLayout.inputs(); var out = slotLayout.output();
            if (owner.height >= 220) {
                line(frame, text("inputs"), grid.x(), grid.y() - 12, grid.width(), UiWorkbench.secondaryTextColor());
                if (output != null) line(frame, text(document.type() == RecipeType.SMITHING_TRIM ? "trim_pattern" : "output"),
                        out.x(), out.y() - 12, Math.max(out.width(), 56), UiWorkbench.secondaryTextColor());
            }
            if (output != null) {
                var arrow = slotLayout.arrow();
                line(frame, Component.literal("→"), arrow.x(), arrow.y() + arrow.height() / 2 - 4, arrow.width(), UiWorkbench.secondaryTextColor());
            }
        }
        if (view == 1) {
            var pane = layout.body(layout.editor()); int x = pane.x(), y = pane.y(), width = pane.width();
            int stride = owner.height < 220 ? 26 : 36;
            propertyLabel(owner, frame, x, y, width, "id");
            if (!structuredRecipe() && !isFumo()) propertyLabel(owner, frame, x, y + stride, width, "group");
            if (!structuredRecipe() && !isFumo() && document.type() != RecipeType.BREWING && document.type() != RecipeType.SMITHING_TRIM)
                propertyLabel(owner, frame, x, y + stride * 2, isCooking() ? width / 3 - 3 : width, "count");
            if (isCooking()) {
                propertyLabel(owner, frame, x + width / 3, y + stride * 2, width / 3 - 3, "time");
                propertyLabel(owner, frame, x + width * 2 / 3, y + stride * 2, width / 3 - 3, "experience");
            }
        }
        if (layout.split() || view == 2) {
            var pane = layout.body(layout.result(view));
            int x = pane.x(), width = pane.width(), top = pane.y() + 28;
            int rows = Math.max(1, (pane.bottom() - 24 - top) / 11);
            int pages = artifact == null ? 1 : Math.max(1, (previewLines.length + rows - 1) / rows);
            codePage = Math.max(0, Math.min(codePage, pages - 1));
            previousPage.active = codePage > 0; nextPage.active = codePage + 1 < pages;
            previousPage.visible = nextPage.visible = artifact != null && pages > 1;
            fixIssue.visible = artifact == null && (!invalidNumbers.isEmpty() || !issues.isEmpty()
                    && !issues.get(0).key().equals("registry_unavailable"));
            if (fixIssue.visible) {
                int issueBottom = top + (rows < 3 || issues.isEmpty() || !invalidNumbers.isEmpty() ? 16
                        : 19 + 11 * Math.min(issues.size(), rows - 2));
                fixIssue.setY(Math.min(pane.bottom() - 20, issueBottom));
            }
            if (artifact != null) {
                line(frame, text("code_page", codePage + 1, pages), x, pane.bottom() - 14, width - 62, UiWorkbench.secondaryTextColor());
                int gutter = Math.max(18, Minecraft.getInstance().font.width(Integer.toString(previewLines.length)) + 7);
                for (int i = 0; i < rows && codePage * rows + i < previewLines.length; i++) {
                    int index = codePage * rows + i;
                    line(frame, Component.literal(Integer.toString(index + 1)), x, top + i * 11, gutter, UiWorkbench.secondaryTextColor());
                    line(frame, Component.literal(previewLines[index]), x + gutter, top + i * 11, width - gutter, UiWorkbench.textColor());
                }
            } else if (validationStatus != null) {
                if (rows < 3 || issues.isEmpty() || !invalidNumbers.isEmpty()) {
                    line(frame, validationStatus, x, top, width, UiWorkbench.textColor());
                } else {
                    line(frame, text("preview_issues").copy().withStyle(style -> style.withBold(true)), x, top, width, UiWorkbench.textColor());
                    for (int i = 0; i < Math.min(rows - 2, issues.size()); i++) {
                        var issue = issues.get(i);
                        line(frame, issueLabel(issue),
                                x, top + 16 + i * 11, width, UiWorkbench.secondaryTextColor());
                    }
                }
            }
        }
        var footer = layout.status();
        Component feedback = validationStatus != null && (status.equals(text("intro")) || status.equals(text("modified")))
                ? validationStatus : status;
        line(frame, feedback, footer.x() + 3, footer.y() + 4, footer.width() - 6, UiWorkbench.secondaryTextColor());
    }

    private static void sectionTitle(UiFrame frame, RecipeEditorLayout.Rect area, String key) {
        line(frame, text(key).copy().withStyle(style -> style.withBold(true)),
                area.x() + 3, area.y() + 4, area.width() - 6, UiWorkbench.textColor());
    }

    static void line(UiFrame frame, Component text, int x, int y, int width, int color) {
        if (width <= 0 || y < 0) return;
        var font = Minecraft.getInstance().font;
        frame.drawScaledText(font, UiRichText.fit(font, text, width), x, y, color, 1);
    }

    private void change(RecipeDocument next) {
        if (next.equals(document)) return;
        boolean sameLayout = next.type() == document.type();
        undo.remember(document); redo.clear(); document = next; codePage = 0;
        if (!sameLayout) rewardPage = 0;
        status = text("modified");
        if (sameLayout) syncSlots();
    }

    private void syncSlots() {
        if (inputs != null && !structuredRecipe()) {
            var values = new ArrayList<ItemStack>(); var names = new ArrayList<Component>();
            for (int i = 0; i < inputCount(); i++) { values.add(icon(document.inputs().get(i), isPotion(i))); names.add(slotName(i)); }
            inputs.setItems(values); inputs.setSlotNames(names);
        }
        if (output != null) { output.setItems(List.of(resultIcon())); output.setSlotNames(List.of(slotName(9))); }
    }

    private void restore(boolean forward) {
        var source = forward ? redo : undo; var destination = forward ? undo : redo;
        source.restore().ifPresent(previous -> { destination.remember(document); document = previous; invalidNumbers.clear(); numberText.clear(); refresh(); });
    }

    private void setSlot(int slot, String value) {
        var slots = new ArrayList<>(document.inputs());
        if (slot < 9) slots.set(slot, value.trim());
        change(new RecipeDocument(document.id(), document.type(), slots, slot == 9 ? value.trim() : document.result(),
                document.count(), document.experience(), document.cookingTime(), document.group(), document.rawJson()));
    }

    private void moveSlot(int from, int to) {
        // Only interchangeable ingredient roles can be moved. Potion/template roles stay explicit.
        if (document.type() != RecipeType.SHAPED && document.type() != RecipeType.SHAPELESS) return;
        var slots = new ArrayList<>(document.inputs()); java.util.Collections.swap(slots, from, to);
        change(new RecipeDocument(document.id(), document.type(), slots, document.result(), document.count(),
                document.experience(), document.cookingTime(), document.group(), document.rawJson()));
    }

    private void openSlot(int slot) {
        boolean pattern = slot == 9 && document.type() == RecipeType.SMITHING_TRIM;
        if (!isPotion(slot) && !pattern) {
            String id = slot == 9 ? document.result() : document.inputs().get(slot);
            var initial = icons.getOrDefault(id, ItemStack.EMPTY).copy();
            if (!initial.isEmpty() && slot == 9) initial.setCount(Math.max(1, document.count()));
            UiWorkbench.selectItem(screen, slotName(slot), () -> slot == 9 ? output : inputs,
                    initial, slot == 9, () -> openSlotManual(slot), stack -> {
                        String selectedId = itemId(stack, id);
                        var values = new ArrayList<>(document.inputs());
                        if (slot < 9) values.set(slot, selectedId);
                        if (slot == 9) { invalidNumbers.remove("count"); numberText.remove("count"); }
                        change(new RecipeDocument(document.id(), document.type(), values, slot == 9 ? selectedId : document.result(),
                                slot == 9 ? stack.getCount() : document.count(), document.experience(), document.cookingTime(), document.group(), document.rawJson()));
                        refresh();
                    });
            return;
        }
        openSlotManual(slot);
    }

    private void openSequence() {
        UiWorkbench.selectItemSequence(screen, text("fill_sequence"), () -> inputs, inputCount(), stacks -> {
            var values = new ArrayList<>(document.inputs());
            for (int i = 0; i < stacks.size(); i++) {
                var stack = stacks.get(i);
                String id = itemId(stack, "");
                if (!id.isEmpty()) values.set(i, id);
            }
            change(new RecipeDocument(document.id(), document.type(), values, document.result(), document.count(),
                    document.experience(), document.cookingTime(), document.group(), document.rawJson()));
            refresh();
        });
    }

    private void openSlotManual(int slot) {
        final String[] value = {slot == 9 ? document.result() : document.inputs().get(slot)};
        openSlot(slot, value);
    }

    private void openSlot(int slot, String[] value) {
        String original = slot == 9 ? document.result() : document.inputs().get(slot);
        final UiTextField[] currentInput = new UiTextField[1];
        builder(slotName(slot), pendingGuard(() -> !value[0].equals(original),
                () -> { setSlot(slot, value[0]); refresh(); }, () -> openSlot(slot, value))).onInit(owner -> {
            back(owner);
            int w = owner.width - 24, y = Math.max(38, owner.viewport().y() + 12);
            boolean pattern = slot == 9 && document.type() == RecipeType.SMITHING_TRIM;
            var input = field(owner, 12, y, w, pattern ? "trim_pattern" : isPotion(slot) ? "potion_id" : slot == 9 ? "result_id" : "ingredient_id", value[0], v -> value[0] = v);
            currentInput[0] = input;
            button(owner, 12, y + 46, w, 22, text("select_item"), () -> {
                if (isPotion(slot)) { status = text("potion_hint"); return; }
                UiWorkbench.selectItem(owner, text("select_item"), () -> currentInput[0], stack -> {
                    value[0] = itemId(stack, value[0]);
                    currentInput[0].setValue(value[0]);
                });
            }).active = !isPotion(slot) && !pattern;
            button(owner, 12, y + 76, w, 22, text("apply"), () -> { setSlot(slot, value[0]); refresh(); });
        }).onForeground((owner, frame) -> {
            line(frame, text(slot == 9 && document.type() == RecipeType.SMITHING_TRIM ? "pattern_hint" : isPotion(slot) ? "potion_hint" : "ingredient_hint"), 12, owner.height - 22, owner.width - 24, UiWorkbench.secondaryTextColor());
        }).open();
    }

    private String itemId(ItemStack stack, String fallback) {
        return icons.entrySet().stream().filter(entry -> entry.getValue().getItem() == stack.getItem())
                .map(Map.Entry::getKey).findFirst().orElse(fallback);
    }

    private void openTypes() {
        builder(text("type"), screen).onInit(owner -> {
            back(owner);
            int width = Math.min(560, owner.width - 24), x = (owner.width - width) / 2;
            int top = Math.max(38, owner.viewport().y() + 8), half = (width - 6) / 2;
            typeButton(owner, x, top, half, RecipeType.GTCEU).setLeadingIcon(GraphicIcon.GREGTECH);
            typeButton(owner, x + half + 6, top, half, RecipeType.CUSTOM).setLeadingIcon(GraphicIcon.CODE);
            int columns = owner.height < 240 ? 3 : 2;
            int cell = (width - (columns - 1) * 4) / columns;
            int index = 0;
            for (var type : RecipeType.values()) {
                if (type == RecipeType.GTCEU || type == RecipeType.CUSTOM) continue;
                typeButton(owner, x + index % columns * (cell + 4), top + 32 + index / columns * 26, cell, type);
                index++;
            }
        }).open();
    }

    private UiButton typeButton(UiScreen owner, int x, int y, int width, RecipeType type) {
        var control = button(owner, x, y, width, 22, typeLabel(type), () -> {
            if (type == document.type()) { refresh(); return; }
            if (document.type() == RecipeType.GTCEU && type == RecipeType.CUSTOM) {
                change(new RecipeDocument(document.id(), type, document.inputs(), "", 1, 0, 200, "",
                        document.rawJson(), RecipeTarget.FORGE_1_20_1));
                refresh();
                return;
            }
            Minecraft.getInstance().setScreen(new ConfirmScreen(ok -> {
                if (ok) {
                    int time = switch (type) {
                        case BLASTING, SMOKING -> 100;
                        case CAMPFIRE -> 600;
                        default -> 200;
                    };
                    var empty = RecipeDocument.empty();
                    clearPendingNumbers();
                    rewardPage = 0;
                    change(type == RecipeType.FUMO_REWARDS ? RecipeDocument.fumo(document.id(), List.of())
                            : new RecipeDocument(document.id(), type, empty.inputs(), "", 1, 0, time,
                            type == RecipeType.GTCEU || type == RecipeType.CUSTOM ? "" : document.group(),
                            type == RecipeType.GTCEU ? GtMachineRecipe.empty().toJson() : ""));
                }
                refresh();
            }, text("type_change"), text("type_change_hint")));
        });
        control.setSelected(type == document.type());
        return control;
    }

    private void applyCustom(String raw) {
        change(new RecipeDocument(document.id(), document.type(), document.inputs(), document.result(), document.count(),
                document.experience(), document.cookingTime(), document.group(), raw, document.customTarget()));
        refresh();
    }

    private Screen pendingGuard(Supplier<Boolean> changed, Runnable apply, Runnable resume) {
        return builder(text("unsaved"), new Screen(text("title")) {
            @Override protected void init() { resume.run(); }
        }).onInit(owner -> {
            if (!changed.get()) { refresh(); return; }
            int x = 12, y = 38, width = owner.width - 24;
            button(owner, x, y, width, 22, text("apply_return"), apply);
            button(owner, x, y + 28, width, 22, text("discard"), this::refresh);
            button(owner, x, y + 56, width, 22, text("continue"), resume);
        }).onForeground((owner, frame) -> line(frame, status, 12, owner.height - 12, owner.width - 24, UiWorkbench.accentColor())).build();
    }

    private boolean save() {
        return save(false, this::refresh);
    }

    private void importClipboard() {
        try {
            var loaded = RecipeImporter.importForEditing(document, Minecraft.getInstance().keyboardHandler.getClipboard(), target);
            if (loaded.type() == RecipeType.FUMO_REWARDS) { view = 0; rewardPage = 0; }
            clearPendingNumbers(); change(loaded); savedId = null; status = text("imported"); refresh();
        } catch (Exception exception) { status = text("failed", exception.getMessage()); refresh(); }
    }

    private void help() {
        boolean brewing = document.type() == RecipeType.BREWING || document.type() == RecipeType.BREWING_CONTAINER;
        boolean fumo = isFumo();
        String section = fumo ? "fumo" : brewing ? "brewing" : format.name().toLowerCase(Locale.ROOT);
        builder(text("help"), screen).onInit(owner -> {
            back(owner);
            button(owner, 12, owner.height - 32, owner.width - 24, 22, text("open_directory"), () -> {
                try {
                    var root = gameDirectory.resolve(RecipeWorkspace.DEFAULT_DIRECTORY);
                    java.nio.file.Files.createDirectories(root);
                    RecipeEditorPlatform.openDirectory(root);
                } catch (Exception exception) { status = text("failed", exception.getMessage()); }
            });
        }).onForeground((owner, frame) -> {
            for (int i = 0; i < (fumo ? 5 : 7); i++)
                line(frame, text("help." + section + "." + i), 12, 42 + i * 13, owner.width - 24, UiWorkbench.textColor());
            line(frame, status, 12, owner.height - 46, owner.width - 24, UiWorkbench.accentColor());
        }).open();
    }

    private boolean save(boolean overwrite, Runnable success) {
        if (!invalidNumbers.isEmpty()) { status = text("invalid_number"); return false; }
        try {
            Path path = workspace.save(document, overwrite || document.id().equals(savedId));
            saved = document; savedId = document.id(); status = text("saved", path.toString()); success.run(); return true;
        }
        catch (java.nio.file.FileAlreadyExistsException exception) {
            Minecraft.getInstance().setScreen(new ConfirmScreen(ok -> { if (ok) save(true, success); else refresh(); },
                    text("overwrite"), text("overwrite_hint", document.id())));
            return false;
        }
        catch (Exception exception) { status = text("failed", exception.getMessage()); return false; }
    }

    private void export() {
        var artifact = artifact(); if (artifact == null || busy) return;
        try { export(workspace.prepareExport(artifact, format, gameDirectory), false); }
        catch (Exception exception) { status = text("failed", exception.getMessage()); }
    }

    private void export(RecipeWorkspace.PreparedExport prepared, boolean overwrite) {
        Component previousStatus = status;
        busy = true; status = text("writing");
        try {
            Path path = prepared.write(overwrite);
            status = text("exported", path.toString());
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            Minecraft.getInstance().setScreen(new ConfirmScreen(ok -> {
                refresh();
                if (ok) export(prepared, true);
                else status = previousStatus;
            }, text("overwrite"), text("overwrite_hint", prepared.path().toString())));
        } catch (Exception exception) { status = text("failed", exception.getMessage()); }
        finally { busy = false; }
    }

    private void confirmReplace(Runnable action) {
        if (!dirty()) { action.run(); return; }
        Minecraft.getInstance().setScreen(new ConfirmScreen(ok -> { if (ok) action.run(); else refresh(); }, text("unsaved"), text("replace_hint")));
    }

    private void openDrafts(int page) {
        try {
            var ids = workspace.list();
            builder(text("drafts"), screen).onInit(owner -> {
                back(owner);
                int y = Math.max(38, owner.viewport().y() + 6);
                int rows = Math.max(1, (owner.height - y - 40) / 26), pages = Math.max(1, (ids.size() + rows - 1) / rows);
                int p = Math.max(0, Math.min(page, pages - 1));
                for (int i = p * rows; i < Math.min(ids.size(), (p + 1) * rows); i++) {
                    String id = ids.get(i);
                    button(owner, 12, y + (i - p * rows) * 26, owner.width - 24, 22, Component.literal(id), () -> confirmReplace(() -> {
                        try { var loaded = workspace.load(id); clearPendingNumbers(); change(loaded); saved = loaded; savedId = id; refresh(); }
                        catch (Exception exception) { status = text("failed", exception.getMessage()); refresh(); }
                    }));
                }
                button(owner, 12, owner.height - 28, (owner.width - 28) / 2, 20, text("previous"), () -> openDrafts(p - 1));
                button(owner, 16 + (owner.width - 28) / 2, owner.height - 28, (owner.width - 28) / 2, 20, text("next"), () -> openDrafts(p + 1));
            }).onForeground((owner, frame) -> { if (ids.isEmpty()) line(frame, text("no_drafts"), 12, owner.viewport().y() + 16, owner.width - 24, UiWorkbench.secondaryTextColor()); }).open();
        } catch (Exception exception) { status = text("failed", exception.getMessage()); }
    }

    private static Component typeLabel(RecipeType type) { return text("type." + type.name().toLowerCase(Locale.ROOT)); }
    private void back(UiScreen owner) { button(owner, 12, 8, Math.min(100, owner.width - 24), 22, text("back"), owner::onClose).setLeadingIcon(GraphicIcon.BACK); }
    static Component text(String key, Object... args) { return Component.translatable("screen.sh_common_lib.recipe_editor." + key, args); }
}
