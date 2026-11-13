// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk;

/** Where an item was observed. Hands are excluded from INVENTORY to prevent double counting. */
public enum InventorySource {
    INVENTORY, MAIN_HAND, OFF_HAND, CURIOS, BACKPACK, AE2, MEKANISM, CUSTOM
}
