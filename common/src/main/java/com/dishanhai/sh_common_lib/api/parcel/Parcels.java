package com.dishanhai.sh_common_lib.api.parcel;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** Creates parcels for rewards, trades, or other server-side delivery. */
public final class Parcels {
    public static final String ITEM_ID = "sealed_crate";
    public static final int MAX_STACKS = 9;

    private Parcels() {}

    /** Returns an empty, editable crate. Call after item registration. */
    public static ItemStack createEmpty() {
        return new ItemStack(ParcelStorage.item());
    }

    /**
     * Creates one sealed crate. Inputs are copied. Each entry must be a nonempty,
     * normally sized stack, and a crate cannot contain another crate.
     */
    public static ItemStack createSealed(List<ItemStack> contents) {
        if (contents.isEmpty() || contents.size() > MAX_STACKS
                || contents.stream().anyMatch(stack -> !canPack(stack))) {
            throw new IllegalArgumentException("Invalid sealed crate contents");
        }
        ItemStack parcel = createEmpty();
        ParcelStorage.write(parcel, contents.stream().map(ItemStack::copy).toList());
        ParcelStorage.seal(parcel, UUID.randomUUID());
        return parcel;
    }

    public static boolean isParcel(ItemStack stack) {
        return stack.getItem() instanceof ParcelItem;
    }

    public static boolean isSealed(ItemStack stack) {
        return isParcel(stack) && ParcelStorage.sealId(stack) != null;
    }

    /** Returns a detached snapshot. An unrelated item has no parcel contents. */
    public static List<ItemStack> contents(ItemStack stack) {
        return isParcel(stack) ? ParcelStorage.read(stack).stream().filter(item -> !item.isEmpty()).toList() : List.of();
    }

    static boolean canPack(ItemStack stack) {
        return !stack.isEmpty() && !isParcel(stack) && stack.getCount() <= stack.getMaxStackSize();
    }
}
