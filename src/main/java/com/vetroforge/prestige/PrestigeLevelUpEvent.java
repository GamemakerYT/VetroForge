package com.vetroforge.prestige;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PrestigeLevelUpEvent extends Event {
    public static final String ARROW = "§8» §f";

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int oldLevel;
    private final int newLevel;

    public PrestigeLevelUpEvent(Player player, int oldLevel, int newLevel) {
        super(false); // sync
        this.player   = player;     // darf null sein, wenn offline – Listener müssen das prüfen
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() { return player; }
    public int getOldLevel()  { return oldLevel; }
    public int getNewLevel()  { return newLevel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}