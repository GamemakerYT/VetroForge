// src/main/java/com/vetroforge/prestige/PrestigeXpGainEvent.java
package com.vetroforge.prestige;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Fired when a player gains Prestige XP (after boosters, before level-ups). */
public class PrestigeXpGainEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int amount;

    public PrestigeXpGainEvent(Player player, int amount) {
        super(false); // sync
        this.player = Objects.requireNonNull(player, "player");
        this.amount = amount;
    }

    public Player getPlayer() { return player; }
    public int getAmount() { return amount; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}