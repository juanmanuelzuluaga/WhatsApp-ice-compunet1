package model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Group implements java.io.Serializable {
    private final String name;
    private final String creator;
    private final Set<String> members;
    private final List<Message> messages;

    public Group(String name, String creator) {
        this.name = name;
        this.creator = creator;
        this.members = new HashSet<>();
        this.messages = new ArrayList<>();
        this.members.add(creator);
    }

    public String getName() { return name; }
    public String getCreator() { return creator; }
    public Set<String> getMembers() { return members; }
    public List<Message> getMessages() { return messages; }

    public boolean addMember(String username) {
        return members.add(username);
    }

    public boolean removeMember(String username) {
        return members.remove(username);
    }

    public boolean hasMember(String username) {
        return members.contains(username);
    }

    public int getMemberCount() {
        return members.size();
    }

    public void addMessage(Message msg) {
        messages.add(msg);
    }

    @Override
    public String toString() {
        return "Grupo: " + name + " (" + members.size() + " miembros)";
    }
}
