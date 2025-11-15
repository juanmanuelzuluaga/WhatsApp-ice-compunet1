package model;

import java.util.UUID;

public class User implements java.io.Serializable {
    private final String id;
    private String name;

    public User(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return name + " (" + id.substring(0, 6) + ")";
    }
}
