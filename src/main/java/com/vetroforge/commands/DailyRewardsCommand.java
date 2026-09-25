package com.vetroforge.commands;

import com.vetroforge.services.DailyRewardService;
import com.vetroforge.util.Branding;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DailyRewardsCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String TITLE = Branding.GUI_TITLE + " §8• §fRewards";

    private final DailyRewardService service;
    private final boolean directDaily;

    public DailyRewardsCommand(DailyRewardService service, boolean directDaily) {
        this.service = service;
        this.directDaily = directDaily;
        if (!directDaily) {
            service.plugin().getServer().getPluginManager().registerEvents(this, service.plugin());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§9§lVetroForge §8» §7Nur Spieler können Rewards öffnen.");
            return true;
        }

        if (!directDaily && args.length > 0) {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "weekly", "woche" -> {
                    if (service.plugin().getFactionWeeklyQuestService() != null) {
                        service.plugin().getFactionWeeklyQuestService().show(player);
                    } else {
                        player.sendMessage(Branding.PREFIX + "§7Weekly Quests sind derzeit nicht verfügbar.");
                    }
                    return true;
                }
                case "events", "event" -> {
                    player.performCommand("events");
                    return true;
                }
                case "vote" -> {
                    voteUnavailable(player);
                    return true;
                }
                default -> {
                    // Unknown sections fall back to the overview instead of a raw usage line.
                }
            }
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
        decorate(inventory);

        DailyRewardService.Status status = service.status(player);
        LevelProgress progress = levelProgress(player);
        inventory.setItem(4, item(Material.NETHER_STAR, "§9§lReward Center", List.of(
                "§7Dein Fortschritt und alle",
                "§7offenen Belohnungen auf einen Blick."
        )));

        inventory.setItem(12, item(Material.EXPERIENCE_BOTTLE, "§a§lLevel " + progress.level(), List.of(
                progress.bar(),
                "",
                "§7XP §8• §f" + progress.xp() + "§8/§f" + progress.needed(),
                "§7Noch §8• §a" + progress.remaining() + " XP",
                "§7Nächstes Level §8• §f" + progress.nextLevel()
        )));

        inventory.setItem(22, item(status.canClaim() ? Material.SUNFLOWER : Material.GRAY_DYE, "§6§lDaily Reward", List.of(
                status.canClaim() ? "§aBereit zum Abholen" : "§7Heute bereits abgeholt",
                "",
                "§7Coins §8• §7XP §8• §7Versorgung",
                "",
                status.canClaim() ? "§eKlick §8» §fAbholen" : "§7Nächste Belohnung in §f" + status.resetIn(),
                "§8ID: daily"
        )));

        inventory.setItem(14, item(Material.AMETHYST_SHARD, "§d§lSerie", List.of(
                "§7Aktuell §8• §f" + status.streak() + " Tage",
                "§7Gesamt §8• §f" + status.totalClaims(),
                "",
                "§7Tag 7 §8• §9Rare Crystal"
        )));

        inventory.setItem(32, item(Material.CLOCK, "§b§lNächste Belohnung", List.of(
                "§7Verfügbar in §8• §f" + status.resetIn(),
                "",
                "§8Daily-Fortschritt bleibt bewusst langsam."
        )));

        inventory.setItem(30, item(Material.HOPPER, "§a§lAlle abholen", List.of(
                "§7Sammelt deine offenen Belohnungen.",
                "",
                status.canClaim() ? "§eKlick §8» §fEinsammeln" : "§8Keine Belohnung offen",
                "§8ID: collect_all"
        )));

        boolean weeklyReady = service.plugin().getFactionWeeklyQuestService() != null;
        inventory.setItem(38, item(Material.WRITABLE_BOOK, "§b§lWeekly Quests", List.of(
                "§7Gemeinsame Wochenziele deiner Faction.",
                "",
                weeklyReady ? "§eKlick §8» §fWochenziele öffnen" : "§8Derzeit nicht verfügbar",
                weeklyReady ? "§8ID: weekly" : "§8Status: offline"
        )));

        boolean eventActive = service.plugin().getEventManager() != null
                && service.plugin().getEventManager().getActiveEvent().isPresent();
        String eventName = eventActive
                ? service.plugin().getEventManager().getActiveEvent().get().name
                : "Kein Event aktiv";
        inventory.setItem(40, item(eventActive ? Material.FIREWORK_ROCKET : Material.GRAY_DYE, "§d§lEvents", List.of(
                "§7" + eventName,
                "",
                "§eKlick §8» §fEventübersicht öffnen",
                "§8ID: events"
        )));

        inventory.setItem(42, item(Material.EMERALD, "§a§lVote Rewards", List.of(
                "§7Vote-Belohnungen werden hier",
                "§7gebündelt, sobald der Vote-Service aktiv ist.",
                "",
                "§eKlick §8» §fStatus anzeigen",
                "§8ID: vote"
        )));

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.35f);
    }

    private void claim(Player player) {
        DailyRewardService.ClaimResult result = service.claim(player);
        player.sendMessage(Branding.PREFIX + result.summary());
        if (result.claimed()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.75f, 1.45f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.75f);
        }
    }

    private LevelProgress levelProgress(Player player) {
        try {
            var plugin = service.plugin();
            int level = plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getLevel(player);
            int xp = plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getLevelXp(player);
            int needed = plugin.getLevelProgressionService() == null ? 1 : plugin.getLevelProgressionService().xpNeeded(level);
            int max = plugin.getLevelProgressionService() == null ? 100 : plugin.getLevelProgressionService().maxLevel();
            int remaining = Math.max(0, needed - xp);
            int next = Math.min(max, level + 1);
            return new LevelProgress(level, xp, needed, remaining, next, progressBar(xp, needed));
        } catch (Throwable ignored) {
            return new LevelProgress(0, 0, 1, 1, 1, "§8[§7■■■■■■■■■■■■■■■■■■■■§8]");
        }
    }

    private String progressBar(int current, int needed) {
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, current / (double) Math.max(1, needed))) * 20.0D);
        return "§8[" + "§a■".repeat(filled) + "§7■".repeat(20 - filled) + "§8]";
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getLore() == null) return;

        for (String line : meta.getLore()) {
            String clean = ChatColor.stripColor(line);
            if (!clean.startsWith("ID: ")) continue;
            String id = clean.substring(4).trim();
            if (id.equalsIgnoreCase("daily") || id.equalsIgnoreCase("collect_all")) {
                player.closeInventory();
                claim(player);
            } else if (id.equalsIgnoreCase("weekly")) {
                player.closeInventory();
                player.performCommand("quest weekly");
            } else if (id.equalsIgnoreCase("events")) {
                player.closeInventory();
                player.performCommand("events");
            } else if (id.equalsIgnoreCase("vote")) {
                player.closeInventory();
                voteUnavailable(player);
            }
            return;
        }
    }

    private void voteUnavailable(Player player) {
        player.sendMessage(Branding.PREFIX + "§7Vote Rewards werden freigeschaltet, sobald der Vote-Service verbunden ist.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.9f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    private void decorate(Inventory inventory) {
        ItemStack black = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, " ", List.of());
        ItemStack gray = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, black);
        for (int slot : List.of(0, 1, 2, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 51, 52, 53)) inventory.setItem(slot, blue);
        for (int slot : List.of(3, 5, 10, 11, 13, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 31, 33, 34, 37, 38, 39, 40, 41, 42, 43, 48, 49, 50)) inventory.setItem(slot, gray);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (directDaily || args.length != 1) return List.of();
        return List.of("daily", "weekly", "events", "vote");
    }

    private record LevelProgress(int level, int xp, int needed, int remaining, int nextLevel, String bar) {
    }
}
