package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.internal.item.Artisan;
import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanUse;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;

final class NeoForgeArtisan extends DiggerItem {
    private static final TagKey<Block> MINEABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, Artisan.MINEABLE_PATH));
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShCommonLib.MOD_ID);
    private static final DeferredItem<NeoForgeArtisan> ARTISAN = ITEMS.registerItem(Artisan.ID, NeoForgeArtisan::new);
    private static final DeferredItem<NeoForgeArtisan> COSMIC = ITEMS.registerItem(Artisan.COSMIC_ID, properties -> new NeoForgeArtisan(properties, true));
    private final boolean cosmic;

    static NeoForgeArtisan cosmicItem() {
        return COSMIC.get();
    }

    static NeoForgeArtisan item() {
        return ARTISAN.get();
    }

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        if (NeoForgeCosmicStorage.available()) {
            bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(NeoForgeCosmicStorage::registerLink));
        }
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
                event.accept(ARTISAN.get());
                event.accept(COSMIC.get());
            }
        });
    }

    private NeoForgeArtisan(Properties properties) {
        this(properties, false);
    }

    private NeoForgeArtisan(Properties properties, boolean cosmic) {
        super(Tiers.IRON, MINEABLE, properties.attributes(AxeItem.createAttributes(Tiers.IRON, cosmic ? CosmicArtisanModes.ATTACK_DAMAGE - 3 : 6.0F,
                cosmic ? CosmicArtisanModes.ATTACK_SPEED - 4 : -3.1F)));
        this.cosmic = cosmic;
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) {
        return Artisan.durabilityCost(stack, amount, settings());
    }

    ArtisanSettings settings() {
        return ArtisanConfig.current(cosmic);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return settings().maxDurability();
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return settings().enabled() ? Artisan.miningSpeed(state, super.getDestroySpeed(stack, state), settings()) : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        if (cosmic && settings().enabled() && Mode.FORCE_MINING.enabled(NeoForgeCosmicModes.flags(stack))) return true;
        return settings().enabled() && (settings().unlimitedMiningLevel() ? state.is(MINEABLE) : super.isCorrectToolForDrops(stack, state));
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility action) {
        return settings().enabled() && (ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(action)
                || ItemAbilities.DEFAULT_AXE_ACTIONS.contains(action)
                || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(action)
                || ItemAbilities.DEFAULT_HOE_ACTIONS.contains(action)
                || (cosmic && Mode.SHEARS.enabled(NeoForgeCosmicModes.flags(stack))
                    && ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(action)));
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return settings().enabled();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!settings().enabled()) return InteractionResult.FAIL;
        if (!cosmic) return Artisan.useOn(context);
        return CosmicArtisanUse.useOn(context, NeoForgeCosmicModes.flags(context.getItemInHand()), tool -> context.getLevel() instanceof ServerLevel serverLevel
                ? NeoForgeCosmicModes.applyMiningModes(tool, serverLevel) : tool, drop -> {
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer owner) {
                NeoForgeCosmicStorage.insert(owner, context.getItemInHand(), drop);
            }
        });
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return cosmic && settings().enabled()
                ? CosmicArtisanUse.interact(stack, player, target, hand, NeoForgeCosmicModes.flags(stack))
                : InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var result = cosmic && settings().enabled() ? CosmicArtisanUse.useInAir(player, NeoForgeCosmicModes.flags(stack)) : InteractionResult.PASS;
        return new InteractionResultHolder<>(result, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Artisan.TOOLTIP);
        if (cosmic) tooltip.add(Component.translatable("tooltip.sh_common_lib.cosmic_artisan.combat"));
        if (cosmic) tooltip.add(Component.translatable("tooltip.sh_common_lib.cosmic_artisan.passives")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
