package com.dishanhai.sh_common_lib.api.parcel;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Forge registration and NBT adapter. Use {@link Parcels} to access parcels. */
public final class ParcelStorage {
    private static final String CONTENTS = "ShCommonLibContents";
    private static final String SEAL = "ShCommonLibSeal";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ShCommonLib.MOD_ID);
    private static final RegistryObject<ParcelItem> PARCEL = ITEMS.register(Parcels.ITEM_ID, ParcelItem::new);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ShCommonLib.MOD_ID);
    private static final RegistryObject<MenuType<ParcelMenu>> EDITOR =
            MENUS.register(Parcels.ITEM_ID, () -> IForgeMenuType.create(ParcelMenu::fromNetwork));
    private static final String NETWORK_VERSION = "1";

    private ParcelStorage() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(item());
        });
        SimpleChannel channel = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "parcel"), () -> NETWORK_VERSION,
                NETWORK_VERSION::equals, NETWORK_VERSION::equals);
        channel.registerMessage(0, OpenParcelRequest.class, OpenParcelRequest::write, OpenParcelRequest::read,
                (message, contextSupplier) -> {
                    var context = contextSupplier.get();
                    var player = context.getSender();
                    if (player != null) context.enqueueWork(() -> message.handle(player));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        MinecraftForge.EVENT_BUS.addListener(ParcelStorage::onCreativeUse);
        if (FMLEnvironment.dist == Dist.CLIENT) ParcelClient.register(modBus, channel::sendToServer);
    }

    private static void onCreativeUse(PlayerInteractEvent.RightClickItem event) {
        // Forge 1.20.1 restores the old stack count on creative use. Delivery can refill the hand.
        if (event.getEntity() instanceof ServerPlayer player && player.getAbilities().instabuild
                && !player.isShiftKeyDown() && ParcelItem.unpack(player, event.getItemStack())) {
            player.inventoryMenu.broadcastChanges();
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    static Item item() {
        return PARCEL.get();
    }

    static MenuType<ParcelMenu> menuType() {
        return EDITOR.get();
    }

    static void openEditor(ServerPlayer player, InteractionHand hand, ItemStack parcel) {
        int slot = ParcelMenu.heldSlot(player.getInventory(), hand);
        NetworkHooks.openScreen(player, ParcelMenu.provider(slot, parcel), buffer -> buffer.writeVarInt(slot));
    }

    static UUID sealId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(SEAL) ? tag.getUUID(SEAL) : null;
    }

    static void seal(ItemStack stack, UUID sealId) {
        stack.getOrCreateTag().putUUID(SEAL, sealId);
    }

    static List<ItemStack> read(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return List.of();
        return tag.getList(CONTENTS, Tag.TAG_COMPOUND).stream()
                .map(entry -> ((CompoundTag) entry).isEmpty() ? ItemStack.EMPTY : ItemStack.of((CompoundTag) entry))
                .toList();
    }

    static void write(ItemStack stack, List<ItemStack> contents) {
        int size = contents.size();
        while (size > 0 && contents.get(size - 1).isEmpty()) size--;
        if (size == 0) {
            stack.removeTagKey(CONTENTS);
            return;
        }
        ListTag entries = new ListTag();
        for (int index = 0; index < size; index++) {
            ItemStack entry = contents.get(index);
            entries.add(entry.isEmpty() ? new CompoundTag() : entry.save(new CompoundTag()));
        }
        stack.getOrCreateTag().put(CONTENTS, entries);
    }
}
