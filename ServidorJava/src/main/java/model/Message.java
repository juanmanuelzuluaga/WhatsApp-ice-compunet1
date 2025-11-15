package model;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

public class Message implements java.io.Serializable {
    private final String id;
    private final String from;
    private final String to;
    private final String content;
    private final boolean isGroup;
    private final Date timestamp;

    public Message(String from, String to, String content, boolean isGroup) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.content = content;
        this.isGroup = isGroup;
        this.timestamp = new Date();
    }

    public Message(String id, String from, String to, String content, boolean isGroup, long timestamp) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.content = content;
        this.isGroup = isGroup;
        this.timestamp = new Date(timestamp);
    }

    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getContent() { return content; }
    public boolean isGroupMessage() { return isGroup; }
    public Date getTimestamp() { return timestamp; }

    public String getSender() { return from; }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + from + " -> " + to + ": " + content;
    }
}
