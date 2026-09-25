package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.UiDecor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public final class HubCommand implements CommandExecutor {

    private final FactionsPlugin plugin;

    public HubCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(UiDecor.err() + "Nur Spieler.");
            return true;
        }

        String target = plugin.getConfig().getString("proxy.lobby-server", "Lobby");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(target);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            player.sendMessage(UiDecor.pfx() + "§7Verbinde mit §f" + target + "§7...");
        } catch (Exception ex) {
            player.sendMessage(UiDecor.err() + "Hub-Verbindung fehlgeschlagen.");
        }
        return true;
    }
}
