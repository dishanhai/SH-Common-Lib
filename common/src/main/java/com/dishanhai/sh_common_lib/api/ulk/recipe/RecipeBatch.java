// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Pure, ordered batch preflight. Never writes files or changes the running recipe manager. */
public final class RecipeBatch {
    public static final int MAX_RECIPES = 256;
    private RecipeBatch() { }

    /** Structural validation only. This does not verify registry references or custom serializers. */
    public static Result prepare(List<RecipeDocument> documents, RecipeTarget target, RecipeFormat format) {
        return prepare(documents, target, format, document -> List.of());
    }

    /**
     * Runs the supplied semantic validator only for structurally valid documents. For a live server,
     * pass RecipeReferences.validate with a server registry lookup on its owner thread. Validator
     * failures propagate: a broken validator must not be reported as a valid recipe.
     */
    public static Result prepare(List<RecipeDocument> documents, RecipeTarget target, RecipeFormat format,
                                 Function<RecipeDocument, List<RecipeIssue>> validator) {
        Objects.requireNonNull(documents);
        Objects.requireNonNull(target);
        Objects.requireNonNull(format);
        Objects.requireNonNull(validator);
        if (documents.size() > MAX_RECIPES) throw new IllegalArgumentException("At most " + MAX_RECIPES + " recipes per batch");
        var snapshot = List.copyOf(documents);
        var issues = new ArrayList<Diagnostic>();
        var artifacts = new ArrayList<RecipeArtifact>();
        var ids = new HashSet<String>();
        var paths = new HashSet<String>();
        for (int index = 0; index < snapshot.size(); index++) {
            var document = snapshot.get(index);
            if (!ids.add(document.id())) issues.add(new Diagnostic(index, document.id(), new RecipeIssue("duplicate_id", "id")));
            var errors = RecipeGenerator.validate(document, target, format);
            if (errors.isEmpty()) errors = List.copyOf(validator.apply(document));
            for (var error : errors) issues.add(new Diagnostic(index, document.id(), error));
            if (!errors.isEmpty()) continue;
            var artifact = RecipeGenerator.generate(document, target, format);
            String path = Path.of(artifact.relativePath()).normalize().toString();
            if (!paths.add(path)) issues.add(new Diagnostic(index, document.id(), new RecipeIssue("duplicate_output", "id")));
            if (artifact.content().getBytes(StandardCharsets.UTF_8).length > 1_048_576)
                issues.add(new Diagnostic(index, document.id(), new RecipeIssue("artifact_too_large", "rawJson")));
            artifacts.add(artifact);
        }
        // Withhold all artifacts on any failure so callers cannot accidentally export a partial batch.
        return new Result(issues.isEmpty() ? artifacts : List.of(), issues);
    }

    public record Diagnostic(int index, String recipeId, RecipeIssue issue) {
        public Diagnostic { Objects.requireNonNull(recipeId); Objects.requireNonNull(issue); }
    }
    public record Result(List<RecipeArtifact> artifacts, List<Diagnostic> issues) {
        public Result {
            artifacts = List.copyOf(artifacts);
            issues = List.copyOf(issues);
            if (!issues.isEmpty() && !artifacts.isEmpty()) throw new IllegalArgumentException("Invalid batch cannot expose artifacts");
        }
        public boolean valid() { return issues.isEmpty(); }
    }
}
