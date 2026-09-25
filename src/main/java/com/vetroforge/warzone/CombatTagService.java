// src/main/java/com/vetroforge/warzone/CombatTagService.java
package com.vetroforge.warzone;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat-Tag: wer in einer PvP-Zone (Warzone/Raid) Schaden von einem anderen
 * Spieler nimmt oder austeilt, ist für eine konfigurierbare Zeit "im Kampf" und
 * kann in dieser Zeit keine Flucht-Befehle (/spawn, /f home, ...) mehr nutzen.
 */
public final class CombatTagService implements Listener {

    private final FactionsPlugin plugin;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();

    public CombatTagService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isTagged(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    public long remainingSeconds(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999L) / 1000L);
    }

    public void tag(Player p) {
        int seconds = Math.max(1, plugin.getConfig().getInt("warzone.combatTag.durationSeconds", 15));
        taggedUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("warzone.combatTag.enabled", true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!enabled()) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        WarzoneRegionManager warzone = plugin.getWarzoneRegionManager();
        if (warzone == null) return;
        if (!warzone.isPvpZone(victim.getLocation()) && !warzone.isPvpZone(attacker.getLocation())) return;

        tag(victim);
        tag(attacker);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!enabled()) return;
        Player p = e.getPlayer();
        if (!isTagged(p.getUniqueId())) return;
        if (p.hasPermission("vetroforge.combat.bypass")) return;

        String[] typed = e.getMessage().substring(1).trim().split("\\s+");
        if (typed.length == 0 || typed[0].isEmpty()) return;

        PluginCommand resolved = Bukkit.getPluginCommand(typed[0].toLowerCase(Locale.ROOT));
        String base = resolved != null ? resolved.getName().toLowerCase(Locale.ROOT) : typed[0].toLowerCase(Locale.ROOT);
        String sub = typed.length > 1 ? typed[1].toLowerCase(Locale.ROOT) : null;

        for (String[] blocked : blockedCommands()) {
            if (!base.equals(blocked[0])) continue;
            if (blocked[1] != null && !blocked[1].equals(sub)) continue;

            e.setCancelled(true);
            p.sendMessage(VetroForgeUtil.tr(plugin, p, "combat.blocked",
                    VetroForgeUtil.vars("seconds", remainingSeconds(p.getUniqueId()))));
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (enabled() && isTagged(uuid) && !p.hasPermission("vetroforge.combat.bypass")
                && plugin.getConfig().getBoolean("warzone.combatTag.dropItemsOnLogout", true)) {
            dropInventory(p);
        }

        taggedUntil.remove(uuid);
    }

    /** Warzone-Death-Loss: Ausrüstung geht bei einem Tod in der Warzone nur verloren,
     *  wenn das Opfer zum Todeszeitpunkt noch im Combat-Tag steht -- ein Tod ohne
     *  aktives PvP (Sturz, Mob, o.ä.) bleibt ohne Verlust. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!enabled()) return;
        if (!plugin.getConfig().getBoolean("warzone.combatTag.dropItemsOnDeath", true)) return;

        Player p = e.getEntity();
        WarzoneRegionManager warzone = plugin.getWarzoneRegionManager();
        if (warzone == null || !warzone.isPvpZone(p.getLocation())) return;

        if (isTagged(p.getUniqueId()) && !p.hasPermission("vetroforge.combat.bypass")) return;

        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    /** Combat-Log-Strafe: anders als ein normaler Tod (Inventar bleibt dabei erhalten)
     *  verliert ein Spieler, der während eines aktiven Combat-Tags den Server verlässt,
     *  seine komplette Ausrüstung -- als Abschreckung, sich einem Kampf per Logout zu entziehen. */
    private void dropInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        Location loc = p.getLocation();
        World world = p.getWorld();

        for (ItemStack item : inv.getContents()) {
            dropIfPresent(world, loc, item);
        }
        for (ItemStack item : inv.getArmorContents()) {
            dropIfPresent(world, loc, item);
        }
        dropIfPresent(world, loc, inv.getItemInOffHand());

        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(new ItemStack(Material.AIR));
    }

    private void dropIfPresent(World world, Location loc, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            world.dropItemNaturally(loc, item);
        }
    }

    private List<String[]> blockedCommands() {
        List<String> configured = plugin.getConfig().getStringList("warzone.combatTag.blockedCommands");
        List<String> source = (configured == null || configured.isEmpty())
                ? List.of("spawn", "hub", "f home")
                : configured;

        return source.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT).split("\\s+", 2))
                .map(parts -> new String[]{parts[0], parts.length > 1 ? parts[1] : null})
                .toList();
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
