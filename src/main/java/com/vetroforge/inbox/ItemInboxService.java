package com.vetroforge.inbox;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemInboxService implements Listener, CommandExecutor {

    private static final String TITLE = ChatColor.RED + "" + ChatColor.BOLD + "VetroForge" + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + "Item Inbox";

    private final FactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, List<ItemStack>> inbox = new ConcurrentHashMap<>();

    public ItemInboxService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/item-inbox.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public void giveOrStore(Player player, ItemStack item, String reason) {
        if (player == null || item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (leftover.isEmpty()) return;

        for (ItemStack stack : leftover.values()) {
            store(player.getUniqueId(), stack);
        }
            player.sendMessage(ChatColor.RED + "VetroForge " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.YELLOW + "Dein Inventar war voll. "
                + ChatColor.GRAY + "Nutze " + ChatColor.WHITE + "/iteminbox"
                + ChatColor.GRAY + " zum Abholen.");
        if (reason != null && !reason.isBlank()) {
            plugin.getLogger().info("[ItemInbox] Stored item for " + player.getName() + " reason=" + reason);
        }
    }

    public int size(UUID uuid) {
        return inbox.getOrDefault(uuid, List.of()).size();
    }

    /** Legt ein Item für einen (möglicherweise offline) Spieler in die Inbox, z.B. bei Admin-Eingriffen. */
    public void storeForOffline(UUID uuid, ItemStack item) {
        if (uuid == null || item == null || item.getType().isAir()) return;
        store(uuid, item);
    }

    private void store(UUID uuid, ItemStack item) {
        inbox.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(item.clone());
        save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur ingame.");
            return true;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        List<ItemStack> items = new ArrayList<>(inbox.getOrDefault(player.getUniqueId(), List.of()));
        Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
        ItemStack filler = filler();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        for (int i = 0; i < Math.min(45, items.size()); i++) {
            inventory.setItem(i, items.get(i).clone());
        }
        inventory.setItem(49, info(items.size()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot < 0 || slot >= 45) return;

        List<ItemStack> items = inbox.get(player.getUniqueId());
        if (items == null || slot >= items.size()) return;

        ItemStack stack = items.get(slot);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
        if (!leftover.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Dein Inventar ist voll.");
            return;
        }

        items.remove(slot);
        if (items.isEmpty()) inbox.remove(player.getUniqueId());
        save();
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        open(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        int amount = size(event.getPlayer().getUniqueId());
        if (amount <= 0) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            player.sendMessage(ChatColor.RED + "VetroForge " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.GRAY + "Du hast " + ChatColor.YELLOW + amount
                    + ChatColor.GRAY + " offene Items. " + ChatColor.WHITE + "/iteminbox");
        }, 40L);
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        item.editMeta(meta -> {
            meta.setDisplayName(ChatColor.DARK_GRAY + " ");
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private ItemStack info(int amount) {
        ItemStack item = new ItemStack(Material.CHEST);
        item.editMeta(meta -> {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "VetroForge " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + "Inbox");
            meta.setLore(List.of(
                    ChatColor.DARK_GRAY + "────────────",
                    ChatColor.GRAY + "Offene Items " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + amount,
                    ChatColor.GRAY + "Klick auf ein Item zum Abholen."
            ));
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!data.isConfigurationSection("players")) return;
        for (String rawUuid : data.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                List<?> raw = data.getList("players." + rawUuid + ".items", List.of());
                List<ItemStack> items = new ArrayList<>();
                for (Object entry : raw) {
                    if (entry instanceof ItemStack stack) items.add(stack.clone());
                }
                if (!items.isEmpty()) inbox.put(uuid, items);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        data.set("players", null);
        for (Map.Entry<UUID, List<ItemStack>> entry : inbox.entrySet()) {
            data.set("players." + entry.getKey() + ".items", entry.getValue());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("ItemInbox konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }
}
