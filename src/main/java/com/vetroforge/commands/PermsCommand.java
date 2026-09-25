// src/main/java/com/vetroforge/commands/PermsCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.VetroPermsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * /vetroperm user <Spieler> set <Rank>
 */
public class PermsCommand implements CommandExecutor, Listener {
    private static final String TITLE = "§c§lVetroForge§8 » §fRanks";

    private final FactionsPlugin plugin;
    private final VetroPermsManager perms;

    public PermsCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.perms  = plugin.getVetroPermsManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage(UiDecor.noPermission());
            return true;
        }

        if (args.length == 0 && sender instanceof Player player) {
            openRankMenu(player, player);
            return true;
        }

        if (args.length == 2
                && "user".equalsIgnoreCase(args[0])
                && sender instanceof Player player) {
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cSpieler nicht gefunden (muss online sein).");
                return true;
            }
            openRankMenu(player, target);
            return true;
        }

        if (args.length != 4
                || !"user".equalsIgnoreCase(args[0])
                || !"set".equalsIgnoreCase(args[2])) {
            sender.sendMessage("§c§lVetroForge §8» §7/" + label + " §8| §7/" + label + " user <Spieler> §8| §7/" + label + " user <Spieler> set <Rank>");
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cSpieler nicht gefunden (muss online sein).");
            return true;
        }

        String rank = args[3];
        Set<String> valid = perms.getRankIds(); // <— statt direktem Feldzugriff
        if (!valid.contains(rank)) {
            String list = valid.stream().sorted().collect(Collectors.joining(", "));
            sender.sendMessage("§cUngültiger Rank. Verfügbar: §7" + list);
            return true;
        }

        perms.setRank(target, rank);
        sender.sendMessage("§aRank von §f" + target.getName() + " §awurde auf §e" + rank + " §agesetzt.");
        return true;
    }

    private void openRankMenu(Player viewer, Player target) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE + " §8• §f" + target.getName());
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "§8", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        List<String> ranks = new ArrayList<>(perms.getRankIds());
        ranks.sort(String::compareToIgnoreCase);
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        String current = perms.getRank(target);
        for (int i = 0; i < ranks.size() && i < slots.length; i++) {
            String rank = ranks.get(i);
            Material material = rank.equalsIgnoreCase(current) ? Material.REDSTONE_BLOCK : Material.PAPER;
            inventory.setItem(slots[i], item(material,
                    "§c§l" + rank,
                    List.of(
                            "§8────────────",
                            "§7Spieler §8» §f" + target.getName(),
                            "§7Aktuell §8» " + perms.getColor(target) + current,
                            "",
                            "§eKlicken zum Setzen"
                    )));
        }

        inventory.setItem(22, item(Material.BARRIER, "§cSchliessen", List.of("§7Menue verlassen")));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 22) {
            viewer.closeInventory();
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || item.getItemMeta().getDisplayName() == null) return;
        String rank = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (!perms.getRankIds().contains(rank)) return;
        String targetName = ChatColor.stripColor(title.substring(title.lastIndexOf("•") + 1)).trim();
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            viewer.sendMessage("§cSpieler nicht gefunden.");
            viewer.closeInventory();
            return;
        }
        perms.setRank(target, rank);
        viewer.sendMessage("§c§lVetroForge §8» §7Rank von §f" + target.getName() + " §7auf §c" + rank + " §7gesetzt.");
        openRankMenu(viewer, target);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }
}
