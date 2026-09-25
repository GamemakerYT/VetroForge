package com.vetroforge.raid.obsidian;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.RaidManager;
import com.vetroforge.raid.runes.RuneType;
import com.vetroforge.raid.runes.RuneUtil;
import com.vetroforge.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

public class ExplosionListener implements Listener {

    private final FactionsPlugin plugin;
    private final ObsidianHealthManager hp;

    public ExplosionListener(FactionsPlugin plugin, ObsidianHealthManager hp) {
        this.plugin = plugin;
        this.hp = hp;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent e) {

        RaidManager rm = plugin.getRaidManager();

        Iterator<Block> it = e.blockList().iterator();

        while (it.hasNext()) {

            Block b = it.next();

            if (b.getType() != Material.OBSIDIAN) continue;

            String faction = plugin.getFactionManager().getFactionAt(b.getLocation());
            if (faction == null) continue;
            RaidManager.Session session = rm == null ? null : rm.getSessionByTarget(faction);
            if (session == null) continue;

            it.remove(); // Vanilla destroys nothing; the raid HP system owns this block.

            int damage = hp.tntDamage(session.attackerFaction);
            if (e.getEntity() instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
                if (plugin.getSkillManager() != null) {
                    damage = (int) Math.round(damage * (1.0D + plugin.getSkillManager().getDemolitionBonusPercent(player) / 100.0D));
                }
                if (RuneUtil.hasRune(plugin, player.getInventory().getItemInMainHand(), RuneType.TNT_EXPERT)) {
                    double mul = Math.max(1.0D, plugin.getConfig().getDouble("runes.bonus.tntExpert", 1.20));
                    damage = (int) Math.round(damage * mul);
                }
            }
            int left = hp.damage(b, damage);

            if (left <= 0) {
                rm.recordDestroyedBlock(faction, b, Material.OBSIDIAN);
                rm.addObsidianBreak(faction, null);
                if (e.getEntity() instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player
                        && plugin.getSkillManager() != null) {
                    plugin.getSkillManager().addXp(player, SkillType.RAIDER, 15);
                }
            }
        }
    }
}
