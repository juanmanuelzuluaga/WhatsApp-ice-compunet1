package util;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioPlayer {
    private static final int BUFFER_SIZE = 1024;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000, 16, 1, true, false);

    private SourceDataLine speakers;
    private boolean isPlaying = false;
    private Thread playbackThread;
    private BlockingQueue<byte[]> audioBuffer;

    public AudioPlayer() {
        this.audioBuffer = new LinkedBlockingQueue<>(50); // Buffer de 50 paquetes
    }

    public void saveVoiceNote(byte[] audioData, String fileName) throws IOException {
        File audioDir = new File("data/audio");
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }

        ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
        AudioInputStream audioStream = new AudioInputStream(byteStream, AUDIO_FORMAT, audioData.length / AUDIO_FORMAT.getFrameSize());

        File outputFile = new File("data/audio/" + fileName + ".wav");
        AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputFile);

        audioStream.close();
        byteStream.close();
    }

    public void playVoiceNote(String fileName) {
        try {
            File audioFile = new File("data/audio/" + fileName + ".wav");
            if (!audioFile.exists()) {
                System.out.println("Archivo no encontrado: " + audioFile.getAbsolutePath());
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(AUDIO_FORMAT);
            speakers.start();

            System.out.println("Reproduciendo nota de voz: " + fileName);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                speakers.write(buffer, 0, bytesRead);
            }

            speakers.drain();
            speakers.close();
            audioStream.close();

            System.out.println("Reproduccion terminada.");

        } catch (Exception e) {
            System.out.println("Error reproduciendo: " + e.getMessage());
        }
    }

    public void startPlayingForCall() {
        if (isPlaying) return;

        isPlaying = true;
        playbackThread = new Thread(() -> {
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
                speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(AUDIO_FORMAT);
                speakers.start();

                System.out.println("Reproduccion de llamada iniciada...");

                while (isPlaying) {
                    try {
                        byte[] audioData = audioBuffer.take();
                        speakers.write(audioData, 0, audioData.length);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                // No mostramos error si fue una interrupción normal.
                if (isPlaying) {
                    System.out.println("Error en reproduccion de llamada: " + e.getMessage());
                }
            } finally {
                if (speakers != null) {
                    speakers.drain();
                    speakers.stop();
                    speakers.close();
                }
                System.out.println("Reproduccion de llamada detenida.");
            }
        });

        playbackThread.start();
    }

    public void addAudioData(byte[] audioData) {
        if (isPlaying && audioData != null) {
            if (!audioBuffer.offer(audioData)) {
                audioBuffer.poll();
                audioBuffer.offer(audioData);
            }
        }
    }

    public void playRawAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return;

        try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
            AudioInputStream audioStream = new AudioInputStream(byteStream, AUDIO_FORMAT, audioData.length / AUDIO_FORMAT.getFrameSize());

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(AUDIO_FORMAT);
            speakers.start();

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                speakers.write(buffer, 0, bytesRead);
            }

            speakers.drain();
            speakers.close();
            audioStream.close();
            byteStream.close();

        } catch (Exception e) {
            System.err.println("Error reproduciendo audio crudo: " + e.getMessage());
        }
    }

    public void stopPlaying() {
        isPlaying = false;
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
        audioBuffer.clear();
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
