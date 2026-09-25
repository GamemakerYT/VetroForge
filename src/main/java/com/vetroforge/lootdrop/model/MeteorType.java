package com.vetroforge.lootdrop.model;

public enum MeteorType {
    SUPPLY("Supply", 20),
    MYSTIC("Mystic", 30),
    RAID("Raid", 25),
    EVENT("Event", 30);

    private final String display;
    private final int lockSeconds;

    MeteorType(String display, int lockSeconds) {
        this.display = display;
        this.lockSeconds = lockSeconds;
    }

    public String display() { return display; }
    public int lockSeconds() { return lockSeconds; }
}
