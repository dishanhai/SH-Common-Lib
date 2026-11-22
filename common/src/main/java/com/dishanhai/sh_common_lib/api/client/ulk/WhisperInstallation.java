// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import com.dishanhai.sh_common_lib.api.ulk.storage.CheckedFileStore;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/** Installs the official Windows CPU version of whisper.cpp and its multilingual model. */
public final class WhisperInstallation {
    private static final System.Logger LOGGER = System.getLogger(WhisperInstallation.class.getName());
    private static final Asset WINDOWS_X64 = new Asset(
            "https://github.com/ggml-org/whisper.cpp/releases/download/v1.8.3/whisper-bin-x64.zip", 3_968_674,
            "d824b1e37599f882b396e73f1ee0bfd5d0529f700314c48311dcbd00b803321d");
    private static final Asset MULTILINGUAL_BASE = new Asset(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin",
            147_951_465, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe");
    private static final List<String> LIBRARIES = List.of("ggml-base.dll", "ggml-cpu.dll", "ggml.dll",
            "SDL2.dll", "whisper.dll");

    private WhisperInstallation() {}

    public static boolean supported() {
        String architecture = System.getProperty("os.arch", "");
        return OfflineSpeechRecognition.isWindows()
                && (architecture.equalsIgnoreCase("amd64") || architecture.equalsIgnoreCase("x86_64"));
    }

    /** Call on a worker thread. Existing files are retained. */
    public static void install(Path directory) throws IOException {
        if (!supported()) throw new IOException("Whisper quick setup requires Windows x64");
        var recognition = OfflineSpeechRecognition.forWhisperDirectory(directory);
        if (recognition.installed()) return;
        var executable = recognition.executable();
        var model = recognition.model();
        boolean needsEngine = !Files.isRegularFile(executable) || !Files.isExecutable(executable);
        boolean needsModel = !Files.isRegularFile(model);
        if ((needsEngine && exists(executable)) || (needsModel && exists(model))) {
            throw new IOException("Cannot install over existing Whisper files");
        }

        Files.createDirectories(directory);
        Path staging = Files.createTempDirectory(directory, ".whisper-download-");
        try {
            String executableName = executable.getFileName().toString();
            Path stagedModel = staging.resolve(model.getFileName());
            if (needsEngine) {
                Path archive = staging.resolve("whisper.zip");
                download(WINDOWS_X64, archive);
                try (var zip = new ZipFile(archive.toFile())) {
                    for (String library : LIBRARIES) unpack(zip, staging, library);
                    unpack(zip, staging, executableName);
                }
                for (String library : LIBRARIES) {
                    Path existing = directory.resolve(library);
                    if (exists(existing) && (!Files.isRegularFile(existing, LinkOption.NOFOLLOW_LINKS)
                            || Files.mismatch(staging.resolve(library), existing) != -1)) {
                        throw new IOException("Conflicting Whisper library: " + library);
                    }
                }
            }
            if (needsModel) download(MULTILINGUAL_BASE, stagedModel);

            if (needsEngine) {
                for (String library : LIBRARIES) {
                    Path target = directory.resolve(library);
                    if (!exists(target)) Files.move(staging.resolve(library), target);
                }
            }
            if (needsModel) Files.move(stagedModel, model);
            if (needsEngine) Files.move(staging.resolve(executableName), executable);
            if (!recognition.installed()) throw new IOException("Whisper setup is incomplete");
        } finally {
            try {
                try (var files = Files.newDirectoryStream(staging)) {
                    for (Path file : files) Files.deleteIfExists(file);
                }
                Files.deleteIfExists(staging);
            } catch (IOException error) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not remove Whisper download files", error);
            }
        }
    }

    private static boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void unpack(ZipFile zip, Path staging, String name) throws IOException {
        var entry = zip.getEntry("Release/" + name);
        if (entry == null || entry.isDirectory()) throw new IOException("Missing Whisper file in archive: " + name);
        try (var input = zip.getInputStream(entry)) {
            Files.copy(input, staging.resolve(name));
        }
    }

    private static void download(Asset asset, Path destination) throws IOException {
        var connection = (HttpsURLConnection) URI.create(asset.url()).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        try {
            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                throw new IOException("Whisper download HTTP status: " + connection.getResponseCode());
            }
            try (var input = connection.getInputStream(); var output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[16_384];
                long remaining = asset.bytes();
                for (int read; (read = input.read(buffer)) != -1;) {
                    remaining -= read;
                    if (remaining < 0) throw new IOException("Whisper download exceeded its expected size");
                    output.write(buffer, 0, read);
                }
                if (remaining != 0) throw new IOException("Whisper download ended early");
            }
            if (!asset.sha256().equals(CheckedFileStore.sha256(destination))) {
                throw new IOException("Whisper download failed SHA-256 verification");
            }
        } finally {
            connection.disconnect();
        }
    }

    private record Asset(String url, long bytes, String sha256) {}
}
