// src/main/java/com/vetroforge/commands/SkillCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.skills.SkillType;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SkillCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public SkillCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length >= 1) {
            SkillType type = parse(args[0]);
            if (type != null && plugin.getSkillManager().isEnabled(p, type)) {
                plugin.getSkillsGui().openDetail(p, type);
                return true;
            }
        }
        plugin.getSkillsGui().openMain(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (SkillType type : SkillType.values()) {
                String value = type.name().toLowerCase(Locale.ROOT);
                if (value.startsWith(prefix)) out.add(value);
            }
        }
        return out;
    }

    private SkillType parse(String raw) {
        if (raw == null) return null;
        try {
            return SkillType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
