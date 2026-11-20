// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_common_lib.api.ulk.fumo.FumoRewardDefinition;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeExportSettings;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeDocument;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeWorkspace;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiNavigationRail;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Uses actual widgets, screen initialization and the native mouse event bridge. */
@EventBusSubscriber(modid = "sh_common_lib", value = Dist.CLIENT)
public final class RecipeEditorSmoke {
    private static int step, scale = 4;
    private static long deadline, next;
    private static boolean finished;
    private static Screen canvas;
    private static Object itemCatalog, itemSearchIndex;
    private static String expectedResultId;
    private static String expectedId;
    private static final StringBuilder viewports = new StringBuilder();

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        String output = System.getProperty("shclib.recipe.smoke");
        if (output == null || finished) return;
        var client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (deadline == 0) deadline = now + 120_000_000_000L;
        if (now < next) return;
        try {
            require(now < deadline, "Timed out at step " + step);
            if (client.getOverlay() != null) return;
            if (step == 0) {
                if (!(client.screen instanceof TitleScreen)) return;
                var recipe = new com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipe("recipe_smoke:public_api",
                        com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipe.Kind.POTION,
                        "minecraft:water", "minecraft:diamond", "minecraft:awkward");
                com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipes.register(recipe);
                require(com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipes.find(recipe.id()).orElseThrow().equals(recipe), "Public brewing registration failed");
                com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipes.remove(recipe.id());
                Class.forName("net.minecraft.world.inventory.BrewingStandMenu$IngredientsSlot");
                require(RecipeEditorAccess.unavailable() == null, "Recipe editor capability check rejected this UUK");
                RecipeEditorAccess.open(client.screen, () -> null);
            } else if (step == 1) {
                canvas = client.screen;
                require(canvas.width >= 320 && canvas.height >= 180, "Unexpected viewport");
                viewports.append(canvas.width).append('x').append(canvas.height).append(" scale=").append(client.getWindow().getGuiScale()).append('\n');
                assertBounds(canvas);
                capture(output, "canvas-" + scale);
                click(canvas, slots(canvas).get(0));
            } else if (step == 2) {
                require(slots(client.screen).size() == 2, "Slot did not open resource grid and selection preview");
                require(!fields(client.screen).isEmpty(), "Resource search field is missing");
                assertBounds(client.screen);
                itemCatalog = fieldValue(com.dishanhai.sh_better_ui.client.gui.scaled.UiResourcePicker.class, "cachedItems", null);
                var grid = slots(client.screen).getFirst();
                var first = stack(grid, 0);
                var nextRow = stack(grid, (grid.getWidth() + 2) / 20);
                require(scroll(client.screen, grid.getX() + 5, grid.getY() + 5, -1), "Picker did not handle the wheel");
                require(net.minecraft.world.item.ItemStack.isSameItemSameComponents(nextRow, stack(grid, 0)), "Wheel did not advance by one row");
                require(scroll(client.screen, grid.getX() + 5, grid.getY() + 5, 1), "Picker did not scroll back");
                require(net.minecraft.world.item.ItemStack.isSameItemSameComponents(first, stack(grid, 0)), "Reverse scrolling did not restore the grid");
                verifyPickerScrollEnd(client.screen, grid);
                type(fields(client.screen).get(0), "minecraft:stone");
                itemSearchIndex = fieldValue(itemCatalog.getClass(), "searchIndex", itemCatalog);
                require(itemSearchIndex != null, "Search did not build the item index");
            } else if (step == 3) {
                var picker = client.screen;
                click(picker, slots(picker).get(0));
                require(client.screen == picker, "Click committed before confirmation");
                capture(output, "picker-staged-" + scale);
            } else if (step == 4) {
                capture(output, "picker-confirmation-" + scale);
                var grid = slots(client.screen).get(0);
                expectedId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack(grid, 1).getItem()).toString();
                clickAt(client.screen, grid.getX() + 25, grid.getY() + 5);
                require(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack(slots(client.screen).get(1), 0).getItem()).toString().equals(expectedId),
                        "Repeated click did not replace the selection draft");
                click(client.screen, pickerButton(client.screen, "confirm"));
            } else if (step == 5) {
                require(client.screen != canvas, "Confirmation should rebuild the recipe canvas");
                require(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack(slots(client.screen).get(0), 0).getItem()).toString().equals(expectedId),
                        "Confirmed resource did not reach the recipe");
                capture(output, "selected-" + scale);
                click(client.screen, slots(client.screen).get(1));
            } else if (step == 6) {
                require(itemCatalog == fieldValue(com.dishanhai.sh_better_ui.client.gui.scaled.UiResourcePicker.class, "cachedItems", null),
                        "Opening another slot rebuilt the item catalog");
                require(itemSearchIndex == fieldValue(itemCatalog.getClass(), "searchIndex", itemCatalog),
                        "Opening another slot rebuilt the search index");
                type(fields(client.screen).get(0), "minecraft:stone");
                click(client.screen, slots(client.screen).get(0));
                type(fields(client.screen).get(1), "0");
                require(!pickerButton(client.screen, "confirm").active, "Invalid quantity allowed confirmation");
                type(fields(client.screen).get(1), "64");
                click(client.screen, pickerButton(client.screen, "confirm"));
            } else if (step == 7) {
                require(stack(slots(client.screen).get(1), 0).getCount() == 64, "Output quantity did not apply");
                expectedResultId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack(slots(client.screen).get(1), 0).getItem()).toString();
                capture(output, "workspace-" + scale);
                click(client.screen, slots(client.screen).get(1));
            } else if (step == 8) {
                require(fields(client.screen).get(1).getValue().equals("64"), "Preselected quantity was lost");
                type(fields(client.screen).get(1), "5");
                click(client.screen, pickerButton(client.screen, "cancel"));
            } else if (step == 9) {
                require(stack(slots(client.screen).get(1), 0).getCount() == 64, "Cancel changed output quantity");
                click(client.screen, slots(client.screen).get(0));
            } else if (step == 10) {
                click(client.screen, pickerButton(client.screen, "manual"));
            } else if (step == 11) {
                require(!fields(client.screen).isEmpty(), "Manual ID editor did not initialize");
                type(fields(client.screen).get(0), "#minecraft:logs");
                click(client.screen, button(client.screen, "apply"));
            } else if (step == 12) {
                var rail = client.screen.children().stream().filter(UiNavigationRail.class::isInstance)
                        .map(UiNavigationRail.class::cast).findFirst().orElseThrow();
                // The middle entry is Properties in both the horizontal segments and the sidebar.
                clickAt(client.screen, rail.getX() + rail.getWidth() / 2.0, rail.getY() + rail.getHeight() / 2.0);
            } else if (step == 13) {
                require(fields(client.screen).size() >= 3, "Properties fields did not initialize");
                require(fields(client.screen).getFirst().getValue().equals(expectedResultId), "Recipe ID did not use the selected output ID");
                assertBounds(client.screen);
                capture(output, "properties-" + scale);
                click(client.screen, button(client.screen, "actions"));
            } else if (step == 14) {
                assertBounds(client.screen);
                require(overlay(client.screen).isModalOpen(), "Actions did not open as an overlay");
                capture(output, "actions-" + scale);
                pressOverlay(client.screen, GLFW.GLFW_KEY_ESCAPE);
                var editor = client.screen;
                editor.onClose();
                require(client.screen == editor && overlay(editor).isModalOpen(), "Unsaved prompt replaced the editor");
                pressOverlay(editor, GLFW.GLFW_KEY_ENTER);
                require(client.screen == editor, "Continue editing closed the recipe");
                verifySequenceDrag(editor);
                verifyManualResultId(client.screen);
                if (scale == 4) verifyDraftScroll(client.screen, output);
                if (scale != 5) {
                    scale = scale == 4 ? 3 : scale == 3 ? 1 : 5;
                    setScale(client);
                    RecipeEditor.open(client.screen, () -> null, RecipeTarget.NEOFORGE_1_21_1);
                    step = 0;
                } else RecipeEditor.open(client.screen, () -> null, RecipeTarget.NEOFORGE_1_21_1);
            } else if (step == 15) {
                if (!GenericRecipeSmoke.run(client, output)) return;
                RecipeEditor.open(client.screen, () -> null, RecipeTarget.NEOFORGE_1_21_1);
            } else if (step == 16) {
                if (!GtMachineSmoke.run(client, output)) return;
                RecipeEditor.open(client.screen, () -> null, RecipeTarget.NEOFORGE_1_21_1);
            } else if (step >= 17 && fumo(client, output, step - 17)) {
                Files.writeString(Path.of(output, "success.txt"), viewports
                        + "Native picker cache reuse, row scrolling, sequence drag and cancel, output ID, workspace navigation, generic field editing and Forge GTCEu authoring passed; Fumo item selection, weight validation and editing,"
                        + " removal, undo, default and custom JSON export, and overwrite confirmation passed.\n");
                finished = true;
                client.stop();
                return;
            }
            step++;
            if (step == 0) step = 1;
            next = now + 500_000_000L;
        } catch (Throwable failure) {
            finished = true;
            try {
                var message = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(message));
                Files.writeString(Path.of(output, "failure.txt"), message.toString());
            } catch (Exception ignored) {}
            failure.printStackTrace();
            client.stop();
        }
    }

    private static boolean fumo(Minecraft client, String output, int phase) throws Exception {
        var screen = client.screen;
        var workspace = new RecipeWorkspace(Path.of(output, RecipeWorkspace.DEFAULT_DIRECTORY));
        Path export = Path.of(output, RecipeWorkspace.DEFAULT_DIRECTORY, "exports").resolve(FumoRewardDefinition.DATA_PATH);
        String relativeRoot = "config/custom recipe smoke";
        Path customExport = Path.of(output, relativeRoot).resolve(FumoRewardDefinition.DATA_PATH);
        switch (phase) {
            case 0 -> assertBounds(screen);
            case 1 -> click(screen, button(screen, "type.shaped"));
            case 2 -> click(screen, button(screen, "type.fumo_rewards"));
            case 3 -> clickChoice(screen, true);
            case 4 -> {
                assertBounds(screen);
                click(screen, button(screen, "fumo.add"));
            }
            case 5 -> {
                assertBounds(screen);
                click(screen, button(screen, "select_item"));
            }
            case 6 -> type(fields(screen).getFirst(), "*minecraft:diamond");
            case 7 -> {
                var grid = slots(screen).getFirst();
                expectedId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack(grid, 0).getItem()).toString();
                click(screen, grid);
                click(screen, pickerButton(screen, "confirm"));
            }
            case 8 -> {
                require(fields(screen).getFirst().getValue().equals(expectedId), "Fumo item picker did not populate the form");
                type(fields(screen).get(1), "0");
                click(screen, button(screen, "apply"));
            }
            case 9 -> {
                require(fields(screen).get(1).getValue().equals("0"), "Invalid weight left the Fumo form");
                type(fields(screen).get(1), "7");
                click(screen, button(screen, "apply"));
            }
            case 10 -> {
                assertBounds(screen);
                capture(output, "fumo-canvas");
                click(screen, fumoRow(screen));
            }
            case 11 -> {
                require(fields(screen).get(1).getValue().equals("7"), "Fumo weight was lost");
                type(fields(screen).get(1), "11");
                click(screen, button(screen, "apply"));
            }
            case 12 -> click(screen, button(screen, "undo"));
            case 13 -> click(screen, fumoRow(screen));
            case 14 -> {
                require(fields(screen).get(1).getValue().equals("7"), "Fumo weight undo failed");
                click(screen, button(screen, "fumo.remove"));
            }
            case 15 -> {
                require(screen.children().stream().noneMatch(RecipeEditorSmoke::isFumoRow), "Removing a Fumo reward did not clear it");
                click(screen, button(screen, "undo"));
            }
            case 16 -> {
                var rail = screen.children().stream().filter(UiNavigationRail.class::isInstance)
                        .map(UiNavigationRail.class::cast).findFirst().orElseThrow();
                screen.setFocused(rail);
                require(rail.keyPressed(GLFW.GLFW_KEY_END, 0, 0) && rail.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                        "Fumo export navigation failed");
            }
            case 17 -> {
                workspace.saveExportSettings(RecipeExportSettings.DEFAULT);
                Files.deleteIfExists(export);
                click(screen, button(screen, "export"));
            }
            case 18 -> {
                var json = com.google.gson.JsonParser.parseString(Files.readString(export)).getAsJsonObject();
                require(FumoRewardDefinition.read(json).equals(List.of(new FumoRewardDefinition.Entry(expectedId, 7))),
                        "Fumo export did not retain item and weight");
                RecipeEditorAccess.openDeveloperSettings(screen, () -> null);
            }
            case 19 -> {
                assertBounds(screen);
                click(screen, button(screen, "export_settings.custom_off"));
                type(fields(screen).getFirst(), relativeRoot);
                click(screen, button(screen, "export_settings.save"));
                capture(output, "recipe-export-settings");
            }
            case 20 -> {
                require(workspace.loadExportSettings().equals(new RecipeExportSettings(true, relativeRoot)),
                        "Developer export settings did not persist");
                screen.onClose();
            }
            case 21 -> {
                Files.deleteIfExists(customExport);
                click(screen, button(screen, "export"));
            }
            case 22 -> {
                require(Files.mismatch(export, customExport) == -1, "Configured JSON export did not match workspace export");
                Files.writeString(customExport, "existing");
                click(screen, button(screen, "export"));
            }
            case 23 -> clickChoice(screen, false);
            case 24 -> {
                require(Files.readString(customExport).equals("existing"), "Cancelled overwrite changed the file");
                click(screen, button(screen, "export"));
            }
            case 25 -> clickChoice(screen, true);
            case 26 -> {
                require(Files.mismatch(export, customExport) == -1, "Confirmed overwrite did not write the configured path");
                return true;
            }
            default -> throw new IllegalStateException("Unexpected Fumo smoke phase " + phase);
        }
        return false;
    }

    static void clickChoice(Screen screen, boolean yes) {
        chooseOverlay(screen, yes ? 1 : 0);
    }

    static void chooseOverlay(Screen screen, int tabs) {
        require(overlay(screen).isModalOpen(), "Expected a confirmation overlay");
        for (int i = 0; i < tabs; i++) pressOverlay(screen, GLFW.GLFW_KEY_TAB);
        pressOverlay(screen, GLFW.GLFW_KEY_ENTER);
    }

    private static com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost overlay(Screen screen) {
        var layer = com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost.current(screen);
        require(layer != null, "Expected a workbench overlay");
        return layer;
    }

    private static void pressOverlay(Screen screen, int key) {
        var layer = overlay(screen);
        require(layer.keyPressed(key, 0, 0), "Overlay did not capture the key");
        layer.keyReleased(key, 0, 0);
    }

    private static Object fieldValue(Class<?> type, String name, Object owner) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void verifySequenceDrag(Screen editor) throws Exception {
        click(editor, button(editor, "actions"));
        chooseOverlay(editor, 3);
        var picker = Minecraft.getInstance().screen;
        require(picker != editor && slots(picker).size() == 2, "Sequence action did not open the resource picker");
        var grid = slots(picker).getFirst();
        var preview = slots(picker).get(1);
        for (int i = 0; i < 3; i++) clickAt(picker, grid.getX() + 5 + i * 20, grid.getY() + 5);
        var first = stack(preview, 0).getItem();
        var second = stack(preview, 1).getItem();
        var third = stack(preview, 2).getItem();
        double x = preview.getX() + 5, y = preview.getY() + 5;
        drag(picker, x, y, x + 20, y, true);
        require(Minecraft.getInstance().screen == picker && stack(preview, 0).getItem() == first,
                "Escape during drag discarded or reordered the selection");
        drag(picker, x, y, x + 20, y, false);
        require(stack(preview, 0).getItem() == second && stack(preview, 1).getItem() == first, "Drag did not reorder the preview");
        drag(picker, x, y, x, y + 20, false);
        require(stack(preview, 0).getItem() == first && stack(preview, 1).getItem() == third
                && stack(preview, 2).getItem() == second && stack(preview, 3).isEmpty(), "Dropping on an empty cell did not move to the end");
        click(picker, pickerButton(picker, "confirm"));
        var updated = workspaceView(Minecraft.getInstance().screen, GLFW.GLFW_KEY_HOME);
        var ingredients = slots(updated).getFirst();
        require(stack(ingredients, 0).getItem() == first && stack(ingredients, 1).getItem() == third
                && stack(ingredients, 2).getItem() == second, "Confirmed recipe lost the dragged order");
    }

    private static void verifyManualResultId(Screen screen) {
        var properties = workspaceView(screen, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN);
        type(fields(properties).getFirst(), "");
        var canvas = workspaceView(properties, GLFW.GLFW_KEY_HOME);
        click(canvas, slots(canvas).get(1));
        var picker = Minecraft.getInstance().screen;
        click(picker, pickerButton(picker, "manual"));
        var form = Minecraft.getInstance().screen;
        type(fields(form).getFirst(), "minecraft:diamond");
        click(form, button(form, "apply"));
        var edited = workspaceView(Minecraft.getInstance().screen, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN);
        require(fields(edited).getFirst().getValue().equals("minecraft:diamond"), "Manual output did not fill the blank recipe ID");
    }

    private static void verifyPickerScrollEnd(Screen screen, UiIngredientSlots grid) throws Exception {
        var bar = screen.children().stream().filter(com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar.class::isInstance)
                .map(com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar.class::cast)
                .filter(widget -> widget.visible && widget.getX() > grid.getX() && widget.getY() == grid.getY())
                .findFirst().orElseThrow();
        screen.setFocused(bar);
        require(bar.keyPressed(GLFW.GLFW_KEY_END, 0, 0), "Scrollbar did not accept End");
        var last = stack(grid, 0);
        scroll(screen, grid.getX() + 5, grid.getY() + 5, -1);
        require(net.minecraft.world.item.ItemStack.isSameItemSameComponents(last, stack(grid, 0)), "Grid wrapped at the end");
        require(bar.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0), "Scrollbar did not accept Home");
    }

    private static void verifyDraftScroll(Screen screen, String output) throws Exception {
        var workspace = new RecipeWorkspace(Path.of(output, RecipeWorkspace.DEFAULT_DIRECTORY));
        var draft = RecipeDocument.empty();
        for (int i = 0; i < 9; i++) {
            workspace.save(new RecipeDocument("recipe_smoke:scroll/" + i, draft.type(), draft.inputs(), "",
                    1, 0, 200, "", ""), true);
        }
        click(screen, button(screen, "drafts"));
        var dialog = Minecraft.getInstance().screen;
        var row = draftRow(dialog, "recipe_smoke:scroll/0");
        require(row != null && scroll(dialog, row.getX() + 5, row.getY() + 5, -1), "Draft list did not scroll");
        require(draftRow(dialog, "recipe_smoke:scroll/0") == null && draftRow(dialog, "recipe_smoke:scroll/1") != null,
                "Draft list did not shift by one row");
        click(dialog, button(dialog, "back"));
        require(Minecraft.getInstance().screen == screen, "Draft dialog did not return to its editor");
    }

    private static UiButton draftRow(Screen screen, String id) {
        return screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(row -> row.getMessage().getString().equals(id)).findFirst().orElse(null);
    }

    private static Screen workspaceView(Screen screen, int edge, int... directions) {
        var rail = screen.children().stream().filter(UiNavigationRail.class::isInstance)
                .map(UiNavigationRail.class::cast).findFirst().orElseThrow();
        screen.setFocused(rail);
        require(rail.keyPressed(edge, 0, 0), "Could not focus the workspace view");
        for (int direction : directions) if (rail.keyPressed(direction, 0, 0)) break;
        require(rail.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Could not open the workspace view");
        return Minecraft.getInstance().screen;
    }

    private static void drag(Screen screen, double x, double y, double toX, double toY, boolean cancel) {
        var press = new ScreenEvent.MouseButtonPressed.Pre(screen, x, y, 0);
        NeoForge.EVENT_BUS.post(press);
        if (!press.isCanceled()) screen.mouseClicked(x, y, 0);
        var movement = new ScreenEvent.MouseDragged.Pre(screen, toX, toY, 0, toX - x, toY - y);
        NeoForge.EVENT_BUS.post(movement);
        if (!movement.isCanceled()) screen.mouseDragged(toX, toY, 0, toX - x, toY - y);
        if (cancel) screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        var release = new ScreenEvent.MouseButtonReleased.Pre(screen, toX, toY, 0);
        NeoForge.EVENT_BUS.post(release);
        if (!release.isCanceled()) screen.mouseReleased(toX, toY, 0);
    }

    private static boolean isFumoRow(Object child) {
        return child instanceof UiButton button && button.getMessage().getContents() instanceof TranslatableContents label
                && label.getKey().equals("screen.sh_common_lib.recipe_editor.fumo.row");
    }

    private static UiButton fumoRow(Screen screen) {
        return screen.children().stream().filter(RecipeEditorSmoke::isFumoRow).map(UiButton.class::cast).findFirst().orElseThrow();
    }

    static void assertBounds(Screen screen) {
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            require(widget.getX() >= 0 && widget.getY() >= 0
                    && widget.getX() + widget.getWidth() <= screen.width
                    && widget.getY() + widget.getHeight() <= screen.height,
                    "Widget outside viewport: " + widget.getMessage().getString());
        }
    }

    private static void setScale(Minecraft client) {
        client.options.guiScale().set(scale);
        client.resizeDisplay();
    }

    private static List<UiIngredientSlots> slots(Screen screen) {
        return screen.children().stream().filter(UiIngredientSlots.class::isInstance).map(UiIngredientSlots.class::cast).toList();
    }
    static List<UiTextField> fields(Screen screen) {
        return screen.children().stream().filter(UiTextField.class::isInstance).map(UiTextField.class::cast).toList();
    }
    static UiButton button(Screen screen, String key) {
        String label = Component.translatable("screen.sh_common_lib.recipe_editor." + key).getString();
        return screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    static void click(Screen screen, AbstractWidget widget) {
        double x = widget.getX() + 5, y = widget.getY() + 5;
        clickAt(screen, x, y);
    }
    static void clickAt(Screen screen, double x, double y) {
        var press = new ScreenEvent.MouseButtonPressed.Pre(screen, x, y, 0);
        NeoForge.EVENT_BUS.post(press);
        if (!press.isCanceled()) screen.mouseClicked(x, y, 0);
        var release = new ScreenEvent.MouseButtonReleased.Pre(screen, x, y, 0);
        NeoForge.EVENT_BUS.post(release);
        if (!release.isCanceled()) screen.mouseReleased(x, y, 0);
    }
    static boolean scroll(Screen screen, double x, double y, double vertical) {
        return screen.mouseScrolled(x, y, 0, vertical);
    }
    private static UiButton pickerButton(Screen screen, String key) {
        String label = Component.translatable("sh_better_ui.resource_picker." + key).getString();
        return screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static net.minecraft.world.item.ItemStack stack(UiIngredientSlots slots, int index) throws Exception {
        var field = UiIngredientSlots.class.getDeclaredField("items"); field.setAccessible(true);
        return (net.minecraft.world.item.ItemStack) ((List<?>) field.get(slots)).get(index);
    }
    static void type(UiTextField field, String value) {
        Minecraft.getInstance().screen.setFocused(field);
        int length = field.getValue().length();
        for (int i = 0; i < length; i++) field.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char c : value.toCharArray()) require(field.charTyped(c, 0), "Text input rejected");
    }
    static void capture(String output, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(output, name + ".png"));
        }
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
