package com.dishanhai.sh_common_lib.api.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Client-side tooltip previews for portable storage and other items with contents. */
public final class ItemContentPreview {
    private static final int COLLAPSED_LIMIT = 5;
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private ItemContentPreview() {}

    /**
     * Registers a provider once during client setup. Return all entries in display order
     * for the summary. The limit allows expensive name resolution to be skipped for hidden rows.
     * The first matching provider owns the preview.
     */
    public static void register(Predicate<ItemStack> matches,
                                BiFunction<ItemStack, Integer, Preview> preview) {
        PROVIDERS.add(new Provider(Objects.requireNonNull(matches), Objects.requireNonNull(preview)));
    }

    public static void append(ItemStack stack, List<Component> tooltip) {
        if (stack.isEmpty()) return;
        for (Provider provider : PROVIDERS) {
            if (provider.matches.test(stack)) {
                int limit = Minecraft.getInstance().hasShiftDown() ? Integer.MAX_VALUE : COLLAPSED_LIMIT;
                Preview preview = provider.preview.apply(stack, limit);
                tooltip.addAll(preview.before());
                appendEntries(tooltip, preview.title(), preview.entries(), limit);
                return;
            }
        }
    }

    private static void appendEntries(List<Component> tooltip, Component title, List<Entry> entries, int limit) {
        if (entries.isEmpty()) return;
        tooltip.add(Component.empty());
        tooltip.add(title);
        int visible = Math.min(limit, entries.size());
        for (int index = 0; index < visible; index++) {
            Entry entry = entries.get(index);
            tooltip.add(Component.translatable("tooltip.sh_common_lib.contents.entry", formatAmount(entry.amount()), entry.name())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (visible < entries.size()) {
            tooltip.add(Component.translatable("tooltip.sh_common_lib.contents.more", entries.size() - visible)
                    .withStyle(ChatFormatting.GRAY));
        }
        BigInteger total = entries.stream().map(Entry::amount).reduce(BigInteger.ZERO, BigInteger::add);
        tooltip.add(Component.translatable("tooltip.sh_common_lib.contents.total", formatAmount(total), entries.size())
                .withStyle(ChatFormatting.GRAY));
        if (entries.size() > COLLAPSED_LIMIT) {
            tooltip.add(Component.translatable(limit == COLLAPSED_LIMIT
                    ? "tooltip.sh_common_lib.contents.expand" : "tooltip.sh_common_lib.contents.collapse")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String formatAmount(BigInteger amount) {
        String text = amount.toString();
        return text.length() <= 18 ? text : text.substring(0, 6) + "…" + text.substring(text.length() - 6);
    }

    public record Entry(Component name, BigInteger amount) {
        public static Entry of(ItemStack stack) {
            return new Entry(stack.getHoverName(), BigInteger.valueOf(stack.getCount()));
        }
    }

    public record Preview(Component title, List<Component> before, List<Entry> entries) {
        public Preview(Component title, List<Entry> entries) {
            this(title, List.of(), entries);
        }
    }

    private record Provider(Predicate<ItemStack> matches, BiFunction<ItemStack, Integer, Preview> preview) {}
}
