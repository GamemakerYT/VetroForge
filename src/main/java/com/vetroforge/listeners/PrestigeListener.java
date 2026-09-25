// src/main/java/com/vetroforge/listeners/PrestigeListener.java
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.prestige.PrestigeLevelUpEvent;
import com.vetroforge.util.ActionBarUtil;
import com.vetroforge.util.PrestigeUtil;
import com.vetroforge.warzone.WarzoneRegionManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

import java.time.Duration;

public class PrestigeListener implements Listener {

    private static final String ARROW = "§8» §f";

    private final FactionsPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(250));

    public PrestigeListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrestigeLevelUp(PrestigeLevelUpEvent e) {
        final Player p = e.getPlayer();
        if (p == null || !p.isOnline()) return;

        final int newLvl = e.getNewLevel();
        final String prettyTier = PrestigeUtil.titleFor(newLvl); // z.B. "Bronze IV"

        // 1) Titel (Adventure) + Sound
        try {
            p.showTitle(Title.title(
                    mm.deserialize("<gradient:#ffcc00:#ff8800><bold>PRESTIGE " + newLvl + "</bold></gradient>"),
                    mm.deserialize("<yellow>Neues Level erreicht: <white>" + prettyTier),
                    TITLE_TIMES
            ));
        } catch (Throwable ignored) {
            // Fallback: klassischer Title
            p.sendTitle("§6§lPRESTIGE " + newLvl, "§eNeues Level: §f" + prettyTier, 10, 40, 10);
        }
        try { p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f); } catch (Throwable ignored){}

        // 2) Actionbar
        ActionBarUtil.send(p, "§6Prestige " + ARROW + "§e" + prettyTier + " §7(§f" + newLvl + "§7)");

        // 3) Broadcast (optional per config: prestige.broadcastLevelUp)
        if (plugin.getConfig().getBoolean("prestige.broadcastLevelUp", true)) {
            Bukkit.broadcastMessage("§6§lVetroForge §8• §e" + p.getName()
                    + " §7hat §6Prestige " + ARROW + "§e" + prettyTier + " §7(§f" + newLvl + "§7) erreicht!");
        }

        // 4) Scoreboard auffrischen – je nach Standort Warzone- oder Lobby-Board
        try {
            WarzoneRegionManager wz = plugin.getWarzoneRegionManager();
            boolean inWarzone = wz != null && wz.isWarzone(p.getLocation());
            if (inWarzone) plugin.getSidebarScoreboardManager().showWarzoneBoard(p);
            else           plugin.getSidebarScoreboardManager().showLobbyBoard(p);
        } catch (Throwable ignored) {}

        try {
            if (plugin.getNametagTabManager() != null) {
                plugin.getNametagTabManager().updateTargetEverywhere(p);
            }
            if (plugin.getTablistManager() != null) {
                plugin.getTablistManager().updatePlayer(p);
            }
        } catch (Throwable ignored) {}
    }
}
