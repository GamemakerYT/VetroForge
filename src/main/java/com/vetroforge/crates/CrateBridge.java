package com.vetroforge.crates;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;

public class CrateBridge {

    public enum Provider { AUTO, CRAZYCRATES, CRATE_RELOADED, EXCELLENT_CRATES, INTERNAL }

    private final FactionsPlugin plugin;
    private Provider provider = Provider.INTERNAL;

    // PDC Keys für Fallback-Keys
    private final NamespacedKey KEY_FLAG = new NamespacedKey("vetroforge","crate_key");
    private final NamespacedKey KEY_ID   = new NamespacedKey("vetroforge","crate_id");
    private final NamespacedKey KEY_RAR  = new NamespacedKey("vetroforge","crate_rarity");

    public CrateBridge(FactionsPlugin plugin){
        this.plugin = plugin;
        detectProvider();
    }

    private void detectProvider(){
        String cfg = plugin.getConfig().getString("crates.bridge.provider", "AUTO").toUpperCase(Locale.ROOT);
        Provider want;
        try { want = Provider.valueOf(cfg); } catch (IllegalArgumentException e){ want = Provider.AUTO; }

        if (want == Provider.AUTO){
            if (Bukkit.getPluginManager().getPlugin("CrazyCrates") != null) {
                provider = Provider.CRAZYCRATES; return;
            }
            if (Bukkit.getPluginManager().getPlugin("CrateReloaded") != null) {
                provider = Provider.CRATE_RELOADED; return;
            }
            if (Bukkit.getPluginManager().getPlugin("ExcellentCrates") != null) {
                provider = Provider.EXCELLENT_CRATES; return;
            }
            provider = Provider.INTERNAL;
        } else {
            provider = want;
        }
        plugin.getLogger().info("[CrateBridge] Provider = " + provider);
    }

    /** Mappt Rarity -> Crate-ID aus config. */
    public String crateIdFor(Rarity r){
        String path = "crates.bridge.ids." + r.name();
        String def  = switch (r){
            case COMMON -> "Common";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Legendary";
            case MYSTIC -> "Mystic";
        };
        return plugin.getConfig().getString(path, def);
    }

    /* ================= High-level API ================= */

    /** Gibt dem Spieler Keys (provider-spezifisch); fällt auf Item-Keys zurück. */
    public void giveKey(Player p, Rarity r, int amount){
        amount = Math.max(1, amount);
        boolean ok = switch (provider){
            case CRAZYCRATES -> giveCrazyCrates(p, crateIdFor(r), amount);
            case CRATE_RELOADED -> giveCrateReloaded(p, crateIdFor(r), amount);
            case EXCELLENT_CRATES -> giveExcellent(p, crateIdFor(r), amount);
            default -> false;
        };
        if (!ok){
            p.getInventory().addItem(makeKeyItem(r, crateIdFor(r), amount));
        }
    }

    /** Erzeugt einen Key-ItemStack (Fallback / Chest-Loot). */
    public ItemStack makeKeyItem(Rarity r, String crateId, int amount){
        int safeAmount = Math.max(1, Math.min(64, amount));
        ItemStack it = new ItemStack(Material.NETHER_STAR, safeAmount);
        ItemMeta im = it.getItemMeta();
        String display = r == Rarity.MYSTIC ? "Mystic" : r.display;
        im.setDisplayName(color(r) + "" + ChatColor.BOLD + display + " Crystal");
        im.setLore(java.util.List.of(
                VetroForgeUtil.tr(plugin, null, "crates.key_lore.line1", VetroForgeUtil.vars("rarity", display)),
                VetroForgeUtil.tr(plugin, null, "crates.key_lore.line2"),
                ChatColor.DARK_GRAY + "Crate: " + ChatColor.WHITE + crateId,
                ChatColor.DARK_GRAY + "(VetroForge)"
        ));
        im.getPersistentDataContainer().set(KEY_FLAG, PersistentDataType.BYTE, (byte)1);
        im.getPersistentDataContainer().set(KEY_ID,   PersistentDataType.STRING, crateId);
        im.getPersistentDataContainer().set(KEY_RAR,  PersistentDataType.STRING, r.name());
        it.setItemMeta(im);
        return it;
    }

    /** Erkennung eines Fallback-Key-Items. */
    public boolean isOurKey(ItemStack it){
        if (it == null || !it.hasItemMeta()) return false;
        var c = it.getItemMeta().getPersistentDataContainer();
        Byte f = c.get(KEY_FLAG, PersistentDataType.BYTE);
        return f != null && f == (byte)1;
    }

    public String getCrateId(ItemStack it){
        if (!isOurKey(it)) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }

    /* ================= Provider Hooks (Reflection) ================= */

    private boolean giveCrazyCrates(Player p, String crateId, int amount){
        try {
            // CrazyCrates v1/v2 – Soft Reflection
            Class<?> api = Class.forName("com.badbones69.crazycrates.api.CrazyCrates");
            Method inst = api.getMethod("getInstance");
            Object cc = inst.invoke(null);
            // v2023+: giveKeys(Player, Crate, int)
            Method getCrate = api.getMethod("getCrateFromName", String.class);
            Object crate = getCrate.invoke(cc, crateId);
            if (crate == null) return false;
            Method give = api.getMethod("addKeys", org.bukkit.OfflinePlayer.class, int.class, Object.class);
            give.invoke(cc, p, amount, crate);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean giveCrateReloaded(Player p, String crateId, int amount){
        try {
            // CrateReloaded API:
            // me.badbones69.crate.api.CrateAPI.getInstance().addKeys(p, crateId, amount)
            Class<?> api = Class.forName("me.badbones69.crate.api.CrateAPI");
            Object inst = api.getMethod("getInstance").invoke(null);
            api.getMethod("addKeys", Player.class, String.class, int.class).invoke(inst, p, crateId, amount);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean giveExcellent(Player p, String crateId, int amount){
        try {
            // ExcellentCrates:
            // su.nightexpress.excellentcrates.CratesAPI.giveKeys(p, crateId, amount)
            Class<?> api = Class.forName("su.nightexpress.excellentcrates.CratesAPI");
            api.getMethod("giveKeys", Player.class, String.class, int.class).invoke(null, p, crateId, amount);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
    private String color(com.vetroforge.items.Rarity r){
        return switch (r){
            case COMMON -> "§7"; case RARE -> "§9"; case EPIC -> "§d"; case LEGENDARY -> "§6"; case MYSTIC -> "§5";
        };
    }
}
