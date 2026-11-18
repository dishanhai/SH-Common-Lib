// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Editable subset of GTCEu Modern 1.4.4's Forge 1.20.1 machine recipe codec.
 * A positive EU/t is consumed, a negative EU/t is produced. Fluid amounts are millibuckets.
 * Drafts may be incomplete. Validate before exporting or resolving registry references.
 */
public record GtMachineRecipe(String machine, int duration, long eut,
                              List<Entry> itemInputs, List<Entry> itemOutputs,
                              List<Entry> fluidInputs, List<Entry> fluidOutputs) {
    public record Entry(String id, int amount) {
        public Entry { Objects.requireNonNull(id); }
    }

    public GtMachineRecipe {
        Objects.requireNonNull(machine);
        itemInputs = List.copyOf(itemInputs);
        itemOutputs = List.copyOf(itemOutputs);
        fluidInputs = List.copyOf(fluidInputs);
        fluidOutputs = List.copyOf(fluidOutputs);
    }

    public static GtMachineRecipe empty() {
        return new GtMachineRecipe("gtceu:mixer", 200, 32, List.of(), List.of(), List.of(), List.of());
    }

    public List<RecipeIssue> validate() {
        var issues = new ArrayList<RecipeIssue>();
        if (!RecipeGenerator.validId(machine)) issues.add(new RecipeIssue("invalid_id", "gt_machine"));
        if (duration < 1) issues.add(new RecipeIssue("invalid_time", "gt_duration"));
        if (eut == Long.MIN_VALUE) issues.add(new RecipeIssue("invalid_eut", "gt_eut"));
        if (itemInputs.isEmpty() && fluidInputs.isEmpty() && eut <= 0) issues.add(new RecipeIssue("required", "inputs"));
        if (itemOutputs.isEmpty() && fluidOutputs.isEmpty() && eut >= 0) issues.add(new RecipeIssue("required", "result"));
        validateEntries(itemInputs, "gt_item_inputs", true, false, issues);
        validateEntries(itemOutputs, "gt_item_outputs", false, false, issues);
        validateEntries(fluidInputs, "gt_fluid_inputs", false, true, issues);
        validateEntries(fluidOutputs, "gt_fluid_outputs", false, true, issues);
        return List.copyOf(issues);
    }

    private static void validateEntries(List<Entry> entries, String field, boolean allowTag, boolean fluid,
                                        List<RecipeIssue> issues) {
        for (Entry entry : entries) {
            String id = entry.id();
            String name = allowTag && id.startsWith("#") ? id.substring(1) : id;
            if (!RecipeGenerator.validId(name) || id.equals(fluid ? "minecraft:empty" : "minecraft:air")) {
                issues.add(new RecipeIssue("invalid_ingredient", field));
            }
            if (entry.amount() < 1) issues.add(new RecipeIssue("invalid_count", field));
        }
    }

    /** The same JSON representation is used in drafts and in Forge datapacks. */
    public String toJson() {
        var json = new JsonObject();
        json.addProperty("type", machine);
        json.addProperty("duration", duration);
        json.add("inputs", capabilities(itemInputs, fluidInputs));
        json.add("outputs", capabilities(itemOutputs, fluidOutputs));
        if (eut != 0) {
            var energy = new JsonObject();
            var values = new JsonArray();
            var content = new JsonObject();
            // Long.MIN_VALUE is outside the game codec; maintain the exact unsigned magnitude in a draft.
            content.addProperty("content", java.math.BigInteger.valueOf(eut).abs());
            values.add(content);
            energy.add("eu", values);
            json.add(eut > 0 ? "tickInputs" : "tickOutputs", energy);
        }
        return json.toString();
    }

    private static JsonObject capabilities(List<Entry> items, List<Entry> fluids) {
        var capabilities = new JsonObject();
        var itemArray = new JsonArray();
        for (Entry item : items) {
            var ingredient = new JsonObject();
            String id = item.id();
            if (id.startsWith("#")) ingredient.addProperty("tag", id.substring(1));
            else ingredient.addProperty("item", id);
            if (item.amount() != 1) {
                var sized = new JsonObject();
                sized.addProperty("type", "gtceu:sized");
                sized.addProperty("count", item.amount());
                sized.add("ingredient", ingredient);
                ingredient = sized;
            }
            itemArray.add(wrap(ingredient));
        }
        if (!items.isEmpty()) capabilities.add("item", itemArray);
        var fluidArray = new JsonArray();
        for (Entry fluid : fluids) {
            var value = new JsonObject();
            value.addProperty("fluid", fluid.id());
            var ingredient = new JsonObject();
            ingredient.addProperty("amount", fluid.amount());
            ingredient.add("value", value);
            fluidArray.add(wrap(ingredient));
        }
        if (!fluids.isEmpty()) capabilities.add("fluid", fluidArray);
        return capabilities;
    }

    private static JsonObject wrap(JsonElement value) {
        var wrapper = new JsonObject();
        wrapper.add("content", value);
        return wrapper;
    }

    /** Throws if decoding would discard mechanics such as NBT, chance, multiple alternatives or conditions. */
    public static GtMachineRecipe parse(String raw) {
        return decode(RecipeImporter.parse(raw));
    }

    static GtMachineRecipe decode(JsonObject json) {
        fields(json, "type", "duration", "inputs", "outputs", "tickInputs", "tickOutputs",
                "inputChanceLogics", "outputChanceLogics", "tickInputChanceLogics", "tickOutputChanceLogics");
        for (String field : List.of("inputChanceLogics", "outputChanceLogics", "tickInputChanceLogics", "tickOutputChanceLogics")) {
            if (json.has(field) && object(json.get(field)).size() != 0) throw unsupported();
        }
        var inputs = getCapabilities(json, "inputs", "item", "fluid");
        var outputs = getCapabilities(json, "outputs", "item", "fluid");
        try {
            var energyIn = energy(json, "tickInputs");
            var energyOut = energy(json, "tickOutputs");
            if (energyIn.signum() > 0 && energyOut.signum() > 0) throw unsupported();
            return new GtMachineRecipe(string(json.get("type")), integer(json.get("duration")),
                    energyIn.subtract(energyOut).longValueExact(), entries(inputs, "item"), entries(outputs, "item"),
                    entries(inputs, "fluid"), entries(outputs, "fluid"));
        } catch (ArithmeticException exception) {
            throw unsupported();
        }
    }

    private static JsonObject getCapabilities(JsonObject json, String field, String... allowed) {
        var result = json.has(field) ? object(json.get(field)) : new JsonObject();
        fields(result, allowed);
        return result;
    }

    private static java.math.BigInteger energy(JsonObject json, String key) {
        var tick = getCapabilities(json, key, "eu");
        if (!tick.has("eu")) return java.math.BigInteger.ZERO;
        var values = array(tick.get("eu"));
        if (values.isEmpty()) return java.math.BigInteger.ZERO;
        if (values.size() != 1) throw unsupported();
        var amount = number(content(values.get(0)));
        if (amount.signum() <= 0 || amount.compareTo(java.math.BigDecimal.valueOf(Long.MIN_VALUE).negate()) > 0) throw unsupported();
        return amount.toBigIntegerExact();
    }

    private static List<Entry> entries(JsonObject capabilities, String key) {
        if (!capabilities.has(key)) return List.of();
        var entries = new ArrayList<Entry>();
        for (var wrapper : array(capabilities.get(key))) {
            var payload = object(content(wrapper));
            if (key.equals("fluid")) {
                fields(payload, "amount", "value");
                var value = payload.get("value");
                if (value != null && value.isJsonArray()) {
                    if (value.getAsJsonArray().size() != 1) throw unsupported();
                    value = value.getAsJsonArray().get(0);
                }
                var fluid = object(value);
                fields(fluid, "fluid");
                entries.add(new Entry(string(fluid.get("fluid")), integer(payload.get("amount"))));
            } else {
                int count = 1;
                if (payload.has("type")) {
                    fields(payload, "type", "count", "ingredient");
                    if (!string(payload.get("type")).equals("gtceu:sized")) throw unsupported();
                    count = integer(payload.get("count"));
                    payload = object(payload.get("ingredient"));
                }
                if (payload.size() != 1) throw unsupported();
                boolean tag = payload.has("tag");
                String name = string(payload.get(tag ? "tag" : "item"));
                if (!tag && name.startsWith("#")) throw unsupported();
                String id = tag ? "#" + name : name;
                entries.add(new Entry(id, count));
            }
        }
        return entries;
    }

    private static JsonElement content(JsonElement element) {
        var wrapper = object(element);
        fields(wrapper, "content", "chance", "maxChance", "tierChanceBoost");
        if ((wrapper.has("chance") && integer(wrapper.get("chance")) != 10000)
                || (wrapper.has("maxChance") && integer(wrapper.get("maxChance")) != 10000)
                || (wrapper.has("tierChanceBoost") && integer(wrapper.get("tierChanceBoost")) != 0)) throw unsupported();
        return wrapper.get("content");
    }

    private static void fields(JsonObject object, String... allowed) {
        if (!Set.of(allowed).containsAll(object.keySet())) throw unsupported();
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw unsupported();
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value) {
        if (value == null || !value.isJsonArray()) throw unsupported();
        return value.getAsJsonArray();
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw unsupported();
        return value.getAsString();
    }

    private static java.math.BigDecimal number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw unsupported();
        return value.getAsBigDecimal();
    }

    private static int integer(JsonElement value) {
        return number(value).intValueExact();
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("Unsupported GTCEu machine recipe");
    }
}
