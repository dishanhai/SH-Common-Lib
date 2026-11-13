// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

/** Positions the entire JEI drawable below the title, including its external border. */
record RecipePreviewBounds(int width, int height, int recipeX, int recipeY,
                           int clipX, int clipY, int clipWidth, int clipHeight) {
    static RecipePreviewBounds of(int outerWidth, int outerHeight, int contentX, int contentY, int lineHeight) {
        if (outerWidth <= 0 || outerHeight <= 0 || lineHeight <= 0 || contentX < 0 || contentY < 0)
            throw new IllegalArgumentException("Invalid recipe preview bounds");
        int recipeTop = lineHeight + 10;
        return new RecipePreviewBounds(outerWidth + 12, recipeTop + outerHeight + 6,
                6 + contentX, recipeTop + contentY, 6, recipeTop, outerWidth, outerHeight);
    }
}
