// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

/** Optional bridge to JEIGuess's public Forge config value. No classes are linked when absent. */
final class JeiGuessCompatibility implements JeiSearchProviderState.External {
    static java.util.List<String> hotWords() {
        try {
            Class<?> network = Class.forName("com.maixr.suggest.HotWordsNetwork");
            if (!Boolean.TRUE.equals(network.getMethod("serverHasMod").invoke(null))) return java.util.List.of();
            Class<?> history = Class.forName("com.maixr.suggest.SearchHistoryStore");
            Object store = history.getMethod("get").invoke(null);
            Object result = history.getMethod("hotWords", int.class).invoke(store, 32);
            if (!(result instanceof java.util.List<?> values)) return java.util.List.of();
            return values.stream().filter(String.class::isInstance).map(String.class::cast).limit(32).toList();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return java.util.List.of(); }
    }
    private Object value() throws ReflectiveOperationException {
        return Class.forName("com.maixr.suggest.SuggestConfig").getField("ENABLED").get(null);
    }

    @Override public boolean enabled() throws ReflectiveOperationException {
        Object config = value();
        Object current = config.getClass().getMethod("get").invoke(config);
        if (!(current instanceof Boolean enabled)) throw new ReflectiveOperationException("Invalid JEIGuess setting");
        return enabled;
    }

    @Override public void setEnabled(boolean enabled) throws ReflectiveOperationException {
        Object config = value();
        config.getClass().getMethod("set", Object.class).invoke(config, enabled);
        config.getClass().getMethod("save").invoke(config);
    }
}
