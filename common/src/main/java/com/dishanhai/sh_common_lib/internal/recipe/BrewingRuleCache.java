// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.recipe;

import com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipe;
import com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Resolves a server resource snapshot against the latest program registrations. */
public final class BrewingRuleCache<R> {
    private record State<R>(boolean active, long revision, List<BrewingDefinitions.Definition> resources, List<R> rules) {}

    private final Function<BrewingDefinitions.Definition, R> compiler;
    private volatile State<R> state = new State<>(false, -1, List.of(), List.of());

    public BrewingRuleCache(Function<BrewingDefinitions.Definition, R> compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    /** Publishes only after every definition has been compiled successfully or deliberately skipped. */
    public synchronized void load(List<BrewingDefinitions.Definition> resources) {
        state = resolve(List.copyOf(resources), BrewingRecipes.snapshot());
    }

    public List<R> current() {
        State<R> current = state;
        if (current.active() && current.revision() != BrewingRecipes.snapshot().revision()) {
            synchronized (this) {
                current = state;
                var program = BrewingRecipes.snapshot();
                if (current.active() && current.revision() != program.revision()) {
                    current = resolve(current.resources(), program);
                    state = current;
                }
            }
        }
        return current.rules();
    }

    /** Stops serving rules when the server closes without removing third-party registrations. */
    public synchronized void clear() { state = new State<>(false, -1, List.of(), List.of()); }

    private State<R> resolve(List<BrewingDefinitions.Definition> resources, BrewingRecipes.Snapshot snapshot) {
        var program = new ArrayList<BrewingDefinitions.Definition>();
        for (BrewingRecipe recipe : snapshot.recipes()) {
            program.add(new BrewingDefinitions.Definition(recipe.id(),
                    recipe.kind() == BrewingRecipe.Kind.POTION ? BrewingDefinitions.Kind.POTION : BrewingDefinitions.Kind.CONTAINER,
                    recipe.input(), recipe.ingredient(), recipe.output()));
        }
        var rules = new ArrayList<R>();
        for (var definition : BrewingDefinitions.merge(program, resources)) {
            R rule = compiler.apply(definition);
            if (rule != null) rules.add(rule);
        }
        return new State<>(true, snapshot.revision(), resources, List.copyOf(rules));
    }
}
