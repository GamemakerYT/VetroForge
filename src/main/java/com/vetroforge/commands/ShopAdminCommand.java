// src/main/java/com/vetroforge/commands/ShopAdminCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commerce.AICommerceManager;
import org.bukkit.ChatColor;
import org.bukkit.command.*;

import java.util.*;

public class ShopAdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "vetroforge.shop.tune";

    private final FactionsPlugin plugin;

    public ShopAdminCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission(PERM) && !s.hasPermission("vetroforge.shop.admin")) {
            s.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }

        if (a.length == 0 || !"tune".equalsIgnoreCase(a[0])) {
            printUsage(s, l);
            return true;
        }

        boolean sell = false;   // default: buy
        boolean apply = false;

        for (int i=1;i<a.length;i++){
            String tok = a[i].toLowerCase(Locale.ROOT);
            if (tok.equals("sell")) sell = true;
            else if (tok.equals("buy")) sell = false;
            else if (tok.equals("--apply") || tok.equals("-a")) apply = true;
        }

        // Manager vorhanden?
        if (plugin.getAiCommerceManager() == null) {
            s.sendMessage(ChatColor.RED + "AICommerceManager fehlt.");
            return true;
        }
        if (plugin.getGeneralShopManager() == null) {
            s.sendMessage(ChatColor.RED + "GeneralShopManager fehlt (für Transaktionsfenster).");
            return true;
        }

        // Transaktionen ziehen
        Map<String,Integer> recent;
        try {
            recent = plugin.getGeneralShopManager().drainRecent(sell);
        } catch (Throwable t) {
            s.sendMessage(ChatColor.RED + "Konnte Transaktionsfenster nicht lesen.");
            return true;
        }

        if (recent == null || recent.isEmpty()) {
            s.sendMessage(ChatColor.GRAY + "Keine aktuellen Transaktionen – nichts zu tunen.");
            return true;
        }

        AICommerceManager ai = plugin.getAiCommerceManager();
        Map<String, Double> proposals = ai.proposePricesMap(recent, sell);
        if (proposals.isEmpty()) {
            s.sendMessage(ChatColor.GRAY + "AI lieferte keine Vorschläge.");
            return true;
        }

        s.sendMessage(ChatColor.DARK_RED + "—————— " + ChatColor.RED + "VetroForge • Shop-Tuning" + ChatColor.DARK_RED + " ——————");
        s.sendMessage(ChatColor.GRAY + "Modus: " + (sell ? ChatColor.YELLOW+"Sell-Preise" : ChatColor.YELLOW+"Buy-Preise"));
        for (Map.Entry<String, Double> e : proposals.entrySet()) {
            s.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + e.getKey()
                    + ChatColor.GRAY + " → " + ChatColor.GREEN + String.format(Locale.US,"%.2f", e.getValue()));
        }
        s.sendMessage(ChatColor.DARK_RED + "——————————————————————————————");

        if (apply) {
            ai.applyPriceProposals(proposals, sell);
            s.sendMessage(ChatColor.GREEN + "Preise angewendet & gespeichert.");
        } else {
            s.sendMessage(ChatColor.GRAY + "Hinweis: mit "
                    + ChatColor.YELLOW + "/"+l+" tune " + (sell?"sell":"buy") + " --apply"
                    + ChatColor.GRAY + " anwenden.");
        }
        return true;
    }

    private void printUsage(CommandSender s, String label) {
        s.sendMessage(ChatColor.YELLOW + "Verwendung:");
        s.sendMessage(ChatColor.GRAY + " /" + label + " tune buy [--apply]");
        s.sendMessage(ChatColor.GRAY + " /" + label + " tune sell [--apply]");
        s.sendMessage(ChatColor.DARK_GRAY + "  Nutzt jüngste Transaktionen und passt Preise ~±25% an.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            if ("tune".startsWith(a[0].toLowerCase(Locale.ROOT))) out.add("tune");
        } else if (a.length == 2 && "tune".equalsIgnoreCase(a[0])) {
            for (String m : new String[]{"buy","sell"})
                if (m.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(m);
        } else if (a.length == 3 && "tune".equalsIgnoreCase(a[0])) {
            if ("--apply".startsWith(a[2].toLowerCase(Locale.ROOT))) out.add("--apply");
        }
        return out;
    }
}
