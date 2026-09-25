package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Aktualisiert:
 *  - ActionBar jetzt über Adventure Component (kein String mehr)
 *  - ItemMeta#displayName(Component) statt setDisplayName(String)
 *  - Keine @SuppressWarnings("deprecation") mehr nötig
 */
public class StoryManager {

    public enum StepType { REACH_WARZONE, KILL_PLAYERS, BREAK_BLOCK, COLLECT_ITEM, RAID_WIN, FLAG_CAPTURE }

    public static final class Step {
        public final StepType type;
        public final int target;
        public final boolean warzoneOnly;
        public final Material material;
        public final String display; // &-Farben erlaubt

        public Step(StepType type, int target, boolean warzoneOnly, Material material, String display) {
            this.type = type;
            this.target = Math.max(1, target);
            this.warzoneOnly = warzoneOnly;
            this.material = material;
            this.display = display;
        }
    }

    public static final class Story {
        public final String id;
        public final String title;
        public final List<Step> steps;
        public final int rewardMoney;
        public final int rewardPrestige;
        public final int rewardFactionCoins;
        public final List<String> rewardItems;

        public Story(String id, String title, List<Step> steps,
                     int rewardMoney, int rewardPrestige, int rewardFactionCoins, List<String> rewardItems) {
            this.id = id;
            this.title = title;
            this.steps = steps;
            this.rewardMoney = rewardMoney;
            this.rewardPrestige = rewardPrestige;
            this.rewardFactionCoins = rewardFactionCoins;
            this.rewardItems = rewardItems;
        }
    }

    private final FactionsPlugin plugin;
    private final Map<String, Story> stories = new LinkedHashMap<>();

    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();

    public StoryManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    public void reload() {
        stories.clear();
        loadFromConfig();
    }

    /* =============== Config laden =============== */

    private void loadFromConfig() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("stories");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            String base = "stories." + id + ".";
            String title = color(plugin.getConfig().getString(base + "title", "&f" + id));
            ConfigurationSection ssec = plugin.getConfig().getConfigurationSection(base + "steps");

            List<Step> steps = new ArrayList<>();
            if (ssec != null) {
                for (String idx : ssec.getKeys(false)) {
                    String p = base + "steps." + idx + ".";
                    StepType type = parseType(plugin.getConfig().getString(p + "type", "KILL_PLAYERS"));
                    int target = plugin.getConfig().getInt(p + "target", 1);
                    boolean wzOnly = plugin.getConfig().getBoolean(p + "warzoneOnly", false);
                    Material mat = plugin.getConfig().isString(p + "material")
                            ? Material.matchMaterial(plugin.getConfig().getString(p + "material", "STONE"))
                            : null;
                    String display = color(plugin.getConfig().getString(p + "display", "&7Step " + idx));
                    steps.add(new Step(type, target, wzOnly, mat, display));
                }
            }

            int rm = plugin.getConfig().getInt(base + "rewards.money", 0);
            int rp = plugin.getConfig().getInt(base + "rewards.prestige", 0);
            int rf = plugin.getConfig().getInt(base + "rewards.factionCoins", 0);
            List<String> ritems = plugin.getConfig().getStringList(base + "rewards.items");

            stories.put(id, new Story(id, title, steps, rm, rp, rf, ritems));
        }

        plugin.getLogger().info("[StoryManager] Loaded " + stories.size() + " stories.");
    }

    private StepType parseType(String s) {
        try { return StepType.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return StepType.KILL_PLAYERS; }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private File file(Player p) {
        File dir = new File(plugin.getDataFolder(), "playerdata");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, p.getUniqueId() + ".yml");
    }
    private YamlConfiguration yaml(Player p) { return YamlConfiguration.loadConfiguration(file(p)); }
    private void save(Player p, YamlConfiguration y) { try { y.save(file(p)); } catch (IOException ignored) {} }

    /* ================ Public API ================ */

    public Collection<Story> getAllStories() { return stories.values(); }
    public Story getStory(String id) { return stories.get(id); }

    public boolean hasActive(Player p) { return getActiveId(p) != null; }
    public String getActiveId(Player p) { return yaml(p).getString("story.active.id", null); }
    public int getActiveStepIndex(Player p) { return yaml(p).getInt("story.active.step", 0); }
    public int getActiveProgress(Player p) { return yaml(p).getInt("story.active.progress", 0); }
    public List<String> getCompleted(Player p) { return yaml(p).getStringList("story.completed"); }

    public boolean start(Player p, String id) {
        Story st = stories.get(id);
        if (st == null) return false;

        YamlConfiguration y = yaml(p);
        y.set("story.active.id", id);
        y.set("story.active.step", 0);
        y.set("story.active.progress", 0);
        save(p, y);

        p.sendMessage(color(plugin.getI18n().tr(p, "story.started",
                map("title", ChatColor.stripColor(st.title)))));

        showCurrentStep(p, st, 0, 0);
        return true;
    }

    public void abandon(Player p) {
        YamlConfiguration y = yaml(p);
        y.set("story.active.id", null);
        y.set("story.active.step", 0);
        y.set("story.active.progress", 0);
        save(p, y);
    }

    public void adminComplete(Player p) {
        String id = getActiveId(p);
        if (id == null) return;
        Story st = stories.get(id);
        if (st == null) return;
        finishStory(p, st);
    }

    /* ================ Warzone-Hooks ================ */
    /** Wird vom WarzoneEnterListener aufgerufen. */
    public void onWarzoneEnter(Player p) { recordReachWarzone(p); }

    public void onWarzoneLeave(Player p) { /* no-op */ }

    /* ================ Recording Hooks ================ */

    public void recordReachWarzone(Player p) { recordGeneric(p, StepType.REACH_WARZONE, null, 1, true); }
    public void recordKill(Player p, boolean inWarzone) { recordGeneric(p, StepType.KILL_PLAYERS, null, 1, inWarzone); }
    public void recordBlockBreak(Player p, Material material) { recordGeneric(p, StepType.BREAK_BLOCK, material, 1, true); }
    public void recordItemCollect(Player p, Material material) { recordGeneric(p, StepType.COLLECT_ITEM, material, 1, true); }
    public void recordRaidWin(Player p) { recordGeneric(p, StepType.RAID_WIN, null, 1, true); }
    public void recordFlagCapture(Player p) { recordGeneric(p, StepType.FLAG_CAPTURE, null, 1, true); }

    private void recordGeneric(Player p, StepType type, Material material, int amount, boolean contextOk) {
        String id = getActiveId(p);
        if (id == null) return;
        Story st = stories.get(id);
        if (st == null) return;

        int idx = getActiveStepIndex(p);
        if (idx < 0 || idx >= st.steps.size()) return;
        Step step = st.steps.get(idx);

        if (step.type != type) return;
        if (step.material != null && material != null && step.material != material) return;

        if (step.warzoneOnly && !contextOk && !isPlayerInWarzone(p)) return;

        int prog = Math.min(step.target, getActiveProgress(p) + Math.max(1, amount));
        setProgress(p, prog);

        // ActionBar mit Adventure Component (kein deprecated String)
        Component bar = LEGACY_AMP.deserialize(color(plugin.getI18n().tr(p, "story.progress_actionbar",
                map("title", ChatColor.stripColor(st.title),
                        "step", ChatColor.translateAlternateColorCodes('&', step.display),
                        "cur", String.valueOf(prog),
                        "max", String.valueOf(step.target)))));
        p.sendActionBar(bar);

        if (prog >= step.target) advance(p, st, idx);
    }

    private void advance(Player p, Story st, int idx) {
        int next = idx + 1;
        if (next >= st.steps.size()) {
            finishStory(p, st);
            return;
        }
        YamlConfiguration y = yaml(p);
        y.set("story.active.step", next);
        y.set("story.active.progress", 0);
        save(p, y);
        showCurrentStep(p, st, next, 0);
    }

    private void finishStory(Player p, Story st) {
        if (st.rewardFactionCoins > 0) {
            try {
                String fac = plugin.getFactionManager().getFactionOf(p.getUniqueId());
                if (fac != null) plugin.getFactionCoinManager().addCoins(fac, st.rewardFactionCoins);
            } catch (Throwable ignored) {}
        }

        if (st.rewardPrestige > 0) {
            try { plugin.getPrestigeManager().addLevels(p.getUniqueId(), st.rewardPrestige); }
            catch (Throwable ignored) {}
        }

        if (st.rewardMoney > 0 && plugin.getCoinsManager() != null) {
            plugin.getCoinsManager().addCoins(p.getUniqueId(), st.rewardMoney);
        }

        if (st.rewardItems != null) for (String def : st.rewardItems) giveItemSpec(p, def);

        YamlConfiguration y = yaml(p);
        List<String> done = y.getStringList("story.completed");
        if (!done.contains(st.id)) done.add(st.id);
        y.set("story.completed", done);
        y.set("story.active.id", null);
        y.set("story.active.step", 0);
        y.set("story.active.progress", 0);
        save(p, y);

        p.sendMessage("§8—————— §cVetro§6Forge §7• §fStory §8——————");
        p.sendMessage(color(plugin.getI18n().tr(p, "story.completed_title",
                map("title", ChatColor.stripColor(st.title)))));
        p.sendMessage(color(plugin.getI18n().tr(p, "story.rewards_header")));
        if (st.rewardMoney > 0)
            p.sendMessage(color(plugin.getI18n().tr(p, "story.reward_money", map("amount", String.valueOf(st.rewardMoney)))));
        if (st.rewardPrestige > 0)
            p.sendMessage(color(plugin.getI18n().tr(p, "story.reward_prestige", map("amount", String.valueOf(st.rewardPrestige)))));
        if (st.rewardFactionCoins > 0)
            p.sendMessage(color(plugin.getI18n().tr(p, "story.reward_fcoins", map("amount", String.valueOf(st.rewardFactionCoins)))));
        if (st.rewardItems != null && !st.rewardItems.isEmpty())
            p.sendMessage(color(plugin.getI18n().tr(p, "story.reward_items", map("count", String.valueOf(st.rewardItems.size())))));
        p.sendMessage("§8——————————————————————————————");
    }

    private void showCurrentStep(Player p, Story st, int idx, int prog) {
        Step step = st.steps.get(idx);
        p.sendMessage("§8—————— §cVetro§6Forge §7• §fStory §8——————");
        p.sendMessage(color(plugin.getI18n().tr(p, "story.status.header",
                map("title", ChatColor.stripColor(st.title), "id", st.id))));
        p.sendMessage(color(plugin.getI18n().tr(p, "story.status.step_line",
                map("idx", String.valueOf(idx + 1),
                        "total", String.valueOf(st.steps.size()),
                        "text", ChatColor.translateAlternateColorCodes('&', step.display)))));
        p.sendMessage(color(plugin.getI18n().tr(p, "story.status.progress_line",
                map("cur", String.valueOf(prog), "max", String.valueOf(step.target)))));
        p.sendMessage("§8——————————————————————————————");
    }

    private void setProgress(Player p, int value) {
        YamlConfiguration y = yaml(p);
        y.set("story.active.progress", Math.max(0, value));
        save(p, y);
    }

    private boolean isPlayerInWarzone(Player p) {
        try {
            return plugin.getWarzoneRegionManager() != null
                    && plugin.getWarzoneRegionManager().isWarzone(p.getLocation());
        } catch (Throwable t) {
            return true; // im Zweifel nicht blockieren
        }
    }

    private void giveItemSpec(Player p, String spec) {
        if (spec == null || spec.isBlank()) return;
        String[] parts = spec.split(":", 3);
        Material m = Material.matchMaterial(parts[0]);
        int amt = 1;
        if (parts.length >= 2) {
            try { amt = Math.max(1, Integer.parseInt(parts[1])); } catch (Exception ignored) {}
        }
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(m == null ? Material.STONE : m, amt);
        if (parts.length >= 3) {
            org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
            // Adventure-Displayname statt deprecated setDisplayName
            im.displayName(LEGACY_AMP.deserialize(ChatColor.translateAlternateColorCodes('&', parts[2])));
            it.setItemMeta(im);
        }
        p.getInventory().addItem(it);
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        if (kv == null) return m;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = kv[i], v = kv[i + 1];
            if (k != null) m.put(k, v == null ? "" : v);
        }
        return m;
    }
}
