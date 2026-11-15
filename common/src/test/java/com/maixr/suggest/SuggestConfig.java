// Author: dishanhai; test-only fixture matching JEIGuess's public config ABI.
package com.maixr.suggest;

public final class SuggestConfig {
    public static final BooleanValue ENABLED = new BooleanValue();

    public static class ConfigValue<T> {
        private T value;
        public int saves;
        ConfigValue(T initial) { value = initial; }
        public T get() { return value; }
        public void set(T replacement) { value = replacement; }
        public void save() { saves++; }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue() { super(true); }
    }
}
