// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class TrashCanModuleItem extends Item {
    private final Extension extension;

    TrashCanModuleItem(Properties properties, Extension extension) {
        super(properties);
        this.extension = extension;
    }

    static boolean matches(ItemStack stack, Extension extension) {
        return stack.getItem() instanceof TrashCanModuleItem module && module.extension == extension
                && (extension != Extension.STRESS || stressLimit(stack) >= 0);
    }

    static int stressLimit(ItemStack stack) {
        try {
            return Integer.parseInt(stack.getHoverName().getString().strip());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
