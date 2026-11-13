// Author: dishanhai
package net.minecraft.world.item;

/** Small mutable test stand-in; production code uses Minecraft's ItemStack. */
public final class ItemStack {
    private final String id;
    private int count;
    public ItemStack(String id, int count) { this.id = id; this.count = count; }
    public String id() { return id; }
    public boolean isEmpty() { return count <= 0; }
    public int getCount() { return count; }
    public void setCount(int value) { count = value; }
    public ItemStack copy() { return new ItemStack(id, count); }
}
