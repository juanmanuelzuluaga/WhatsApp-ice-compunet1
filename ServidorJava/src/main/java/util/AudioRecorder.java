package util;

import javax.sound.sampled.*;
import java.io.*;

public class AudioRecorder {
    private static final int BUFFER_SIZE = 1024;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000, 16, 1, true, false);

    private TargetDataLine microphone;
    private boolean isRecording = false;
    private Thread recordingThread;
    private AudioDataListener audioDataListener;

    // Interface para enviar datos de audio en tiempo real
    public interface AudioDataListener {
        void onAudioData(byte[] audioData);
    }

    // Grabar nota de voz y guardar en archivo
    public void recordVoiceNote(String fileName, int durationSeconds) {
        try {
            ByteArrayOutputStream recordedBytes = new ByteArrayOutputStream();

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(AUDIO_FORMAT);
            microphone.start();

            isRecording = true;
            System.out.println("🎤 Grabando nota de voz por " + durationSeconds + " segundos...");

            byte[] buffer = new byte[BUFFER_SIZE];
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000);

            while (isRecording && System.currentTimeMillis() < endTime) {
                int count = microphone.read(buffer, 0, buffer.length);
                if (count > 0) {
                    recordedBytes.write(buffer, 0, count);
                }
            }

            microphone.stop();
            microphone.close();

            // Guardar archivo
            saveVoiceNote(recordedBytes.toByteArray(), fileName);
            System.out.println(" Nota de voz guardada: " + fileName);

        } catch (Exception e) {
            System.out.println(" Error grabando: " + e.getMessage());
        }
    }

    // Grabar audio para llamadas en tiempo real
    public void startRecordingForCall(AudioDataListener listener) {
        if (isRecording) return;

        this.audioDataListener = listener;
        isRecording = true;

        recordingThread = new Thread(() -> {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(AUDIO_FORMAT);
                microphone.start();

                byte[] buffer = new byte[BUFFER_SIZE];
                System.out.println(" Grabando para llamada...");

                while (isRecording) {
                    int count = microphone.read(buffer, 0, buffer.length);
                    if (count > 0 && audioDataListener != null) {
                        // Enviar datos de audio al CallManager
                        byte[] audioData = new byte[count];
                        System.arraycopy(buffer, 0, audioData, 0, count);
                        audioDataListener.onAudioData(audioData);
                    }
                }

                microphone.stop();
                microphone.close();
                System.out.println(" Grabación de llamada detenida");

            } catch (Exception e) {
                System.out.println(" Error en grabación de llamada: " + e.getMessage());
            }
        });

        recordingThread.start();
    }

    public void stopRecording() {
        isRecording = false;
        if (recordingThread != null) {
            recordingThread.interrupt();
        }
    }

    private void saveVoiceNote(byte[] audioData, String fileName) throws IOException {
        // Crear directorio si no existe
        File audioDir = new File("data/audio");
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }

        ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
        AudioInputStream audioStream = new AudioInputStream(byteStream, AUDIO_FORMAT, audioData.length / AUDIO_FORMAT.getFrameSize());

        File outputFile = new File("data/audio/" + fileName + ".wav");
        AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputFile);
    }

    public boolean isRecording() {
        return isRecording;
    }
}
