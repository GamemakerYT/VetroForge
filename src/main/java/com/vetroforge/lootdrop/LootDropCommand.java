// src/main/java/com/vetroforge/lootdrop/LootDropCommand.java
package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.lootdrop.model.Tier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class LootDropCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public LootDropCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.admin")) { s.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }

        if (a.length == 0) { sendHelp(s); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "here" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Nur ingame."); return true; }
                Tier t = (a.length >= 2) ? parseTier(a[1]) : null;
                Location sky = p.getLocation().clone().add(0, 40, 0);
                plugin.getLootDropManager().spawnRandomDrop(sky, t);
                s.sendMessage(ChatColor.GREEN+"OK.");
            }
            case "at" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Nur ingame."); return true; }
                ParseResult pr = parseAtArgs(p, a);
                if (pr.error != null) { s.sendMessage(ChatColor.RED + pr.error); return true; }

                double ySky = (pr.hasExplicitY)
                        ? pr.y + 40.0
                        : pr.world.getHighestBlockYAt((int)Math.floor(pr.x), (int)Math.floor(pr.z)) + 40.0;

                Location sky = new Location(pr.world, pr.x, ySky, pr.z);
                plugin.getLootDropManager().spawnRandomDrop(sky, pr.tier);
                s.sendMessage(ChatColor.GREEN + "OK.");
            }
            case "storm" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Nur ingame."); return true; }
                int count = (a.length >= 2) ? safeInt(a[1], 5) : 5;
                double spread = (a.length >= 3) ? safeDouble(a[2], 40.0) : 40.0;
                plugin.getLootDropManager().storm(p.getLocation(), count, spread);
                s.sendMessage(ChatColor.GREEN+"Storm gestartet ("+count+" MYTHIC).");
            }
            case "flare" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Nur ingame."); return true; }
                Tier t = (a.length >= 2) ? parseTier(a[1]) : null;
                plugin.getLootDropManager().flareAt(p, t);
                s.sendMessage(ChatColor.GREEN+"Flare gezündet.");
            }
            case "list" -> {
                sendList(s);
            }
            case "remove", "cancel" -> {
                if (a.length < 2) { s.sendMessage(ChatColor.RED + "Nutze: /lootdrop remove <id-anfang>"); return true; }
                sendRemove(s, a[1]);
            }
            default -> sendHelp(s);
        }
        return true;
    }

    private void sendHelp(CommandSender s){
        s.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Lootdrop" + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + "Übersicht");
        s.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━");
        s.sendMessage(ChatColor.RED + "/lootdrop here " + ChatColor.DARK_GRAY + "[tier]" + ChatColor.GRAY + "     Drop über dir");
        s.sendMessage(ChatColor.RED + "/lootdrop at " + ChatColor.DARK_GRAY + "<x> <z> [tier]" + ChatColor.GRAY + "  Drop bei Koordinaten");
        s.sendMessage(ChatColor.RED + "/lootdrop storm " + ChatColor.DARK_GRAY + "<n> [spread]" + ChatColor.GRAY + "  Mythic-Cluster");
        s.sendMessage(ChatColor.RED + "/lootdrop flare " + ChatColor.DARK_GRAY + "[tier]" + ChatColor.GRAY + "     Drop mit Effekt");
        s.sendMessage(ChatColor.RED + "/lootdrop list" + ChatColor.GRAY + "                Aktive Drops anzeigen");
        s.sendMessage(ChatColor.RED + "/lootdrop remove " + ChatColor.DARK_GRAY + "<id>" + ChatColor.GRAY + "     Drop entfernen");
        s.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━");
        s.sendMessage(ChatColor.GRAY + "Tiers: " + ChatColor.WHITE + Arrays.stream(Tier.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")));
    }

    private void sendList(CommandSender s) {
        var active = plugin.getLootDropManager().getActive();
        s.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Lootdrop" + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + "Aktive Drops " + ChatColor.DARK_GRAY + "(" + active.size() + ")");
        if (active.isEmpty()) {
            s.sendMessage(ChatColor.GRAY + "Keine aktiven Drops.");
            return;
        }
        for (var drop : active) {
            Location loc = drop.location();
            String world = loc.getWorld() == null ? "-" : loc.getWorld().getName();
            s.sendMessage(ChatColor.DARK_GRAY + "• " + ChatColor.WHITE + drop.id().toString().substring(0, 8)
                    + ChatColor.GRAY + "  " + drop.tier().displayName()
                    + ChatColor.GRAY + "  " + world + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                    + ChatColor.GRAY + "  " + (drop.unlocked() ? ChatColor.GREEN + "offen" : ChatColor.YELLOW + "" + drop.unlockSecondsLeft() + "s")
                    + ChatColor.GRAY + "  " + drop.secondsLeft() + "s übrig");
        }
    }

    private void sendRemove(CommandSender s, String idPrefix) {
        var active = plugin.getLootDropManager().getActive();
        for (var drop : active) {
            if (drop.id().toString().startsWith(idPrefix.toLowerCase(Locale.ROOT))) {
                drop.despawn();
                s.sendMessage(ChatColor.GREEN + "Drop " + ChatColor.WHITE + idPrefix + ChatColor.GREEN + " entfernt.");
                return;
            }
        }
        s.sendMessage(ChatColor.RED + "Kein aktiver Drop mit dieser ID. Nutze /lootdrop list.");
    }

    /* ---------------------- Parser ---------------------- */

    private static final class ParseResult {
        World world;
        double x; double y; double z;
        boolean hasExplicitY;
        Tier tier;
        String error;
    }

    private ParseResult parseAtArgs(Player base, String[] a){
        ParseResult r = new ParseResult();
        r.tier = null;
        r.world = base.getWorld();

        List<String> args = new ArrayList<>(Arrays.asList(a));
        args.remove(0);

        if (args.size() < 2) { r.error = "Benutzung: /lootdrop at <x> <z> [tier] | <x> <y> <z> [tier] | <world> <x> <z> [tier] | <world> <x> <y> <z> [tier]"; return r; }

        int idx = 0;

        World maybeWorld = Bukkit.getWorld(args.get(0));
        if (maybeWorld != null) {
            r.world = maybeWorld;
            idx++;
            if (args.size() - idx < 2) { r.error = "Zu wenige Argumente für Welt. Erwartet: <world> <x> <z> [tier] oder <world> <x> <y> <z> [tier]"; return r; }
        }

        String sx = args.get(idx++);
        String s2 = args.get(idx++);
        if (args.size() - idx >= 1) {
            r.hasExplicitY = true;
            r.x = parseCoord(sx, base.getLocation().getX());
            r.y = parseCoord(s2, base.getLocation().getY());
            String sz = args.get(idx++);
            r.z = parseCoord(sz, base.getLocation().getZ());
        } else {
            r.hasExplicitY = false;
            r.x = parseCoord(sx, base.getLocation().getX());
            r.z = parseCoord(s2, base.getLocation().getZ());
            r.y = Double.NaN;
        }

        if (args.size() - idx >= 1) r.tier = parseTier(args.get(idx));
        return r;
    }

    private Tier parseTier(String s){
        if (s == null) return null;
        try { return Tier.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e){ return null; }
    }

    private double parseCoord(String token, double base){
        token = token.trim();
        if (token.startsWith("~")) {
            String off = token.substring(1).trim();
            if (off.isEmpty()) return base;
            return base + safeDouble(off, 0.0);
        }
        return safeDouble(token, base);
    }

    private int safeInt(String s, int def){ try { return Integer.parseInt(s.trim()); } catch (Exception e){ return def; } }
    private double safeDouble(String s, double def){ try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e){ return def; } }

    /* ---------------------- TabComplete ---------------------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        // Basis-Subcommands
        if (args.length == 1) return startsWith(args[0], Arrays.asList("here","at","storm","flare","list","remove"));

        if ("remove".equalsIgnoreCase(args[0]) && args.length == 2) {
            List<String> ids = plugin.getLootDropManager().getActive().stream()
                    .map(d -> d.id().toString().substring(0, 8)).collect(Collectors.toList());
            return startsWith(args[1], ids);
        }

        // /lootdrop at ...
        if ("at".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) return Collections.emptyList();
            World curWorld = p.getWorld();
            Location loc = p.getLocation();

            // arg1 nach "at": Weltname ODER X
            if (args.length == 2) {
                List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                List<String> coords = coordSuggestions(loc.getX(), args[1]);
                coords.add("~"); // immer ~ anbieten
                worlds.addAll(coords);
                return startsWith(args[1], dedup(worlds));
            }

            // Prüfen, ob arg2 exakt eine existierende Welt ist → dann folgt X
            boolean worldGiven = (Bukkit.getWorld(args[1]) != null);
            World w = worldGiven ? Bukkit.getWorld(args[1]) : curWorld;

            // arg3: wenn Welt gegeben → X, sonst Y/Z
            if (args.length == 3) {
                if (worldGiven) {
                    return startsWith(args[2], coordSuggestions(loc.getX(), args[2]));
                } else {
                    // könnte Y oder Z werden – gib generische Vorschläge
                    List<String> sugg = coordSuggestions(loc.getY(), args[2]);
                    sugg.addAll(coordSuggestions(loc.getZ(), args[2]));
                    sugg.add("~");
                    return startsWith(args[2], dedup(sugg));
                }
            }

            // arg4: wenn Welt gegeben → Y/Z, sonst Tier
            if (args.length == 4) {
                if (worldGiven) {
                    // hier entweder Y oder Z – generisch vorschlagen
                    List<String> sugg = coordSuggestions(loc.getY(), args[3]);
                    sugg.addAll(coordSuggestions(loc.getZ(), args[3]));
                    sugg.add("~");
                    return startsWith(args[3], dedup(sugg));
                } else {
                    return startsWith(args[3], Arrays.asList("bronze","silver","gold","mythic"));
                }
            }

            // arg5: (Welt gegeben) → Z ODER Tier, (ohne Welt) → nichts
            if (args.length == 5) {
                if (worldGiven) {
                    // wenn schon 3 Koords geschrieben wurden, ist das hier Tier
                    return startsWith(args[4], Arrays.asList("bronze","silver","gold","mythic"));
                }
            }

            // arg6: (Welt + X + Y + Z) → Tier
            if (args.length == 6) {
                return startsWith(args[5], Arrays.asList("bronze","silver","gold","mythic"));
            }

            return Collections.emptyList();
        }

        // /lootdrop storm ...
        if ("storm".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return startsWith(args[1], Arrays.asList("3","5","7","9","12"));
            if (args.length == 3) return startsWith(args[2], Arrays.asList("20","40","60","80"));
        }

        // /lootdrop here|flare [tier]
        if ((args[0].equalsIgnoreCase("here") || args[0].equalsIgnoreCase("flare")) && args.length == 2) {
            return startsWith(args[1], Arrays.asList("bronze","silver","gold","mythic"));
        }

        return Collections.emptyList();
    }

    /* ---------------------- Helpers für Vorschläge ---------------------- */

    private List<String> coordSuggestions(double base, String prefix){
        int b = (int)Math.round(base);
        List<String> list = new ArrayList<>(Arrays.asList(
                "~","~1","~-1","~5","~-5","~10","~-10",
                String.valueOf(b),
                String.valueOf(b+5), String.valueOf(b-5),
                String.valueOf(b+10), String.valueOf(b-10),
                String.valueOf(b+100), String.valueOf(b-100)
        ));
        return startsWith(prefix, dedup(list));
    }

    private List<String> startsWith(String prefix, List<String> items){
        if (prefix == null || prefix.isEmpty()) return items;
        String p = prefix.toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .limit(50)
                .collect(Collectors.toList());
    }

    private List<String> dedup(List<String> in){
        LinkedHashSet<String> set = new LinkedHashSet<>(in);
        return new ArrayList<>(set);
    }
}
