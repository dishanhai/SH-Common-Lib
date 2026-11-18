// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Local, interruptible recognition of a single short utterance using a multilingual whisper.cpp model. */
public record OfflineSpeechRecognition(Path executable, Path model) {
    private static final AudioFormat FORMAT = new AudioFormat(16_000, 16, 1, true, false);
    private static final int FRAME_BYTES = 640; // 20 ms of signed 16-bit PCM.
    private static final int MIN_VOICED_FRAMES = 5;
    private static final long CAPTURE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long END_SILENCE_NANOS = TimeUnit.MILLISECONDS.toNanos(800);

    public boolean installed() {
        return Files.isRegularFile(executable) && Files.isExecutable(executable) && Files.isRegularFile(model);
    }

    /**
     * Call on a worker thread. An empty result means no speech was detected or transcribed.
     * The callback runs on the worker after capture, before transcription.
     */
    public Optional<String> listen(Runnable onRecognizing)
            throws IOException, LineUnavailableException, InterruptedException {
        var utterance = capture();
        if (utterance.length == 0) return Optional.empty();
        onRecognizing.run();
        return transcribe(utterance);
    }

    private static byte[] capture() throws LineUnavailableException, InterruptedException {
        var buffer = new ByteArrayOutputStream(160_000);
        var frame = new byte[FRAME_BYTES];
        int voicedFrames = 0;
        try (var mic = AudioSystem.getTargetDataLine(FORMAT)) {
            mic.open(FORMAT);
            mic.start();
            long start = System.nanoTime();
            long lastVoice = start;
            while (System.nanoTime() - start < CAPTURE_NANOS) {
                if (Thread.interrupted()) throw new InterruptedException();
                if (voicedFrames >= MIN_VOICED_FRAMES && System.nanoTime() - lastVoice >= END_SILENCE_NANOS) break;
                if (mic.available() < frame.length) {
                    Thread.sleep(8);
                    continue;
                }
                int read = mic.read(frame, 0, frame.length);
                if (read <= 0) break;
                buffer.write(frame, 0, read);
                if (voiced(frame, read)) {
                    lastVoice = System.nanoTime();
                    voicedFrames++;
                }
            }
        }
        return voicedFrames >= MIN_VOICED_FRAMES ? buffer.toByteArray() : new byte[0];
    }

    private static boolean voiced(byte[] frame, int length) {
        long squares = 0;
        for (int index = 0; index + 1 < length; index += 2) {
            int sample = (frame[index + 1] << 8) | (frame[index] & 0xff);
            squares += sample * sample;
        }
        return squares > (length / 2L) * 280 * 280;
    }

    private Optional<String> transcribe(byte[] pcm) throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("shclib-speech-");
        Path wav = directory.resolve("speech.wav");
        Path transcript = directory.resolve("result.txt");
        try {
            try (var input = new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT,
                    pcm.length / FORMAT.getFrameSize())) {
                AudioSystem.write(input, AudioFileFormat.Type.WAVE, wav.toFile());
            }
            if (Thread.interrupted()) throw new InterruptedException();
            Process process = new ProcessBuilder(executable.toAbsolutePath().toString(), "-m", model.toAbsolutePath().toString(),
                    "-f", wav.toString(), "-l", "auto", "-nt", "-otxt", "-of", directory.resolve("result").toString())
                    .directory(executable.toAbsolutePath().getParent().toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                if (!process.waitFor(25, TimeUnit.SECONDS)) throw new IOException("Speech recognition timed out");
                if (process.exitValue() != 0) throw new IOException("whisper-cli exited with code " + process.exitValue());
                String text = Files.readString(transcript).strip();
                return text.isEmpty() ? Optional.empty() : Optional.of(text);
            } finally {
                if (process.isAlive()) process.destroyForcibly().onExit().join();
            }
        } finally {
            Files.deleteIfExists(transcript);
            Files.deleteIfExists(wav);
            Files.deleteIfExists(directory);
        }
    }
}
