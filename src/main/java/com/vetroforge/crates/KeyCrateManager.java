// src/main/java/com/vetroforge/crates/KeyCrateManager.java
package com.vetroforge.crates;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import com.vetroforge.lootdrop.LootItemFactory;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Crate-/Key-Manager:
 * - Per-Crate Rollen aus config: crates.<id>.rewards (openRollGui)
 * - Globales Rollen via RewardManager (openCrate)
 * - Key-Erzeugung per PDC-Tag "vfkey"
 */
public class KeyCrateManager implements Listener {

    // PDC Keys
    private static final String TAG_REWARD = "vfcrate_reward"; // Marker auf Reel-Items (crateId:index)
    private static final String TAG_KEY    = "vfkey";           // Marker auf Key-Items (crateId)
    private static final String TITLE_FUSION = ChatColor.AQUA + "✦ " + ChatColor.WHITE + "Key Fusion" + ChatColor.AQUA + " ✦";

    private final FactionsPlugin plugin;
    private final RollGui rollGui;
    private final RewardManager rewardManager;
    private final CrateHistoryService history;
    private final Random rnd = new Random();

    private final NamespacedKey KEY_REWARD_TAG;
    private final NamespacedKey KEY_KEY_ID;

    public KeyCrateManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.rollGui = new RollGui(plugin);
        this.rewardManager = new RewardManager(plugin);
        this.history = new CrateHistoryService(plugin);
        this.KEY_REWARD_TAG = new NamespacedKey(plugin, TAG_REWARD);
        this.KEY_KEY_ID     = new NamespacedKey(plugin, TAG_KEY);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Zugriff für /reward Command */
    public RewardManager getRewardManager() { return rewardManager; }
    public CrateHistoryService getHistory() { return history; }

    /** Alle Crate-IDs aus der Config. */
    public Set<String> getCrateIds() {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection cs = cfg.getConfigurationSection("crates");
        if (cs == null) return Collections.emptySet();
        Set<String> ids = new LinkedHashSet<>();
        for (String id : cs.getKeys(false)) {
            if (cfg.isList("crates." + id + ".rewards")
                    && !cfg.getBoolean("crates." + id + ".lootdropOnly", false)) ids.add(id);
        }
        return ids;
    }

    /* =======================================================================
       Globale Crate (über RewardManager) – z.B. für NPC oder simple /key open
       ======================================================================= */

    /** Öffnet die animierte Roll-GUI und vergibt am Ende einen Reward aus dem RewardManager. */
    public void openCrate(Player p) {
        if (p == null || !p.isOnline()) return;

        // ein paar Samples für’s Reel
        List<ItemStack> reel = rewardManager.buildReelSamples(14);
        if (reel == null || reel.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Keine Rewards definiert.");
            return;
        }

        rollGui.start(p, reel, new RollGui.RollResult() {
            @Override public void onFinish(ItemStack finalItem) {
                try {
                    if (finalItem == null) { p.sendMessage(ChatColor.RED + "Kein Gewinn ermittelt."); return; }
                    String id = rewardManager.findRewardId(finalItem);
                    if (id == null) { p.sendMessage(ChatColor.RED + "Konnte Reward nicht auflösen."); return; }
                    RewardManager.Reward r = rewardManager.get(id);
                    if (r == null) { p.sendMessage(ChatColor.RED + "Unbekannter Reward: " + id); return; }

                    ItemStack win = rewardManager.buildWinnings(r);
                    giveToInventoryOrDrop(p, win);

                    // Broadcast optional
                    if (r.broadcast) {
                        Bukkit.broadcastMessage(ChatColor.GOLD + p.getName()
                                + ChatColor.GRAY + " hat gewonnen: "
                                + ChatColor.YELLOW + displayNameOf(win));
                    }

                    // optionale Commands
                    if (r.commands != null) {
                        for (String raw : r.commands) {
                            String cmd = raw.replace("%player%", p.getName()).replace("{PLAYER}", p.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        }
                    }
                    p.sendMessage(ChatColor.GREEN + "Gewonnen: " + ChatColor.WHITE + displayNameOf(win));
                } catch (Throwable t) {
                    p.sendMessage(ChatColor.RED + "Fehler beim Auszahlen: " + t.getMessage());
                }
            }
            @Override public void onAbort() { /* GUI geschlossen – nichts tun */ }
        });
    }

    /* =======================================================================
       Crate-spezifisches Rollen aus der Config (crates.<id>.rewards)
       ======================================================================= */

    /** Öffnet animierte Roll-GUI für eine bestimmte Crate aus der config. */
    public void openRollGui(Player p, String crateId) {
        if (p == null || !p.isOnline()) return;
        if (plugin.getConfig().getBoolean("crates." + crateId + ".lootdropOnly", false)) {
            p.sendMessage(ChatColor.DARK_PURPLE + "Mystic " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.RED + "Diese Rewards gibt es nur aus Lootdrops.");
            return;
        }

        Rarity crateRarity = rarityForCrateId(crateId);
        if (crateRarity != null) {
            openRarityRollGui(p, crateId, crateRarity);
            return;
        }

        List<RewardCfg> rewards = loadRewardsFromConfig(crateId);
        if (rewards.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Diese Crate hat keine Rewards (config prüfen: crates." + crateId + ".rewards).");
            return;
        }

        // Reel bauen (mit PDC-Tag „crateId:index“)
        List<ItemStack> reel = new ArrayList<ItemStack>();
        int base = Math.max(7, rewards.size() * 2);
        for (int i = 0; i < base; i++) {
            RewardCfg r = rewards.get(i % rewards.size());
            reel.add(displayItem(crateId, i % rewards.size(), r));
        }
        Collections.shuffle(reel, rnd);

        rollGui.start(p, reel, new RollGui.RollResult() {
            @Override public void onFinish(ItemStack finalItem) {
                RewardCfg r = resolveRewardFromItem(crateId, finalItem, rewards);
                if (r == null) { p.sendMessage(ChatColor.RED + "Konnte Reward nicht auflösen."); return; }
                giveToInventoryOrDrop(p, r.item);
                history.record(p, crateId, r.item);
                announceMysticJackpot(p, crateId, r.item);
                // optionale Commands
                if (r.commands != null) {
                    for (String raw : r.commands) {
                        String cmd = raw.replace("{PLAYER}", p.getName()).replace("%player%", p.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                }
                p.sendMessage(ChatColor.GREEN + "Gewonnen: " + ChatColor.WHITE + displayNameOf(r.item));
            }
            @Override public void onAbort() { }
        });
    }

    private void openRarityRollGui(Player player, String crateId, Rarity rarity) {
        List<ItemStack> reel = buildRarityReel(player, rarity, 18);
        if (reel.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Diese Crystal-Rarity hat aktuell keine Rewards.");
            return;
        }

        rollGui.start(player, reel, new RollGui.RollResult() {
            @Override public void onFinish(ItemStack finalItem) {
                if (finalItem == null || finalItem.getType().isAir()) {
                    player.sendMessage(ChatColor.RED + "Kein Gewinn ermittelt.");
                    return;
                }

                ItemStack win = finalItem.clone();
                giveToInventoryOrDrop(player, win);
                history.record(player, crateId, win);
                announceMysticJackpot(player, crateId, win);

                player.sendMessage(ChatColor.GREEN + "Gewonnen: "
                        + ChatColor.WHITE + displayNameOf(win));
            }

            @Override public void onAbort() { }
        });
    }

    private List<ItemStack> buildRarityReel(Player player, Rarity rarity, int samples) {
        List<ItemStack> reel = new ArrayList<>();
        int attempts = Math.max(samples * 3, 24);
        for (int i = 0; i < attempts && reel.size() < samples; i++) {
            ItemStack item = LootItemFactory.rollFromCrate(plugin, rarity, player);
            if (item == null || item.getType().isAir()) continue;
            reel.add(item);
        }
        Collections.shuffle(reel, rnd);
        return reel;
    }

    public boolean redeemHeldKey(Player player) {
        if (player == null) return false;
        ItemStack held = player.getInventory().getItemInMainHand();
        String crateId = getCrateId(held);

        if (crateId == null && plugin.getCrateBridge() != null) {
            crateId = plugin.getCrateBridge().getCrateId(held);
        }

        if (crateId == null) {
            player.sendMessage(tr(player, "hold_key"));
            return false;
        }

        if (!isRedeemableCrate(crateId)) {
            player.sendMessage(ChatColor.GOLD + "Crystals " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.RED + "Dieser Crystal kann aktuell nicht am NPC geöffnet werden.");
            return false;
        }

        consumeOne(held);
        player.sendMessage(tr(player, "opening", "crate", crateId));
        openRollGui(player, crateId);
        return true;
    }

    public boolean isKeyItem(ItemStack item) {
        String crateId = getCrateId(item);
        if (crateId != null) return true;
        return plugin.getCrateBridge() != null && plugin.getCrateBridge().getCrateId(item) != null;
    }

    /* -------------------------------------------
       Key-Helfer (Erstellen/Verteilen)
       ------------------------------------------- */

    /** Baut ein Crystal-Item mit PDC-Tag "vfkey" → crateId. */
    public ItemStack createKeyItem(String crateId, int amount) {
        ItemStack key = new ItemStack(Material.NETHER_STAR, Math.max(1, Math.min(64, amount)));
        ItemMeta m = key.getItemMeta();
        Rarity rarity = rarityForCrateId(crateId);
        if (rarity == null) rarity = Rarity.COMMON;
        String display = crystalDisplayForCrate(crateId, rarity);
        String disp = plugin.getConfig().getString("crates." + crateId + ".keyName", rarity.color + "" + ChatColor.BOLD + display + " Crystal");
        if (m != null) {
            m.setDisplayName(ChatColor.translateAlternateColorCodes('&', disp));
            m.setLore(keyLore(null, display, crateId));
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            m.getPersistentDataContainer().set(KEY_KEY_ID, PersistentDataType.STRING, crateId);
            key.setItemMeta(m);
        }
        return key;
    }

    public ItemStack createKeyItem(int tier) {
        Rarity rarity = rarityForTier(tier);
        String crateId = crateIdFor(rarity);
        if (plugin.getCrateBridge() != null) {
            try { return plugin.getCrateBridge().makeKeyItem(rarity, crateId, 1); }
            catch (Throwable ignored) {}
        }
        return createKeyItem(crateId, 1);
    }

    public ItemStack createKeyItem(Rarity rarity, int amount) {
        Rarity safe = rarity == null ? Rarity.COMMON : rarity;
        String crateId = crateIdFor(safe);
        if (plugin.getCrateBridge() != null) {
            try { return plugin.getCrateBridge().makeKeyItem(safe, crateId, amount); }
            catch (Throwable ignored) {}
        }
        return createKeyItem(crateId, amount);
    }

    /** Gibt einem Spieler Keys für die Default-Crate (config: crates.default oder "basic"). */
    public void giveKey(Player p, int amount) {
        if (p == null) return;
        p.getInventory().addItem(createKeyItem(Rarity.COMMON, Math.max(1, amount)));
    }

    public void giveKey(Player player, Rarity rarity, int amount) {
        if (player == null) return;
        player.getInventory().addItem(createKeyItem(rarity, Math.max(1, amount)));
    }

    public boolean fuse(Player player) {
        openFusionGui(player);
        return true;
    }

    public void openFusionGui(Player player) {
        if (player == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_FUSION);
        fillIce(inv);

        inv.setItem(11, fusionButton(Material.NETHER_STAR,
                ChatColor.BLUE + "9 Common Crystals -> 1 Rare Crystal",
                List.of(
                        ChatColor.GRAY + "Du hast: " + ChatColor.WHITE + countKeys(player, Rarity.COMMON) + " Common Crystals",
                        ChatColor.GRAY + "Klicke zum Fusionieren."
                ),
                "common_to_rare"));
        inv.setItem(15, fusionButton(Material.NETHER_STAR,
                ChatColor.LIGHT_PURPLE + "5 Rare Crystals -> 1 Epic Crystal",
                List.of(
                        ChatColor.GRAY + "Du hast: " + ChatColor.WHITE + countKeys(player, Rarity.RARE) + " Rare Crystals",
                        ChatColor.GRAY + "Klicke zum Fusionieren."
                ),
                "rare_to_epic"));
        inv.setItem(4, fusionButton(Material.PACKED_ICE,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Crystal Fusion",
                List.of(ChatColor.GRAY + "Upgrade deine Crystals ohne Risiko."), "info"));
        player.openInventory(inv);
    }

    private boolean fuseCommonToRare(Player player) {
        if (player == null) return false;
        if (countKeys(player, Rarity.COMMON) >= 9) {
            consumeKeys(player, Rarity.COMMON, 9);
            giveKey(player, Rarity.RARE, 1);
            player.sendMessage(ChatColor.GREEN + "Crystal-Fusion: " + ChatColor.WHITE + "9x Common"
                    + ChatColor.GRAY + " -> " + ChatColor.BLUE + "1x Rare Crystal");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Nicht genug Common Crystals. Benötigt: 9.");
        return false;
    }

    private boolean fuseRareToEpic(Player player) {
        if (player == null) return false;
        if (countKeys(player, Rarity.RARE) >= 5) {
            consumeKeys(player, Rarity.RARE, 5);
            giveKey(player, Rarity.EPIC, 1);
            player.sendMessage(ChatColor.GREEN + "Crystal-Fusion: " + ChatColor.WHITE + "5x Rare"
                    + ChatColor.GRAY + " -> " + ChatColor.LIGHT_PURPLE + "1x Epic Crystal");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Nicht genug Rare Crystals. Benötigt: 5.");
        return false;
    }

    @EventHandler
    public void onFusionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!TITLE_FUSION.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "key_fusion_action"), PersistentDataType.STRING);
        if (action == null || action.equals("info")) return;
        boolean changed = switch (action) {
            case "common_to_rare" -> fuseCommonToRare(player);
            case "rare_to_epic" -> fuseRareToEpic(player);
            default -> false;
        };
        if (changed) openFusionGui(player);
    }

    /* =======================================================================
       Interne Helfer für crate-spezifische Rewards (aus config)
       ======================================================================= */

    /** kleines Modell für config-basierte Rewards */
    private static final class RewardCfg {
        final ItemStack item;
        final List<String> commands;
        RewardCfg(ItemStack item, List<String> commands) {
            this.item = item; this.commands = (commands == null ? Collections.<String>emptyList() : commands);
        }
    }

    private List<RewardCfg> loadRewardsFromConfig(String crateId) {
        String base = "crates." + crateId + ".rewards";
        List<Map<?, ?>> maps = plugin.getConfig().getMapList(base);
        List<RewardCfg> out = new ArrayList<RewardCfg>();
        if (maps != null) {
            for (Map<?, ?> m : maps) {
                RewardCfg r = mapToReward(m);
                if (r != null && isAboveCrateTier(crateId, r.item)) continue;
                if (r != null) out.add(r);
            }
        }
        // Fallback
        if (out.isEmpty()) {
            out.add(new RewardCfg(named(new ItemStack(Material.IRON_INGOT, 8), "&f8x Eisen"), Collections.<String>emptyList()));
            out.add(new RewardCfg(named(new ItemStack(Material.GOLD_INGOT, 6), "&e6x Gold"), Collections.<String>emptyList()));
            out.add(new RewardCfg(named(new ItemStack(Material.DIAMOND, 3), "&b3x Diamant"), Collections.<String>emptyList()));
        }
        return out;
    }

    private RewardCfg mapToReward(Map<?, ?> m) {
        try {
            String matName = asStr(m.containsKey("material") ? m.get("material") : m.get("item"), "STONE");
            int amount = asInt(m.get("amount"), 1);
            String name = asStr(m.get("name"), null);

            @SuppressWarnings("unchecked")
            List<String> cmds = (List<String>) (m.containsKey("commands") ? m.get("commands") : Collections.<String>emptyList());

            Material mat = Material.matchMaterial(matName);
            if (mat == null) mat = Material.STONE;
            ItemStack it = new ItemStack(mat, Math.max(1, amount));
            if (name != null && name.trim().length() > 0) it = named(it, name);
            return new RewardCfg(it, (cmds == null ? Collections.<String>emptyList() : cmds));
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isAboveCrateTier(String crateId, ItemStack item) {
        Rarity crate = rarityForCrateId(crateId);
        if (crate == null) crate = Rarity.COMMON;
        Rarity itemRarity = rarityOfReward(item);
        if (itemRarity == null) return false;
        if (itemRarity == Rarity.MYSTIC) return true;
        return itemRarity.ordinal() > crate.ordinal();
    }

    private Rarity rarityOfReward(ItemStack item) {
        if (item == null) return null;
        try {
            Rarity rarity = com.vetroforge.items.CustomItemUtil.rarityOf(plugin, item);
            if (rarity != null && rarity != Rarity.COMMON) return rarity;
        } catch (Throwable ignored) {}
        String text = "";
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) text += " " + ChatColor.stripColor(meta.getDisplayName());
            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) text += " " + ChatColor.stripColor(line);
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("mystic") || lower.contains("mythic")) return Rarity.MYSTIC;
        if (lower.contains("legendary")) return Rarity.LEGENDARY;
        if (lower.contains("epic")) return Rarity.EPIC;
        if (lower.contains("rare")) return Rarity.RARE;
        return Rarity.COMMON;
    }

    private ItemStack named(ItemStack it, String name) {
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private void fillIce(Inventory inv) {
        ItemStack ice = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack frost = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack snow = pane(Material.WHITE_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ice);
        for (int slot : new int[]{0, 1, 7, 8, 9, 17, 18, 19, 25, 26}) inv.setItem(slot, frost);
        for (int slot : new int[]{2, 3, 5, 6, 20, 21, 23, 24}) inv.setItem(slot, snow);
    }

    private ItemStack fusionButton(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "key_fusion_action"), PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack displayItem(String crateId, int rewardIndex, RewardCfg r) {
        ItemStack it = r.item.clone();
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            // Tag für Auflösung im Callback
            String tag = crateId + ":" + rewardIndex;
            im.getPersistentDataContainer().set(KEY_REWARD_TAG, PersistentDataType.STRING, tag);
            it.setItemMeta(im);
        }
        return it;
    }

    private RewardCfg resolveRewardFromItem(String crateId, ItemStack shown, List<RewardCfg> rewards) {
        if (shown == null || !shown.hasItemMeta()) return null;
        String tag = shown.getItemMeta().getPersistentDataContainer().get(KEY_REWARD_TAG, PersistentDataType.STRING);
        if (tag == null) return null;
        String expect = crateId + ":";
        if (!tag.startsWith(expect)) return null;
        try {
            int idx = Integer.parseInt(tag.substring(expect.length()));
            if (idx >= 0 && idx < rewards.size()) return rewards.get(idx);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    /* =======================================================================
       gemeinsame Utils
       ======================================================================= */

    private void giveToInventoryOrDrop(Player p, ItemStack it) {
        if (it == null || it.getType().isAir()) return;
        // interne Marker entfernen (falls aus Reel)
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            try { im.getPersistentDataContainer().remove(KEY_REWARD_TAG); } catch (Throwable ignored) {}
            it.setItemMeta(im);
        }
        if (plugin.getItemInboxService() != null) {
            plugin.getItemInboxService().giveOrStore(p, it, "crate-npc");
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        if (!left.isEmpty()) {
            for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    private void announceMysticJackpot(Player player, String crateId, ItemStack item) {
        if (!plugin.getConfig().getBoolean("crates.jackpot.mysticBroadcast", true)) return;
        if (rarityForCrateId(crateId) != Rarity.MYSTIC) return;

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE + "MYSTIC JACKPOT"
                + ChatColor.DARK_PURPLE + " ✦ " + ChatColor.WHITE + player.getName()
                + ChatColor.GRAY + " hat " + ChatColor.LIGHT_PURPLE + displayNameOf(item)
                + ChatColor.GRAY + " aus einer Mystic Crate gezogen!");
    }

    private String displayNameOf(ItemStack it) {
        if (it == null) return "n/a";
        ItemMeta im = it.getItemMeta();
        if (im != null && im.hasDisplayName()) return im.getDisplayName();
        return it.getType().name() + " x" + it.getAmount();
    }

    private static String asStr(Object o, String def) { return (o == null) ? def : String.valueOf(o); }
    private static int asInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private String getCrateId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();

        String direct = pdc.get(KEY_KEY_ID, PersistentDataType.STRING);
        if (direct != null && !direct.isBlank()) return normalizeCrateId(direct);

        String bridge = pdc.get(new NamespacedKey("vetroforge", "crate_id"), PersistentDataType.STRING);
        if (bridge != null && !bridge.isBlank()) return normalizeCrateId(bridge);

        String legacy = pdc.get(new NamespacedKey(plugin, "crate_key_tag"), PersistentDataType.STRING);
        if (legacy != null && !legacy.isBlank()) {
            int idx = legacy.indexOf(':');
            return normalizeCrateId(idx >= 0 ? legacy.substring(idx + 1) : legacy);
        }

        String tier = pdc.get(new NamespacedKey(plugin, "crate_tier"), PersistentDataType.STRING);
        if (tier != null && !tier.isBlank()) return normalizeCrateId(tier);

        if (item.getItemMeta().hasDisplayName()) {
            String plain = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            if (plain != null) {
                String lower = plain.toLowerCase(Locale.ROOT);
                for (String candidate : List.of("mystic", "mythic", "legendary", "epic", "rare", "common", "boss", "default")) {
                    if (lower.contains(candidate)) return normalizeCrateId(candidate);
                }
            }
        }
        return null;
    }

    private int countKeys(Player player, Rarity rarity) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (rarityForItem(item) == rarity) count += item.getAmount();
        }
        return count;
    }

    private void consumeKeys(Player player, Rarity rarity, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (rarityForItem(item) != rarity) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setContents(contents);
    }

    private Rarity rarityForItem(ItemStack item) {
        String crateId = getCrateId(item);
        if (crateId == null && plugin.getCrateBridge() != null) crateId = plugin.getCrateBridge().getCrateId(item);
        return rarityForCrateId(crateId);
    }

    private boolean isRedeemableCrate(String crateId) {
        String normalized = normalizeCrateId(crateId);
        if (normalized == null) return false;
        String path = "crates." + normalized;
        if (!plugin.getConfig().isConfigurationSection(path)) return false;
        if (plugin.getConfig().getBoolean(path + ".lootdropOnly", false)) return false;
        return plugin.getConfig().isList(path + ".rewards");
    }

    private Rarity rarityForCrateId(String crateId) {
        if (crateId == null) return null;
        String normalized = crateId.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            case "legendary" -> Rarity.LEGENDARY;
            case "mystic", "mythic" -> Rarity.MYSTIC;
            case "common", "default" -> Rarity.COMMON;
            default -> null;
        };
    }

    private String normalizeCrateId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = raw.trim();
        if (clean.equalsIgnoreCase("mythic")) clean = "mystic";

        if (plugin.getConfig().isConfigurationSection("crates." + clean)) return clean;
        String lower = clean.toLowerCase(Locale.ROOT);
        if (plugin.getConfig().isConfigurationSection("crates." + lower)) return lower;

        String title = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        if (plugin.getConfig().isConfigurationSection("crates." + title)) return title;
        return title;
    }

    private String crateIdFor(Rarity rarity) {
        if (rarity == null) rarity = Rarity.COMMON;
        String configured = plugin.getConfig().getString("crates.bridge.ids." + rarity.name());
        if (configured != null && !configured.isBlank()) return configured;
        return switch (rarity) {
            case COMMON -> "Common";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Legendary";
            case MYSTIC -> "Mystic";
        };
    }

    private String rarityDisplay(Rarity rarity) {
        if (rarity == null) return "Common";
        if (rarity == Rarity.MYSTIC) return "Mystic";
        return rarity.display;
    }

    private String crystalDisplayForCrate(String crateId, Rarity fallback) {
        if (crateId != null) {
            String normalized = crateId.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("boss")) return "Boss";
            if (normalized.equals("default")) return "Common";
            if (normalized.equals("mythic")) return "Mystic";
        }
        return rarityDisplay(fallback);
    }

    private List<String> keyLore(Player player, String display, String crateId) {
        return List.of(
                tr(player, "key_lore.line1", "rarity", display),
                tr(player, "key_lore.line2"),
                ChatColor.DARK_GRAY + "Crate: " + ChatColor.WHITE + crateId
        );
    }

    private Rarity rarityForTier(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 2 -> Rarity.RARE;
            case 3 -> Rarity.EPIC;
            case 4 -> Rarity.LEGENDARY;
            case 5 -> Rarity.MYSTIC;
            default -> Rarity.COMMON;
        };
    }

    private void consumeOne(ItemStack item) {
        if (item == null) return;
        if (item.getAmount() <= 1) item.setAmount(0);
        else item.setAmount(item.getAmount() - 1);
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "crates." + key, VetroForgeUtil.vars(values));
    }
}
