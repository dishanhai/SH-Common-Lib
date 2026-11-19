// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiNavigationRail;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_common_lib.api.ulk.recipe.GtMachineRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditorSmoke.*;

/** Forge-target authoring through a real NeoForge client, without loading GTCEu. */
final class GtMachineSmoke {
    private static int stage;

    static boolean run(Minecraft client, String output) throws Exception {
        switch (stage++) {
            case 0 -> {
                client.options.guiScale().set(4);
                client.resizeDisplay();
                require(client.screen.width == 420 && client.screen.height == 240, "Expected compact GT viewport 420x240");
                choose(client, "type.shaped");
                choose(client, "type.gtceu");
                confirm(client.screen);
                choose(client, "gt.outputs");
                var direct = client.screen.getFocused();
                require(direct instanceof UiTextField field && field.getMessage().equals(
                        Component.translatable("screen.sh_common_lib.recipe_editor.gt.item_id")),
                        "Canvas did not focus the staged output row");
                client.screen.onClose();
                confirm(client.screen);
                choose(client, "gtceu_edit");
                assertBounds(client.screen);
                var machine = fields(client.screen);
                require(machine.get(0).getValue().equals("gtceu:mixer"), "New GT machine has no type");
                type(machine.get(1), "360");
                type(machine.get(2), "-128");
                add(client, false, false, "#minecraft:logs", "2");
                add(client, false, true, "minecraft:water", "500");
                add(client, true, true, "minecraft:lava", "250");
                add(client, true, false, "minecraft:diamond", "3");
                assertBounds(client.screen);
                client.screen.setFocused(null);
            }
            case 1 -> {
                capture(output, "gt-machine-compact");
                client.options.guiScale().set(2);
                client.resizeDisplay();
                require(client.screen.width >= 560, "GT inspection viewport did not expand");
                assertBounds(client.screen);
            }
            case 2 -> {
                capture(output, "gt-machine-workbench");
                choose(client, "apply");
            }
            case 3 -> {
                capture(output, "gt-canvas");
                choose(client, "gtceu_edit");
                var duration = fields(client.screen).get(1);
                require(duration.getValue().equals("360"), "Applied GT duration was lost");
                type(duration, "999");
                client.screen.onClose();
                confirm(client.screen);
                view(client.screen, GLFW.GLFW_KEY_END);
            }
            case 4 -> {
                require(button(client.screen, "copy").active, "Forge GT preview unavailable on NeoForge");
                choose(client, "copy");
                var recipe = GtMachineRecipe.parse(client.keyboardHandler.getClipboard());
                require(recipe.machine().equals("gtceu:mixer") && recipe.duration() == 360 && recipe.eut() == -128,
                        "Copied machine parameters differ from the applied values");
                require(recipe.itemInputs().equals(List.of(new GtMachineRecipe.Entry("#minecraft:logs", 2)))
                                && recipe.itemOutputs().equals(List.of(new GtMachineRecipe.Entry("minecraft:diamond", 3)))
                                && recipe.fluidInputs().equals(List.of(new GtMachineRecipe.Entry("minecraft:water", 500)))
                                && recipe.fluidOutputs().equals(List.of(new GtMachineRecipe.Entry("minecraft:lava", 250))),
                        "Copied GT capabilities differ from the four resource lists");
                choose(client, "undo");
                view(client.screen, GLFW.GLFW_KEY_HOME);
                choose(client, "gtceu_edit");
                require(fields(client.screen).get(1).getValue().equals("200"), "Undo did not restore initial GT draft");
                client.screen.onClose();
                return true;
            }
            default -> throw new AssertionError("GT smoke already finished");
        }
        return false;
    }

    private static void add(Minecraft client, boolean output, boolean fluid, String id, String amount) {
        var screen = client.screen;
        var label = Component.translatable("screen.sh_common_lib.recipe_editor.gt." + (fluid ? "add_fluid" : "add_item"));
        var add = screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(b -> b.getMessage().equals(label) && (b.getX() >= screen.width / 2) == output).findFirst().orElseThrow();
        click(screen, add);
        var idField = (UiTextField) client.screen.getFocused();
        var qty = fields(client.screen).stream().filter(f -> f.getY() == idField.getY() - 21
                && f.getX() > idField.getX() && f.getX() < idField.getX() + idField.getWidth()).findFirst().orElseThrow();
        type(idField, id);
        type(qty, amount);
    }

    private static void view(Screen screen, int edge) {
        var rail = screen.children().stream().filter(UiNavigationRail.class::isInstance)
                .map(UiNavigationRail.class::cast).findFirst().orElseThrow();
        screen.setFocused(rail);
        require(rail.keyPressed(edge, 0, 0) && rail.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                "Workspace keyboard navigation failed");
    }

    private static void choose(Minecraft client, String key) {
        click(client.screen, button(client.screen, key));
    }

    private static void confirm(Screen screen) {
        require(screen instanceof ConfirmScreen, "Expected a confirmation dialog");
        var yes = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(control -> control.getMessage().getString().equals(CommonComponents.GUI_YES.getString()))
                .findFirst().orElseThrow();
        click(screen, yes);
    }
}
