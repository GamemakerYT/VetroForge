package com.vetroforge;

import com.vetroforge.commands.HologramCommand;
import com.vetroforge.commands.LootDropCommand;
import com.vetroforge.commands.SkillCommand;
import com.vetroforge.hologram.HologramManager;
import com.vetroforge.listeners.ChatFormatListener;
import com.vetroforge.listeners.SkillExperienceListener;
import com.vetroforge.listeners.SkillProfileListener;
import com.vetroforge.scoreboard.ScoreboardService;
import com.vetroforge.skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class FactionsPlugin extends JavaPlugin {

    private static FactionsPlugin instance;

    private SkillManager skillManager;
    private HologramManager hologramManager;
    private ScoreboardService scoreboardService;
    private Logger prefixedLogger;

    public static FactionsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        setupLogger();

        this.skillManager = new SkillManager(this);
        this.hologramManager = new HologramManager(this);
        this.scoreboardService = new ScoreboardService(this);

        Bukkit.getPluginManager().registerEvents(new SkillProfileListener(skillManager), this);
        Bukkit.getPluginManager().registerEvents(new SkillExperienceListener(skillManager), this);
        Bukkit.getPluginManager().registerEvents(scoreboardService, this);
        Bukkit.getPluginManager().registerEvents(new ChatFormatListener(this, scoreboardService), this);
        Bukkit.getPluginManager().registerEvents(hologramManager, this);

        registerCommands();

        scoreboardService.start();
        hologramManager.loadAndSpawnAll();
        skillManager.loadOnlinePlayers();
        Bukkit.getOnlinePlayers().forEach(scoreboardService::setup);
    }

    @Override
    public void onDisable() {
        if (skillManager != null) {
            skillManager.saveAll();
            skillManager.shutdown();
        }
        if (hologramManager != null) {
            hologramManager.shutdown();
        }
        if (scoreboardService != null) {
            scoreboardService.shutdown();
        }
        instance = null;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public ScoreboardService getScoreboardService() {
        return scoreboardService;
    }

    public Logger getPrefixedLogger() {
        return prefixedLogger;
    }

    private void setupLogger() {
        String prefix = getConfig().getString("logger-prefix", "[VetroForge]");
        this.prefixedLogger = new PrefixedLogger(getLogger(), prefix);
    }

    private void registerCommands() {
        LootDropCommand lootDropCommand = new LootDropCommand(this);
        PluginCommand lootdrop = getCommand("lootdrop");
        if (lootdrop != null) {
            lootdrop.setExecutor(lootDropCommand);
            lootdrop.setTabCompleter(lootDropCommand);
        }

        HologramCommand hologramCommand = new HologramCommand(hologramManager);
        PluginCommand holo = getCommand("holo");
        if (holo != null) {
            holo.setExecutor(hologramCommand);
            holo.setTabCompleter(hologramCommand);
        }

        SkillCommand skillCommand = new SkillCommand(skillManager);
        PluginCommand skill = getCommand("skill");
        if (skill != null) {
            skill.setExecutor(skillCommand);
            skill.setTabCompleter(skillCommand);
        }
    }
}
