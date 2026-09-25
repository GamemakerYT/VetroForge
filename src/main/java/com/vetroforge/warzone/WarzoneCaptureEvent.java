// src/main/java/com/vetroforge/warzone/event/WarzoneCaptureEvent.java
package com.vetroforge.warzone;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Wird ausgelöst, wenn ein Warzone-Flaggenpunkt eingenommen wurde.
 *
 * Beispiel:
 *   Bukkit.getPluginManager().callEvent(new WarzoneCaptureEvent(player, "Alpha"));
 */
public class WarzoneCaptureEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String pointId;
    private final long   timestamp;

    /**
     * @param player  Spieler, der den Punkt final erobert hat
     * @param pointId interne/kurze Kennung des Punktes (z.B. "Alpha")
     */
    public WarzoneCaptureEvent(Player player, String pointId) {
        this.player = Objects.requireNonNull(player, "player");
        this.pointId = Objects.requireNonNull(pointId, "pointId");
        this.timestamp = System.currentTimeMillis();
    }

    public Player getPlayer()   { return player; }
    public String getPointId()  { return pointId; }
    /** Millisekunden-Snapshot beim Auslösen (nützlich fürs Logging). */
    public long getTimestamp()  { return timestamp; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }

    @Override public String toString() {
        return "WarzoneCaptureEvent{player=" + player.getName() + ", pointId='" + pointId + "', ts=" + timestamp + '}';
    }
}