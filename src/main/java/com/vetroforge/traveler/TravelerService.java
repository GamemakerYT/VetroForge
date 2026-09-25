package com.vetroforge.traveler;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import com.vetroforge.lootdrop.LootItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public final class TravelerService implements Listener {

    private static final String TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Reisender Händler";

    private final FactionsPlugin plugin;
    private final NamespacedKey npcKey;
    private final NamespacedKey offerKey;
    private UUID spawnedId;
    private BukkitTask task;

    public TravelerService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "traveler_npc");
        this.offerKey = new NamespacedKey(plugin, "traveler_offer");
    }

    public void start() {
        stop();
        spawnCurrent();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnCurrent, 20L * 60L, 20L * 60L * 5L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        removeSpawned();
    }

    public boolean setSpot(String id, Location location) {
        if (id == null || id.isBlank() || location == null || location.getWorld() == null) return false;
        String clean = cleanId(id);
        String path = "traveler.spots." + clean;
        plugin.getConfig().set(path + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", location.getYaw());
        plugin.getConfig().set(path + ".pitch", location.getPitch());
        plugin.saveConfig();
        spawnCurrent();
        return true;
    }

    public boolean removeSpot(String id) {
        String clean = cleanId(id);
        if (!plugin.getConfig().isConfigurationSection("traveler.spots." + clean)) return false;
        plugin.getConfig().set("traveler.spots." + clean, null);
        plugin.saveConfig();
        spawnCurrent();
        return true;
    }

    public List<String> spotLines() {
        List<String> ids = spotIds();
        if (ids.isEmpty()) return List.of(ChatColor.YELLOW + "Keine Traveler-Spots gesetzt.");
        String active = activeSpotId();
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.LIGHT_PURPLE + "Traveler-Spots:");
        for (String id : ids) {
            Location location = spot(id);
            String marker = id.equals(active) ? ChatColor.GREEN + "aktiv " : ChatColor.DARK_GRAY + "- ";
            lines.add(marker + ChatColor.WHITE + id + ChatColor.GRAY + " @ " + format(location));
        }
        return lines;
    }

    public List<String> spotIdsView() {
        return new ArrayList<>(spotIds());
    }

    public String activeInfo() {
        String active = activeSpotId();
        if (active == null) return ChatColor.YELLOW + "Kein aktiver Traveler-Spot vorhanden.";
        return ChatColor.LIGHT_PURPLE + "Aktiver Traveler: " + ChatColor.WHITE + active
                + ChatColor.GRAY + " @ " + format(spot(active))
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + rotationText();
    }

    public void spawnCurrent() {
        String active = activeSpotId();
        if (active == null) {
            removeSpawned();
            return;
        }
        Location location = spot(active);
        if (location == null || location.getWorld() == null) {
            removeSpawned();
            return;
        }
        Entity existing = spawnedId == null ? null : Bukkit.getEntity(spawnedId);
        if (existing instanceof WanderingTrader trader && existing.isValid()
                && existing.getWorld().equals(location.getWorld())
                && existing.getLocation().distanceSquared(location) < 4.0D) {
            style(trader);
            return;
        }

        removeSpawned();
        WanderingTrader trader = (WanderingTrader) location.getWorld().spawnEntity(location, EntityType.WANDERING_TRADER);
        spawnedId = trader.getUniqueId();
        style(trader);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isTraveler(event.getRightClicked())) return;
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(offerKey, PersistentDataType.STRING);
        if (id == null) return;

        Offer offer = offers().stream().filter(o -> o.id.equals(id)).findFirst().orElse(null);
        if (offer == null) return;
        if (plugin.getCoinsManager() == null) {
            player.sendMessage(ChatColor.RED + "Coins-System ist gerade nicht verfügbar.");
            return;
        }
        int coins = plugin.getCoinsManager().getCoins(player.getUniqueId());
        if (coins < 0) {
            player.sendMessage(ChatColor.YELLOW + "Deine Coins werden noch geladen.");
            return;
        }
        if (coins < offer.price) {
            player.sendMessage(ChatColor.RED + "Nicht genug Coins. Benötigt: " + offer.price);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        plugin.getCoinsManager().removeCoins(player.getUniqueId(), offer.price);
        ItemStack reward = offer.reward.get();
        if (plugin.getItemInboxService() != null) {
            plugin.getItemInboxService().giveOrStore(player, reward, "traveler:" + offer.id);
            if ("raid_pack".equals(offer.id)) {
                plugin.getItemInboxService().giveOrStore(player, new ItemStack(Material.OBSIDIAN, 64), "traveler:" + offer.id);
            }
        } else {
            player.getInventory().addItem(reward);
            if ("raid_pack".equals(offer.id)) {
                player.getInventory().addItem(new ItemStack(Material.OBSIDIAN, 64));
            }
        }
        player.sendMessage(ChatColor.GREEN + "Gekauft: " + ChatColor.WHITE + strip(offer.name)
                + ChatColor.GRAY + " für " + ChatColor.YELLOW + offer.price + " Coins");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WANDERING_TRADER_YES, 0.9f, 1.1f);
        open(player);
    }

    private void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        ItemStack filler = item(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ", List.of(), null);
        ItemStack accent = item(Material.MAGENTA_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ", List.of(), null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        for (int slot : new int[]{0, 1, 7, 8, 18, 19, 25, 26}) inv.setItem(slot, accent);
        int balance = coins(player);
        inv.setItem(4, item(Material.WANDERING_TRADER_SPAWN_EGG, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Reisender Händler",
                List.of(
                        ChatColor.DARK_GRAY + "Seltene Wochenware",
                        "",
                        ChatColor.GRAY + "Aktiver Spot: " + ChatColor.WHITE + nullToDash(activeSpotId()),
                        ChatColor.GRAY + "Deine Coins: " + ChatColor.YELLOW + balance,
                        ChatColor.GRAY + rotationText(),
                        "",
                        ChatColor.YELLOW + "Der Händler wechselt jede Woche den Spot.",
                        ChatColor.DARK_GRAY + "Setz Spots mit /admintraveler set <id>."
                ), null));

        int[] slots = {10, 12, 14, 16};
        List<Offer> offers = offers();
        for (int i = 0; i < Math.min(slots.length, offers.size()); i++) {
            Offer offer = offers.get(i);
            boolean canBuy = balance >= offer.price;
            inv.setItem(slots[i], item(offer.icon, offer.name, offerLore(offer, balance, canBuy), offer.id));
        }
        player.openInventory(inv);
    }

    private List<String> offerLore(Offer offer, int balance, boolean canBuy) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + offer.description);
        lore.add("");
        lore.add(ChatColor.GRAY + "Preis: " + ChatColor.YELLOW + offer.price + " Coins");
        lore.add(ChatColor.GRAY + "Du hast: " + ChatColor.YELLOW + Math.max(0, balance) + " Coins");
        lore.add("");
        if (canBuy) {
            lore.add(ChatColor.GREEN + "Klicke zum Kaufen.");
        } else {
            lore.add(ChatColor.RED + "Dir fehlen " + (offer.price - Math.max(0, balance)) + " Coins.");
        }
        lore.add(ChatColor.DARK_GRAY + "Traveler-Angebot: " + offer.id);
        return lore;
    }

    private List<Offer> offers() {
        List<Offer> pool = new ArrayList<>(List.of(
                new Offer("legendary_crystal", Material.NETHER_STAR,
                        ChatColor.GOLD + "" + ChatColor.BOLD + "Legendary Crystal",
                        "Starker Crystal für den Crystal NPC.",
                        plugin.getConfig().getInt("traveler.prices.legendary-crystal", 55_000),
                        () -> plugin.getKeyCrateManager().createKeyItem(Rarity.LEGENDARY, 1)),
                new Offer("dragon_breath", Material.BLAZE_ROD,
                        ChatColor.GOLD + "" + ChatColor.BOLD + "DragonsBreath",
                        "Seltenes LootDrop-Item aus der Warzone.",
                        plugin.getConfig().getInt("traveler.prices.dragonsbreath", 125_000),
                        () -> LootItemFactory.dragonsBreath(plugin)),
                new Offer("event_relic", Material.ECHO_SHARD,
                        ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Event Relic",
                        "Seltene Event-Beute aus Lootdrops.",
                        plugin.getConfig().getInt("traveler.prices.event-relic", 90_000),
                        () -> LootItemFactory.eventItem(plugin)),
                new Offer("legendary_book", Material.ENCHANTED_BOOK,
                        ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Legendäres Enchant-Book",
                        "Für spätere Forge-Fusionen und starke Sets.",
                        plugin.getConfig().getInt("traveler.prices.legendary-book", 35_000),
                        () -> LootItemFactory.customEnchantBook(plugin, Rarity.LEGENDARY)),
                new Offer("epic_crystal_bundle", Material.NETHER_STAR,
                        ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "2x Epic Crystal",
                        "Zwei Epic Crystals für den Crystal NPC.",
                        plugin.getConfig().getInt("traveler.prices.epic-crystal-bundle", 42_000),
                        () -> plugin.getKeyCrateManager().createKeyItem(Rarity.EPIC, 2)),
                new Offer("raid_pack", Material.TNT,
                        ChatColor.RED + "" + ChatColor.BOLD + "Raid Vorrat",
                        "64 TNT und 64 Obsidian als Vorbereitung.",
                        plugin.getConfig().getInt("traveler.prices.raid-pack", 18_000),
                        this::raidPack)
        ));
        Collections.rotate(pool, -Math.floorMod(currentWeek() - 1, pool.size()));
        return pool.subList(0, Math.min(4, pool.size()));
    }

    private ItemStack raidPack() {
        ItemStack item = new ItemStack(Material.TNT, 64);
        item.editMeta(meta -> {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Raid Vorrat");
            meta.setLore(List.of(ChatColor.GRAY + "64 TNT. Obsidian-Pack folgt als separates Angebot."));
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private void style(WanderingTrader trader) {
        trader.setAI(false);
        trader.setInvulnerable(true);
        trader.setPersistent(true);
        trader.setRemoveWhenFarAway(false);
        trader.setCustomName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Reisender Händler");
        trader.setCustomNameVisible(true);
        trader.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isTraveler(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    private void removeSpawned() {
        if (!Bukkit.isPrimaryThread()) {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, this::removeSpawned);
            }
            return;
        }
        if (spawnedId != null) {
            Entity entity = Bukkit.getEntity(spawnedId);
            if (entity != null) entity.remove();
            spawnedId = null;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(WanderingTrader.class)) {
                if (isTraveler(entity)) entity.remove();
            }
        }
    }

    private List<String> spotIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("traveler.spots");
        if (section == null) return List.of();
        return section.getKeys(false).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String activeSpotId() {
        List<String> ids = spotIds();
        if (ids.isEmpty()) return null;
        int week = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return ids.get(Math.floorMod(week - 1, ids.size()));
    }

    private Location spot(String id) {
        String path = "traveler.spots." + cleanId(id);
        World world = Bukkit.getWorld(plugin.getConfig().getString(path + ".world", ""));
        if (world == null) return null;
        return new Location(
                world,
                plugin.getConfig().getDouble(path + ".x"),
                plugin.getConfig().getDouble(path + ".y"),
                plugin.getConfig().getDouble(path + ".z"),
                (float) plugin.getConfig().getDouble(path + ".yaw"),
                (float) plugin.getConfig().getDouble(path + ".pitch")
        );
    }

    private ItemStack item(Material material, String name, List<String> lore, String offerId) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            if (offerId != null) meta.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, offerId);
        });
        return item;
    }

    private String cleanId(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private String format(Location location) {
        if (location == null || location.getWorld() == null) return "unbekannt";
        return location.getWorld().getName() + " "
                + location.getBlockX() + " "
                + location.getBlockY() + " "
                + location.getBlockZ();
    }

    private int coins(Player player) {
        return plugin.getCoinsManager() == null ? 0 : plugin.getCoinsManager().getCoins(player.getUniqueId());
    }

    private String nullToDash(String value) {
        return value == null ? "-" : value;
    }

    private int currentWeek() {
        return LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private String rotationText() {
        LocalDate today = LocalDate.now();
        LocalDate nextMonday = today.plusDays(Math.floorMod(8 - today.getDayOfWeek().getValue(), 7));
        if (nextMonday.equals(today)) nextMonday = today.plusDays(7);
        return "Nächste Rotation: " + nextMonday;
    }

    private String strip(String value) {
        return ChatColor.stripColor(value == null ? "" : value);
    }

    private record Offer(String id, Material icon, String name, String description, int price, Supplier<ItemStack> reward) {
    }
}
