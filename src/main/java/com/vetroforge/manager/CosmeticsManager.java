package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Cosmetics mit Kategorien + VetroPerms.
 */
public class CosmeticsManager implements Listener {

    /* ================= Static ================= */

    private static final String TITLE_PUBLIC_CATS = ChatColor.GOLD + "Cosmetics • Kategorien";
    private static final String TITLE_STAFF_CATS  = ChatColor.AQUA + "Staff • Kategorien";
    private static final String TITLE_ITEMS       = ChatColor.YELLOW + "Cosmetics: ";

    private static final String TAG = "cosm_tag"; // PDC-Tag

    private static final String DATA_FILE = "data/cosmetics.yml";

    private static final int PAGE_SIZE = 28; // 4x7 Grid (mit Rahmen)

    /* ===== Permission Nodes (VetroPerms) ===== */
    private static final String PERM_STAFF_OPEN = "cosmetics.staff";
    private static final String PERM_STAFF_ITEM_PREFIX = "cosmetics.staff."; // + <id>

    /* ================= Types ================= */

    public static final class CosmeticDef {
        public enum Kind { PARTICLE, COMMAND }

        public final String id;
        public final String name;
        public final Material icon;
        public final double price;
        public final Kind kind;
        public final Particle particle;  // PARTICLE
        public final String style;       // circle | halo | wings | orbit
        public final String command;     // COMMAND
        public final boolean staffOnly;

        public CosmeticDef(String id, String name, Material icon, double price, Kind kind,
                           Particle particle, String style, String command, boolean staffOnly) {
            this.id = id;
            this.name = name;
            this.icon = (icon == null ? Material.PAPER : icon);
            this.price = Math.max(0.0, price);
            this.kind = kind;
            this.particle = particle;
            this.style = style;
            this.command = command;
            this.staffOnly = staffOnly;
        }
    }

    public static final class Category {
        public final String id;
        public final String name;
        public final Material icon;
        public final boolean staff;
        public final List<String> itemIds;

        public Category(String id, String name, Material icon, boolean staff, List<String> itemIds) {
            this.id = id;
            this.name = name;
            this.icon = (icon == null ? Material.CHEST : icon);
            this.staff = staff;
            this.itemIds = itemIds;
        }
    }

    /* ===== Holders (GUI-State) ===== */

    private interface HolderBase extends InventoryHolder { default Inventory getInventory(){ return null; } }

    private static final class CatHolder implements HolderBase {
        final boolean staff;
        CatHolder(boolean staff){ this.staff = staff; }
    }

    private static final class ItemHolder implements HolderBase {
        final boolean staff;
        final String categoryId;
        final int page;
        ItemHolder(boolean staff, String categoryId, int page){
            this.staff = staff; this.categoryId = categoryId; this.page = page;
        }
    }

    /* ================= Fields ================= */

    private final FactionsPlugin plugin;
    private final NamespacedKey KEY;

    private final File dataFile;
    private YamlConfiguration dataYml;

    // defs
    private final Map<String, CosmeticDef> all = new LinkedHashMap<>();
    private final Map<String, Category> pubCats = new LinkedHashMap<>();
    private final Map<String, Category> staffCats = new LinkedHashMap<>();

    // player state
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, PreviewState> previews = new HashMap<>();

    // runner
    private BukkitTask runner;

    private record PreviewState(String cosmeticId, long expiresAtMillis) {}

    public CosmeticsManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, TAG);

        Bukkit.getPluginManager().registerEvents(this, plugin);

        this.dataFile = new File(plugin.getDataFolder(), DATA_FILE);
        loadData();
        loadFromConfig();
        startRunner();
    }

    /* ================= Public API ================= */

    public void openMenu(Player p) {
        p.openInventory(buildCategoryMenu(false));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .6f, 1.2f);
    }

    public void openStaffMenu(Player p) {
        if (!hasPerm(p, PERM_STAFF_OPEN)) {
            p.sendMessage(cc("&cKeine Berechtigung."));
            return;
        }
        p.openInventory(buildCategoryMenu(true));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .6f, 1.2f);
    }

    public void reload() {
        loadFromConfig();
    }

    public boolean startPreview(Player player, String cosmeticId, int seconds) {
        if (player == null || cosmeticId == null) return false;
        CosmeticDef def = all.get(cosmeticId);
        if (def == null || def.kind != CosmeticDef.Kind.PARTICLE) return false;
        if (isPreviewBlocked(player)) {
            player.sendMessage(cc("&cPreview ist in Raid/Warzone deaktiviert."));
            return false;
        }
        int duration = Math.max(5, Math.min(120, seconds));
        previews.put(player.getUniqueId(), new PreviewState(cosmeticId, System.currentTimeMillis() + duration * 1000L));
        player.sendMessage(cc("&dCosmetic Preview &8» &f" + def.name + " &7für &f" + duration + "s&7."));
        player.sendActionBar(cc("&dPreview aktiv &8• &7" + def.name + " &8• &eUnlock im Cosmetics-Menü"));
        return true;
    }

    /* ================= Listener ================= */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof HolderBase)) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return;

        String tag = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (tag == null) return;

        // Global toggle button
        if ("toggle".equals(tag)) {
            selected.remove(p.getUniqueId());
            savePlayer(p.getUniqueId());
            p.closeInventory();
            p.sendMessage(cc("&7Cosmetics &cdeaktiviert."));
            return;
        }

        // Kategorie-Klick
        if (holder instanceof CatHolder ch) {
            if (tag.startsWith("cat:")) {
                openItems(p, tag.substring(4), false, 1);
                return;
            }
            if (tag.startsWith("scat:")) {
                if (!hasPerm(p, PERM_STAFF_OPEN)) { p.sendMessage(cc("&cKeine Berechtigung.")); return; }
                openItems(p, tag.substring(5), true, 1);
                return;
            }
            return;
        }

        // Items-View
        if (holder instanceof ItemHolder ih) {
            if ("back".equals(tag)) {
                if (ih.staff) openStaffMenu(p); else openMenu(p);
                return;
            }
            if (tag.startsWith("page:")) {
                int next = safeInt(tag.substring(5), ih.page);
                openItems(p, ih.categoryId, ih.staff, Math.max(1, next));
                return;
            }

            // Item-Kauf/Nutzung
            String id = null;
            if (tag.startsWith("cosm:")) id = tag.substring(5);
            if (tag.startsWith("staff:")) {
                id = tag.substring(6);
                // pro Staff-Item Node
                if (!hasPerm(p, PERM_STAFF_ITEM_PREFIX + id)) {
                    p.sendMessage(cc("&cKeine Berechtigung für &f" + id));
                    return;
                }
            }
            if (id == null) return;

            CosmeticDef def = all.get(id);
            if (def == null) return;

            handleUse(p, def);

            // refresh aktuelle Seite
            openItems(p, ih.categoryId, ih.staff, ih.page);
        }
    }

    /* ================= Build GUIs ================= */

    private Inventory buildCategoryMenu(boolean staff) {
        List<Category> cats = new ArrayList<>(staff ? staffCats.values() : pubCats.values());
        int size = clampToInv(cats.size() + 9);
        Inventory inv = Bukkit.createInventory(new CatHolder(staff), size,
                staff ? TITLE_STAFF_CATS : TITLE_PUBLIC_CATS);

        fill(inv);

        int slot = 10;
        for (Category c : cats) {
            if (slot >= size - 9) break;
            ItemStack it = new ItemStack(c.icon);
            ItemMeta im = it.getItemMeta();
            im.setDisplayName((staff ? ChatColor.AQUA : ChatColor.YELLOW) + c.name);
            im.setLore(Arrays.asList(cc("&7Klicke zum Öffnen"), cc("&8ID: " + c.id)));
            im.getPersistentDataContainer().set(KEY, PersistentDataType.STRING,
                    (staff ? "scat:" : "cat:") + c.id);
            it.setItemMeta(im);
            inv.setItem(slot++, it);
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        // Toggle OFF in Mitte unten
        inv.setItem(size - 5, button(Material.BARRIER, cc("&cCosmetics aus"), "toggle"));
        return inv;
    }

    private void openItems(Player p, String categoryId, boolean staff, int page) {
        Category cat = (staff ? staffCats.get(categoryId) : pubCats.get(categoryId));
        if (cat == null) { p.sendMessage(cc("&cKategorie nicht gefunden.")); return; }

        String title = TITLE_ITEMS + ChatColor.WHITE + cat.name + ChatColor.GRAY + "  §7(p" + page + ")";
        Inventory inv = Bukkit.createInventory(new ItemHolder(staff, cat.id, page), 54, title);

        // Rahmen/Fill
        fill(inv);

        // Grid-Bereiche (Zeilen 2..5, Spalten 2..8)
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(cat.itemIds.size(), start + PAGE_SIZE);

        int placed = 0;
        for (int idx = start; idx < end; idx++) {
            String id = cat.itemIds.get(idx);
            CosmeticDef d = all.get(id);
            if (d == null) continue;

            ItemStack icon = iconFor(p, d, staff);
            int slot = gridSlot(placed++);
            inv.setItem(slot, icon);
        }

        // Back / Nav
        inv.setItem(45, button(Material.ARROW, cc("&a« Zurück"), "back"));
        if (page > 1) inv.setItem(48, button(Material.ARROW, cc("&7Seite " + (page - 1)), "page:" + (page - 1)));
        if (end < cat.itemIds.size()) inv.setItem(50, button(Material.ARROW, cc("&7Seite " + (page + 1)), "page:" + (page + 1)));

        // Toggle OFF
        inv.setItem(49, button(Material.BARRIER, cc("&cAus"), "toggle"));

        p.openInventory(inv);
    }

    private ItemStack iconFor(Player viewer, CosmeticDef d, boolean staffView) {
        boolean isOwned = owned(viewer, d.id);
        boolean isSelected = d.id.equals(selected.get(viewer.getUniqueId()));

        ItemStack it = new ItemStack(d.icon);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName((staffView ? ChatColor.AQUA : ChatColor.YELLOW) + d.name);

        List<String> lore = new ArrayList<>();
        if (d.kind == CosmeticDef.Kind.PARTICLE) {
            lore.add(cc("&7Typ: &f" + d.particle.name()));
            lore.add(cc("&7Stil: &f" + (d.style == null ? "circle" : d.style)));
        } else {
            lore.add(cc("&7Aktion: &fCommand"));
        }

        lore.add(" ");
        if (isOwned) {
            lore.add(isSelected ? cc("&aAktiviert") : cc("&7Gekauft"));
            lore.add(cc("&8• Linksklick: " + (isSelected ? "Deaktivieren" : "Aktivieren")));
        } else {
            String price = (d.price <= 0.0) ? cc("&fKostenlos") : cc("&f$" + fmt(d.price));
            lore.add(cc("&7Preis: ") + price);
            lore.add(cc("&8• Linksklick: Kaufen"));
        }

        im.setLore(lore);
        String key = staffView ? "staff:" + d.id : "cosm:" + d.id;
        im.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, key);
        it.setItemMeta(im);
        return it;
    }

    /* ================= Use / Buy ================= */

    private void handleUse(Player p, CosmeticDef d) {
        Set<String> mine = owned.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());

        if (!mine.contains(d.id)) {
            // kaufen
            if (d.price > 0.0) {
                boolean ok = plugin.getEconomyManager().withdraw(p.getUniqueId(), d.price);
                if (!ok) { p.sendMessage(cc("&cNicht genug Geld. &7Preis: &f$" + fmt(d.price))); return; }
            }
            mine.add(d.id);
            p.sendMessage(cc("&aGekauft: &f" + d.name));
        }

        if (d.kind == CosmeticDef.Kind.PARTICLE) {
            // toggle
            if (d.id.equals(selected.get(p.getUniqueId()))) {
                selected.remove(p.getUniqueId());
                p.sendMessage(cc("&7" + d.name + " &8deaktiviert."));
            } else {
                selected.put(p.getUniqueId(), d.id);
                p.sendMessage(cc("&aAktiviert: &f" + d.name));
            }
        } else {
            // command
            String cmd = (d.command == null ? "" : d.command)
                    .replace("{PLAYER}", p.getName())
                    .replace("{UUID}", p.getUniqueId().toString());
            if (!cmd.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            p.sendMessage(cc("&aCosmetic angewendet: &f" + d.name));
        }

        savePlayer(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .7f, 1.3f);
    }

    /* ================= Runner ================= */

    private void startRunner() {
        if (runner != null) runner.cancel();
        runner = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long t = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PreviewState preview = previews.get(p.getUniqueId());
                if (preview != null && preview.expiresAtMillis() <= t) {
                    previews.remove(p.getUniqueId());
                    p.sendActionBar(cc("&7Cosmetic Preview beendet."));
                    preview = null;
                }
                String id = preview != null ? preview.cosmeticId() : selected.get(p.getUniqueId());
                if (id == null) continue;
                CosmeticDef d = all.get(id);
                if (d == null || d.kind != CosmeticDef.Kind.PARTICLE) continue;
                try { render(d, p, t); } catch (Throwable ignored) {}
            }
        }, 10L, 5L);
    }

    private void render(CosmeticDef d, Player p, long t) {
        Particle type = (d.particle == null ? Particle.CRIT : d.particle);
        String style = (d.style == null ? "circle" : d.style.toLowerCase(Locale.ROOT));
        switch (style) {
            case "halo" -> {
                double y = p.getLocation().getY() + 2.0;
                double r = 0.6;
                int pts = 16;
                for (int i = 0; i < pts; i++) {
                    double a = 2 * Math.PI * i / pts;
                    double x = p.getLocation().getX() + Math.cos(a) * r;
                    double z = p.getLocation().getZ() + Math.sin(a) * r;
                    p.getWorld().spawnParticle(type, x, y, z, 1, 0,0,0, 0.0);
                }
            }
            case "wings" -> {
                var loc = p.getLocation();
                double yaw = Math.toRadians(loc.getYaw());
                double bx = -Math.sin(yaw), bz = Math.cos(yaw);
                for (double o = -0.6; o <= 0.6; o += 0.2) {
                    double x = loc.getX() - bx * 0.4 + bz * o;
                    double y = loc.getY() + 1.5 + Math.sin((t % 2000) / 2000.0 * Math.PI * 2 + o) * 0.1;
                    double z = loc.getZ() - bz * 0.4 - bx * o;
                    p.getWorld().spawnParticle(type, x, y, z, 1, 0,0,0, 0.0);
                }
            }
            case "orbit" -> {
                double r = 0.5;
                double a = (t % 2000) / 2000.0 * 2 * Math.PI;
                double x = p.getLocation().getX() + Math.cos(a) * r;
                double y = p.getLocation().getY() + 1.0 + 0.2 * Math.sin(a * 2);
                double z = p.getLocation().getZ() + Math.sin(a) * r;
                p.getWorld().spawnParticle(type, x, y, z, 2, 0,0,0, 0.0);
            }
            default -> { // circle
                double y = p.getLocation().getY() + 0.2;
                double r = 0.6;
                int pts = 12;
                double phase = (t % 2000) / 2000.0 * 2 * Math.PI;
                for (int i = 0; i < pts; i++) {
                    double a = 2 * Math.PI * i / pts + phase;
                    double x = p.getLocation().getX() + Math.cos(a) * r;
                    double z = p.getLocation().getZ() + Math.sin(a) * r;
                    p.getWorld().spawnParticle(type, x, y, z, 1, 0,0,0, 0.0);
                }
            }
        }
    }

    private boolean isPreviewBlocked(Player player) {
        if (plugin.getWarzoneRegionManager() != null && plugin.getWarzoneRegionManager().isWarzone(player.getLocation())) {
            return true;
        }
        if (plugin.getRaidManager() == null || plugin.getFactionManager() == null) return false;
        String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
        return faction != null && plugin.getRaidManager().isFactionInRaid(faction);
    }

    /* ================= Data ================= */

    private void loadData() {
        try {
            if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
            if (!dataFile.exists()) dataFile.createNewFile();
        } catch (IOException ignored) {}
        dataYml = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection ps = dataYml.getConfigurationSection("players");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(k);
                    String sel = ps.getString(k + ".selected", "");
                    List<String> own = ps.getStringList(k + ".owned");
                    if (sel != null && !sel.isBlank()) selected.put(id, sel);
                    owned.put(id, new HashSet<>(own));
                } catch (Throwable ignored) {}
            }
        }
    }

    private void savePlayer(UUID id) {
        if (id == null) return;
        String b = "players." + id;
        dataYml.set(b + ".owned", new ArrayList<>(owned.getOrDefault(id, Collections.emptySet())));
        dataYml.set(b + ".selected", selected.getOrDefault(id, ""));
        try { dataYml.save(dataFile); } catch (IOException ignored) {}
    }

    /* ================= Config ================= */

    private void loadFromConfig() {
        all.clear();
        pubCats.clear();
        staffCats.clear();

        // Items definieren
        loadItemGroup("cosmetics.items", false);
        loadItemGroup("cosmetics.staff", true);

        // Kategorien (Public)
        loadCategories("cosmetics.categories", false, pubCats);
        // Kategorien (Staff)
        loadCategories("cosmetics.staffCategories", true, staffCats);
    }

    private void loadItemGroup(String path, boolean staff) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;

            String name = cc(s.getString("name", id));
            Material icon = mat(s.getString("icon", "PAPER"));
            double price = s.getDouble("price", 0.0);
            String type = s.getString("type", "particle").toLowerCase(Locale.ROOT);

            CosmeticDef.Kind kind;
            Particle particle = null;
            String style = null;
            String cmd = null;

            if ("command".equals(type)) {
                kind = CosmeticDef.Kind.COMMAND;
                cmd = s.getString("command", "");
            } else {
                kind = CosmeticDef.Kind.PARTICLE;
                particle = particleSafe(s.getString("particle", "CRIT"));
                style = s.getString("style", "circle");
            }

            CosmeticDef def = new CosmeticDef(id, name, icon, price, kind, particle, style, cmd, staff);
            all.put(id, def);
        }
    }

    private void loadCategories(String path, boolean staff, Map<String, Category> sink) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;

            String name = cc(s.getString("name", id));
            Material icon = mat(s.getString("icon", "CHEST"));
            List<String> items = s.getStringList("items");
            // Filter: nur bekannte Items und nur passende staff-Flag
            List<String> valid = new ArrayList<>();
            for (String it : items) {
                CosmeticDef d = all.get(it);
                if (d == null) continue;
                if (d.staffOnly != staff) continue;
                valid.add(it);
            }
            sink.put(id, new Category(id, name, icon, staff, valid));
        }
    }

    /* ================= Utils ================= */

    private boolean owned(Player p, String id) {
        return owned.getOrDefault(p.getUniqueId(), Collections.emptySet()).contains(id);
    }

    private int gridSlot(int i) {
        // 4 Reihen (2..5), 7 Spalten (2..8)
        int row = i / 7;   // 0..3
        int col = i % 7;   // 0..6
        return (row + 1) * 9 + (col + 1); // (Zeile+1)*9 + (Spalte+1)
    }

    private ItemStack button(Material m, String name, String tag) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        im.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, tag);
        it.setItemMeta(im);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta(); m.setDisplayName(" "); pane.setItemMeta(m);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane.clone());
    }

    private int clampToInv(int n) {
        int s = Math.max(9, ((n + 8) / 9) * 9);
        return Math.min(54, s);
    }

    private Material mat(String name) {
        Material m = Material.matchMaterial(name == null ? "" : name);
        if (m == null) {
            try { m = Material.valueOf((name == null ? "" : name).toUpperCase(Locale.ROOT)); } catch (Throwable ignored) {}
        }
        return (m == null ? Material.PAPER : m);
    }

    private Particle particleSafe(String s) {
        try { return Particle.valueOf((s == null ? "CRIT" : s).toUpperCase(Locale.ROOT)); }
        catch (Throwable t) { return Particle.CRIT; }
    }

    private static String cc(String s){ return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private static String fmt(double v){ return String.format(Locale.US, "%,.0f", v); }
    private static int safeInt(String s, int def){ try { return Integer.parseInt(s); } catch (Exception e){ return def; } }

    /* ================= VetroPerms Bridge (reflection) ================= */

    private boolean hasPerm(Player p, String node) {
        if (p == null || node == null || node.isBlank()) return false;
        try {
            // Versuche häufige Getter im Plugin:
            Object perms = null;
            for (String m : new String[]{"getVetroPerms","getPerms","getPermissionsManager","getVetroPermsManager"}) {
                try { perms = plugin.getClass().getMethod(m).invoke(plugin); if (perms != null) break; } catch (Throwable ignored) {}
            }
            if (perms != null) {
                // Versuche Signaturen:
                for (String m : new String[]{"has","hasPermission","check","test"}) {
                    // has(Player,String)
                    try {
                        Method mm = perms.getClass().getMethod(m, Player.class, String.class);
                        Object r = mm.invoke(perms, p, node);
                        if (r instanceof Boolean) return (Boolean) r;
                    } catch (Throwable ignored) {}
                    // has(UUID,String)
                    try {
                        Method mm = perms.getClass().getMethod(m, UUID.class, String.class);
                        Object r = mm.invoke(perms, p.getUniqueId(), node);
                        if (r instanceof Boolean) return (Boolean) r;
                    } catch (Throwable ignored) {}
                    // has(String,String)
                    try {
                        Method mm = perms.getClass().getMethod(m, String.class, String.class);
                        Object r = mm.invoke(perms, p.getName(), node);
                        if (r instanceof Boolean) return (Boolean) r;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Fallback: OP oder Bukkit-Permission
        try { if (p.isOp()) return true; } catch (Throwable ignored) {}
        try { return p.hasPermission(node); } catch (Throwable ignored) {}
        return false;
    }
}
