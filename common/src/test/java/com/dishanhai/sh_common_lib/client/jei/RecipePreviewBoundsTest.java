// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

public final class RecipePreviewBoundsTest {
    public static void main(String[] args) {
        var bounds = RecipePreviewBounds.of(142, 84, 5, 7, 9);
        require(bounds.recipeX() - 5 == bounds.clipX(), "JEI left border must fit");
        require(bounds.recipeY() - 7 == bounds.clipY(), "JEI top border must fit");
        require(bounds.clipY() >= 5 + 9, "recipe background must not cover title");
        require(bounds.clipX() + bounds.clipWidth() <= bounds.width() - 6, "right padding");
        require(bounds.clipY() + bounds.clipHeight() <= bounds.height() - 6, "bottom padding");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
