// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import static com.dishanhai.sh_better_ui.client.api.UiWorkbench.button;
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
    private int view, codePage;
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

    private void open() {
        screen = UiWorkbench.builder(text("title"), exitGuard())
                .onInit(this::init).onForeground(this::draw).build();
        screen.open();
    }

    private Screen exitGuard() {
        return UiWorkbench.builder(text("unsaved"), new Screen(text("title")) {
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

    private void init(UiScreen owner) {
        generatedDocument = null;
        inputs = null; output = null; copy = null; export = null;
        int w = owner.width, y = 8, bw = Math.max(24, (w - 40) / 7);
        button(owner, 8, y, bw, 20, text("back"), owner::onClose).setLeadingIcon(GraphicIcon.BACK);
        button(owner, 12 + bw, y, bw, 20, text("save"), this::save).setLeadingIcon(GraphicIcon.SAVE);
        undoButton = button(owner, 16 + bw * 2, y, bw, 20, text("undo"), () -> restore(false));
        redoButton = button(owner, 20 + bw * 3, y, bw, 20, text("redo"), () -> restore(true));
        undoButton.setLeadingIcon(GraphicIcon.UNDO); redoButton.setLeadingIcon(GraphicIcon.FORWARD);
        button(owner, 24 + bw * 4, y, bw, 20, text("drafts"), () -> openDrafts(0)).setLeadingIcon(GraphicIcon.FOLDER);
        button(owner, 28 + bw * 5, y, bw, 20, text("new"), () ->
                confirmReplace(() -> { clearPendingNumbers(); change(RecipeDocument.empty()); saved = document; savedId = null; refresh(); })).setLeadingIcon(GraphicIcon.NEW);
        button(owner, 32 + bw * 6, y, bw, 20, text("import"), () -> confirmReplace(this::importClipboard)).setLeadingIcon(GraphicIcon.PASTE);

        int tabW = Math.max(30, (w - 24) / 3);
        for (int i = 0; i < 3; i++) {
            final int tab = i;
            var b = button(owner, 8 + i * (tabW + 4), 34, tabW, 20, text("view." + i), () -> {
                view = tab; refresh();
            });
            b.setSelected(view == i);
            b.setLeadingIcon(i == 0 ? GraphicIcon.RECIPE : i == 1 ? GraphicIcon.SETTINGS : GraphicIcon.CODE);
        }
        if (icons.isEmpty()) icons.putAll(UiWorkbench.itemIcons());
        int bodyY = 60;
        if (w >= 650) {
            canvas(owner, 12, bodyY, 190);
            properties(owner, 214, bodyY, 200);
            result(owner, 426, bodyY, w - 438);
        } else if (view == 0) canvas(owner, 12, bodyY, w - 24);
        else if (view == 1) properties(owner, 12, bodyY, w - 24);
        else result(owner, 12, bodyY, w - 24);
    }

    private void canvas(UiScreen owner, int x, int y, int width) {
        button(owner, x, y, width, 22, typeLabel(document.type()), this::openTypes).setLeadingIcon(GraphicIcon.RECIPE_TYPE);
        if (document.type() == RecipeType.CUSTOM) {
            button(owner, x, y + 32, width, 22, text("custom_edit"), this::openCustom);
            return;
        }
        int count = inputCount();
        var values = new ArrayList<ItemStack>();
        var names = new ArrayList<Component>();
        for (int i = 0; i < count; i++) { values.add(icon(document.inputs().get(i), isPotion(i))); names.add(slotName(i)); }
        int columns = count == 9 ? 3 : count;
        int cell = cellSize(owner.height, count);
        int gridWidth = Math.min(width, columns * cell + (columns - 1) * 2);
        int gridHeight = count == 9 ? cell * 3 + 4 : cell;
        inputs = owner.add(new UiIngredientSlots(Minecraft.getInstance().font,
                new Bounds(x, y + 46, gridWidth, gridHeight), text("inputs"), columns, values,
                UiWorkbench.preferences(owner), this::openSlot, i -> setSlot(i, ""), this::moveSlot));
        inputs.setSlotNames(names);
        inputs.setAllowDrag(document.type() == RecipeType.SHAPED || document.type() == RecipeType.SHAPELESS);
        if (document.type() == RecipeType.SHAPED || document.type() == RecipeType.SHAPELESS) {
            button(owner, x, y + 52 + gridHeight, width, 20, text("fill_sequence"), this::openSequence);
        }
        if (document.type() != RecipeType.SMITHING_TRIM || target == RecipeTarget.NEOFORGE_26_1_2) {
            int oy = y + 46;
            output = owner.add(new UiIngredientSlots(Minecraft.getInstance().font,
                    new Bounds(x + gridWidth + 24, oy, cell, cell), text("output"), 1,
                    List.of(resultIcon()),
                    UiWorkbench.preferences(owner), i -> openSlot(9), i -> setSlot(9, ""), (a,b) -> {}));
            output.setSlotNames(List.of(slotName(9)));
            output.setAllowDrag(false);
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

    private static int cellSize(int height, int inputs) {
        return Math.max(18, Math.min(30, (height - 124) / (inputs == 9 ? 3 : 1)));
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
        field(owner, x, y, width, "id", document.id(), value -> edit(value, document.result(), document.count(),
                document.experience(), document.cookingTime(), document.group()));
        field(owner, x, y + 30, width, "group", document.group(), value -> edit(document.id(), document.result(), document.count(),
                document.experience(), document.cookingTime(), value));
        if (document.type() != RecipeType.BREWING && document.type() != RecipeType.SMITHING_TRIM && document.type() != RecipeType.CUSTOM) {
            field(owner, x, y + 60, isCooking() ? width / 3 - 3 : width, "count", numberText.getOrDefault("count", Integer.toString(document.count())), value -> {
                try { int parsed = Integer.parseInt(value); invalidNumbers.remove("count"); edit(document.id(), document.result(), parsed, document.experience(), document.cookingTime(), document.group()); }
                catch (NumberFormatException ignored) { invalidNumbers.add("count"); status = text("invalid_number"); }
            });
        }
        if (isCooking()) {
            field(owner, x + width / 3, y + 60, width / 3 - 3, "time", numberText.getOrDefault("time", Integer.toString(document.cookingTime())), value -> {
                try { int parsed = Integer.parseInt(value); invalidNumbers.remove("time"); edit(document.id(), document.result(), document.count(), document.experience(), parsed, document.group()); }
                catch (NumberFormatException ignored) { invalidNumbers.add("time"); status = text("invalid_number"); }
            });
            field(owner, x + width * 2 / 3, y + 60, width / 3 - 3, "experience", numberText.getOrDefault("experience", Float.toString(document.experience())), value -> {
                try { float parsed = Float.parseFloat(value); invalidNumbers.remove("experience"); edit(document.id(), document.result(), document.count(), parsed, document.cookingTime(), document.group()); }
                catch (NumberFormatException ignored) { invalidNumbers.add("experience"); status = text("invalid_number"); }
            });
        }
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
        if (!invalidNumbers.isEmpty()) { status = text("invalid_number"); return null; }
        long now = System.nanoTime();
        if (document.equals(generatedDocument) && format == generatedFormat && now < registryValidationTime) return generatedArtifact;
        registryValidationTime = now + 1_000_000_000L;
        generatedDocument = document; generatedFormat = format; generatedArtifact = null;
        try {
            var issues = new ArrayList<>(RecipeGenerator.validate(document, target, format));
            if (issues.isEmpty()) issues.addAll(RecipeRegistryValidation.validate(document));
            if (!issues.isEmpty()) { var issue = issues.get(0); status = text("issue", text("field." + issue.field()), text("error." + issue.key())); return null; }
            generatedArtifact = RecipeGenerator.generate(document, target, format);
            return generatedArtifact;
        } catch (RuntimeException exception) { status = text("failed", exception.getMessage()); return null; }
    }

    private void draw(UiScreen owner, UiFrame frame) {
        undoButton.active = undo.canRestore(); redoButton.active = redo.canRestore();
        var artifact = artifact();
        if (copy != null) { copy.active = artifact != null && !busy; export.active = copy.active; }
        int w = owner.width;
        if (w >= 650 || view == 0) {
            int x = 12, y = 94;
            if (document.type() != RecipeType.CUSTOM) {
                line(frame, text("inputs"), x, y, w >= 650 ? 190 : w - 24, UiWorkbench.textColor());
                int cell = cellSize(owner.height, inputCount());
                int columns = inputCount() == 9 ? 3 : inputCount();
                int gridWidth = columns * cell + (columns - 1) * 2;
                line(frame, text(document.type() == RecipeType.SMITHING_TRIM ? "trim_pattern" : "output"), x + gridWidth + 24, y,
                        w >= 650 ? 190 - gridWidth - 24 : w - gridWidth - 48, UiWorkbench.textColor());
            }
            if (owner.height >= 220) line(frame, text("slot_help"), x, owner.height - 38, w >= 650 ? 190 : w - 24, UiWorkbench.secondaryTextColor());
        }
        if (w >= 650 || view == 1) {
            int x = w >= 650 ? 214 : 12, width = w >= 650 ? 200 : w - 24;
            line(frame, text("id"), x, 60, width, UiWorkbench.secondaryTextColor());
            line(frame, text("group"), x, 90, width, UiWorkbench.secondaryTextColor());
            if (document.type() != RecipeType.BREWING && document.type() != RecipeType.SMITHING_TRIM && document.type() != RecipeType.CUSTOM)
                line(frame, text("count"), x, 120, isCooking() ? width / 3 - 3 : width, UiWorkbench.secondaryTextColor());
            if (isCooking()) {
                line(frame, text("time"), x + width / 3, 120, width / 3 - 3, UiWorkbench.secondaryTextColor());
                line(frame, text("experience"), x + width * 2 / 3, 120, width / 3 - 3, UiWorkbench.secondaryTextColor());
            }
        }
        if ((w >= 650 || view == 2) && artifact != null) {
            int x = w >= 650 ? 426 : 12, width = w >= 650 ? w - 438 : w - 24;
            var lines = artifact.content().split("\n", -1);
            int rows = Math.max(1, (owner.height - 178) / 11), pages = Math.max(1, (lines.length + rows - 1) / rows);
            codePage = Math.max(0, Math.min(codePage, pages - 1));
            line(frame, text("code_page", codePage + 1, pages), x, 142, width, UiWorkbench.accentColor());
            for (int i = 0; i < rows && codePage * rows + i < lines.length; i++) {
                line(frame, Component.literal(lines[codePage * rows + i]), x, 155 + i * 11, width, UiWorkbench.textColor());
            }
        }
        line(frame, status, 12, Math.max(0, owner.height - 12), Math.max(0, w - 24), UiWorkbench.accentColor());
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
        UiWorkbench.builder(slotName(slot), pendingGuard(() -> !value[0].equals(original),
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
        UiWorkbench.builder(text("type"), screen).onInit(owner -> {
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
        UiWorkbench.builder(text("custom_edit"), pendingGuard(() -> !raw[0].equals(document.rawJson()),
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
        return UiWorkbench.builder(text("unsaved"), new Screen(text("title")) {
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
        UiWorkbench.builder(text("help"), screen).onInit(owner -> {
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
            UiWorkbench.builder(text("drafts"), screen).onInit(owner -> {
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
