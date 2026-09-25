// src/main/java/com/vetroforge/commands/StoryCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.StoryManager;
import com.vetroforge.manager.StoryManager.Story;
import com.vetroforge.manager.StoryManager.Step;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public class StoryCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;

    public StoryCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(color(plugin.getI18n().tr("de", "common.players_only")));
            return true;
        }

        if (a.length == 0) return showStatus(p);

        if (a[0].equalsIgnoreCase("gui")) {
            var gui = plugin.getStoryGuiManager();
            if (gui != null) gui.openMain(p);
            else showList(p);            return true;
        }

        if (a[0].equalsIgnoreCase("list")) return showList(p);

        if (a[0].equalsIgnoreCase("start") && a.length >= 2) {
            String id = a[1];
            Story st = plugin.getStoryManager().getStory(id);
            if (st == null) {
                p.sendMessage(color(plugin.getI18n().tr(p, "story.cmd.unknown_id", Map.of("id", id))));
                return true;
            }
            if (plugin.getStoryManager().hasActive(p)) {
                p.sendMessage(color(plugin.getI18n().tr(p, "story.cmd.already_active")));
                return true;
            }
            plugin.getStoryManager().start(p, id);
            return true;
        }

        if (a[0].equalsIgnoreCase("abandon")) {
            plugin.getStoryManager().abandon(p);
            p.sendMessage(color(plugin.getI18n().tr(p, "story.cmd.abandoned")));
            return true;
        }

        if (a[0].equalsIgnoreCase("admin") && (p.isOp() || p.hasPermission("vetroforge.admin"))) {
            if (a.length >= 2 && a[1].equalsIgnoreCase("complete")) {
                plugin.getStoryManager().adminComplete(p);
                p.sendMessage(color(plugin.getI18n().tr(p, "story.cmd.admin.completed")));
                return true;
            }
            p.sendMessage(color(plugin.getI18n().tr(p, "story.cmd.admin.usage")));
            return true;
        }

        p.sendMessage(color(plugin.getI18n().tr(p, "story.cmd.usage")));
        return true;
    }

    private boolean showStatus(Player p) {
        StoryManager sm = plugin.getStoryManager();
        String id = sm.getActiveId(p);
        if (id == null) {
            p.sendMessage(color(plugin.getI18n().tr(p, "story.status.none_active")));
            p.sendMessage(color(plugin.getI18n().tr(p, "story.status.prompt_list")));
            return true;
        }
        Story st = sm.getStory(id);
        if (st == null) { p.sendMessage("§cStory-Daten fehlen."); return true; }
        int idx = sm.getActiveStepIndex(p);
        int prog = sm.getActiveProgress(p);
        if (idx < 0 || idx >= st.steps.size()) { p.sendMessage("§cUngültiger Schritt."); return true; }
        Step step = st.steps.get(idx);

        p.sendMessage("§8—————— §cVetro§6Forge §7• §fStory §8——————");
        p.sendMessage(color(plugin.getI18n().tr(p,"story.status.header",
                Map.of("title", ChatColor.stripColor(st.title), "id", st.id))));
        p.sendMessage(color(plugin.getI18n().tr(p,"story.status.step_line",
                Map.of("idx", String.valueOf(idx+1), "total", String.valueOf(st.steps.size()),
                        "text", ChatColor.translateAlternateColorCodes('&', step.display)))));
        p.sendMessage(color(plugin.getI18n().tr(p,"story.status.progress_line",
                Map.of("cur", String.valueOf(prog), "max", String.valueOf(step.target)))));
        p.sendMessage("§8——————————————————————————————");
        return true;
    }

    private boolean showList(Player p) {
        Collection<Story> all = plugin.getStoryManager().getAllStories();
        if (all.isEmpty()) {
            p.sendMessage(color(plugin.getI18n().tr(p, "story.list.empty")));
            return true;
        }
        List<String> completed = plugin.getStoryManager().getCompleted(p);
        String active = plugin.getStoryManager().getActiveId(p);

        p.sendMessage(color(plugin.getI18n().tr(p, "story.list.header")));
        for (Story st : all) {
            String state = plugin.getI18n().tr(p, "story.list.state.available");
            if (completed.contains(st.id)) state = plugin.getI18n().tr(p, "story.list.state.done");
            else if (st.id.equalsIgnoreCase(active)) state = plugin.getI18n().tr(p, "story.list.state.active");
            p.sendMessage(color(plugin.getI18n().tr(p, "story.list.line",
                    Map.of("title", ChatColor.stripColor(st.title), "id", st.id, "state", state))));
        }
        p.sendMessage("§8——————————————————————————————");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            for (String k : new String[]{"gui","list","start","abandon","admin"}) {
                if (k.startsWith(A[0].toLowerCase(Locale.ROOT))) out.add(k);
            }
            return out;
        }
        if (A.length == 2 && A[0].equalsIgnoreCase("start")) {
            for (Story st : plugin.getStoryManager().getAllStories()) {
                if (st.id.toLowerCase(Locale.ROOT).startsWith(A[1].toLowerCase(Locale.ROOT))) out.add(st.id);
            }
            return out;
        }
        if (A.length == 2 && A[0].equalsIgnoreCase("admin")) {
            if ("complete".startsWith(A[1].toLowerCase(Locale.ROOT))) out.add("complete");
            return out;
        }
        return out;
    }

    private String color(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
}