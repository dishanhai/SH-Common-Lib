// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiNavigationRail;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditorSmoke.*;

final class GenericRecipeSmoke {
    private static int step;

    static boolean run(Minecraft client, String output) throws Exception {
        Screen current = client.screen;
        assertBounds(current);
        switch (step++) {
            case 0 -> {
                client.options.guiScale().set(3);
                client.resizeDisplay();
                client.keyboardHandler.setClipboard("""
                        {"type":"example:press","ingredients":[{"tag":"c:ingots/iron","count":2}],
                         "result":{"item":"example:iron_plate","count":1},"duration":80,
                         "energy_per_tick":32,"meta":{"values":[null,42]}}
                        """);
                click(current, button(current, "actions"));
            }
            case 1 -> click(current, button(current, "import"));
            case 2 -> click(current, button(current, "generic_edit"));
            case 3 -> {
                capture(output, "generic-fields");
                openContainer(current, "meta");
            }
            case 4 -> openContainer(current, "values");
            case 5 -> {
                var field = fields(current).get(1);
                type(field, "");
                click(current, button(current, "apply"));
                require(client.screen == current, "Invalid JSON was applied");
                type(field, "7");
            }
            case 6 -> {
                click(current, button(current, "generic.add"));
                click(client.screen, button(client.screen, "generic.kind.boolean"));
                click(client.screen, button(client.screen, "generic.done"));
                require(fields(client.screen).size() == 3, "Array insertion did not create an editable boolean");
            }
            case 7 -> {
                click(current, button(current, "apply"));
            }
            case 8 -> {
                var rail = current.children().stream().filter(UiNavigationRail.class::isInstance)
                        .map(UiNavigationRail.class::cast).findFirst().orElseThrow();
                current.setFocused(rail);
                rail.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
                rail.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            }
            case 9 -> {
                click(current, button(current, "copy"));
                var result = JsonParser.parseString(client.keyboardHandler.getClipboard()).getAsJsonObject();
                var values = result.getAsJsonObject("meta").getAsJsonArray("values");
                require(values.get(0).isJsonNull() && values.get(1).getAsInt() == 7 && values.get(2).getAsBoolean(),
                        "Generic copy lost existing, edited or added values");
                return true;
            }
            default -> throw new AssertionError("Unexpected generic smoke step");
        }
        return false;
    }

    private static void openContainer(Screen screen, String key) {
        var label = Component.translatable("screen.sh_common_lib.recipe_editor.generic.field", key).getString();
        var edit = screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(row -> row.getMessage().getString().equals(label)).findFirst().orElseThrow();
        clickAt(screen, edit.getX() - 14, edit.getY() + 8);
    }
}
