package com.dishanhai.sh_common_lib.compat.kubejs;

import com.dishanhai.sh_common_lib.api.parcel.Parcels;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The ShParcels binding for KubeJS scripts. */
public final class ParcelScripts {
    private ParcelScripts() {}

    public static ItemStack createEmpty() {
        return Parcels.createEmpty();
    }

    public static ItemStack createSealed(ItemStack[] contents) {
        return Parcels.createSealed(List.of(contents));
    }

    public static boolean isParcel(ItemStack stack) {
        return Parcels.isParcel(stack);
    }

    public static boolean isSealed(ItemStack stack) {
        return Parcels.isSealed(stack);
    }

    public static ItemStack[] contents(ItemStack stack) {
        return Parcels.contents(stack).toArray(ItemStack[]::new);
    }
}
