package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VetroForgeCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String DOCTOR_TITLE = "§4§lVetroForge §8• §cDoctor";
    private final FactionsPlugin plugin;
    private final NamespacedKey doctorKey;

    public VetroForgeCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.doctorKey = new NamespacedKey(plugin, "doctor_check");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("doctor")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("gui")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cDas Doctor-GUI kann nur ein Spieler öffnen.");
                    return true;
                }
                openDoctorGui(player);
                return true;
            }
            sender.sendMessage(plugin.runDoctor());
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§aKonfiguration neu geladen.");
            return true;
        }
        plugin.reloadConfig();
        sender.sendMessage("§aKonfiguration neu geladen. §7Nutze §f/" + label + " doctor §7für Systemcheck.");
        return true;
    }

    private void openDoctorGui(Player player) {
        List<FactionsPlugin.DoctorCheck> checks = plugin.runDoctorChecks();
        Inventory inventory = Bukkit.createInventory(null, 36, DOCTOR_TITLE);
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, "§8", List.of(), null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < checks.size() && i < slots.length; i++) {
            FactionsPlugin.DoctorCheck check = checks.get(i);
            Material material = check.ok() ? Material.LIME_DYE : Material.RED_DYE;
            List<String> lore = new ArrayList<>();
            lore.add(check.ok() ? "§a● Online / OK" : "§c● Fehler / prüfen");
            lore.add("§8" + safe(check.detail()));
            lore.add(" ");
            lore.add("§eKlicken: §7Details & Fix-Hinweis");
            inventory.setItem(slots[i], named(material, (check.ok() ? "§a" : "§c") + check.label(), lore, check.label()));
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onDoctorClick(InventoryClickEvent event) {
        if (!DOCTOR_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String label = clicked.getItemMeta().getPersistentDataContainer().get(doctorKey, PersistentDataType.STRING);
        if (label == null || label.isBlank()) return;

        for (FactionsPlugin.DoctorCheck check : plugin.runDoctorChecks()) {
            if (!check.label().equalsIgnoreCase(label)) continue;
            player.sendMessage("§c§lVetroForge §8» §fDoctor: " + (check.ok() ? "§a" : "§c") + check.label());
            player.sendMessage("§7Status: " + (check.ok() ? "§aOK" : "§cPrüfen"));
            player.sendMessage("§7Detail: §f" + safe(check.detail()));
            player.sendMessage("§7Fix: §e" + doctorFix(check.label(), check.ok()));
            return;
        }
    }

    @EventHandler
    public void onDoctorDrag(InventoryDragEvent event) {
        if (DOCTOR_TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    private String doctorFix(String label, boolean ok) {
        String normalized = label.toLowerCase(Locale.ROOT);
        if (normalized.contains("mysql")) {
            return ok ? "MySQL ist erreichbar. Bei Profilproblemen player_cache/web_users prüfen." : "mysql.host, user, password und DB-Rechte in config.yml prüfen.";
        }
        if (normalized.contains("redis")) {
            return ok ? "Redis antwortet. Coins/Rank-Sync kann laufen." : "Redis Dienst starten oder redis.host/port/password korrigieren.";
        }
        if (normalized.contains("webapi")) {
            return ok ? "WebAPI läuft. Extern über Nginx/HTTPS prüfen, wenn die Website leer bleibt." : "webapi.enabled=true setzen und Port 8500 frei halten.";
        }
        if (normalized.contains("ssl")) {
            return ok ? "Public URL nutzt HTTPS." : "status.public-url auf https://vetroforge.net/status/ setzen und Nginx/Certbot prüfen.";
        }
        if (normalized.contains("lootdrop")) {
            return ok ? "Lootdrop-Config vorhanden." : "lootdrop Sektion aus default config mergen.";
        }
        if (normalized.contains("shop")) {
            return ok ? "Shop-Kategorien sind geladen." : "shop.categories in config.yml prüfen.";
        }
        return ok ? "Kein Handlungsbedarf." : "Config und Serverlog prüfen.";
    }

    private ItemStack named(Material material, String name, List<String> lore, String doctorLabel) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            if (doctorLabel != null) {
                meta.getPersistentDataContainer().set(doctorKey, PersistentDataType.STRING, doctorLabel);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("doctor", "reload").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("doctor")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("gui").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
