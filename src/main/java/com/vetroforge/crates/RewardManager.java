// ===================================================================
// File: src/main/java/com/vetroforge/crates/RewardManager.java
// ===================================================================
package com.vetroforge.crates;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hält alle möglichen Crate-Rewards, lädt/speichert optional YAML
 * und liefert gewichtete Ziehungen + Reel-Samples für die Roll-Animation.
 */
public class RewardManager {

    /** Einfaches Reward-Modell */
    public static final class Reward {
        public final String id;
        public final ItemStack displayBase;   // Basis-Item für Anzeige und Gewinn
        public final int minAmount;
        public final int maxAmount;
        public final double weight;           // relative Chance
        public final boolean broadcast;       // global announcen?
        public final List<String> commands;   // z.B. "lp user %player% parent add vip"

        public Reward(String id,
                      ItemStack displayBase,
                      int minAmount, int maxAmount,
                      double weight,
                      boolean broadcast,
                      List<String> commands) {
            this.id = id;
            this.displayBase = displayBase.clone();
            this.minAmount = Math.max(1, minAmount);
            this.maxAmount = Math.max(this.minAmount, maxAmount);
            this.weight = Math.max(0.0, weight);
            this.broadcast = broadcast;
            this.commands = commands == null ? Collections.<String>emptyList() : new ArrayList<String>(commands);
        }
    }

    private final FactionsPlugin plugin;
    private final Map<String, Reward> rewards = new LinkedHashMap<String, Reward>();
    private final NamespacedKey keyRewardId;

    // Speicher
    private final File file;

    public RewardManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.keyRewardId = new NamespacedKey(plugin, "crate_reward_id");
        this.file = new File(plugin.getDataFolder(), "data/crate-rewards.yml");
        loadOrSeed();
    }

    public NamespacedKey getRewardKey() { return keyRewardId; }

    /* ===================== Public API ===================== */

    public Collection<Reward> all() { return rewards.values(); }

    public Reward get(String id) { return rewards.get(id); }

    /** Zufällige gewichtete Ziehung */
    public Reward pickWeighted() {
        double total = 0.0;
        for (Reward r : rewards.values()) total += r.weight;
        if (total <= 0.0) return null;
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double run = 0.0;
        for (Reward r : rewards.values()) {
            run += r.weight;
            if (roll <= run) return r;
        }
        // fallback
        for (Reward r : rewards.values()) return r;
        return null;
    }

    /** Liefert eine Liste an Display-Items für die Roll-Animation. */
    public List<ItemStack> buildReelSamples(int count) {
        List<ItemStack> out = new ArrayList<ItemStack>(count);
        for (int i=0;i<count;i++) {
            Reward r = pickWeighted();
            if (r == null) break;
            out.add(makeDisplayForRoll(r));
        }
        return out;
    }

    /** Bereitet das finale Gewinn-Item mit korrekter Menge & Marker vor. */
    public ItemStack buildWinnings(Reward r) {
        int amount = r.minAmount == r.maxAmount
                ? r.minAmount
                : ThreadLocalRandom.current().nextInt(r.minAmount, r.maxAmount + 1);
        ItemStack it = r.displayBase.clone();
        it.setAmount(Math.max(1, Math.min(amount, it.getMaxStackSize())));
        tagRewardId(it, r.id);
        return it;
    }

    /** Liest Reward-ID aus einem Item, sofern vorhanden. */
    public String findRewardId(ItemStack it) {
        if (it == null) return null;
        ItemMeta im = it.getItemMeta();
        if (im == null) return null;
        if (!im.getPersistentDataContainer().has(keyRewardId, PersistentDataType.STRING)) return null;
        return im.getPersistentDataContainer().get(keyRewardId, PersistentDataType.STRING);
    }

    /* ===================== intern ===================== */

    private void tagRewardId(ItemStack it, String id) {
        ItemMeta im = it.getItemMeta();
        if (im == null) return;
        im.getPersistentDataContainer().set(keyRewardId, PersistentDataType.STRING, id);
        it.setItemMeta(im);
    }

    private ItemStack makeDisplayForRoll(Reward r) {
        ItemStack it = r.displayBase.clone();
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            if (!im.hasDisplayName()) {
                im.setDisplayName(ChatColor.GOLD + prettifyId(r.id));
            }
            List<String> lore = im.hasLore() ? new ArrayList<String>(im.getLore()) : new ArrayList<String>();
            lore.add(ChatColor.DARK_GRAY + "Chance: " + ChatColor.YELLOW + String.format(java.util.Locale.US, "%.1f%%", r.weight * 100.0));
            im.setLore(lore);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(im);
        }
        // kleiner „glow“-Effekt für seltene
        if (r.weight <= 0.25) {
            try { it.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1); } catch (Throwable ignored) {}
        }
        tagRewardId(it, r.id);
        return it;
    }

    private String prettifyId(String id) {
        return id.replace('_',' ').trim();
    }

    private void loadOrSeed() {
        if (!file.exists()) {
            seedDefaults();
            saveYaml();
        } else {
            loadYaml();
            if (rewards.isEmpty()) {
                seedDefaults();
                saveYaml();
            }
        }
    }

    private void seedDefaults() {
        rewards.clear();
        // Beispiel-Belohnungen
        add(new Reward("obsidian_x8", named(new ItemStack(Material.OBSIDIAN, 8), ChatColor.GRAY + "Obsidian ×8"),
                8, 8, 1.00, false, null));
        add(new Reward("tnt_x8", named(new ItemStack(Material.TNT, 8), ChatColor.RED + "TNT ×8"),
                8, 8, 0.80, false, null));
        add(new Reward("diamond_sword", named(new ItemStack(Material.DIAMOND_SWORD, 1), ChatColor.AQUA + "Diamond Sword"),
                1, 1, 0.60, true, null));
        add(new Reward("emerald_x16", named(new ItemStack(Material.EMERALD, 16), ChatColor.GREEN + "Emerald ×16"),
                8, 16, 0.90, false, null));
        // Crystal als Gewinn
        add(new Reward("crystal_x1", crystalReward("Legendary", ChatColor.GOLD + "" + ChatColor.BOLD + "Legendary Crystal"),
                1, 1, 0.35, false, null));
    }

    private void add(Reward r) { rewards.put(r.id, r); }

    private ItemStack named(ItemStack it, String name) {
        ItemMeta im = it.getItemMeta();
        if (im != null) { im.setDisplayName(name); it.setItemMeta(im); }
        return it;
    }

    private ItemStack crystalReward(String crateId, String name) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(
                    ChatColor.GRAY + "Dieser Legendary Crystal kann",
                    ChatColor.GRAY + "beim Crystal NPC eingelöst werden."
            ));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "vfkey"), PersistentDataType.STRING, crateId);
            item.setItemMeta(meta);
        }
        return item;
    }

    /* ===== YAML I/O (einfach, erweiterbar) ===== */

    private void saveYaml() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("rewards", null);
        List<Map<String,Object>> list = new ArrayList<Map<String,Object>>();
        for (Reward r : rewards.values()) {
            Map<String,Object> m = new LinkedHashMap<String,Object>();
            m.put("id", r.id);
            m.put("type", r.displayBase.getType().name());
            m.put("name", r.displayBase.getItemMeta() != null ? r.displayBase.getItemMeta().getDisplayName() : null);
            m.put("min", r.minAmount);
            m.put("max", r.maxAmount);
            m.put("weight", r.weight);
            m.put("broadcast", r.broadcast);
            m.put("commands", r.commands);
            list.add(m);
        }
        cfg.set("rewards", list);
        try { cfg.save(file); } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void loadYaml() {
        rewards.clear();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> list = cfg.getList("rewards");
        if (list == null) return;
        for (Object o : list) {
            if (!(o instanceof Map)) continue;
            Map<String,Object> m = (Map<String,Object>) o;
            String id = asStr(m.get("id"));
            String type = asStr(m.get("type"));
            String name = asStr(m.get("name"));
            int min = asInt(m.get("min"), 1);
            int max = asInt(m.get("max"), min);
            double weight = asDouble(m.get("weight"), 1.0);
            boolean broadcast = asBool(m.get("broadcast"), false);
            List<String> commands = new ArrayList<String>();
            Object cmdObj = m.get("commands");
            if (cmdObj instanceof List) {
                for (Object c : (List<?>) cmdObj) commands.add(String.valueOf(c));
            }
            Material mat = Material.matchMaterial(type);
            if (mat == null) continue;
            ItemStack it = new ItemStack(mat, 1);
            if (name != null && !name.isEmpty()) {
                ItemMeta im = it.getItemMeta();
                if (im != null) { im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name)); it.setItemMeta(im); }
            }
            add(new Reward(id, it, min, max, weight, broadcast, commands));
        }
    }

    private String asStr(Object o) { return o == null ? null : String.valueOf(o); }
    private int asInt(Object o, int def) {
        if (o instanceof Number) return ((Number)o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
    private double asDouble(Object o, double def) {
        if (o instanceof Number) return ((Number)o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }
    private boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean) return ((Boolean)o).booleanValue();
        String s = String.valueOf(o);
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return def;
    }
}
