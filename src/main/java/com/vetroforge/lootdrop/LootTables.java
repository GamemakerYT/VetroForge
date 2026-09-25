package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import com.vetroforge.lootdrop.model.Tier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LootTables {

    private final FactionsPlugin plugin;
    private final Random rnd;

    public LootTables(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.rnd = ThreadLocalRandom.current();
    }

    /** Rollt Items basierend auf Tier; penalty ∈ [0..1+], doubleDrop ⇒ extra Roll. */
    public List<ItemStack> roll(Tier tier, double penalty, boolean doubleDrop) {
        List<ItemStack> out = new ArrayList<>();
        switch (tier) {
            case BRONZE -> out.addAll(rollBronze(penalty));
            case SILVER -> out.addAll(rollSilver(penalty));
            case GOLD   -> out.addAll(rollGold(penalty));
            case MYTHIC -> out.addAll(rollMythic(penalty));
        }
        if (doubleDrop) {
            // zweite, abgeschwächte Runde
            out.addAll(roll(tier, Math.min(1.5, penalty + 0.25), false));
        }
        return out;
    }

    private List<ItemStack> rollBronze(double pen){
        return highTierLoot(Rarity.LEGENDARY, false);
    }

    private List<ItemStack> rollSilver(double pen){
        return highTierLoot(Rarity.LEGENDARY, false);
    }

    private List<ItemStack> rollGold(double pen){
        return highTierLoot(Rarity.LEGENDARY, false);
    }

    private List<ItemStack> rollMythic(double pen){
        return highTierLoot(Rarity.MYSTIC, true);
    }

    private List<ItemStack> highTierLoot(Rarity rarity, boolean mystic) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack resource = guaranteedResource();
        if (resource != null) out.add(resource);
        out.add(xpBottle(300));
        out.add(LootItemFactory.rollKey(plugin, rarity, rnd));

        Weighted<ItemStack> rare = new Weighted<>();
        rare.add(LootItemFactory.customEnchantBook(plugin, rarity), 15);
        rare.add(LootItemFactory.randomArmorPiece(plugin, rarity), 35);
        rare.add(LootItemFactory.shadowblade(plugin, rarity), 20);
        if (mystic) rare.add(LootItemFactory.dragonsBreath(plugin), 12);
        if (mystic && plugin.getConfig().getBoolean("customitems.eventDrops.enabled", true)) {
            rare.add(LootItemFactory.eventItem(plugin), 8);
        }
        ItemStack high = rare.pick(rnd);
        if (high != null) out.add(high);
        return out;
    }

    /* ===== Helpers ===== */

    private ItemStack guaranteedResource() {
        Weighted<ItemStack> resources = new Weighted<>();
        resources.add(simple(Material.ENDER_PEARL, "§5Enderperlen x30", 30), 20);
        resources.add(simple(Material.OBSIDIAN, "§5Obsidian x64", 64), 30);
        resources.add(simple(Material.TNT, "§cTNT x64", 64), 25);
        return resources.pick(rnd);
    }

    private ItemStack simple(Material m, String name, int amount){
        ItemStack it = new ItemStack(m, amount);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack token(String name){
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        // hier Tag „weapon-xp“ setzen
        return it;
    }

    private ItemStack xpBottle(int xp) {
        ItemStack it = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§aXP-Flasche +" + xp);
        im.setLore(List.of("§7Gibt §f" + xp + " Level-XP§7 beim Anwenden."));
        im.getPersistentDataContainer().set(new NamespacedKey(plugin, "level_xp_bottle"), PersistentDataType.INTEGER, xp);
        it.setItemMeta(im);
        return it;
    }

    /* einfacher Gewichtspicker */
    private static final class Weighted<T> {
        private final List<T> values = new ArrayList<>();
        private final List<Integer> weights = new ArrayList<>();
        private int sum = 0;

        void add(T v, int w){ if (w<=0) return; values.add(v); weights.add(w); sum+=w; }
        void add(T v, int amount, int w){ add(v, w); } // amount wird über ItemStack-Menge abgebildet
        T pick(Random r){
            if (values.isEmpty()) return null;
            int x = r.nextInt(Math.max(1,sum));
            for (int i=0;i<values.size();i++){
                x -= weights.get(i);
                if (x < 0) return values.get(i);
            }
            return values.get(values.size()-1);
        }
    }
}
