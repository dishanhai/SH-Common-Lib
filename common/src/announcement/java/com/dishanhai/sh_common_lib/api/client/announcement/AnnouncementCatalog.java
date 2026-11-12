// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Reads a mod's release-note directory and its local changelog_seen.txt. */
public final class AnnouncementCatalog {
    private static final System.Logger LOGGER = System.getLogger(AnnouncementCatalog.class.getName());
    private static final Pattern VERSION = Pattern.compile(
            "(?:^|[-_])([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:[-+][0-9A-Za-z.-]+)?\\.md$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile(
            "^\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)(?:\\s*-\\s*(.*))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRAPPED_LINK = Pattern.compile(
            "^\\[(\\[[^\\]]+\\]\\(https?://[^)\\s]+\\))\\s*\\](.*)$", Pattern.CASE_INSENSITIVE);

    private static final String PRESET = "Update-0.0.0.md";
    private final Path directory;

    public AnnouncementCatalog(Path directory) {
        this.directory = Objects.requireNonNull(directory);
    }

    void installDefaultPreset() {
        Path target = directory.resolve(PRESET);
        if (Files.exists(target)) return;
        try (var template = AnnouncementCatalog.class.getResourceAsStream("/announcements/sh_common_lib/" + PRESET)) {
            if (template == null) throw new IOException("Missing bundled announcement " + PRESET);
            Files.createDirectories(directory);
            Files.copy(template, target);
        } catch (FileAlreadyExistsException ignored) {
            // A concurrent client created it first.
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not install " + target, error);
        }
    }

    /** Scans recursively. The highest filename version comes first, including disabled entries. */
    public List<Document> documents() {
        if (Files.notExists(directory)) return List.of();
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .map(AnnouncementCatalog::readDocument)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Document::fileVersion)
                            .thenComparing(doc -> doc.source().toString(), String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(Document::source).reversed())
                    .toList();
        } catch (IOException | UncheckedIOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not scan announcements in " + directory, error);
            return List.of();
        }
    }

    public String seenVersion() {
        Path file = directory.resolve("changelog_seen.txt");
        if (Files.notExists(file)) return "";
        try {
            return Files.readString(file).trim();
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read " + file, error);
            return "";
        }
    }

    /** Persists the displayed version even if a newer file appeared while the screen was open. */
    public boolean markSeen(Document document) {
        Path target = directory.resolve("changelog_seen.txt");
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "changelog_seen-", ".tmp");
            try {
                Files.writeString(temp, document.version());
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not write " + target, error);
            return false;
        }
    }

    /** Reads one versioned Markdown file. Returns null for an invalid filename or unreadable file. */
    public static Document readDocument(Path source) {
        var filename = VERSION.matcher(source.getFileName().toString());
        if (!filename.find()) return null;
        var fileVersion = new Version(new BigInteger(filename.group(1)), new BigInteger(filename.group(2)),
                new BigInteger(filename.group(3)));
        String defaultVersion = String.join(".", filename.group(1), filename.group(2), filename.group(3));

        List<String> lines;
        try {
            lines = Files.readAllLines(source);
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read " + source, error);
            return null;
        }

        var metadata = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        var links = new ArrayList<Link>();
        int bodyStart = frontMatterEnd(lines);
        for (int i = 1; i < bodyStart - 1; i++) {
            String line = lines.get(i);
            int separator = line.indexOf(':');
            if (separator < 1) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if ("link".equalsIgnoreCase(key)) {
                Link link = parseLink(value);
                if (link != null) links.add(link);
            } else {
                metadata.put(key, value);
            }
        }

        var primary = new ArrayList<String>();
        var english = new ArrayList<String>();
        List<String> body = primary;
        for (int i = bodyStart; i < lines.size(); i++) {
            String line = lines.get(i);
            String tag = line.trim();
            if ("<!-- lang:en -->".equalsIgnoreCase(tag)) body = english;
            else body.add(line);
        }

        String version = metadata.getOrDefault("version", defaultVersion);
        return new Document(version.isBlank() ? defaultVersion : version, metadata.getOrDefault("title", ""),
                metadata.getOrDefault("title_en", ""), !"false".equalsIgnoreCase(metadata.get("enabled")),
                source, fileVersion, links, String.join("\n", primary), String.join("\n", unwrapEnglishSection(english)));
    }

    private static List<String> unwrapEnglishSection(List<String> lines) {
        int first = 0;
        int last = lines.size() - 1;
        while (first <= last && lines.get(first).isBlank()) first++;
        while (last >= first && lines.get(last).isBlank()) last--;
        if (last - first < 3 || !lines.get(first).strip().equalsIgnoreCase("<details>")) return lines;
        String summary = lines.get(first + 1).strip().toLowerCase(Locale.ROOT);
        if (!summary.startsWith("<summary>") || !summary.endsWith("</summary>")
                || !(summary.contains("switch to english") || summary.contains("english release notes")
                || summary.contains("切换至英文版"))) return lines;
        int depth = 1;
        for (int i = first + 2; i <= last; i++) {
            String tag = lines.get(i).strip().toLowerCase(Locale.ROOT);
            if (tag.equals("<details>") || tag.startsWith("<details ")) depth++;
            if (tag.equals("</details>") && --depth == 0) {
                return i == last ? lines.subList(first + 2, last) : lines;
            }
        }
        return lines;
    }

    private static int frontMatterEnd(List<String> lines) {
        if (!lines.isEmpty() && lines.get(0).trim().equals("---")) {
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("---")) return i + 1;
            }
        }
        return 0;
    }

    private static Link parseLink(String value) {
        var wrapped = WRAPPED_LINK.matcher(value);
        if (wrapped.matches()) value = wrapped.group(1) + wrapped.group(2);
        var match = LINK.matcher(value);
        if (!match.matches() || match.group(1).isBlank()) return null;
        return new Link(match.group(1).trim(), match.group(2), Objects.toString(match.group(3), "").trim());
    }

    public record Document(String version, String title, String englishTitle, boolean enabled, Path source,
                           Version fileVersion, List<Link> links, String body, String englishBody) {
        public Document {
            links = List.copyOf(links);
        }

        public boolean hasEnglish() { return !englishBody.isBlank(); }

        public String title(boolean english) { return english ? englishTitle : title; }

        public String body(boolean english) { return english && hasEnglish() ? englishBody : body; }
    }

    public record Link(String text, String url, String description) {
        public String label(boolean english) {
            int separator = description.indexOf(" / ");
            String detail = separator < 0 ? description
                    : (english ? description.substring(separator + 3) : description.substring(0, separator)).trim();
            return detail.isBlank() ? text : text + " - " + detail;
        }
    }

    public record Version(BigInteger major, BigInteger minor, BigInteger patch) implements Comparable<Version> {
        @Override
        public int compareTo(Version other) {
            int result = major.compareTo(other.major);
            if (result == 0) result = minor.compareTo(other.minor);
            return result == 0 ? patch.compareTo(other.patch) : result;
        }
    }
}
