package com.flyaway.tops.model;

import java.util.UUID;

public class TopPlayer {
    private final UUID uuid;
    private final String playerName;
    private final String displayValue; // Форматированное значение (например: "1,000 монет", "50 часов")
    private final double rawValue; // Числовое значение для сортировки

    public TopPlayer(UUID uuid, String playerName, String displayValue, double rawValue) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.displayValue = displayValue;
        this.rawValue = rawValue;
    }

    // Геттеры
    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public String getDisplayValue() { return displayValue; }
    public double getRawValue() { return rawValue; }
}
