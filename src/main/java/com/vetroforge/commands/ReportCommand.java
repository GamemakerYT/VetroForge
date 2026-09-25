// src/main/java/com/vetroforge/commands/ReportCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.notify.NotifyManager;
import com.vetroforge.punish.PunishmentManager;
import com.vetroforge.util.GradientUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ReportCommand implements CommandExecutor, TabCompleter, Listener {

    private final FactionsPlugin plugin;
    private final NotifyManager notify;

    public ReportCommand(FactionsPlugin plugin, NotifyManager notify){
        this.plugin = plugin;
        this.notify = notify;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length < 1) {
            s.sendMessage(GradientUtil.header("§e/ " + l, "§7"));
            s.sendMessage("§7Nutzung: §f/" + l + " <Spieler> [GrundIndex|Grund…]");
            s.sendMessage("§7Beispiele:");
            s.sendMessage(" §8• §f/" + l + " Steve");
            s.sendMessage(" §8• §f/" + l + " Steve cheating");
            s.sendMessage("§7Siehe Gründe: §f/reasons report");
            s.sendMessage("§8——————————————");
            return true;
        }

        String targetName = a[0];
        if (a.length == 1 && s instanceof Player player) {
            openReportGui(player, targetName);
            return true;
        }
        if (a.length == 1) {
            s.sendMessage(ChatColor.RED + "Konsole: /" + l + " <Spieler> <Grund>");
            return true;
        }

        // Sprache ermitteln (fallback "de")
        String lang = "de";
        try {
            if (s instanceof Player p) {
                lang = plugin.getLanguageManager().getPlayerLangKey(p);
            }
        } catch (Throwable ignored) {}

        // Grund: Index oder Freitext
        String reason;
        Integer idx = tryParseInt(a[1]);
        if (idx != null && plugin.getPunishmentManager() != null) {
            String picked = plugin.getPunishmentManager()
                    .getReasonByIndex(PunishmentManager.Type.REPORT, lang, idx);
            if (picked == null) {
                s.sendMessage(ChatColor.RED + "Ungültiger Grund-Index. Nutze §e/reasons report§c.");
                return true;
            }
            reason = picked;
        } else {
            reason = join(a, 1);
        }

        submitReport(s, targetName, reason);
        return true;
    }

    private void submitReport(CommandSender sender, String targetName, String reason) {
        if (sender instanceof Player player && submitToCore(player, targetName, reason)) {
            sender.sendMessage(ChatColor.GREEN + "Danke! Dein Report gegen "
                    + ChatColor.WHITE + targetName + ChatColor.GREEN + " wurde an das Team gesendet.");
            return;
        }

        try { plugin.getReportService().recordReport(targetName); } catch (Throwable ignored) {}

        try {
            plugin.getPunishmentHistory().logReport(
                    Bukkit.getOfflinePlayer(targetName).getUniqueId(),
                    reason,
                    sender instanceof Player p ? p.getUniqueId() : null,
                    System.currentTimeMillis()
            );
        } catch (Throwable ignored) {}

        String head = GradientUtil.header("§cReport", "§7");
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean wants = false;
            try { wants = notifyManager().isEnabled(p.getUniqueId()); } catch (Throwable ignored) {}
            if (wants || p.hasPermission("vetroforge.staff.notify")) {
                p.sendMessage(head);
                p.sendMessage("§7Von:   §f" + sender.getName());
                p.sendMessage("§7Gegen: §f" + targetName);
                p.sendMessage("§7Grund: §f" + reason);
                p.sendMessage("§8——————————————");
            }
        }
        try {
            notifyManager().broadcast("reports",
                    "&cReport &8• &7Von: &f" + sender.getName() + " &8• &7Gegen: &f" + targetName + " &8• &7Grund: &f" + reason,
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
                    "vetroforge.staff.notify");
        } catch (Throwable ignored) {}

        sender.sendMessage(ChatColor.GREEN + "Danke! Dein Report gegen "
                + ChatColor.WHITE + targetName + ChatColor.GREEN + " wurde an das Team gesendet.");
    }

    private boolean submitToCore(Player reporter, String targetName, String reason) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("SUBMIT_REPORT");
            out.writeUTF(reporter.getUniqueId().toString());
            out.writeUTF(reporter.getName());
            out.writeUTF(targetName);
            out.writeUTF(reason);
            reporter.sendPluginMessage(plugin, com.vetroforge.staff.StaffGuiService.CHANNEL, bytes.toByteArray());
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Report] Core-Submit fehlgeschlagen, nutze lokalen Fallback: " + throwable.getMessage());
            return false;
        }
    }

    private void openReportGui(Player player, String targetName) {
        Inventory inv = Bukkit.createInventory(new ReportHolder(targetName, null), 27, "§4§lVetroForge §8• §cReport");
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(4, item(Material.PLAYER_HEAD, "§cReport: §f" + targetName, List.of("§7Waehle einen Grund.")));
        inv.setItem(10, item(Material.DIAMOND_SWORD, "§cCheating", List.of("§7KillAura, Reach, Fly, Speed", "§7oder andere Hacks.")));
        inv.setItem(11, item(Material.ENDER_EYE, "§dChest ESP", List.of("§7Verdacht auf Chest-/Base-ESP.")));
        inv.setItem(12, item(Material.OAK_SIGN, "§eBeleidigung", List.of("§7Toxic Chat, Hass, Provokation.")));
        inv.setItem(14, item(Material.TNT, "§6Griefing", List.of("§7Nur sinnvoll bei eigenen", "§7Faction-Mitgliedern.")));
        inv.setItem(15, item(Material.GOLD_INGOT, "§6Scamming", List.of("§7Betrug, Diebstahl, Trade-Abuse.")));
        inv.setItem(16, item(Material.BARRIER, "§cSonstiges", List.of("§7Team schaut manuell drauf.")));
        player.openInventory(inv);
    }

    /* ---------- Helpers ---------- */

    private Integer tryParseInt(String s){
        try { return Integer.valueOf(Integer.parseInt(s)); }
        catch (Exception e){ return null; }
    }

    private String join(String[] arr, int start){
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < arr.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ReportHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (holder.confirmReason != null) {
            if (clicked.getType() == Material.LIME_CONCRETE) {
                player.closeInventory();
                submitReport(player, holder.targetName, holder.confirmReason);
            } else if (clicked.getType() == Material.RED_CONCRETE) {
                openReportGui(player, holder.targetName);
            }
            return;
        }
        String reason = switch (clicked.getType()) {
            case DIAMOND_SWORD -> "Cheating";
            case ENDER_EYE -> "Chest ESP";
            case OAK_SIGN -> "Beleidigung";
            case TNT -> "Griefing";
            case GOLD_INGOT -> "Scamming";
            case BARRIER -> "Sonstiges";
            default -> null;
        };
        if (reason == null) return;
        openConfirmGui(player, holder.targetName, reason);
    }

    private void openConfirmGui(Player player, String targetName, String reason) {
        Inventory inv = Bukkit.createInventory(new ReportHolder(targetName, reason), 27, "§4§lVetroForge §8• §cConfirm");
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(4, item(Material.PLAYER_HEAD, "§cReport: §f" + targetName, List.of(
                "§7Grund: §f" + reason,
                "",
                "§eBitte bestaetigen."
        )));
        inv.setItem(11, item(Material.LIME_CONCRETE, "§a§lBestaetigen", List.of(
                "§7Report gegen §f" + targetName,
                "§7mit Grund §f" + reason + "§7 senden."
        )));
        inv.setItem(15, item(Material.RED_CONCRETE, "§cZurueck", List.of(
                "§7Grund nochmal auswaehlen."
        )));
        player.openInventory(inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ReportHolder) event.setCancelled(true);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private NotifyManager notifyManager() {
        return notify != null ? notify : plugin.getNotifyManager();
    }

    private static final class ReportHolder implements InventoryHolder {
        private final String targetName;
        private final String confirmReason;
        private ReportHolder(String targetName, String confirmReason) {
            this.targetName = targetName;
            this.confirmReason = confirmReason;
        }
        @Override public Inventory getInventory() { return null; }
    }

    /* ---------- Tab Complete ---------- */

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            String pref = a[0].toLowerCase(Locale.ROOT);
            for (Player pl : Bukkit.getOnlinePlayers()) {
                String name = pl.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(pref)) out.add(name);
            }
            return out;
        }
        if (a.length == 2) {
            // Kleine Starthilfe: Beispiel-Indizes/Gründe
            for (String opt : new String[]{"1","2","3","cheating","teaming","spam"}) {
                if (opt.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(opt);
            }
            return out;
        }
        return out;
    }
}
