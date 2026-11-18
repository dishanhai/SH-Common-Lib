// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

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
                RecipeEditor.open(client.screen, () -> null, RecipeTarget.NEOFORGE_1_21_1);
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
                type(fields(client.screen).get(0), "minecraft:stone");
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
                type(fields(client.screen).get(0), "minecraft:stone");
                click(client.screen, slots(client.screen).get(0));
                type(fields(client.screen).get(1), "0");
                require(!pickerButton(client.screen, "confirm").active, "Invalid quantity allowed confirmation");
                type(fields(client.screen).get(1), "64");
                click(client.screen, pickerButton(client.screen, "confirm"));
            } else if (step == 7) {
                require(stack(slots(client.screen).get(1), 0).getCount() == 64, "Output quantity did not apply");
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
                click(client.screen, button(client.screen, "view.1"));
            } else if (step == 13) {
                require(fields(client.screen).size() >= 3, "Properties fields did not initialize");
                assertBounds(client.screen);
                capture(output, "properties-" + scale);
                click(client.screen, button(client.screen, "actions"));
            } else if (step == 14) {
                assertBounds(client.screen);
                require(button(client.screen, "fill_sequence").active, "Sequence action missing from actions page");
                capture(output, "actions-" + scale);
                click(client.screen, button(client.screen, "back"));
                if (scale != 5) {
                    scale = scale == 4 ? 3 : scale == 3 ? 1 : 5;
                    setScale(client);
                    RecipeEditor.open(client.screen, () -> null, RecipeTarget.NEOFORGE_1_21_1);
                    step = 0;
                } else {
                    Files.writeString(Path.of(output, "success.txt"), viewports + "Native slot click, staged/repeated selection, confirm, output quantity/invalid input/cancel, manual tag ID, properties initialized; compact/wide/action bounds at scales 4/3/1/5\n");
                    finished = true;
                    client.stop();
                    return;
                }
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

    private static void assertBounds(Screen screen) {
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
    private static List<UiTextField> fields(Screen screen) {
        return screen.children().stream().filter(UiTextField.class::isInstance).map(UiTextField.class::cast).toList();
    }
    private static UiButton button(Screen screen, String key) {
        String label = Component.translatable("screen.sh_common_lib.recipe_editor." + key).getString();
        return screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static void click(Screen screen, AbstractWidget widget) {
        double x = widget.getX() + 5, y = widget.getY() + 5;
        clickAt(screen, x, y);
    }
    private static void clickAt(Screen screen, double x, double y) {
        var press = new ScreenEvent.MouseButtonPressed.Pre(screen, x, y, 0);
        NeoForge.EVENT_BUS.post(press);
        if (!press.isCanceled()) screen.mouseClicked(x, y, 0);
        var release = new ScreenEvent.MouseButtonReleased.Pre(screen, x, y, 0);
        NeoForge.EVENT_BUS.post(release);
        if (!release.isCanceled()) screen.mouseReleased(x, y, 0);
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
    private static void type(UiTextField field, String value) {
        Minecraft.getInstance().screen.setFocused(field);
        int length = field.getValue().length();
        for (int i = 0; i < length; i++) field.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char c : value.toCharArray()) require(field.charTyped(c, 0), "Text input rejected");
    }
    private static void capture(String output, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(output, name + ".png"));
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
