package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;

public class SpellCommand implements CommandExecutor {
    private final FactionsPlugin plugin;

    public SpellCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length == 0) {
            p.sendMessage("§eVerwendung: /" + label + " <spellId>");
            return true;
        }

        var sm = plugin.getSpellManager();
        if (sm == null) {
            p.sendMessage("§cSpell-System ist nicht verfügbar.");
            return true;
        }

        String spellId = args[0].toLowerCase(Locale.ROOT);
        boolean ok = tryCast(sm, p, spellId);

        if (!ok) p.sendMessage("§cZauber '" + spellId + "' existiert nicht oder konnte nicht gewirkt werden.");
        return true;
    }

    /** Versucht gängige Methodennamen/Signaturen im SpellManager. */
    private boolean tryCast(Object manager, Player p, String spellId) {
        Class<?> cls = manager.getClass();

        // 1) Bevorzugt: (Player, String)
        for (String name : new String[]{"castSpell", "cast", "useSpell", "use"}) {
            try {
                Method m = cls.getMethod(name, Player.class, String.class);
                Object ret = m.invoke(manager, p, spellId);
                return (ret instanceof Boolean b) ? b : true;
            } catch (NoSuchMethodException ignored) { } catch (Throwable ignored) { }
        }

        // 2) Alternative: (CommandSender, String)
        for (String name : new String[]{"castSpell", "cast", "useSpell", "use"}) {
            try {
                Method m = cls.getMethod(name, CommandSender.class, String.class);
                Object ret = m.invoke(manager, p, spellId);
                return (ret instanceof Boolean b) ? b : true;
            } catch (NoSuchMethodException ignored) { } catch (Throwable ignored) { }
        }

        // 3) Alternative: (Player, String, int) – z.B. Level/Stufe
        for (String name : new String[]{"castSpell", "cast", "useSpell", "use"}) {
            try {
                Method m = cls.getMethod(name, Player.class, String.class, int.class);
                Object ret = m.invoke(manager, p, spellId, 1);
                return (ret instanceof Boolean b) ? b : true;
            } catch (NoSuchMethodException ignored) { } catch (Throwable ignored) { }
        }

        return false;
    }
}