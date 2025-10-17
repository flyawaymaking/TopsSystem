package com.flyaway.tops.model;

import java.util.List;

public class TopCategory {
    private final String id;
    private final String displayName;
    private final String icon;
    private final String description;
    private final List<TopPlayer> topPlayers;

    public TopCategory(String id, String displayName, String icon, String description, List<TopPlayer> topPlayers) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.topPlayers = topPlayers;
    }

    // Геттеры
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }
    public String getDescription() { return description; }
    public List<TopPlayer> getTopPlayers() { return topPlayers; }
}
