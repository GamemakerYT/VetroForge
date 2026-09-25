// src/main/java/com/vetroforge/commands/OptimizeCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import org.bukkit.Bukkit;
import org.bukkit.command.*;

public class OptimizeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.admin") && !s.isOp()) { s.sendMessage(UiDecor.noPermission()); return true; }
        long before = Runtime.getRuntime().freeMemory();
        System.gc();
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("VetroForge"), () -> {
            long after = Runtime.getRuntime().freeMemory();
            s.sendMessage("§aGC done. §7Freier Speicher: §f"+(after/1024/1024)+"MB §8(+"+((after-before)/1024/1024)+"MB)");
        }, 40L);
        return true;
    }
}