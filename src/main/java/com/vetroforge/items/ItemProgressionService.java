package com.vetroforge.items;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemProgressionService implements Listener, CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final ItemAuditLog audit;
    private final File codexFile;
    private final YamlConfiguration codex;
    private final Map<UUID, List<ItemStack>> soulboundRespawns = new ConcurrentHashMap<>();
    private final NamespacedKey codexEntry;

    public ItemProgressionService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.audit = new ItemAuditLog(plugin);
        this.codexFile = new File(plugin.getDataFolder(), "data/item-codex.yml");
        this.codex = YamlConfiguration.loadConfiguration(codexFile);
        this.codexEntry = new NamespacedKey(plugin, "codex_entry");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "inspect" -> inspect(player);
            case "reroll" -> reroll(player);
            case "salvage" -> salvage(player);
            case "soulbind" -> soulbind(player);
            case "protect" -> protect(player);
            case "codex" -> codex(player, parseFilter(args), parsePage(args));
            default -> help(player);
        };
    }

    private boolean inspect(Player player) {
        ItemStack item = held(player);
        if (!isCustom(item)) return message(player, "§cHalte ein Custom-Item in der Hand.");
        player.sendMessage("§6Item Inspect §8» §f" + CustomItemUtil.baseId(plugin, item));
        player.sendMessage("§7Rarity: " + CustomItemUtil.rarityOf(plugin, item).color + CustomItemUtil.rarityOf(plugin, item).display);
        player.sendMessage("§7Power Score: §f" + CustomItemUtil.powerScore(plugin, item));
        player.sendMessage("§7Source: §f" + CustomItemUtil.sourceOf(plugin, item));
        player.sendMessage("§7Owner: §f" + CustomItemUtil.ownerOf(plugin, item));
        player.sendMessage("§7Soulbound: §f" + CustomItemUtil.isSoulbound(plugin, item));
        player.sendMessage("§7Trade-Lock: §f" + CustomItemUtil.isTradeLocked(plugin, item));
        return true;
    }

    private boolean reroll(Player player) {
        ItemStack item = held(player);
        if (!isCustom(item)) return message(player, "§cHalte ein Custom-Item in der Hand.");
        if (!consume(player, Material.AMETHYST_SHARD, 8)) return message(player, "§cDu brauchst 8 Amethyst Shards für einen Reroll.");
        CustomItemUtil.reroll(plugin, item);
        audit.write("REROLL", player, item, null);
        player.sendMessage("§6Item Forge §8» §aRolls neu ausgewürfelt. §7Power: §f" + CustomItemUtil.powerScore(plugin, item));
        return true;
    }

    private boolean salvage(Player player) {
        ItemStack item = held(player);
        if (!isCustom(item)) return message(player, "§cHalte ein Custom-Item in der Hand.");
        int shards = 2 + CustomItemUtil.rarityOf(plugin, item).ordinal() * 3 + Math.max(0, CustomItemUtil.powerScore(plugin, item) / 150);
        audit.write("SALVAGE", player, item, "shards=" + shards);
        player.getInventory().setItemInMainHand(null);
        player.getInventory().addItem(new ItemStack(Material.AMETHYST_SHARD, shards));
        player.sendMessage("§6Item Forge §8» §aItem zerlegt: §d+" + shards + " Amethyst Shards");
        return true;
    }

    private boolean soulbind(Player player) {
        ItemStack item = held(player);
        if (!isCustom(item)) return message(player, "§cHalte ein Custom-Item in der Hand.");
        if (!consume(player, Material.ECHO_SHARD, 1)) return message(player, "§cDu brauchst einen Echo Shard.");
        CustomItemUtil.bindSoulbound(plugin, item, player.getUniqueId());
        audit.write("SOULBIND", player, item, null);
        return message(player, "§dSoulbound §8» §aItem ist jetzt an dich gebunden.");
    }

    private boolean protect(Player player) {
        ItemStack item = held(player);
        if (!isCustom(item)) return message(player, "§cHalte ein Custom-Item in der Hand.");
        if (!consume(player, Material.NETHER_STAR, 1)) return message(player, "§cDu brauchst einen Nether Star.");
        CustomItemUtil.setUpgradeProtection(plugin, item, true);
        audit.write("PROTECT", player, item, null);
        return message(player, "§aUpgrade Protection §8» §fSchutz wurde aktiviert.");
    }

    private boolean codex(Player player, Rarity filter, int page) {
        Set<String> found = new TreeSet<>(codex.getStringList("players." + player.getUniqueId()));
        List<CustomItemCatalog.Entry> all = new ArrayList<>(CustomItemCatalog.all());
        if (filter != null) all.removeIf(entry -> rarity(entry) != filter);
        all.sort(Comparator
                .comparingInt((CustomItemCatalog.Entry entry) -> rarity(entry).ordinal()).reversed()
                .thenComparing(entry -> entry.display));
        int pageSize = 45;
        int maxPage = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        page = Math.max(1, Math.min(page, maxPage));
        Inventory inventory = Bukkit.createInventory(new CodexHolder(filter), 54,
                tr(player, "items.codex.title") + " §7(" + found.size() + "/" + all.size() + ") §8[" + page + "/" + maxPage + "]");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, named(Material.BLACK_STAINED_GLASS_PANE, " "));
        }
        int index = 0;
        for (CustomItemCatalog.Entry entry : all.subList((page - 1) * pageSize, Math.min(all.size(), page * pageSize))) {
            boolean discovered = found.contains(entry.id);
            Rarity rarity = rarity(entry);
            ItemStack icon = discovered
                    ? entry.factory.make(plugin, rarity)
                    : named(Material.GRAY_DYE, "§8✖ " + entry.display);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName((discovered ? rarity.color + "✔ " : "§8✖ ") + entry.display);
            meta.setLore(List.of(
                    "§7Rarity: " + rarity.color + rarity.display,
                    discovered ? tr(player, "items.codex.discovered") : tr(player, "items.codex.missing"),
                    "§8ID: " + entry.id,
                    discovered ? "§aKlicken: Vorschau" : "§8Noch nicht entdeckt"
            ));
            meta.getPersistentDataContainer().set(codexEntry, PersistentDataType.STRING, entry.id);
            icon.setItemMeta(meta);
            inventory.setItem(index++, icon);
        }
        if (page > 1) inventory.setItem(45, nav(Material.ARROW, "§e← Seite " + (page - 1), filter, page - 1));
        inventory.setItem(47, filter(filter));
        inventory.setItem(49, bestiary(player));
        if (page < maxPage) inventory.setItem(53, nav(Material.ARROW, "§eSeite " + (page + 1) + " →", filter, page + 1));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        return true;
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private boolean help(Player player) {
        player.sendMessage("§6/item inspect §8• §7Metadaten anzeigen");
        player.sendMessage("§6/item reroll §8• §7Rolls neu würfeln");
        player.sendMessage("§6/item salvage §8• §7Item zerlegen");
        player.sendMessage("§6/item soulbind §8• §7Item an dich binden");
        player.sendMessage("§6/item protect §8• §7Upgrade-Schutz aktivieren");
        player.sendMessage("§6/item codex §8• §7Sammlung anzeigen");
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) discover(event.getPlayer(), item);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (isCustom(item) && CustomItemUtil.isTradeLocked(plugin, item)) {
            String owner = CustomItemUtil.ownerOf(plugin, item);
            if (owner != null && !owner.equals(event.getPlayer().getUniqueId().toString())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cDieses Event-Item ist noch trade-locked.");
                return;
            }
        }
        discover(event.getPlayer(), item);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (CustomItemUtil.isSoulbound(plugin, item) || CustomItemUtil.isTradeLocked(plugin, item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDieses Item kann aktuell nicht gedroppt werden.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof CodexHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String id = clicked.getItemMeta().getPersistentDataContainer().get(codexEntry, PersistentDataType.STRING);
            if (id == null) return;
            if (id.startsWith("page:")) {
                try {
                    String[] parts = id.split(":", 3);
                    codex(player, rarity(parts[1]), Integer.parseInt(parts[2]));
                } catch (RuntimeException ignored) {}
                return;
            }
            if (id.startsWith("filter:")) {
                codex(player, rarity(id.substring("filter:".length())), 1);
                return;
            }
            CustomItemCatalog.Entry entry = CustomItemCatalog.all().stream()
                    .filter(candidate -> candidate.id.equalsIgnoreCase(id)).findFirst().orElse(null);
            if (entry == null) return;
            boolean discovered = codex.getStringList("players." + player.getUniqueId()).contains(entry.id);
            if (!discovered) {
                player.sendMessage(tr(player, "items.codex.missing"));
                return;
            }
            ItemStack preview = entry.factory.make(plugin, rarity(entry));
            player.sendMessage("§6Item Codex §8» " + rarity(entry).color + entry.display
                    + " §8• §7Power §f" + CustomItemUtil.powerScore(plugin, preview));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.15f);
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (!CustomItemUtil.isTradeLocked(plugin, item)) return;
        if (event.getClickedInventory() != null && event.getClickedInventory() != player.getInventory()) {
            event.setCancelled(true);
            player.sendMessage("§cDieses Event-Item ist noch trade-locked.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CodexHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        if (!isMysticArtifact(next)) return;
        ItemStack current = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isMysticArtifact(current) || isMysticArtifact(offhand)) {
            event.setCancelled(true);
            player.sendMessage("§dMystic Artifact §8» §cDu kannst nur ein Mystic-Artefakt gleichzeitig aktiv halten.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> keep = new ArrayList<>();
        event.getDrops().removeIf(item -> {
            if (!CustomItemUtil.isSoulbound(plugin, item)) return false;
            keep.add(item.clone());
            return true;
        });
        if (!keep.isEmpty()) soulboundRespawns.put(event.getEntity().getUniqueId(), keep);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> keep = soulboundRespawns.remove(event.getPlayer().getUniqueId());
        if (keep == null || keep.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin,
                () -> keep.forEach(item -> event.getPlayer().getInventory().addItem(item)));
    }

    public void discover(Player player, ItemStack item) {
        if (!isCustom(item)) return;
        bindEventOwner(player, item);
        String id = CustomItemUtil.baseId(plugin, item);
        String path = "players." + player.getUniqueId();
        Set<String> found = new TreeSet<>(codex.getStringList(path));
        if (!found.add(id)) return;
        codex.set(path, new ArrayList<>(found));
        saveCodex();
        player.sendMessage(tr(player, "items.codex.new", Map.of("item", id)));
    }

    public void audit(String action, Player player, ItemStack item, String details) {
        audit.write(action, player, item, details);
    }

    public void recordBestiaryKill(Player player, String mobId, boolean boss) {
        if (player == null || mobId == null || mobId.isBlank()) return;
        String root = "bestiary." + player.getUniqueId();
        String id = mobId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        codex.set(root + ".kills." + id, codex.getInt(root + ".kills." + id, 0) + 1);
        codex.set(root + ".total", codex.getInt(root + ".total", 0) + 1);
        if (boss) codex.set(root + ".bosses", codex.getInt(root + ".bosses", 0) + 1);
        saveCodex();
    }

    private void bindEventOwner(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(CustomItemUtil.KEY_EVENT(plugin), org.bukkit.persistence.PersistentDataType.STRING)) return;
        if (pdc.has(CustomItemUtil.KEY_OWNER(plugin), org.bukkit.persistence.PersistentDataType.STRING)) return;
        pdc.set(CustomItemUtil.KEY_OWNER(plugin), org.bukkit.persistence.PersistentDataType.STRING, player.getUniqueId().toString());
        item.setItemMeta(meta);
    }

    private boolean isMysticArtifact(ItemStack item) {
        return isCustom(item) && CustomItemUtil.rarityOf(plugin, item) == Rarity.MYSTIC
                && CustomItemUtil.armorSetOf(plugin, item) == null;
    }

    private Rarity rarity(CustomItemCatalog.Entry entry) {
        if (!entry.hasRarity) return entry.id.equalsIgnoreCase("dragons_breath") ? Rarity.LEGENDARY : Rarity.MYSTIC;
        try {
            return CustomItemUtil.rarityOf(plugin, entry.factory.make(plugin, Rarity.RARE));
        } catch (Throwable ignored) {
            return Rarity.RARE;
        }
    }

    private ItemStack nav(Material material, String name, Rarity filter, int page) {
        ItemStack item = named(material, name);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(codexEntry, PersistentDataType.STRING, "page:" + key(filter) + ":" + page);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filter(Rarity current) {
        Rarity next = current == null ? Rarity.COMMON
                : current == Rarity.MYSTIC ? null : Rarity.values()[current.ordinal() + 1];
        ItemStack item = named(Material.HOPPER, "§6Filter §8» " + (current == null ? "§fAlle" : current.color + current.display));
        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of("§7Klicken: nächster Raritätsfilter", "§8Alle • Common • Rare • Epic • Legendary • Mythic"));
        meta.getPersistentDataContainer().set(codexEntry, PersistentDataType.STRING, "filter:" + key(next));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack bestiary(Player player) {
        String root = "bestiary." + player.getUniqueId();
        var kills = codex.getConfigurationSection(root + ".kills");
        List<String> lore = new ArrayList<>();
        lore.add("§8Warzone Progress");
        lore.add("");
        lore.add("§7Kills: §f" + codex.getInt(root + ".total", 0));
        lore.add("§7Bosses: §c" + codex.getInt(root + ".bosses", 0));
        lore.add("");
        if (kills == null || kills.getKeys(false).isEmpty()) {
            lore.add("§8Noch keine Warzone-Mobs besiegt.");
        } else {
            kills.getKeys(false).stream()
                    .sorted(Comparator.comparingInt((String id) -> kills.getInt(id)).reversed())
                    .limit(6)
                    .forEach(id -> lore.add("§8• §f" + id.replace('_', ' ') + " §7x" + kills.getInt(id)));
        }
        ItemStack item = named(Material.WRITABLE_BOOK, "§c§lWarzone Bestiary");
        ItemMeta meta = item.getItemMeta();
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Rarity parseFilter(String[] args) {
        return args.length < 2 ? null : rarity(args[1]);
    }

    private Rarity rarity(String raw) {
        if (raw == null || raw.equalsIgnoreCase("all")) return null;
        if (raw.equalsIgnoreCase("mythic")) return Rarity.MYSTIC;
        try { return Rarity.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private String key(Rarity rarity) { return rarity == null ? "all" : rarity.name().toLowerCase(Locale.ROOT); }

    private int parsePage(String[] args) {
        if (args.length < 3) return 1;
        try {
            return Integer.parseInt(args[2]);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void saveCodex() {
        try {
            File parent = codexFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            codex.save(codexFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[ItemCodex] Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    private boolean consume(Player player, Material material, int amount) {
        if (!player.getInventory().containsAtLeast(new ItemStack(material), amount)) return false;
        player.getInventory().removeItem(new ItemStack(material, amount));
        return true;
    }

    private ItemStack held(Player player) { return player.getInventory().getItemInMainHand(); }
    private boolean isCustom(ItemStack item) { return CustomItemUtil.baseId(plugin, item) != null; }
    private boolean message(Player player, String text) { player.sendMessage(text); return true; }
    private String tr(Player player, String key) { return VetroForgeUtil.tr(plugin, player, key); }
    private String tr(Player player, String key, Map<String, String> vars) { return VetroForgeUtil.tr(plugin, player, key, vars); }

    private static final class CodexHolder implements InventoryHolder {
        private final Rarity filter;
        private CodexHolder(Rarity filter) { this.filter = filter; }
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("inspect", "reroll", "salvage", "soulbind", "protect", "codex").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
