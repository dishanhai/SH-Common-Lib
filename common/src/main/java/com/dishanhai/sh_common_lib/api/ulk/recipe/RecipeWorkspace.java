// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.dishanhai.sh_common_lib.api.ulk.fumo.FumoRewardDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded UTF-8 drafts and explicit workspace-relative exports. */
public final class RecipeWorkspace {
    public static final String DEFAULT_DIRECTORY = "config/sh_common_lib/recipe_editor";
    private static final String DEVELOPER_SETTINGS = "developer.json";
    private static final int MAX_SETTINGS_BYTES = 32 * 1024;
    private static final long MAX_BYTES = 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path root;

    public RecipeWorkspace(Path root) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
    }

    public Path save(RecipeDocument doc) throws IOException {
        return save(doc, true);
    }

    /** Creates a draft by default in UI flows; replacing another draft requires explicit consent. */
    public Path save(RecipeDocument doc, boolean overwrite) throws IOException {
        Path destination = draftPath(doc.id());
        var json = new JsonObject();
        json.addProperty("schema", 1);
        json.addProperty("id", doc.id());
        json.addProperty("type", doc.type().name());
        var inputs = new JsonArray();
        doc.inputs().forEach(inputs::add);
        json.add("inputs", inputs);
        json.addProperty("result", doc.result());
        json.addProperty("count", doc.count());
        json.addProperty("experience", doc.experience());
        json.addProperty("cookingTime", doc.cookingTime());
        json.addProperty("group", doc.group());
        json.addProperty("rawJson", doc.rawJson());
        if (doc.customTarget() != null) json.addProperty("customTarget", doc.customTarget().name());
        if (doc.type() == RecipeType.FUMO_REWARDS)
            json.add("rewards", FumoRewardDefinition.json(doc.rewards()).get("rewards"));
        write(destination, GSON.toJson(json) + "\n", overwrite);
        return destination;
    }

    public RecipeDocument load(String id) throws IOException {
        Path file = draftPath(id);
        checkLinks(file);
        if (Files.size(file) > MAX_BYTES) throw new IOException("Draft too large");
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("schema").getAsInt() != 1 || !id.equals(json.get("id").getAsString())) throw new IOException("Invalid draft schema or ID");
            var type = RecipeType.valueOf(json.get("type").getAsString());
            if (type == RecipeType.FUMO_REWARDS) return RecipeDocument.fumo(id, FumoRewardDefinition.read(json));
            var inputs = new ArrayList<String>();
            json.getAsJsonArray("inputs").forEach(value -> inputs.add(value.getAsString()));
            return new RecipeDocument(id, type, inputs,
                    json.get("result").getAsString(), json.get("count").getAsInt(), json.get("experience").getAsFloat(),
                    json.get("cookingTime").getAsInt(), json.get("group").getAsString(), json.get("rawJson").getAsString(),
                    json.has("customTarget") ? RecipeTarget.valueOf(json.get("customTarget").getAsString()) : null);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid draft", exception);
        }
    }

    public List<String> list() throws IOException {
        Path drafts = resolve("drafts");
        if (!Files.isDirectory(drafts, LinkOption.NOFOLLOW_LINKS)) return List.of();
        checkLinks(drafts);
        var ids = new ArrayList<String>();
        try (var paths = Files.walk(drafts)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String relative = drafts.relativize(path).toString().replace('\\', '/');
                        int separator = relative.indexOf('/');
                        if (separator > 0) {
                            String id = relative.substring(0, separator) + ":" + relative.substring(separator + 1, relative.length() - 5);
                            if (RecipeGenerator.validId(id)) ids.add(id);
                        }
                    });
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    /**
     * Lexically sorted ID-prefix query. Each call is a fresh filesystem snapshot, not a durable cursor.
     * At most 16,384 directory entries are visited; oversized trees fail rather than appear complete.
     * Draft contents are not loaded or validated. Missing directories are empty; I/O failures propagate.
     */
    public Page query(String prefix, int offset, int limit) throws IOException {
        Objects.requireNonNull(prefix);
        if (offset < 0 || limit < 1 || limit > 256) throw new IllegalArgumentException("Invalid page bounds");
        Path drafts = resolve("drafts");
        checkLinks(drafts);
        try {
            if (!Files.readAttributes(root, java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).isDirectory()) throw new IOException("Workspace root is not a directory");
            if (!Files.readAttributes(drafts, java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).isDirectory()) throw new IOException("Draft root is not a directory");
        } catch (java.nio.file.NoSuchFileException missing) {
            return new Page(List.of(), offset, 0);
        }
        var ids = new ArrayList<String>();
        try (var paths = Files.walk(drafts)) {
            var iterator = paths.iterator();
            int visited = 0;
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (++visited > 16_384) throw new IOException("Draft directory scan limit exceeded");
                if (!Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS).isRegularFile() || !path.getFileName().toString().endsWith(".json")) continue;
                String relative = drafts.relativize(path).toString().replace('\\', '/');
                int separator = relative.indexOf('/');
                if (separator > 0) {
                    String id = relative.substring(0, separator) + ":" + relative.substring(separator + 1, relative.length() - 5);
                    if (RecipeGenerator.validId(id) && id.startsWith(prefix)) ids.add(id);
                }
            }
        } catch (java.io.UncheckedIOException failure) {
            throw failure.getCause();
        }
        ids.sort(String::compareTo);
        int from = Math.min(offset, ids.size());
        int to = from + Math.min(limit, ids.size() - from);
        return new Page(ids.subList(from, to), offset, ids.size());
    }

    public record Page(List<String> ids, int offset, int total) {
        public Page { ids = List.copyOf(ids); }
        public boolean hasMore() { return (long) offset + ids.size() < total; }
    }

    public Path export(RecipeArtifact artifact, boolean overwrite) throws IOException {
        Objects.requireNonNull(artifact);
        Path file = resolve(artifact.relativePath());
        write(file, artifact.content(), overwrite);
        return file;
    }

    public RecipeExportSettings loadExportSettings() throws IOException {
        Path file = resolve(DEVELOPER_SETTINGS);
        checkLinks(file);
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_SETTINGS_BYTES + 1);
            if (bytes.length > MAX_SETTINGS_BYTES) throw new IOException("Developer settings file is too large");
            var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            var enabled = json.getAsJsonPrimitive("customJsonExport");
            var directory = json.getAsJsonPrimitive("jsonExportRoot");
            if (enabled == null || !enabled.isBoolean() || directory == null || !directory.isString())
                throw new IOException("Invalid JSON export settings");
            return new RecipeExportSettings(enabled.getAsBoolean(), directory.getAsString());
        } catch (NoSuchFileException missing) {
            return RecipeExportSettings.DEFAULT;
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid JSON export settings", invalid);
        }
    }

    public void saveExportSettings(RecipeExportSettings settings) throws IOException {
        var json = new JsonObject();
        json.addProperty("customJsonExport", settings.customJsonExport());
        json.addProperty("jsonExportRoot", settings.jsonExportRoot());
        String value = GSON.toJson(json) + "\n";
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_SETTINGS_BYTES)
            throw new IOException("Developer settings file is too large");
        write(resolve(DEVELOPER_SETTINGS), value, true);
    }

    /** Resolves the exact output once so confirmation and writing use the same destination. */
    public PreparedExport prepareExport(RecipeArtifact artifact, RecipeFormat format, Path gameDirectory) throws IOException {
        String relative = artifact.relativePath();
        if (format == RecipeFormat.JSON) {
            var settings = loadExportSettings();
            if (settings.customJsonExport()) {
                if (!relative.endsWith(".json")) throw new IllegalArgumentException("Expected a JSON artifact");
                var destination = new RecipeWorkspace(settings.resolveRoot(gameDirectory));
                if (relative.startsWith("exports/")) relative = relative.substring("exports/".length());
                return new PreparedExport(destination, destination.resolve(relative), artifact.content());
            }
        }
        return new PreparedExport(this, resolve(relative), artifact.content());
    }

    public static final class PreparedExport {
        private final RecipeWorkspace workspace;
        private final Path path;
        private final String content;

        private PreparedExport(RecipeWorkspace workspace, Path path, String content) {
            this.workspace = workspace;
            this.path = path;
            this.content = content;
        }

        public Path path() { return path; }

        public Path write(boolean overwrite) throws IOException {
            workspace.write(path, content, overwrite);
            return path;
        }
    }

    private Path draftPath(String id) {
        if (!RecipeGenerator.validId(id)) throw new IllegalArgumentException("Invalid recipe ID");
        String[] parts = id.split(":", 2);
        return resolve("drafts/" + parts[0] + "/" + parts[1] + ".json");
    }

    private Path resolve(String relative) {
        Objects.requireNonNull(relative);
        Path part = Path.of(relative);
        if (part.isAbsolute() || relative.isEmpty() || relative.indexOf('\\') >= 0 || relative.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Invalid workspace path");
        }
        Path path = root.resolve(part).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IllegalArgumentException("Path outside workspace");
        return path;
    }

    private void checkLinks(Path path) throws IOException {
        Path current = root;
        if (Files.isSymbolicLink(current)) throw new IOException("Workspace root is a link");
        for (Path part : root.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Workspace path contains a link");
        }
    }

    private void write(Path path, String value, boolean overwrite) throws IOException {
        byte[] bytes = Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Document too large");
        checkLinks(path);
        Files.createDirectories(path.getParent());
        checkLinks(path);
        Path temp = Files.createTempFile(path.getParent(), ".recipe-", ".tmp");
        try {
            Files.write(temp, bytes);
            if (overwrite) {
                try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException exception) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } else {
                Files.move(temp, path);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
