// src/main/java/com/vetroforge/commands/HelpCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.ChatBoxUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class HelpCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public HelpCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }

        String section = a.length == 0 ? "main" : a[0].toLowerCase(Locale.ROOT);
        List<String> lines = linesFor(p, section);
        if (lines == null) return true;

        p.sendMessage(Component.text(" "));
        p.sendMessage(ChatBoxUtil.boxTop());
        p.sendMessage(ChatBoxUtil.center(legacy(title(section))));
        p.sendMessage(ChatBoxUtil.divider());
        for (String raw : lines) {
            String line = raw.replaceFirst("^/","§8/").replaceAll("§8/§8/","§8//");
            p.sendMessage(ChatBoxUtil.center(legacy(line)));
        }
        p.sendMessage(ChatBoxUtil.boxBottom());
        return true;
    }

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private List<String> linesFor(Player player, String section) {
        return switch (section) {
            case "main", "player", "players" -> List.of(
                    "§c§lVetroForge §8• §7Help",
                    "§8/§fhelp factions §7- Factions, Raid, Warzone",
                    "§8/§fhelp lobby §7- Lobby, Profile, Cosmetics",
                    "§8/§fhelp staff §7- Staff Tools",
                    "§8/§fhelp admin §7- Admin Tools",
                    "§8/§fcommands §7- Alle freigeschalteten Befehle",
                    "§8/§fshop §7- Server-Shop",
                    "§8/§fah §7- Auktionshaus",
                    "§8/§fpv <nr> §7- Private Vaults",
                    "§8/§fdailykit §7- Daily Rank Kit",
                    "§8/§freport <spieler> §7- Spieler melden"
            );
            case "factions", "faction", "f" -> List.of(
                    "§8/§ff §7- Faction-Menü",
                    "§8/§ff create <name> §7- Faction erstellen",
                    "§8/§ff invite <spieler> §7- Spieler einladen",
                    "§8/§ff home §7- Faction-Home",
                    "§8/§ff sethome §7- Faction-Home setzen",
                    "§8/§fraid §7- Raid-Menü",
                    "§8/§fwarzone §7- Warzone-Infos",
                    "§8/§fcrystals §7- Kriegsbeute"
            );
            case "lobby" -> List.of(
                    "§8/§fspawn §7- Spawn",
                    "§8/§fhub §7- Lobby wechseln",
                    "§8/§fcosmetics §7- Cosmetics",
                    "§8/§flanguage §7- Sprache",
                    "§8/§flinkweb <code> §7- Profil im Web verbinden",
                    "§8/§ftoggleplayers §7- Spieler anzeigen/ausblenden"
            );
            case "staff" -> {
                if (!player.hasPermission("vetroforge.staff.help")) {
                    player.sendMessage(ChatColor.RED + "Keine Rechte.");
                    yield null;
                }
                yield List.of(
                        "§8/§fnotify §7- Staff-Nachrichten toggeln",
                        "§8/§ftc [msg] §7- Teamchat",
                        "§8/§ferrors §7- Fehler-GUI",
                        "§8/§fstaffprotect §7- Schutzliste",
                        "§8§oModeration liegt bei VetroCore."
                );
            }
            case "admin" -> {
                if (!player.hasPermission("vetroforge.admin")) {
                    player.sendMessage(ChatColor.RED + "Keine Rechte.");
                    yield null;
                }
                yield List.of(
                        "§8/§fvf doctor §7- Systemdiagnose",
                        "§8/§fadminshop gui §7- Shop bearbeiten",
                        "§8/§fadminah gui §7- Auktionshaus Admin",
                        "§8/§fcrateadmin §7- Crates bearbeiten",
                        "§8/§fadminlootdrop §7- Lootdrop-Regionen",
                        "§8/§feventadmin §7- Events",
                        "§8§oRanks liegen bei VetroPerms."
                );
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unbekannte Help-Seite. Nutze /help.");
                yield null;
            }
        };
    }

    private String title(String section) {
        return switch (section) {
            case "factions", "faction", "f" -> "§cFactions Help";
            case "lobby" -> "§cLobby Help";
            case "staff" -> "§cStaff Help";
            case "admin" -> "§cAdmin Help";
            default -> "§cVetroForge Help";
        };
    }
}
