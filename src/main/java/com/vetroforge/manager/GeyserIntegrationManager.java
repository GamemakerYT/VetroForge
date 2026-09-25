// src/main/java/com/vetroforge/manager/GeyserIntegrationManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Prüft, ob GeyserMC geladen ist, und bietet Bedrock-Unterstützung an.
 */
public class GeyserIntegrationManager {
    private final boolean enabled;

    public GeyserIntegrationManager(FactionsPlugin plugin) {
        Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser");
        this.enabled = geyser != null && geyser.isEnabled();
    }

    /**
     * Gibt true zurück, wenn Bedrock-Spieler zugelassen sind.
     */
    public boolean isBedrockSupportEnabled() {
        return enabled;
    }

    /**
     * Beispiel-Aufruf, um Geyser-Config zu ändern oder Features zu aktivieren.
     */
    public void initialize() {
        if (!enabled) return;
        Bukkit.getConsoleSender().sendMessage("§aGeyser detected – Bedrock support aktiv.");
        // zusätzliche Geyser-spezifische Initialisierung hier
    }
}