package persistence;

import model.Message;
import model.AudioMessage;

import java.io.*;
import java.util.*;

public class ChatHistory {
    private static final String HISTORY_DIR = "data/history/";
    private static final String AUDIO_DIR = "data/audio/";

    public ChatHistory() {
        createDirectories();
    }

    private void createDirectories() {
        new File(HISTORY_DIR).mkdirs();
        new File(AUDIO_DIR).mkdirs();
    }

    public void saveMessage(Message message) {
        if (message == null) return;

        String filename = getHistoryFilename(message.getTo(), message.isGroupMessage());
        List<Message> messages = loadMessages(message.getTo(), message.isGroupMessage());
        messages.add(message);

        saveMessagesToFile(messages, filename);
    }

    public void saveAudioMessage(AudioMessage audioMessage) {
        if (audioMessage == null) return;

        // Guardar metadatos del audio
        String filename = getHistoryFilename(audioMessage.getTo(), audioMessage.isGroupMessage()) + "_audio";
        List<AudioMessage> audioMessages = loadAudioMessages(audioMessage.getTo(), audioMessage.isGroupMessage());
        audioMessages.add(audioMessage);

        saveAudioMessagesToFile(audioMessages, filename);

        // Guardar datos de audio en archivo separado
        saveAudioData(audioMessage);
    }

    private void saveMessagesToFile(List<Message> messages, String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (Message message : messages) {
                writer.println(serializeMessage(message));
            }
        } catch (IOException e) {
            System.err.println("Error guardando mensajes: " + e.getMessage());
        }
    }

    private void saveAudioMessagesToFile(List<AudioMessage> audioMessages, String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (AudioMessage audioMessage : audioMessages) {
                writer.println(serializeAudioMessage(audioMessage));
            }
        } catch (IOException e) {
            System.err.println("Error guardando audio messages: " + e.getMessage());
        }
    }

    private String serializeMessage(Message message) {
        return String.format("id:%s|from:%s|to:%s|content:%s|timestamp:%d|isGroup:%b",
                message.getId(),
                message.getFrom(),
                message.getTo(),
                escapeContent(message.getContent()),
                message.getTimestamp().getTime(),
                message.isGroupMessage()
        );
    }

    private String serializeAudioMessage(AudioMessage audioMessage) {
        return String.format("id:%s|from:%s|to:%s|timestamp:%d|isGroup:%b|duration:%d|size:%d",
                audioMessage.getId(),
                audioMessage.getFrom(),
                audioMessage.getTo(),
                audioMessage.getTimestamp().getTime(),
                audioMessage.isGroupMessage(),
                audioMessage.getDuration(),
                audioMessage.getAudioSize()
        );
    }

    private Message deserializeMessage(String line) {
        try {
            Map<String, String> data = parseLine(line);
            Message message = new Message(
                    data.get("from"),
                    data.get("to"),
                    unescapeContent(data.get("content")),
                    Boolean.parseBoolean(data.get("isGroup"))
            );
            return message;
        } catch (Exception e) {
            System.err.println("Error deserializando mensaje: " + e.getMessage());
            return null;
        }
    }

    private AudioMessage deserializeAudioMessage(String line) {
        try {
            Map<String, String> data = parseLine(line);
            // Para audio, solo guardamos metadatos, los datos binarios se guardan aparte
            AudioMessage audioMessage = new AudioMessage(
                    data.get("id"),
                    data.get("from"),
                    data.get("to"),
                    Boolean.parseBoolean(data.get("isGroup")),
                    Long.parseLong(data.get("timestamp")),
                    Integer.parseInt(data.get("audioSize")),
                    Integer.parseInt(data.get("duration"))
            );

            return audioMessage;
        } catch (Exception e) {
            System.err.println("Error deserializando audio message: " + e.getMessage());
            return null;
        }
    }

    private Map<String, String> parseLine(String line) {
        Map<String, String> result = new HashMap<>();
        String[] pairs = line.split("\\|");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                result.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return result;
    }

    private String escapeContent(String content) {
        return content.replace("|", "\\|").replace(":", "\\:").replace("\n", "\\n");
    }

    private String unescapeContent(String content) {
        return content.replace("\\|", "|").replace("\\:", ":").replace("\\n", "\n");
    }

    private void saveAudioData(AudioMessage audioMessage) {
        String audioFilename = AUDIO_DIR + audioMessage.getId() + ".audio";
        try (FileOutputStream fos = new FileOutputStream(audioFilename)) {
            fos.write(audioMessage.getAudioData());
        } catch (IOException e) {
            System.err.println("Error guardando datos de audio: " + e.getMessage());
        }
    }

    public List<Message> loadMessages(String target, boolean isGroup) {
        String filename = getHistoryFilename(target, isGroup);
        List<Message> messages = new ArrayList<>();

        File file = new File(filename);
        if (!file.exists()) {
            return messages;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Message message = deserializeMessage(line);
                if (message != null) {
                    messages.add(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Error cargando mensajes: " + e.getMessage());
        }

        return messages;
    }

    public List<AudioMessage> loadAudioMessages(String target, boolean isGroup) {
        String filename = getHistoryFilename(target, isGroup) + "_audio";
        List<AudioMessage> audioMessages = new ArrayList<>();

        File file = new File(filename);
        if (!file.exists()) {
            return audioMessages;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                AudioMessage audioMessage = deserializeAudioMessage(line);
                if (audioMessage != null) {
                    // Cargar datos de audio
                    byte[] audioData = loadAudioData(audioMessage.getId());
                    if (audioData != null) {
                        // Necesitaríamos un método setter en AudioMessage para establecer los datos
                        // audioMessage.setAudioData(audioData);
                    }
                    audioMessages.add(audioMessage);
                }
            }
        } catch (IOException e) {
            System.err.println("Error cargando audio messages: " + e.getMessage());
        }

        return audioMessages;
    }

    public byte[] loadAudioData(String audioId) {
        String audioFilename = AUDIO_DIR + audioId + ".audio";
        File file = new File(audioFilename);

        if (!file.exists()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(audioFilename)) {
            return fis.readAllBytes();
        } catch (IOException e) {
            System.err.println("Error cargando datos de audio: " + e.getMessage());
            return null;
        }
    }



    private String getHistoryFilename(String target, boolean isGroup) {
        String prefix = isGroup ? "group_" : "user_";
        String safeTarget = target.replaceAll("[^a-zA-Z0-9]", "_");
        return HISTORY_DIR + prefix + safeTarget + ".txt";
    }
}
