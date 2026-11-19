// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.trash;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** ULK contract for a resource disposal block and its installed extensions. */
public final class TrashCan {
    public static final String ID = "trash_can";

    private TrashCan() {}

    public enum Extension {
        CHEMICAL("chemical"), SOURCE("source"), STRESS("stress"), ENERGY("energy");

        private final String path;
        private final BooleanProperty property;

        Extension(String path) {
            this.path = path;
            this.property = BooleanProperty.create(path);
        }

        public String itemId() {
            return ID + "_" + path + "_module";
        }

        public String labelKey() {
            return "screen.sh_common_lib.trash_can." + path;
        }

        public BooleanProperty property() {
            return property;
        }

        public boolean isInstalled(BlockState state) {
            return state.hasProperty(property) && state.getValue(property);
        }
    }
}
