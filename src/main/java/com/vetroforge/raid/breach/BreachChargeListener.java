package com.vetroforge.raid.breach;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.obsidian.ObsidianHealthManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.vetroforge.raid.RaidText;

public class BreachChargeListener implements Listener {

    public static final String KEY_ID = "breach_charge";

    private final FactionsPlugin plugin;
    private final ObsidianHealthManager hp;
    private final NamespacedKey key;

    public BreachChargeListener(FactionsPlugin plugin, ObsidianHealthManager hp) {
        this.plugin = plugin;
        this.hp = hp;
        this.key = new NamespacedKey(plugin, KEY_ID);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // 🔥 DAS HAT DIR GEFEHLT
    public static ItemStack makeCharge(FactionsPlugin plugin, int amount) {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(com.vetroforge.util.VetroForgeUtil.color(
                plugin.getI18n().tr("de", "raid.item.breach_charge")
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, KEY_ID),
                PersistentDataType.BYTE,
                (byte) 1
        );

        item.setItemMeta(meta);
        item.setAmount(amount);
        return item;
    }

    private boolean isCharge(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        Byte b = item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.BYTE);

        return b != null && b == 1;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {

        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null) return;
        if (!isCharge(e.getItem())) return;

        Block b = e.getClickedBlock();
        if (b.getType() != Material.OBSIDIAN) return;

        e.setCancelled(true);

        Player player = e.getPlayer();
        if (plugin.getRaidSessionService() == null || !plugin.getRaidSessionService().isInRaid(player)) {
            RaidText.send(plugin, player, "message.breach_active_raid_only");
            return;
        }

        String faction = plugin.getFactionManager().getFactionAt(b.getLocation());
        String target = plugin.getRaidSessionService().getJoinedRaidTarget(player);
        if (faction == null || target == null || !faction.equalsIgnoreCase(target)
                || plugin.getRaidManager() == null
                || plugin.getRaidManager().getSessionByTarget(faction) == null) {
            RaidText.send(plugin, player, "message.breach_wrong_target");
            return;
        }

        consumeOne(e.getItem());
        RaidText.send(plugin, player, "message.breach_armed");

        int dmg = plugin.getConfig().getInt("raid.obsidian.breach", 260);
        if (plugin.getFactionUpgradeManager() != null) {
            String attacker = plugin.getFactionManager().getFactionOf(player.getUniqueId());
            dmg = plugin.getFactionUpgradeManager().applyTntDamage(attacker, dmg);
        }
        int finalDamage = dmg;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (b.getType() != Material.OBSIDIAN) return;

            int left = hp.damage(b, finalDamage);

            if (left <= 0) {
                plugin.getRaidManager().addObsidianBreak(faction, player.getUniqueId());
            }

        }, 80L);
    }

    private void consumeOne(ItemStack item) {
        if (item == null) return;
        if (item.getAmount() <= 1) {
            item.setAmount(0);
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }
}
