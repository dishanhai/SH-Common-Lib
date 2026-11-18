// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_common_lib.api.ulk.recipe.GtMachineRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditor.builder;
import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditor.button;
import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditor.line;
import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditor.text;

/** A local machine sheet commits its parameters and both material lists as one history entry. */
final class GtceuRecipeEditor {
    private static final int ROW_HEIGHT = 44;
    private static final class Row {
        final boolean fluid;
        String id, amount;
        Component name;
        ItemStack icon;

        Row(boolean fluid, String id, int amount) {
            this.fluid = fluid;
            this.id = id;
            this.amount = Integer.toString(amount);
        }
    }

    private static final class Side {
        final List<Row> rows = new ArrayList<>();
        int page;
    }

    private record VisibleRow(Row row, int x, int y, int nameWidth, int unitX) {}
    private record Sheet(int x, int y, int width, int paramsY, int tablesY, int panelWidth,
                         int gap, int visible, int panelHeight) {
        static Sheet fit(int w, int h, int rowCount) {
            int margin = w < 420 ? 7 : w < 620 ? 11 : 20;
            int width = Math.min(w - margin * 2, 824), gap = w < 420 ? 6 : 10;
            int tablesTop = h < 205 ? 67 : 79;
            int capacity = Math.max(1, (h - tablesTop - 70) / ROW_HEIGHT);
            int visible = Math.min(Math.max(1, rowCount), capacity);
            int panelHeight = 28 + visible * ROW_HEIGHT + 26;
            int y = Math.max(1, Math.min(56, (h - tablesTop - panelHeight - 15) / 2));
            return new Sheet((w - width) / 2, y, width, y + (h < 205 ? 26 : 33), y + tablesTop,
                    (width - gap) / 2, gap, visible, panelHeight);
        }

        int panelX(boolean output) { return output ? x + panelWidth + gap : x; }
    }

    private final UiScreen parent;
    private final Consumer<String> apply;
    private final GtMachineRecipe original;
    private final Side inputs = new Side(), outputs = new Side();
    private final List<VisibleRow> visibleRows = new ArrayList<>();
    private final Map<String, ItemStack> items;
    private final Map<String, Fluid> fluids = new HashMap<>();
    private Sheet sheet;
    private String machine, duration, eut;
    private Component status, cycle;
    private Row focusRow;
    private String focusParam = "machine_id";
    private boolean focusAmount;

    static void open(UiScreen parent, String rawJson, Map<String, ItemStack> items, Consumer<String> apply) {
        var editor = load(parent, rawJson, items, apply);
        if (editor != null) editor.show();
    }

    /** Row indices are local to the requested capability. -1 appends a staged entry. */
    static void openLane(UiScreen parent, String rawJson, boolean output, boolean fluid, int row,
                         Map<String, ItemStack> items, Consumer<String> apply) {
        var editor = load(parent, rawJson, items, apply);
        if (editor == null) return;
        var side = output ? editor.outputs : editor.inputs;
        if (row == -1) editor.append(side, fluid);
        else if (row >= 0) editor.focusRow = side.rows.stream().filter(value -> value.fluid == fluid)
                .skip(row).findFirst().orElse(null);
        editor.show();
    }

    private static GtceuRecipeEditor load(UiScreen parent, String raw, Map<String, ItemStack> items, Consumer<String> apply) {
        GtMachineRecipe source;
        try {
            source = raw.isBlank() ? GtMachineRecipe.empty() : GtMachineRecipe.parse(raw);
        } catch (IllegalArgumentException invalid) {
            builder(text("type.gtceu"), parent).onInit(owner -> {
                button(owner, 12, 8, 64, 22, text("back"), owner::onClose);
                button(owner, 12, 66, owner.width - 24, 22, text("copy"),
                        () -> Minecraft.getInstance().keyboardHandler.setClipboard(raw));
            }).onForeground((owner, frame) -> line(frame, gt("unreadable"), 12, 46,
                    owner.width - 24, UiWorkbench.textColor())).open();
            return null;
        }
        return new GtceuRecipeEditor(parent, source, items, apply);
    }

    private GtceuRecipeEditor(UiScreen parent, GtMachineRecipe source, Map<String, ItemStack> items, Consumer<String> apply) {
        this.parent = parent;
        this.original = source;
        this.items = items;
        this.apply = apply;
        machine = source.machine();
        duration = Integer.toString(source.duration());
        eut = Long.toString(source.eut());
        loadRows(inputs, source.itemInputs(), source.fluidInputs());
        loadRows(outputs, source.itemOutputs(), source.fluidOutputs());
        for (var fluid : BuiltInRegistries.FLUID) fluids.put(BuiltInRegistries.FLUID.getKey(fluid).toString(), fluid);
        updateCycle();
    }

    private static void loadRows(Side side, List<GtMachineRecipe.Entry> items, List<GtMachineRecipe.Entry> fluids) {
        items.forEach(entry -> side.rows.add(new Row(false, entry.id(), entry.amount())));
        fluids.forEach(entry -> side.rows.add(new Row(true, entry.id(), entry.amount())));
    }

    private void show() {
        builder(gt("title"), parent).onInit(this::init).onRender(this::surfaces).onForeground(this::draw).open();
    }

    private void init(UiScreen owner) {
        visibleRows.clear();
        sheet = Sheet.fit(owner.width, owner.height, Math.max(inputs.rows.size(), outputs.rows.size()));
        int x = sheet.x(), width = sheet.width(), right = x + width, y = sheet.y();
        button(owner, right - 118, y + 2, 55, 22, Component.translatable("gui.cancel"), owner::onClose);
        button(owner, right - 60, y + 2, 60, 22, text("apply"), this::commit).setSelected(true);
        owner.onCloseRequest(() -> {
            if (original.equals(readModel(false))) return true;
            Minecraft.getInstance().setScreen(new ConfirmScreen(ok -> {
                if (ok) parent.open(); else owner.open();
            }, text("unsaved"), gt("discard_hint")));
            return false;
        });
        int column = Math.min(155, (width - 16) / 4), paramY = sheet.paramsY() + 15;
        param(owner, x + 6, paramY, width - 2 * column - 19, "machine_id", machine, next -> machine = next);
        param(owner, right - 2 * column - 6, paramY, column - 4, "duration", duration, next -> duration = next);
        param(owner, right - column - 6, paramY, column, "eut", eut, next -> eut = next);
        panel(owner, inputs, false);
        panel(owner, outputs, true);
        focusRow = null;
        focusParam = null;
        focusAmount = false;
    }

    private void param(UiScreen owner, int x, int y, int width, String key, String value, Consumer<String> edit) {
        var input = field(owner, x, y, width, gt(key), value, next -> {
            edit.accept(next);
            updateCycle();
        });
        if (key.equals(focusParam)) {
            owner.setFocused(input);
            input.setError(status);
        }
    }

    private void panel(UiScreen owner, Side side, boolean output) {
        int x = sheet.panelX(output) + 6, width = sheet.panelWidth() - 12, y = sheet.tablesY();
        int pages = Math.max(1, (side.rows.size() + sheet.visible() - 1) / sheet.visible());
        int pending = focusRow == null ? -1 : side.rows.indexOf(focusRow);
        if (pending >= 0) side.page = pending / sheet.visible();
        side.page = Math.min(side.page, pages - 1);
        if (pages > 1) {
            var previous = button(owner, x + width - 38, y + 4, 18, 18, text("previous"), () -> {
                side.page--; show();
            });
            previous.setIcon(GraphicIcon.PREVIOUS);
            previous.active = side.page > 0;
            var next = button(owner, x + width - 18, y + 4, 18, 18, text("next"), () -> {
                side.page++; show();
            });
            next.setIcon(GraphicIcon.NEXT);
            next.active = side.page + 1 < pages;
        }
        int start = side.page * sheet.visible();
        for (int i = start; i < Math.min(side.rows.size(), start + sheet.visible()); i++)
            resource(owner, side, output, side.rows.get(i), i, x, y + 28 + (i - start) * ROW_HEIGHT, width);
        int footer = y + sheet.panelHeight() - 23, half = (width - 4) / 2;
        button(owner, x, footer, half, 18, gt("add_item"), () -> { append(side, false); show(); }).setLeadingIcon(GraphicIcon.ADD);
        button(owner, x + half + 4, footer, width - half - 4, 18, gt("add_fluid"), () -> {
            append(side, true); show();
        }).setLeadingIcon(GraphicIcon.ADD);
    }

    private void append(Side side, boolean fluid) {
        status = null;
        focusParam = null;
        focusAmount = false;
        focusRow = new Row(fluid, "", fluid ? 1000 : 1);
        side.rows.add(focusRow);
    }

    private void resource(UiScreen owner, Side side, boolean output, Row row, int index, int x, int y, int width) {
        int qtyWidth = Math.min(63, Math.max(30, width / 5));
        int unitX = x + width - 37, qtyX = unitX - qtyWidth - 2;
        var idLabel = gt(row.fluid ? "fluid_id" : output ? "item_id" : "item_tag");
        var amountLabel = gt(row.fluid ? "volume" : "quantity");
        resolve(row);
        var id = field(owner, x, y + 21, width, idLabel, row.id, next -> row.id = next);
        var amount = field(owner, qtyX, y, qtyWidth, amountLabel, row.amount, next -> row.amount = next);
        var slot = owner.add(new UiIngredientSlots(Minecraft.getInstance().font, new Bounds(x, y, 20, 20),
                idLabel, 1, List.of(row.icon), UiWorkbench.preferences(owner), i -> {
                    if (row.fluid) { owner.setFocused(id); return; }
                    focusRow = row;
                    var seed = items.getOrDefault(row.id, ItemStack.EMPTY);
                    UiWorkbench.selectItem(owner, text("select_item"), owner::getFocused, seed, false, null, stack -> {
                        row.id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        focusRow = row;
                        show();
                    });
                }, i -> { row.id = ""; focusRow = row; show(); }, (from, to) -> {}));
        slot.setAllowDrag(false);
        slot.setRounded(true);
        slot.setSlotNames(List.of(row.name));
        id.setOnChange(next -> {
            row.id = next;
            resolve(row);
            slot.setItems(List.of(row.icon));
            slot.setSlotNames(List.of(row.name));
            status = null;
        });
        var remove = button(owner, x + width - 17, y + 1, 17, 17, gt("remove", index + 1), () -> {
            side.rows.remove(row);
            status = null;
            focusRow = side.rows.isEmpty() ? null : side.rows.get(Math.min(index, side.rows.size() - 1));
            show();
        });
        remove.setIcon(GraphicIcon.REMOVE);
        visibleRows.add(new VisibleRow(row, x, y, qtyX - x - 26, unitX));
        if (focusRow == row) {
            owner.setFocused(focusAmount ? amount : id);
            if (focusAmount) amount.setError(status);
        }
    }

    private void resolve(Row row) {
        if (row.fluid) {
            var fluid = fluids.get(row.id);
            row.icon = fluid == null || fluid.getBucket() == Items.AIR
                    ? Items.BUCKET.getDefaultInstance() : fluid.getBucket().getDefaultInstance();
            var block = fluid == null ? null : fluid.defaultFluidState().createLegacyBlock();
            row.name = block == null || block.isAir() ? fallback(row, "fluid") : block.getBlock().getName();
        } else if (row.id.startsWith("#")) {
            row.icon = Items.NAME_TAG.getDefaultInstance();
            row.name = gt("tag");
        } else {
            row.icon = items.getOrDefault(row.id, ItemStack.EMPTY);
            row.name = row.icon.isEmpty() ? fallback(row, "item") : row.icon.getHoverName();
        }
    }

    private static Component fallback(Row row, String key) {
        int colon = row.id.indexOf(':');
        return colon >= 0 && colon + 1 < row.id.length() ? Component.literal(row.id.substring(colon + 1)) : gt(key);
    }

    private UiTextField field(UiScreen owner, int x, int y, int width, Component label, String value, Consumer<String> edit) {
        var input = owner.add(new UiTextField(Minecraft.getInstance().font, new Bounds(x, y, width, 18),
                label, Math.max(4096, value.length()), UiWorkbench.preferences(owner)));
        input.setAppearance(UiTextField.Appearance.QUIET);
        input.setHint(label);
        input.setValue(value);
        input.setOnChange(next -> { status = null; input.setError(null); edit.accept(next); });
        return input;
    }

    private void surfaces(UiScreen owner, UiFrame frame) {
        var theme = UiTheme.current();
        int edge = frame.preferences().highContrast() ? theme.text() : GuiMotion.lerpColor(theme.background(), theme.border(), .52f);
        RecipeEditor.drawBackdrop(owner, frame);
        RoundedCorners.draw(frame.graphics(), sheet.x(), sheet.y(), 25, 25, 6, 0, 0,
                GuiMotion.lerpColor(theme.surface(), theme.accent(), .22f) | 0xFF000000);
        RoundedCorners.draw(frame.graphics(), sheet.x(), sheet.paramsY(), sheet.width(), 39, 7, 1, edge, theme.surface());
        for (boolean output : List.of(false, true)) {
            int x = sheet.panelX(output);
            RoundedCorners.draw(frame.graphics(), x, sheet.tablesY(), sheet.panelWidth(), sheet.panelHeight(), 8, 1, edge, theme.surface());
            RoundedCorners.draw(frame.graphics(), x + 5, sheet.tablesY() + 24, sheet.panelWidth() - 10, 1, 0, 0, 0, edge);
        }
    }

    private void draw(UiScreen owner, UiFrame frame) {
        var theme = UiTheme.current();
        int ink = UiWorkbench.textColor(), muted = UiWorkbench.secondaryTextColor();
        int x = sheet.x(), right = x + sheet.width(), y = sheet.y();
        GraphicIcon.GREGTECH.draw(frame.graphics(), x + 4, y + 4, 1, ink, frame.preferences());
        line(frame, gt("title").copy().withStyle(s -> s.withBold(true)), x + 32, y + 2, sheet.width() - 155, ink);
        line(frame, gt("target"), x + 32, y + 15, sheet.width() - 155, muted);
        int column = Math.min(155, (sheet.width() - 16) / 4), labelsY = sheet.paramsY() + 4;
        line(frame, gt("machine_id"), x + 10, labelsY, sheet.width() - 2 * column - 20, muted);
        line(frame, gt("duration"), right - 2 * column - 2, labelsY, column - 8, muted);
        line(frame, gt("eut"), right - column - 2, labelsY, column - 4, muted);
        for (boolean output : List.of(false, true)) {
            var side = output ? outputs : inputs;
            int left = sheet.panelX(output), top = sheet.tablesY();
            var icon = output ? GraphicIcon.OUTPUT : GraphicIcon.INPUT;
            int accent = theme.light() ? (output ? 0xFF226756 : 0xFF285794) : (output ? 0xFF82CDB8 : 0xFF91BFFF);
            icon.draw(frame.graphics(), left + 7, top + 4, 1, frame.preferences().highContrast() ? ink : accent, frame.preferences());
            line(frame, gt(output ? "outputs" : "inputs").copy().withStyle(s -> s.withBold(true)),
                    left + 28, top + 8, sheet.panelWidth() / 2 - 22, ink);
            int pages = (side.rows.size() + sheet.visible() - 1) / sheet.visible();
            if (pages > 1) line(frame, gt("page", side.page + 1, pages), left + sheet.panelWidth() / 2 - 12,
                    top + 8, sheet.panelWidth() / 2 - 36, muted);
            if (side.rows.isEmpty()) line(frame, gt("empty"), left + 10, top + 37, sheet.panelWidth() - 20, muted);
        }
        for (var v : visibleRows) {
            line(frame, v.row().name, v.x() + 24, v.y() + 6, v.nameWidth(), ink);
            line(frame, gt(v.row().fluid ? "unit_fluid" : "unit_items"), v.unitX(), v.y() + 6, 19, muted);
        }
        var footer = status == null ? cycle : status;
        if (footer != null) line(frame, footer, x + 2, sheet.tablesY() + sheet.panelHeight() + 5, sheet.width() - 4,
                status == null ? muted : UiWorkbench.accentColor());
    }

    private void updateCycle() {
        try {
            int ticks = Integer.parseInt(duration.trim());
            long power = Long.parseLong(eut.trim());
            if (ticks <= 0) { cycle = null; return; }
            String seconds = BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
            var energy = BigInteger.valueOf(power).abs().multiply(BigInteger.valueOf(ticks));
            cycle = power == 0 ? gt("time_only", seconds) : gt(power > 0 ? "energy_in" : "energy_out",
                    NumberFormat.getIntegerInstance().format(energy), seconds);
        } catch (NumberFormatException invalid) { cycle = null; }
    }

    private void commit() {
        var recipe = readModel(true);
        if (recipe == null) return;
        String source = recipe.toJson();
        if (source.length() > 1_048_576) status = text("too_large");
        else apply.accept(source);
    }

    private GtMachineRecipe readModel(boolean report) {
        int ticks;
        long power;
        try { ticks = Integer.parseInt(duration.trim()); }
        catch (NumberFormatException invalid) { return numberError(report, "duration", null); }
        try { power = Long.parseLong(eut.trim()); }
        catch (NumberFormatException invalid) { return numberError(report, "eut", null); }
        if (power == Long.MIN_VALUE) return numberError(report, "eut", null);
        var itemIn = new ArrayList<GtMachineRecipe.Entry>();
        var fluidIn = new ArrayList<GtMachineRecipe.Entry>();
        var itemOut = new ArrayList<GtMachineRecipe.Entry>();
        var fluidOut = new ArrayList<GtMachineRecipe.Entry>();
        for (var side : List.of(inputs, outputs)) {
            for (var row : side.rows) {
                int amount;
                try { amount = Integer.parseInt(row.amount.trim()); }
                catch (NumberFormatException invalid) { return numberError(report, row.fluid ? "volume" : "quantity", row); }
                var values = side == inputs ? (row.fluid ? fluidIn : itemIn) : (row.fluid ? fluidOut : itemOut);
                values.add(new GtMachineRecipe.Entry(row.id.trim(), amount));
            }
        }
        return new GtMachineRecipe(machine.trim(), ticks, power, itemIn, itemOut, fluidIn, fluidOut);
    }

    private GtMachineRecipe numberError(boolean report, String key, Row row) {
        if (report) {
            status = gt("number", gt(key));
            focusRow = row;
            focusAmount = row != null;
            focusParam = row == null ? key : null;
            show();
        }
        return null;
    }

    private static Component gt(String key, Object... args) { return text("gt." + key, args); }
}
