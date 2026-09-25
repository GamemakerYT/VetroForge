// src/main/java/com/vetroforge/commands/SlowChatCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.*;

public class SlowChatCommand extends BaseCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public SlowChatCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s.hasPermission("vetroforge.staff.slowchat") || s.hasPermission("vetroforge.chat.slow"))) {
            s.sendMessage(ChatColor.RED + "Keine Rechte."); return true;
        }
        int secs = 5;
        if (a.length >= 1) try { secs = Math.max(0, Integer.parseInt(a[0])); } catch (Exception ignored) {}

        plugin.getChatLimiter().setSlowSeconds(secs);

        sendFramed(s, "Chat",
                (secs == 0 ? "&7SlowChat: &aaus" : "&7SlowChat: &e" + secs + "s"),
                "&7Bypass: &evetroforge.chat.slow.bypass"
        );
        return true;
    }
}