package com.vetroforge.raid.runes;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.RaidText;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class RunesCommand implements CommandExecutor {

    private final FactionsPlugin plugin;
    public RunesCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (a.length < 1) {
            plugin.getRunesGui().open(p);
            return true;
        }
        if (!a[0].equalsIgnoreCase("apply")) {
            RaidText.send(plugin, p, "message.rune_unknown_command"); return true;
        }
        if (a.length < 2) {
            s.sendMessage(ChatColor.YELLOW + "/"+l+" apply <type>"); return true;
        }

        RuneType type;
        try { type = RuneType.valueOf(a[1].toUpperCase()); } catch (Exception e) {
            RaidText.send(plugin, p, "message.rune_invalid"); return true;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) { RaidText.send(plugin, p, "message.rune_hand_required"); return true; }

        if (RuneUtil.hasRune(plugin, hand, type)) {
            RaidText.send(plugin, p, "message.rune_already_applied", "rune", type.name());
            return true;
        }

        int cost = Math.max(0, plugin.getConfig().getInt("runes.cost." + type.name(), 50));
        if (cost > 0 && (plugin.getRaidCrystalManager() == null || !plugin.getRaidCrystalManager().consume(p, cost))) {
            RaidText.send(plugin, p, "message.rune_insufficient_crystals", "cost", cost);
            return true;
        }

        RuneUtil.addRune(plugin, hand, type);
        RaidText.send(plugin, p, "message.rune_added", "rune", type.name(), "cost", cost);
        return true;
    }
}
