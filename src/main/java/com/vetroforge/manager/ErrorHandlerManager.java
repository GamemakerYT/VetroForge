// src/main/java/com/vetroforge/manager/ErrorHandlerManager.java
package com.vetroforge.manager;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ErrorHandlerManager {
    private final JavaPlugin plugin;
    public ErrorHandlerManager(JavaPlugin plugin){ this.plugin = plugin; }

    public void notifyStaff(String message){
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("vetroforge.notify")) p.sendMessage("§c[Error] §7" + message);
        }
        plugin.getLogger().warning(message);
    }

    public void safeRun(Runnable r, String context){
        try { r.run(); } catch (Throwable t) {
            notifyStaff("Fehler in "+context+": "+t.getMessage());
        }
    }

    public void replyError(CommandSender s, String msg){
        s.sendMessage("§c" + msg);
    }
}