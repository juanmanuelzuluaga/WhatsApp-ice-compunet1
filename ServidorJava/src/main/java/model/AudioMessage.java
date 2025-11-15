package model;

public class AudioMessage extends Message implements java.io.Serializable{
    private final String audioId;
    private final int audioSize;
    private final int duration;
    private byte[] audioData;
    private String audioPath;

    public AudioMessage(String from, String to, boolean isGroup, byte[] audioData, int duration) {
        super(from, to, "[AUDIO]", isGroup);
        this.audioId = getId();
        this.audioData = audioData;
        this.audioSize = audioData != null ? audioData.length : 0;
        this.duration = duration;
    }

    public AudioMessage(String id, String from, String to, boolean isGroup, long timestamp, int audioSize, int duration) {
        super(id, from, to, "[AUDIO]", isGroup, timestamp);
        this.audioId = id;
        this.audioSize = audioSize;
        this.duration = duration;
    }

    public String getAudioId() { return audioId; }
    public int getAudioSize() { return audioSize; }
    public int getDuration() { return duration; }
    public byte[] getAudioData() { return audioData; }
    public String getAudioPath() { return audioPath; }

    public void setAudioData(byte[] audioData) { this.audioData = audioData; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }

    @Override
    public String toString() {
        return "[" + getTimestamp() + "] 🎵 Audio de " + getFrom() +
                " (" + audioSize + " bytes, " + duration + "s)";
    }
}
