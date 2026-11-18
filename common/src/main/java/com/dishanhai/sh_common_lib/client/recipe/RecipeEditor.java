// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
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
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeEditor;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
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
    private final java.util.Set<String> invalidNumbers = new java.util.HashSet<>();
    private final Map<String, String> numberText = new HashMap<>();
    private RecipeDocument generatedDocument;
    private RecipeFormat generatedFormat;
    private RecipeArtifact generatedArtifact;
    private long registryValidationTime;
    private Component status = text("intro");
    private Component validationStatus;
    private int view, codePage;
    private RecipeEditorLayout.Layout layout;
    private RecipeEditorLayout.Slots slotLayout;
    private boolean busy;

    private RecipeEditor(Screen parent, Supplier<? extends GuiEventListener> returnFocus, RecipeTarget target) {
        this.parent = parent;
        this.returnFocus = returnFocus;
        this.target = target;
        workspace = new RecipeWorkspace(Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/sh_common_lib/recipe_editor"));
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

    private static UiScreenBuilder builder(Component title, Screen parent) {
        return UiWorkbench.builder(title, parent).onRender(RecipeEditor::drawBackdrop);
    }

    private static void drawBackdrop(UiScreen owner, UiFrame frame) {
        var theme = UiTheme.current();
        int background = frame.preferences().highContrast() ? theme.background()
                : theme.light() ? 0xFFF3F3F5 : 0xFF18181B;
        RoundedCorners.draw(frame.graphics(), 0, 0, owner.width, owner.height, 0, 0, 0, background);
    }

    private void open() {
        screen = builder(text("title"), exitGuard()).onInit(this::init).onRender(this::drawSurfaces).onForeground(this::draw).build();
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

    private static UiButton button(UiScreen owner, int x, int y, int width, int height, Component label, Runnable action) {
        var control = UiWorkbench.button(owner, x, y, width, height, label, action);
        control.setCornerRadius(Math.min(8, height / 2f));
        return control;
    }

    private void init(UiScreen owner) {
        generatedDocument = null;
        inputs = null; output = null; copy = null; export = null; slotLayout = null;
        layout = RecipeEditorLayout.compute(owner.width, owner.height);
        int w = owner.width;
        button(owner, 8, 8, 42, 24, text("back"), owner::onClose);
        button(owner, 56, 8, 52, 24, text("drafts"), () -> openDrafts(0));
        button(owner, 114, 8, 52, 24, text("actions"), this::openActions);
        var saveButton = button(owner, w - 78, 8, 70, 24, text("save"), this::save);
        saveButton.setSelected(true);
        undoButton = button(owner, 8, 40, 38, 22, text("undo"), () -> restore(false));
        redoButton = button(owner, 50, 40, 38, 22, text("redo"), () -> restore(true));
        int tabX = 100, tabWidth = Math.max(40, (w - tabX - 8) / 3);
        for (int i = 0; i < 3; i++) {
            final int tab = i;
            var control = button(owner, tabX + i * tabWidth, 40, tabWidth - 3, 22,
                    text("view." + i), () -> { view = tab; refresh(); });
            control.setSelected(view == i);
        }
        if (icons.isEmpty()) icons.putAll(UiWorkbench.itemIcons());
        if (layout.wide() || view == 0) {
            var area = layout.canvas(); canvas(owner, area.x(), area.y(), area.width());
        }
        if (layout.wide() || view == 1) {
            var area = layout.properties(); properties(owner, area.x(), area.y(), area.width());
        }
        if (layout.wide() || view == 2) {
            var area = layout.result(); result(owner, area.x(), area.y(), area.width());
        }
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
        // Native theme colors retain contrast and the player's selected appearance.
        int edge = (UiWorkbench.secondaryTextColor() & 0xFFFFFF) | 0x38000000;
        int fill = (UiWorkbench.secondaryTextColor() & 0xFFFFFF) | 0x10000000;
        RoundedCorners.draw(frame.graphics(), 4, 4, owner.width - 8, 32, 10, 1, edge, fill);
        {
            for (var pane : layout.wide() ? List.of(layout.canvas(), layout.properties(), layout.result()) : List.of(layout.content()))
                RoundedCorners.draw(frame.graphics(), pane.x() - 4, pane.y() - 4,
                        pane.width() + 8, pane.height() + 8, 10, 1, edge, fill);
        }
    }

    private void canvas(UiScreen owner, int x, int y, int width) {
        button(owner, x, y, width, 22, typeLabel(document.type()), this::openTypes);
        if (document.type() == RecipeType.CUSTOM) {
            button(owner, x, y + 32, width, 22, text("custom_edit"), this::openCustom);
            return;
        }
        int count = inputCount();
        var values = new ArrayList<ItemStack>();
        var names = new ArrayList<Component>();
        for (int i = 0; i < count; i++) { values.add(icon(document.inputs().get(i), isPotion(i))); names.add(slotName(i)); }
        boolean hasOutput = document.type() != RecipeType.SMITHING_TRIM || target == RecipeTarget.NEOFORGE_26_1_2;
        var pane = layout.canvas();
        int top = y + (owner.height >= 220 ? 40 : 24);
        slotLayout = RecipeEditorLayout.slots(new RecipeEditorLayout.Rect(x, top, width, pane.bottom() - top), count, hasOutput);
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
    }

    private int inputCount() {
        return switch (document.type()) {
            case SHAPED, SHAPELESS -> 9;
            case SMITHING_TRANSFORM, SMITHING_TRIM -> 3;
            case BREWING, BREWING_CONTAINER -> 2;
            default -> 1;
        };
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
        propertyField(owner, x, y + stride, width, "group", document.group(), value -> edit(document.id(), document.result(), document.count(),
                document.experience(), document.cookingTime(), value));
        if (document.type() != RecipeType.BREWING && document.type() != RecipeType.SMITHING_TRIM && document.type() != RecipeType.CUSTOM) {
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
        change(new RecipeDocument(id, document.type(), document.inputs(), result, count, experience, time, group, document.rawJson()));
    }

    private boolean dirty() { return !document.equals(saved) || !invalidNumbers.isEmpty(); }

    private void clearPendingNumbers() { invalidNumbers.clear(); numberText.clear(); }

    private void result(UiScreen owner, int x, int y, int width) {
        button(owner, x, y, width, 22, text("format", text("format." + format.name().toLowerCase(Locale.ROOT))), () -> {
            format = RecipeFormat.values()[(format.ordinal() + 1) % RecipeFormat.values().length]; codePage = 0; refresh();
        });
        int half = (width - 4) / 2;
        copy = button(owner, x, y + 28, half, 20, text("copy"), () -> {
            var artifact = artifact();
            if (artifact != null) { Minecraft.getInstance().keyboardHandler.setClipboard(artifact.content()); status = text("copied"); }
        });
        copy.setLeadingIcon(GraphicIcon.COPY);
        export = button(owner, x + half + 4, y + 28, width - half - 4, 20, text("export"), () -> export(false));
        export.setLeadingIcon(GraphicIcon.EXPORT);
        int third = (width - 8) / 3;
        button(owner, x, y + 54, third, 18, text("previous"), () -> { codePage = Math.max(0, codePage - 1); });
        button(owner, x + third + 4, y + 54, third, 18, text("next"), () -> { codePage++; });
        button(owner, x + (third + 4) * 2, y + 54, third, 18, text("help"), this::help);
    }

    private RecipeArtifact artifact() {
        if (!invalidNumbers.isEmpty()) { validationStatus = text("invalid_number"); return null; }
        long now = System.nanoTime();
        if (document.equals(generatedDocument) && format == generatedFormat && now < registryValidationTime) return generatedArtifact;
        validationStatus = null;
        registryValidationTime = now + 1_000_000_000L;
        generatedDocument = document; generatedFormat = format; generatedArtifact = null;
        try {
            var issues = new ArrayList<>(RecipeGenerator.validate(document, target, format));
            if (issues.isEmpty()) issues.addAll(RecipeRegistryValidation.validate(document));
            if (!issues.isEmpty()) { var issue = issues.get(0); validationStatus = text("issue", text("field." + issue.field()), text("error." + issue.key())); return null; }
            generatedArtifact = RecipeGenerator.generate(document, target, format);
            return generatedArtifact;
        } catch (RuntimeException exception) { validationStatus = text("failed", exception.getMessage()); return null; }
    }

    private void draw(UiScreen owner, UiFrame frame) {
        undoButton.active = undo.canRestore(); redoButton.active = redo.canRestore();
        var artifact = artifact();
        if (copy != null) { copy.active = artifact != null && !busy; export.active = copy.active; }
        if (owner.width >= 500) line(frame, text("title").copy().withStyle(style -> style.withBold(true)),
                180, 16, owner.width - 270, UiWorkbench.textColor());
        if ((layout.wide() || view == 0) && slotLayout != null) {
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
        if (layout.wide() || view == 1) {
            var pane = layout.properties(); int x = pane.x(), y = pane.y(), width = pane.width();
            int stride = owner.height < 220 ? 26 : 36;
            propertyLabel(owner, frame, x, y, width, "id");
            propertyLabel(owner, frame, x, y + stride, width, "group");
            if (document.type() != RecipeType.BREWING && document.type() != RecipeType.SMITHING_TRIM && document.type() != RecipeType.CUSTOM)
                propertyLabel(owner, frame, x, y + stride * 2, isCooking() ? width / 3 - 3 : width, "count");
            if (isCooking()) {
                propertyLabel(owner, frame, x + width / 3, y + stride * 2, width / 3 - 3, "time");
                propertyLabel(owner, frame, x + width * 2 / 3, y + stride * 2, width / 3 - 3, "experience");
            }
        }
        if ((layout.wide() || view == 2) && artifact != null) {
            var pane = layout.result(); int x = pane.x(), width = pane.width(), top = pane.y() + (owner.height < 200 ? 72 : 86);
            var lines = artifact.content().split("\n", -1);
            int rows = Math.max(1, (pane.bottom() - top) / 11), pages = Math.max(1, (lines.length + rows - 1) / rows);
            codePage = Math.max(0, Math.min(codePage, pages - 1));
            if (owner.height >= 200) line(frame, text("code_page", codePage + 1, pages), x, pane.y() + 74, width, UiWorkbench.accentColor());
            for (int i = 0; i < rows && codePage * rows + i < lines.length && top + i * 11 + 9 <= pane.bottom(); i++)
                line(frame, Component.literal(lines[codePage * rows + i]), x, top + i * 11, width, UiWorkbench.textColor());
        }
        if ((layout.wide() || view == 2) && artifact == null && validationStatus != null) {
            var pane = layout.result();
            line(frame, validationStatus, pane.x(), pane.y() + (owner.height < 200 ? 72 : 86),
                    pane.width(), UiWorkbench.accentColor());
        }
        var footer = layout.status();
        Component feedback = validationStatus != null && (status.equals(text("intro")) || status.equals(text("modified")))
                ? validationStatus : status;
        line(frame, feedback, footer.x(), footer.y() + 4, footer.width(), UiWorkbench.accentColor());
    }

    private static void line(UiFrame frame, Component text, int x, int y, int width, int color) {
        if (width <= 0 || y < 0) return;
        var font = Minecraft.getInstance().font;
        frame.drawScaledText(font, UiRichText.fit(font, text, width), x, y, color, 1);
    }

    private void change(RecipeDocument next) {
        if (next.equals(document)) return;
        boolean sameLayout = next.type() == document.type();
        undo.remember(document); redo.clear(); document = next; codePage = 0;
        status = text("modified");
        if (sameLayout) syncSlots();
    }

    private void syncSlots() {
        if (inputs != null && document.type() != RecipeType.CUSTOM) {
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
                        String selectedId = icons.entrySet().stream().filter(entry -> entry.getValue().getItem() == stack.getItem())
                                .map(Map.Entry::getKey).findFirst().orElse(id);
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
                String id = icons.entrySet().stream().filter(entry -> entry.getValue().getItem() == stack.getItem())
                        .map(Map.Entry::getKey).findFirst().orElse("");
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
                    value[0] = icons.entrySet().stream().filter(entry -> entry.getValue().getItem() == stack.getItem())
                            .map(Map.Entry::getKey).findFirst().orElse(value[0]);
                    currentInput[0].setValue(value[0]);
                });
            }).active = !isPotion(slot) && !pattern;
            button(owner, 12, y + 76, w, 22, text("apply"), () -> { setSlot(slot, value[0]); refresh(); });
        }).onForeground((owner, frame) -> {
            line(frame, text(slot == 9 && document.type() == RecipeType.SMITHING_TRIM ? "pattern_hint" : isPotion(slot) ? "potion_hint" : "ingredient_hint"), 12, owner.height - 22, owner.width - 24, UiWorkbench.secondaryTextColor());
        }).open();
    }

    private void openTypes() {
        builder(text("type"), screen).onInit(owner -> {
            back(owner);
            var types = RecipeType.values(); int columns = owner.height < 220 ? 3 : 2;
            int width = (owner.width - 24 - (columns - 1) * 4) / columns, y = Math.max(38, owner.viewport().y() + 8);
            for (int i = 0; i < types.length; i++) {
                var type = types[i];
                button(owner, 12 + (i % columns) * (width + 4), y + (i / columns) * 26, width, 22, typeLabel(type), () -> {
                    if (type == document.type()) { refresh(); return; }
                    Minecraft.getInstance().setScreen(new ConfirmScreen(ok -> {
                        if (ok) {
                            clearPendingNumbers();
                            change(new RecipeDocument(document.id(), type, List.of("", "", "", "", "", "", "", "", ""), "", 1, 0,
                                    type == RecipeType.BLASTING || type == RecipeType.SMOKING ? 100 : type == RecipeType.CAMPFIRE ? 600 : 200,
                                    document.group(), ""));
                        }
                        refresh();
                    }, text("type_change"), text("type_change_hint")));
                });
            }
        }).open();
    }

    private void openCustom() {
        openCustom(new String[]{document.rawJson()});
    }

    private void applyCustom(String raw) {
        change(new RecipeDocument(document.id(), document.type(), document.inputs(), document.result(), document.count(),
                document.experience(), document.cookingTime(), document.group(), raw));
        refresh();
    }

    private void openCustom(String[] raw) {
        builder(text("custom_edit"), pendingGuard(() -> !raw[0].equals(document.rawJson()),
                () -> applyCustom(raw[0]), () -> openCustom(raw))).onInit(owner -> {
            back(owner);
            int w = owner.width - 24, y = Math.max(38, owner.viewport().y() + 10);
            var field = owner.add(new UiCodeEditor(Minecraft.getInstance().font,
                    new Bounds(12, y, w, Math.max(30, owner.height - y - 82)), text("json"), 1024 * 1024,
                    UiWorkbench.preferences(owner)));
            field.setValue(raw[0]); field.setOnChange(value -> raw[0] = value);
            button(owner, 12, owner.height - 72, w, 20, text("paste_json"), () -> {
                String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (pasted.length() > 1024 * 1024) { status = text("too_large"); return; }
                field.setValue(pasted); raw[0] = field.getValue();
            });
            button(owner, 12, owner.height - 46, w, 20, text("apply"), () -> {
                applyCustom(raw[0]);
            });
        }).onForeground((owner, frame) -> {
            line(frame, text("custom_hint"), 12, owner.height - 24, owner.width - 24, UiWorkbench.secondaryTextColor());
            line(frame, status, 12, owner.height - 12, owner.width - 24, UiWorkbench.accentColor());
        }).open();
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
            var loaded = RecipeImporter.fromJson(document.id(), Minecraft.getInstance().keyboardHandler.getClipboard(), target);
            clearPendingNumbers(); change(loaded); savedId = null; status = text("imported"); refresh();
        } catch (Exception exception) { status = text("failed", exception.getMessage()); refresh(); }
    }

    private void help() {
        boolean brewing = document.type() == RecipeType.BREWING || document.type() == RecipeType.BREWING_CONTAINER;
        String section = brewing ? "brewing" : format.name().toLowerCase(Locale.ROOT);
        builder(text("help"), screen).onInit(owner -> {
            back(owner);
            button(owner, 12, owner.height - 32, owner.width - 24, 22, text("open_directory"), () -> {
                try {
                    var root = Minecraft.getInstance().gameDirectory.toPath().resolve("config/sh_common_lib/recipe_editor");
                    java.nio.file.Files.createDirectories(root);
                    RecipeEditorPlatform.openDirectory(root);
                } catch (Exception exception) { status = text("failed", exception.getMessage()); }
            });
        }).onForeground((owner, frame) -> {
            for (int i = 0; i < 7; i++) line(frame, text("help." + section + "." + i), 12, 42 + i * 13, owner.width - 24, UiWorkbench.textColor());
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

    private void export(boolean overwrite) {
        var artifact = artifact(); if (artifact == null || busy) return;
        busy = true; status = text("writing");
        try {
            Path path = workspace.export(artifact, overwrite);
            status = text("exported", path.toString());
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            Minecraft.getInstance().setScreen(new ConfirmScreen(ok -> { refresh(); if (ok) export(true); },
                    text("overwrite"), text("overwrite_hint", artifact.relativePath())));
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
    private static Component text(String key, Object... args) { return Component.translatable("screen.sh_common_lib.recipe_editor." + key, args); }
}
