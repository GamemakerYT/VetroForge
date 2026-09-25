package com.vetroforge.commands;

import com.vetroforge.util.GradientUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class StaffCommand implements CommandExecutor {
    private static final String PREFIX = "§8» §7";

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        sender.sendMessage(GradientUtil.gradient("VetroForge Staff Help", "#55FF55", "#FFFFFF"));
        sender.sendMessage(PREFIX + "/staff … §8- §eStaff anzeigen");
        sender.sendMessage(PREFIX + "/maintenance … §8- §eWartung verwalten");
        sender.sendMessage(PREFIX + "/broadcast … §8- §eServer Broadcast");
        sender.sendMessage(PREFIX + "/stats … §8- §eServer-Statistiken");
        sender.sendMessage(PREFIX + "/errors … §8- §ePlugin-Fehler");
        sender.sendMessage(PREFIX + "/ip <Spieler> … §8- §eIP anzeigen");
        sender.sendMessage(PREFIX + "/notify … §8- §eKick/Ban Notify");
        sender.sendMessage(PREFIX + "/onlinetime … §8- §eOnline-Zeit");
        sender.sendMessage(PREFIX + "/slowchat … §8- §eChat-Delay");
        sender.sendMessage(PREFIX + "/playhider … §8- §eSpieler ausblenden");
        sender.sendMessage(Component.text("§8» §7/cratehistory <Spieler> §8- §dCrate-History prüfen")
                .hoverEvent(HoverEvent.showText(Component.text("§7Klicken zum Vorschlagen von /cratehistory ")))
                .clickEvent(ClickEvent.suggestCommand("/cratehistory ")));
        return true;
    }
}
