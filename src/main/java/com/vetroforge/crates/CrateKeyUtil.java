// src/main/java/com/vetroforge/crates/CrateKeyUtil.java
package com.vetroforge.crates;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CrateKeyUtil {
    private CrateKeyUtil(){}

    private static NamespacedKey KEY(FactionsPlugin p){ return new NamespacedKey(p, "crate_key"); }
    private static NamespacedKey TIER(FactionsPlugin p){ return new NamespacedKey(p, "crate_tier"); }

    public static ItemStack make(FactionsPlugin p, CrateKeyType t, int amount){
        int safeAmount = Math.max(1, Math.min(64, amount));
        ItemStack it = new ItemStack(Material.NETHER_STAR, safeAmount);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(t.display);
        String display = t.name().charAt(0) + t.name().substring(1).toLowerCase(java.util.Locale.ROOT);
        im.setLore(java.util.List.of(
                VetroForgeUtil.tr(p, null, "crates.key_lore.line1", VetroForgeUtil.vars("rarity", display)),
                VetroForgeUtil.tr(p, null, "crates.key_lore.line2")
        ));
        im.getPersistentDataContainer().set(KEY(p), PersistentDataType.BYTE, (byte)1);
        im.getPersistentDataContainer().set(TIER(p), PersistentDataType.STRING, t.name());
        it.setItemMeta(im);
        return it;
    }

    public static CrateKeyType read(FactionsPlugin p, ItemStack it){
        if (it == null || !it.hasItemMeta()) return null;
        var c = it.getItemMeta().getPersistentDataContainer();
        Byte b = c.get(KEY(p), PersistentDataType.BYTE);
        String s = c.get(TIER(p), PersistentDataType.STRING);
        if (b==null || b!=1 || s==null) return null;
        try { return CrateKeyType.valueOf(s); } catch (Exception ex){ return null; }
    }
}
