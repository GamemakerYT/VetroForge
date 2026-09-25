package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class FactionUpgradeManager implements Listener {

    private static final int MAX_LEVEL = 5;

    private final FactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final NamespacedKey upgradeKey;

    public FactionUpgradeManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/faction-upgrades.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.upgradeKey = new NamespacedKey(plugin, "faction_upgrade");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public int getElo(String faction) {
        return faction == null ? 0 : Math.max(0, yaml.getInt(path(faction, "elo"), 0));
    }

    public void addElo(String faction, int amount) {
        if (faction == null || amount <= 0) return;
        yaml.set(path(faction, "elo"), getElo(faction) + amount);
        save();
    }

    public void setElo(String faction, int amount) {
        if (faction == null) return;
        yaml.set(path(faction, "elo"), Math.max(0, amount));
        save();
    }

    public int getTntLevel(String faction) {
        return level(faction, "tnt");
    }

    public int getObsidianLevel(String faction) {
        return level(faction, "obsidian");
    }

    public int getShardBankLevel(String faction) {
        return level(faction, "shard_bank");
    }

    public int applyShardBankCap(String faction, int baseCap) {
        return Math.max(1, baseCap + getShardBankLevel(faction) * 10);
    }

    public int applyTntDamage(String attackerFaction, int baseDamage) {
        return Math.max(1, baseDamage + getTntLevel(attackerFaction) * 20);
    }

    public int applyObsidianHealth(String defenderFaction, int baseHealth) {
        return Math.max(1, baseHealth + getObsidianLevel(defenderFaction) * 100);
    }

    public void open(Player player) {
        String faction = factionOf(player);
        if (faction == null) {
            player.sendMessage(tr(player, "no_faction"));
            return;
        }

        Inventory inventory = Bukkit.createInventory(new Holder(), 36, tr(player, "title"));
        VetroForgeUtil.fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, item(Material.NETHER_STAR, tr(player, "elo.name"),
                tr(player, "elo.lore", "elo", getElo(faction))));
        inventory.setItem(11, upgrade(player, faction, "tnt", Material.TNT));
        inventory.setItem(13, upgrade(player, faction, "shard_bank", Material.AMETHYST_CLUSTER));
        inventory.setItem(15, upgrade(player, faction, "obsidian", Material.OBSIDIAN));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String upgrade = clicked.getItemMeta().getPersistentDataContainer().get(upgradeKey, PersistentDataType.STRING);
        if (upgrade == null) return;

        String faction = factionOf(player);
        if (faction == null || !plugin.getFactionManager().isLeader(player.getUniqueId())) {
            player.sendMessage(tr(player, "leader_only"));
            return;
        }

        int current = level(faction, upgrade);
        if (current >= MAX_LEVEL) {
            player.sendMessage(tr(player, "max"));
            return;
        }

        int cost = cost(current + 1);
        if (getElo(faction) < cost) {
            player.sendMessage(tr(player, "not_enough", "cost", cost));
            return;
        }

        yaml.set(path(faction, "elo"), getElo(faction) - cost);
        yaml.set(path(faction, "upgrades." + upgrade), current + 1);
        save();
        playUpgradeFeedback(player, upgrade, current + 1);
        player.sendMessage(tr(player, "purchased", "upgrade", tr(player, upgrade + ".name"), "level", current + 1));
        open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private ItemStack upgrade(Player player, String faction, String id, Material material) {
        int current = level(faction, id);
        int value = switch (id) {
            case "tnt" -> current * 20;
            case "obsidian" -> current * 100;
            case "shard_bank" -> current * 10;
            default -> current;
        };
        List<String> lore = new java.util.ArrayList<>();
        lore.add(tr(player, "level", "level", current, "max", MAX_LEVEL));
        lore.add(tr(player, id + ".effect", "value", value));
        if ("shard_bank".equals(id)) {
            if (plugin.getRaidCrystalManager() != null) {
                lore.add("§dFaction Bank §8» §f"
                        + plugin.getRaidCrystalManager().getAvailable(player)
                        + "§8/§f"
                        + plugin.getRaidCrystalManager().getAvailableCap(player));
            }
            if (current < MAX_LEVEL) lore.add("§dNächstes Upgrade §8» §f+10 Shard-Cap");
        }
        lore.add(current >= MAX_LEVEL ? tr(player, "max") : tr(player, "cost", "cost", cost(current + 1)));
        ItemStack item = item(material, tr(player, id + ".name"), lore.toArray(String[]::new));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private int level(String faction, String id) {
        return faction == null ? 0 : Math.max(0, Math.min(MAX_LEVEL, yaml.getInt(path(faction, "upgrades." + id), 0)));
    }

    private int cost(int level) {
        return 100 * Math.max(1, level);
    }

    private void playUpgradeFeedback(Player player, String upgrade, int level) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.25f);
        if (!"shard_bank".equals(upgrade)) return;

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.45f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.7f);
        player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0.0D, 1.2D, 0.0D),
                32,
                0.45D,
                0.55D,
                0.45D,
                0.03D
        );
        player.sendActionBar("§dShard-Bank §8• §fCap jetzt §d+" + (level * 10) + " §8über Basis");
    }

    private String factionOf(Player player) {
        return plugin.getFactionManager().getFactionOf(player.getUniqueId());
    }

    private String path(String faction, String suffix) {
        return "factions." + faction + "." + suffix;
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save faction upgrades: " + exception.getMessage());
        }
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "faction.upgrades." + key, VetroForgeUtil.vars(values));
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
