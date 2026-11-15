// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ulk.compat.Ae2Inventories;
import com.dishanhai.sh_common_lib.api.ulk.compat.Ae2WirelessLinks;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class NeoForgeCosmicStorage {
    private static final String NETWORK = "sh_common_lib:cosmic_network";
    private static final String OWNER = "owner";
    private static final String DIMENSION = "dimension";
    private static final String POSITION = "position";

    private NeoForgeCosmicStorage() {}

    public static boolean available() { return ModList.get().isLoaded("ae2"); }

    static void registerLink() {
        Ae2WirelessLinks.register(NeoForgeArtisan.cosmicItem(), stack -> CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(NETWORK)));
    }

    static boolean bind(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack tool) {
        if (!available() || !CosmicArtisanModes.isActive(tool) || !Mode.AE_LINK.enabled(NeoForgeCosmicModes.flags(tool))
                || !level.mayInteract(player, pos) || Ae2Inventories.wirelessGrid(level, pos) == null) return false;
        Ae2WirelessLinks.unlink(tool);
        var data = new CompoundTag();
        data.putString(OWNER, player.getUUID().toString());
        data.putString(DIMENSION, level.dimension().location().toString());
        data.putLong(POSITION, pos.asLong());
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.put(NETWORK, data));
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable("screen.sh_common_lib.cosmic_artisan.ae_bound",
                pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }

    public static void insert(ServerPlayer player, ItemStack tool, ItemStack drop) {
        if (!available() || drop.isEmpty() || !Mode.AE_STORAGE_PRIORITY.enabled(NeoForgeCosmicModes.flags(tool))) return;
        var point = Ae2WirelessLinks.linkedPoint(tool);
        if (point != null) {
            drop.shrink(Ae2Inventories.insert(Ae2WirelessLinks.grid((ServerLevel) player.level(), point), player, drop));
            return;
        }
        var root = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (root == null || !root.contains(NETWORK)) return;
        var data = root.getCompound(NETWORK);
        if (!player.getUUID().toString().equals(data.getString(OWNER))) return;
        var dimension = ResourceLocation.tryParse(data.getString(DIMENSION));
        if (dimension == null || !data.contains(POSITION)) return;
        var level = ((ServerLevel) player.level()).getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) return;
        var grid = Ae2Inventories.wirelessGrid(level, BlockPos.of(data.getLong(POSITION)));
        drop.shrink(Ae2Inventories.insert(grid, player, drop));
    }
}
