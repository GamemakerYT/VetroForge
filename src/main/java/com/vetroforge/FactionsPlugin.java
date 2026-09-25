// src/main/java/com/vetroforge/FactionsPlugin.java
package com.vetroforge;


import com.sun.net.httpserver.HttpServer;
import com.vetroforge.ai.AIBanManager;
import com.vetroforge.ai.AIService;
import com.vetroforge.ai.CachingAIService;
import com.vetroforge.ai.NopAIService;
import com.vetroforge.chat.ChatLimiter;
import com.vetroforge.commands.AdminTravelerCommand;
import com.vetroforge.commands.*;
import com.vetroforge.auction.AuctionHouseManager;
import com.vetroforge.crates.CrateNpcListener;
import com.vetroforge.crates.CrateNpcManager;
import com.vetroforge.crates.KeyCrateManager;
import com.vetroforge.discordbridge.FactionDiscordSync;
import com.vetroforge.display.*;
import com.vetroforge.economy.EconomyHook;
import com.vetroforge.economy.EconomyManager;
import com.vetroforge.holo.cmd.HoloCommand;
import com.vetroforge.network.CoinsManager;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.factions.FactionRelationsManager;
import com.vetroforge.gui.*;
import com.vetroforge.holo.HoloLeaderboardService;
import com.vetroforge.i18n.I18nManager;
import com.vetroforge.i18n.LanguageManager;
import com.vetroforge.inbox.ItemInboxService;
import com.vetroforge.items.cmd.CustomItemsCommand;
import com.vetroforge.items.cmd.GiveCustomCommand;
import com.vetroforge.lootdrop.cmd.CustomItemFuseCommand;
import com.vetroforge.maintenance.MaintenanceManager;
import com.vetroforge.language.LanguageGui;
import com.vetroforge.language.LanguageJoinListener;
import com.vetroforge.listeners.*;
import com.vetroforge.manager.*;
import com.vetroforge.network.NetworkBridge;
import com.vetroforge.network.PresenceService;
import com.vetroforge.notify.NotifyManager;
import com.vetroforge.persistence.PersistenceManager;
import com.vetroforge.persistence.sql.NetworkDao;
import com.vetroforge.prestige.ChestLootPrestigeListener;
import com.vetroforge.prestige.PrestigeManager;
import com.vetroforge.prestige.PvpKillListener;
import com.vetroforge.prestige.WarzoneCaptureListener;
import com.vetroforge.punish.PunishmentManager;
import com.vetroforge.raid.*;
import com.vetroforge.raid.shop.EnergyCrystalBridge;
import com.vetroforge.raid.shop.EnergyCrystalItems;
import com.vetroforge.raid.shop.EnergyCrystalUseListener;
import com.vetroforge.raid.spectator.RaidSpectatorListener;
import com.vetroforge.raid.spectator.RaidSpectatorService;
import com.vetroforge.scoreboard.ScoreboardUpdateTask;
import com.vetroforge.services.AntiCheatBridge;
import com.vetroforge.services.BoosterManager;
import com.vetroforge.services.ChatLogService;
import com.vetroforge.services.DailyRewardService;
import com.vetroforge.services.PrivateVaultService;
import com.vetroforge.services.RankKitService;
import com.vetroforge.services.ReportService;
import com.vetroforge.services.StaffProtectionService;
import com.vetroforge.skills.listener.SkillEffectsListener;
import com.vetroforge.stats.StatsListener;
import com.vetroforge.stats.StatsManager;
import com.vetroforge.instance.RaidInstanceService;
import com.vetroforge.warzone.WarzoneBossBarManager;
import com.vetroforge.warzone.WarzoneEnterLeaveListener;
import com.vetroforge.warzone.WarzoneMobService;
import com.vetroforge.warzone.WarzoneRegionManager;
import com.vetroforge.web.WebApiManager;
import com.vetroforge.traveler.TravelerService;
import com.vetroforge.world.WorldManager;
import com.vetroforge.commerce.AICommerceManager;
import com.vetroforge.commerce.CommerceNpcService;
import com.vetroforge.commerce.ShopAdminEditor;
import com.vetroforge.world.vworld.VWorldCommand;
import com.vetroforge.world.vworld.VWorldWandListener;
import com.vetroforge.world.vworld.VetroRegionSelectionService;
import com.vetroforge.world.vworld.VetroWorldTemplateService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import com.vetroforge.crates.CrateBridge;
import com.vetroforge.lootdrop.LootChestUX;
import com.vetroforge.lootdrop.TimedLootChestService;
import org.bukkit.command.TabCompleter;
import com.vetroforge.ai.SimpleAIService;
import com.vetroforge.discordbridge.FactionsDiscordBridgeService;
import com.vetroforge.discordbridge.FactionsServerStatusTask;
import com.vetroforge.raid.energy.RaidSessionService;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.jar.JarFile;
public final class FactionsPlugin extends JavaPlugin {

    /* =================== AI =================== */
    private AIService aiService;
    private AIBanManager aiBanManager;
    private AICommerceManager aiCommerceManager;
    private final ChatLogService chatLogService = new ChatLogService();
    private final ReportService reportService = new ReportService();
    private final AntiCheatBridge antiCheatBridge = new AntiCheatBridge();
    private com.vetroforge.auron.AuronAntiCheatService auronAntiCheatService;
    private final StaffProtectionService staffProtectionService = new StaffProtectionService(this);
    private I18nManager i18n;
    private FactionsDiscordBridgeService discordBridge;
    private FactionsServerStatusTask factionsServerStatusTask;
    private LanguageManager languageManager;
    private BroadcastManager broadcastManager;
    private CrateNpcManager crateNpcManager;
    private NametagTabManager nametagTabManager;
    private MaintenanceManager maintenanceManager;
    private WorldManager worldManager;
    private LobbyManager lobbyManager;
    private FactionShopManager factionShopManager;
    private UpdateCheckerManager updateCheckerManager;
    private ChatLimiter chatLimiter;
    private VetroPermsManager vetroPermsManager;
    private EconomyHook economyHook;
    private HoloLeaderboardService holoService;
    private CoinsManager coinsManager;
    private WarzoneBossBarManager warzoneBossBar;
    private WarzoneMobService warzoneMobService;
    private TravelerService travelerService;
    private StoryManager storyManager;
    private TopGuiManager topGuiManager;
    private StoryGuiManager storyGuiManager;
    private com.vetroforge.gui.PrestigeGui prestigeGui;
    private com.vetroforge.motd.MotdManager motdManager;
    private CrateBridge crateBridge;
    private LootChestUX lootChestUX;
    private TimedLootChestService timedLootChestService;
    private com.vetroforge.lobby.LobbyNavigatorGui lobbyNavigatorGui;
    private TicketManager ticketManager;
    private WebhookManager webhookManager;
    private CosmeticsManager cosmeticsManager;
    private com.vetroforge.staff.StaffGuiService staffGuiService;
    private KeyCrateManager keyCrateManager;
    private EconomyManager economyManager;
    private StatsManager statsManager;
    private com.vetroforge.level.LevelProgressionService levelProgressionService;
    private PrestigeManager prestigeManager;
    private FactionManager factionManager;
    private FactionRelationsManager factionRelationsManager;
    private PunishmentManager punishmentManager;
    private com.vetroforge.punish.PunishmentHistoryManager punishmentHistory;
    private NotifyManager notifyManager;
    private SeasonManager seasonManager;
    private OnlineTimeManager onlineTimeManager;
    private TablistManager tablistManager;
    private BalanceTuningManager balanceTuningManager;
    private FactionMenuManager factionMenuManager;
    private WikiManager wikiManager;
    private LanguageGui languageGui;
    private SpellManager spellManager;
    private ChestLootChanceManager chestLootChanceManager;
    private RaidAnnouncements raidAnnouncements;
    private RaidManager raidManager;
    private RaidSpectatorService raidSpectatorService;
    private com.vetroforge.raid.shop.EnergyCrystalManager energyCrystalManager;
    private com.vetroforge.raid.shop.RaidShopGui raidShopGui;
    private WarzoneEnterLeaveListener warzoneEnterLeaveListener;
    private com.vetroforge.world.MysticStormService mysticStormService;
    private com.vetroforge.raid.energy.EnergyManager energyManager;
    private com.vetroforge.raid.AntiStallService antiStallService;
    private NametagTabManager nametag;
    private com.vetroforge.lobby.PlayHider playHider;
    private com.vetroforge.lootdrop.LootDropManager lootDropManager;
    private com.vetroforge.warzone.PrestigeProgressBarManager prestigeProgressBarManager;
    private com.vetroforge.lobby.PlayerVisibilityGui visibilityGui;
    private com.vetroforge.raid.obsidian.ObsidianHealthManager obsidianHealthManager;
    private com.vetroforge.raid.RaidCrystalManager raidCrystalManager;
    private com.vetroforge.raid.scout.ScoutingService scoutingService;
    private com.vetroforge.chat.ChatChannelManager chatChannelManager;
    private com.vetroforge.lobby.PlayerVisibilityManager playerVisibilityManager;
    private com.vetroforge.holo.TopCornerManager topCornerManager;
    private com.vetroforge.contracts.ContractsManager contractsManager;
    private com.vetroforge.gui.SkillTreeGui skillTreeGui;
    private com.vetroforge.gui.LevelGui levelGui;
    private com.vetroforge.gui.CrateAdminGui crateAdminGui;
    private com.vetroforge.lobby.LobbySettingsGui lobbySettingsGui;
    private RaidInstanceService raidInstanceService;
    private RaidRewardService raidRewardService;
    private RaidTargetService raidTargetService;
    private FactionCoinManager factionCoinManager;
    private FactionBannerManager factionBannerManager;
    private com.vetroforge.factions.FactionInboxService factionInboxService;
    private ItemInboxService itemInboxService;
    private com.vetroforge.capture.CaptureZoneManager captureZoneManager;
    private com.vetroforge.capture.CaptureZoneBuffService captureZoneBuffService;
    private FactionUpgradeManager factionUpgradeManager;
    private FactionBannerGui factionBannerGui;
    private com.vetroforge.raid.energy.RaidSessionService raidSessionService;
    private com.vetroforge.raid.RaidJoinService raidJoinService;
    private com.vetroforge.display.TabHeaderFooterTask tabHeaderFooterTask;
    private com.vetroforge.items.armor.CustomArmorSetService customArmorSetService;
    private com.vetroforge.items.ItemProgressionService itemProgressionService;
    private com.vetroforge.quests.FactionWeeklyQuestService factionWeeklyQuestService;
    private com.vetroforge.items.ItemCooldownService itemCooldownService;
    private com.vetroforge.events.EventManager eventManager;
    private com.vetroforge.events.gui.EventsGui eventsGui;
    private com.vetroforge.raid.listener.RaidBoundaryListener raidBoundaryListener;
    private VetroWorldTemplateService vWorldTemplateService;
    private VetroRegionSelectionService vWorldSelectionService;
    private FactionDiscordSync factionDiscordSync;
    private com.vetroforge.discordbridge.ServerStatusWriterService serverStatusWriterService;
    /* =================== Display / UI =================== */
    private SidebarScoreboardManager sidebarScoreboardManager;
    private com.vetroforge.raid.listener.RaidScoutingRestrictionListener raidScoutingRestrictionListener;
    /* =================== Warzone / Regions / Chests =================== */
    private WarzoneRegionManager warzoneRegionManager;
    private com.vetroforge.warzone.CombatTagService combatTagService;
    private ChestSpawnManager chestSpawnManager;
    private com.vetroforge.manager.SkillManager skillManager;
    private com.vetroforge.gui.SkillsGui skillsGui;
    private com.vetroforge.gui.ForgeGui forgeGui;
    private com.vetroforge.gui.RunesGui runesGui;
    private NetworkBridge networkBridge;
    private PresenceService presenceService;
    private NetworkDao networkDao;
    private PrivateVaultService privateVaultService;
    private RankKitService rankKitService;
    private BoosterManager boosterManager;
    private com.vetroforge.services.ClearLagService clearLagService;
    private DailyRewardService dailyRewardService;

    /* =================== Commerce =================== */
    private GeneralShopManager generalShopManager;
    private AuctionHouseManager auctionHouseManager;
    private CommerceNpcService shopNpcService;
    private CommerceNpcService auctionNpcService;
    private com.vetroforge.commands.BossTraderNpcService bossTraderNpcService;
    private ShopAdminEditor shopAdminEditor;

    /* =================== Persistence / Web =================== */
    private PersistenceManager persistenceManager;
    private PvpKillListener pvpKillListener;
    private WebApiManager webApiManager;
    private com.vetroforge.web.WebDeliveryService webDeliveryService;
    private com.vetroforge.doctor.LegacyEntityCleaner legacyEntityCleaner;
    private com.vetroforge.warzone.heatmap.WarzoneActivityService warzoneActivityService;
// src/main/java/com/vetroforge/FactionsPlugin.java
// Methode einfügen.

    private void initVWorld() {
        vWorldTemplateService = new VetroWorldTemplateService(this);
        vWorldSelectionService = new VetroRegionSelectionService();

        getServer().getPluginManager().registerEvents(
                new VWorldWandListener(this, vWorldSelectionService),
                this
        );
        if (getConfig().getBoolean("mysql.enabled", false)
                && getConfig().getBoolean("status.enabled", true)) {
            this.serverStatusWriterService =
                    new com.vetroforge.discordbridge.ServerStatusWriterService(
                            this,
                            "Factions"
                    );

            this.serverStatusWriterService.start();
        } else {
            getLogger().info("[ServerStatus] Writer deaktiviert (mysql.enabled/status.enabled).");
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (getCommand("vworld") == null) {
                getLogger().warning("[VWorld] Command /vworld fehlt in plugin.yml.");
                return;
            }

            VWorldCommand command = new VWorldCommand(
                    this,
                    vWorldTemplateService,
                    vWorldSelectionService
            );

            getCommand("vworld").setExecutor(command);
            getCommand("vworld").setTabCompleter(command);

            getLogger().info("[VWorld] Command aktiviert.");
        });
    }



    private void initDiscordBridge() {
        if (!getConfig().getBoolean("mysql.enabled", false)
                || !getConfig().getBoolean("discord-bridge.enabled", false)) {
            getLogger().info("Discord bridge disabled by config.");
            return;
        }

        discordBridge = new FactionsDiscordBridgeService(this);
        discordBridge.initTables();

        factionsServerStatusTask = new FactionsServerStatusTask(this, discordBridge);
        factionsServerStatusTask.start();

        getLogger().info("Discord bridge enabled for Factions.");
    }

    private void initItemInbox() {
        this.itemInboxService = new ItemInboxService(this);
        register(itemInboxService);
        requireCommand("iteminbox").setExecutor(itemInboxService);
        getLogger().info("[ItemInbox] Listener und /iteminbox registriert.");
    }

    private void initTraveler() {
        this.travelerService = new TravelerService(this);
        register(travelerService);
        travelerService.start();
        AdminTravelerCommand commandHandler = new AdminTravelerCommand(travelerService);
        PluginCommand command = requireCommand("admintraveler");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        getLogger().info("[Traveler] Listener, Rotation und /admintraveler registriert.");
    }

    /**
     * VetroCore läuft als Velocity-Proxy-Plugin und taucht daher nie in Bukkits
     * PluginManager auf -- Autodetection ist hier nicht möglich, das Flag muss
     * vom Serveradmin gesetzt werden (Standard: an, da VetroCore Teil des
     * Standard-Netzwerk-Setups ist).
     */
    private boolean externalModerationActive() {
        return getConfig().getBoolean("integration.vetrocore.moderation", true);
    }

    private boolean externalMaintenanceActive() {
        return getConfig().getBoolean("integration.vetrocore.maintenance", true);
    }

    /* =================== Lifecycle =================== */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        auditAndMergeConfig();
        com.vetroforge.config.ConfigDoctor.mergeDefaults(this, false);
        // Vanilla-Achievement-Broadcasts ("X hat den Fortschritt Y erreicht") ueberall
        // deaktivieren -- unerwuenschtes Chat-Rauschen, nicht Teil des eigenen Progressions-UIs.
        // Vanilla-Todesnachrichten (Chat-Broadcast "X wurde von Y getoetet", teilt sich den
        // Todesbildschirm-Text mit dem betroffenen Spieler) ebenfalls aus -- die Warzone-Mob-
        // Custom-Names (inkl. Disparity-Level/HP) sind fuer den floatenden Nametag gedacht,
        // nicht fuer eine Chat-weite Todesmeldung.
        for (org.bukkit.World w : getServer().getWorlds()) {
            try { w.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false); } catch (Throwable ignored) {}
            try { w.setGameRule(org.bukkit.GameRule.SHOW_DEATH_MESSAGES, false); } catch (Throwable ignored) {}
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.staffGuiService = new com.vetroforge.staff.StaffGuiService(this);
        register(staffGuiService);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this,
                com.vetroforge.staff.StaffGuiService.CHANNEL
        );
        getServer().getMessenger().registerIncomingPluginChannel(
                this,
                com.vetroforge.staff.StaffGuiService.CHANNEL,
                staffGuiService
        );
        initDiscordBridge();
        initVWorld();
        var cfg = getConfig();
        boolean mysqlEnabled = getConfig().getBoolean("mysql.enabled", true);
        String host = getConfig().getString("mysql.host", "127.0.0.1");
        int port = getConfig().getInt("mysql.port", 3306);
        String db = firstConfig("mysql.database", "database.database", "database.name");
        String user = firstConfig("mysql.user", "mysql.username", "database.user", "database.username");
        String pass = configOrEnvironment("mysql.password", "VF_MYSQL_PASSWORD", "");

        var lootTest = new com.vetroforge.lootdrop.cmd.LootTestCommand(this);
        var energyCmd = new com.vetroforge.raid.energy.EnergyCommand(this);
        var crystalsCmd = new com.vetroforge.raid.crystals.CrystalsCommand(this);

        GiveCustomCommand giveCustom = new GiveCustomCommand(this);
        CustomItemsCommand customItems = new CustomItemsCommand(this);

        java.util.Objects.requireNonNull(getCommand("givecustom"), "Missing 'givecustom' in plugin.yml")
                .setExecutor(giveCustom);
        java.util.Objects.requireNonNull(getCommand("givecustom"))
                .setTabCompleter(giveCustom);

        java.util.Objects.requireNonNull(getCommand("customitems"), "Missing 'customitems' in plugin.yml")
                .setExecutor(customItems);
        java.util.Objects.requireNonNull(getCommand("customitems"))
                .setTabCompleter(customItems);


        int webApiPort = getConfig().getInt("webapi.port", 8765);
        if (getConfig().getBoolean("webapi.enabled", false)) {
            webApiManager = new com.vetroforge.web.WebApiManager(this);
            if (!webApiManager.start()) {
                getLogger().warning("[WebAPI] Start fehlgeschlagen – WebAPI bleibt aus.");
                webApiManager = null;
            } else {
                new com.vetroforge.web.WebPlayerCacheListener(this);
            }
        }



            if (getConfig().getBoolean("factions.world.magicForest.enabled", true)) {
                getLogger().info("[VetroForge] MagicForest ist aktiviert (BlockPopulator).");
            } else {
                getLogger().info("[VetroForge] MagicForest ist deaktiviert.");
            }

            getLogger().info("MYSQL HOST: " + getConfig().getString("mysql.host"));
            getLogger().info("MYSQL PORT: " + getConfig().getInt("mysql.port"));
            getLogger().info("MYSQL DATABASE: " + getConfig().getString("mysql.database"));
            getLogger().info("MYSQL USER: " + getConfig().getString("mysql.user"));
            saveDefaultConfig();

            getComponentLogger().info(
                    Component.text("Vetro", NamedTextColor.RED)
                            .append(Component.text("Forge ", NamedTextColor.GOLD))
                            .append(Component.text("starting...", NamedTextColor.GRAY))
            );

            trySaveResource("lang/de.yml");
            trySaveResource("lang/en.yml");
            trySaveResource("lang/fr.yml");
            trySaveResource("lang/nl.yml");
            if (!mysqlEnabled) {
                getLogger().info("[MySQL] mysql.enabled=false - Datenbankfeatures laufen lokal/abgeschaltet.");
                this.networkDao = null;
            } else if (db == null || db.isBlank() || user == null || user.isBlank()) {
                getLogger().warning("[MySQL] Datenbankname oder Benutzer fehlt - Datenbankfeatures laufen lokal/abgeschaltet.");
                this.networkDao = null;
            } else try {
                this.networkDao = new NetworkDao(host, port, db, user, pass);
            } catch (Throwable t) {
                this.networkDao = null;
                getLogger().log(Level.WARNING, "[MySQL] Verbindung fehlgeschlagen. Coins laufen bis zum Fix nur lokal: " + t.getMessage());
            }
            factionDiscordSync = new FactionDiscordSync(this);
            if (getConfig().getBoolean("redis.enabled", false)) {
                this.networkBridge = new NetworkBridge(
                        this,
                        getConfig().getString("redis.host", "127.0.0.1"),
                        getConfig().getInt("redis.port", 6379),
                        getConfig().getString("redis.password", "")
                );
            } else {
                this.networkBridge = null;
                getLogger().info("[NetworkBridge] Redis deaktiviert (redis.enabled=false).");
            }

            this.skillManager = new SkillManager(this);
            this.skillTreeGui = new com.vetroforge.gui.SkillTreeGui(this);
            this.levelGui = new com.vetroforge.gui.LevelGui(this);
            this.crateAdminGui = new com.vetroforge.gui.CrateAdminGui(this);
            this.legacyEntityCleaner = new com.vetroforge.doctor.LegacyEntityCleaner(this);
            EnergyCrystalItems crystalItems = new EnergyCrystalItems(this);
            EnergyCrystalBridge crystalBridge = new EnergyCrystalBridge(this);
            new EnergyCrystalUseListener(this, crystalItems, crystalBridge);
            skillsGui    = new com.vetroforge.gui.SkillsGui(this);
            forgeGui     = new com.vetroforge.gui.ForgeGui(this);
            safeRegister("forge", new com.vetroforge.commands.ForgeCommand(this));
            runesGui     = new com.vetroforge.gui.RunesGui(this);
            this.economyHook = new EconomyHook(this);
            this.economyHook.hookWithRetry();
            this.aiService = new SimpleAIService(this);
            this.aiBanManager = new AIBanManager(this);
            this.aiCommerceManager = new AICommerceManager(this);
            this.economyManager = new EconomyManager(this);
            this.statsManager = new StatsManager(this);
            this.levelProgressionService = new com.vetroforge.level.LevelProgressionService(this);
            this.factionRelationsManager = new FactionRelationsManager(this);
            factionRelationsManager.load();
            if (externalModerationActive()) {
                getLogger().info("[Moderation] VetroCore ist zuständig; Factions-Punishments bleiben deaktiviert.");
            } else {
                this.punishmentManager = new PunishmentManager(this);
                this.punishmentHistory = new com.vetroforge.punish.PunishmentHistoryManager(this);
            }
            register(new com.vetroforge.services.OwnerProtectionListener(this));
            this.seasonManager = new SeasonManager(this);
            this.onlineTimeManager = new OnlineTimeManager(this);
            onlineTimeManager.start();
            this.vetroPermsManager = new VetroPermsManager(this);
            this.tablistManager = new TablistManager(this);
            this.pvpKillListener = new PvpKillListener(this);
            this.topGuiManager = new TopGuiManager(this);
            this.storyManager = new StoryManager(this);
            this.storyGuiManager = new StoryGuiManager(this);
            this.chestLootChanceManager = new ChestLootChanceManager(this);
            this.crateNpcManager = new CrateNpcManager(this);
            this.prestigeManager = new PrestigeManager(this);
            prestigeManager.loadXp();
            this.prestigeGui = new com.vetroforge.gui.PrestigeGui(this);
            this.coinsManager = new CoinsManager(this, networkBridge, networkDao);
            this.webDeliveryService = new com.vetroforge.web.WebDeliveryService(this);
            safeRegister("deliveries", new com.vetroforge.web.DeliveriesCommand(this));
            this.boosterManager = new BoosterManager(this);
            if (networkBridge != null) {
                networkBridge.attachCoinsManager(coinsManager);
                networkBridge.start();
                if (getConfig().getBoolean("presence.enabled", true)) {
                    this.presenceService = new PresenceService(this, networkBridge);
                    this.presenceService.start();
                }
            }
            this.raidInstanceService = new RaidInstanceService(this);
            this.lobbySettingsGui = new com.vetroforge.lobby.LobbySettingsGui(this);
            this.lobbyNavigatorGui = new com.vetroforge.lobby.LobbyNavigatorGui(this);
            this.factionManager = new FactionManager();
            this.factionInboxService = new com.vetroforge.factions.FactionInboxService(this);
            register(factionInboxService);
            initItemInbox();
            register(new com.vetroforge.level.LevelXpBottleListener(this));
            this.cosmeticsManager = new CosmeticsManager(this);
            this.warzoneRegionManager = new WarzoneRegionManager(this);
            this.warzoneActivityService = new com.vetroforge.warzone.heatmap.WarzoneActivityService(this);
            register(new com.vetroforge.warzone.heatmap.WarzoneActivityListener(this));
            prestigeProgressBarManager = new com.vetroforge.warzone.PrestigeProgressBarManager(this);
            prestigeProgressBarManager.start();
            this.chestSpawnManager = new ChestSpawnManager(this);
            this.wikiManager = new WikiManager(this);
            this.i18n = new I18nManager(this);
            this.languageManager = new LanguageManager(i18n);
            this.languageGui = new LanguageGui(this);
            this.sidebarScoreboardManager = new SidebarScoreboardManager(this);
            this.nametagTabManager = new NametagTabManager(this);
            this.tabHeaderFooterTask = new TabHeaderFooterTask(this);
            tabHeaderFooterTask.start();
            this.worldManager = new WorldManager(this);
            this.raidAnnouncements = new RaidAnnouncements(this);
            this.raidManager = new RaidManager(this);
            register(this.raidManager);
            this.raidJoinService = new com.vetroforge.raid.RaidJoinService(this);
            this.raidSessionService = new com.vetroforge.raid.energy.RaidSessionService(this);
            this.raidScoutingRestrictionListener = new com.vetroforge.raid.listener.RaidScoutingRestrictionListener(this);
            this.raidBoundaryListener = new com.vetroforge.raid.listener.RaidBoundaryListener(this);

            this.raidCrystalManager = new com.vetroforge.raid.RaidCrystalManager(this);
            this.raidSpectatorService = new RaidSpectatorService(this);
            this.energyManager = new com.vetroforge.raid.energy.EnergyManager(this);
            safeRegister("energyadmin", new com.vetroforge.raid.energy.EnergyAdminCommand(this));
            factionMenuManager = new FactionMenuManager(this);
            this.factionShopManager = new FactionShopManager(this);
            this.factionCoinManager = new FactionCoinManager(this);
            this.factionBannerManager = new FactionBannerManager(this);
            this.factionUpgradeManager = new FactionUpgradeManager(this);
            this.factionBannerGui = new FactionBannerGui(this);
            this.generalShopManager = new GeneralShopManager(this);
            this.auctionHouseManager = new AuctionHouseManager(this);
            this.shopNpcService = new CommerceNpcService(this, CommerceNpcService.Kind.SHOP);
            this.auctionNpcService = new CommerceNpcService(this, CommerceNpcService.Kind.AUCTION);
            this.shopAdminEditor = new ShopAdminEditor(this);
            new com.vetroforge.commerce.ShopListener(this);
            this.chatLimiter = new ChatLimiter();
            this.spellManager = new SpellManager(this);
            this.keyCrateManager = new KeyCrateManager(this);
            crateBridge = new CrateBridge(this);
            this.warzoneEnterLeaveListener = new WarzoneEnterLeaveListener(this);
            this.persistenceManager = new PersistenceManager(this);
            persistenceManager.loadAll();
            persistenceManager.startAutosave();
            this.playHider = new com.vetroforge.lobby.PlayHider(this);
            this.visibilityGui = new com.vetroforge.lobby.PlayerVisibilityGui(this);
            this.broadcastManager = new BroadcastManager();
            this.updateCheckerManager = new UpdateCheckerManager(this);
            if (externalMaintenanceActive()) {
                getLogger().info("[Maintenance] VetroCore ist zuständig; Factions-Wartungsmodus bleibt deaktiviert.");
            } else {
                this.maintenanceManager = new MaintenanceManager(this);
            }
            this.lootDropManager = new com.vetroforge.lootdrop.LootDropManager(this);
            this.obsidianHealthManager = new com.vetroforge.raid.obsidian.ObsidianHealthManager(this);
            obsidianHealthManager.startAutosave();
            this.raidTargetService = new RaidTargetService(this);
            this.raidRewardService = new RaidRewardService(this);
            getServer().getPluginManager().registerEvents(coinsManager, this);

            new com.vetroforge.raid.obsidian.ExplosionListener(this, obsidianHealthManager);
            new com.vetroforge.raid.breach.BreachChargeListener(this, obsidianHealthManager);
            this.motdManager = new com.vetroforge.motd.MotdManager(this);
            new com.vetroforge.motd.MotdListener(this, motdManager);
            if (getConfig().getBoolean("motd.slotOverride.reserve.enabled", false)) {
                new com.vetroforge.motd.ReserveSlotLoginListener(this);
            }

            motdManager.setWarzoneCounter(() -> {
                int wz = 0;
                var rm = getWarzoneRegionManager();
                if (rm != null) {
                    for (var p : getServer().getOnlinePlayers())
                        if (rm.isWarzone(p.getLocation())) wz++;
                }
                return wz;
            });



            getServer().getPluginManager().registerEvents( new com.vetroforge.raid.RaidStartItemListener(this), this );
            getServer().getPluginManager().registerEvents(new SkillEffectsListener(this), this);
            getServer().getPluginManager().registerEvents(new com.vetroforge.raid.RaidFightListener(this), this);
            getServer().getPluginManager().registerEvents(new com.vetroforge.raid.RaidInteractListener(this), this);
            getServer().getPluginManager().registerEvents( raidScoutingRestrictionListener, this  );
            raidScoutingRestrictionListener.startTask();register(new com.vetroforge.world.RaidBuildProtectionListener(this));
            new com.vetroforge.lootdrop.listeners.LootDropAutoDespawnListener(this);
            new com.vetroforge.raid.ping.PingListener(this, obsidianHealthManager);
            reg("rune", new com.vetroforge.raid.runes.RunesCommand(this), null);
            new com.vetroforge.raid.runes.RuneDamageListener(this);
            new com.vetroforge.items.abilities.DragonsBreathFusionListener(this);
            reg("topraiders", new com.vetroforge.commands.TopRaidersCommand(this), null);

            this.scoutingService = new com.vetroforge.raid.scout.ScoutingService(this);
            reg("scout", new com.vetroforge.commands.ScoutCommand(this, scoutingService), null);
            new com.vetroforge.lootdrop.listeners.DropInteractListener(this, lootDropManager);
            new com.vetroforge.lootdrop.listeners.InterdictionListener(this);
            var ldc = new com.vetroforge.lootdrop.LootDropCommand(this);
            safeRegister("lootdrop", ldc);
            safeRegister("adminlootdrop", new com.vetroforge.lootdrop.AdminLootDropCommand(this));
            getCommand("flare").setExecutor((s, c, l, a) -> ldc.onCommand(s, c, "lootdrop", new String[]{"flare"}));

            Objects.requireNonNull(getCommand("breachcharge")).setExecutor((sender, cmd, lbl, args) -> {
                if (!sender.hasPermission("vetroforge.admin")) {
                    sender.sendMessage("No perm.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("/" + lbl + " give <player> <amount>");
                    return true;
                }
                org.bukkit.entity.Player t = getServer().getPlayerExact(args[0]);
                int amt;
                try {
                    amt = Integer.parseInt(args[1]);
                } catch (Exception ex) {
                    sender.sendMessage("Amount?");
                    return true;
                }
                if (t == null) {
                    sender.sendMessage("Player offline.");
                    return true;
                }
                t.getInventory().addItem(com.vetroforge.raid.breach.BreachChargeListener.makeCharge(this, amt));
                sender.sendMessage("Gave " + amt + " charge(s) to " + t.getName());
                return true;
            });

            energyCrystalManager = new com.vetroforge.raid.shop.EnergyCrystalManager(this);
            raidShopGui = new com.vetroforge.raid.shop.RaidShopGui(this, energyCrystalManager);

            Objects.requireNonNull(getCommand("raidshop")).setExecutor(
                    new com.vetroforge.raid.shop.RaidShopCommand(this, raidShopGui)
            );
            com.vetroforge.commands.RaidCommand raidCommand = new com.vetroforge.commands.RaidCommand(this);
            new com.vetroforge.physics.ExplosionPhysicsListener(this);
            if (!externalMaintenanceActive()) {
                registerWithArgsIfPresent("com.vetroforge.maintenance.MaintenanceListener", this, maintenanceManager);
            }
            new ChestListener(this, chestSpawnManager);
            new ObsidianBreakListener(this);
            new RaidSpectatorListener(this);
            new FriendlyFireListener(this);
            new StatsListener(this);
            new PrestigeListener(this);
            new ChestLootPrestigeListener(this);
            new WarzoneCaptureListener(this);
            new StoryListener(this);
            register(new ChatLimiterListener(this, chatLimiter));
            register(new KeyUseListener(this));
            new CrateNpcListener(this, crateNpcManager);
            new NametagTabListener(this);
            register(new LanguageJoinListener(this, player -> languageGui.open(player)));
            register(new com.vetroforge.listeners.StaffJoinListener(this));
            if (!externalModerationActive()) {
                new com.vetroforge.punish.PunishJoinListener(this);
            }
            holoService = new HoloLeaderboardService(this);
            registerCommand("loottest", lootTest, null);
            registerCommand("energy", energyCmd, null);
            registerCommand("crystals", crystalsCmd, null);
            getCommand("skilladmin").setExecutor(new SkillAdminCommand(this));
            getCommand("skilladmin").setTabCompleter(new SkillAdminCommand(this));
            getCommand("raid").setExecutor(raidCommand);
            getCommand("raid").setTabCompleter(raidCommand);
            getCommand("raidjoin").setExecutor(new RaidJoinCommand(this));
            if (!externalModerationActive()) {
                var reasonsCmd = new ReasonsCommand(this);
                var mute = new MuteCommand(this);
                reg("ban", new BanCommand(this), new BanCommand(this));
                var tempban = new TempbanCommand(this);
                reg("tempban", tempban, tempban);
                reg("kick", new KickCommand(this), new KickCommand(this));
                reg("mute", mute, mute);
                var unmute = new UnmuteCommand(this);
                reg("unmute", unmute, unmute);
                reg("unban", new UnbanCommand(this), null);
                reg("banlist", new BanListCommand(this), null);
                reg("history", new HistoryCommand(this), null);
                reg("reasons", reasonsCmd, null);
                reg("aiban", new AIBanCommand(this), null);
            }
            reg("balancetune", new BalanceTuningCommand(this), null);
            reg("f", new FactionCommand(this), null);
            reg("frelation", new FRelationCommand(this), null);
            reg("raidadmin", new RaidAdminCommand(this), null);

            reg("shop", new ShopCommand(this), null);
            AuctionHouseCommand auctionHouseCommand = new AuctionHouseCommand(this);
            reg("ah", auctionHouseCommand, auctionHouseCommand);
            var adminShop = new AdminShopCommand(this, shopNpcService);
            reg("adminshop", adminShop, adminShop);
            var adminAh = new AdminAhCommand(this, auctionNpcService);
            reg("adminah", adminAh, adminAh);
            reg("key", new CrateCommand(this), null);
            reg("apply", new ApplyCommand(this), new ApplyCommand(this));
            reg("spawn", new SpawnCommand(this), new SpawnCommand(this));
            reg("hub", new HubCommand(this), null);
            SetSpawnCommand setSpawnCommand = new SetSpawnCommand(this);
            reg("setspawn", setSpawnCommand, setSpawnCommand);
            GameModeCommand gameModeCommand = new GameModeCommand();
            reg("gm", gameModeCommand, gameModeCommand);
            var chestCommand = new ChestCommand(this, chestSpawnManager);
            reg("chest", chestCommand, chestCommand);
            reg("sell", new SellCommand(this), null);
            var boosterCommand = new BoosterCommand(this);
            reg("booster", boosterCommand, boosterCommand);
            this.clearLagService = new com.vetroforge.services.ClearLagService(this);
            this.clearLagService.start();
            var clearLagCommand = new ClearLagCommand(this);
            reg("clearlag", clearLagCommand, clearLagCommand);
            reg("season", new SeasonCommand(this), null);
            reg("scoreboard", new ScoreboardCommand(this), new ScoreboardCommand(this));
            reg("lobby", new LobbyCommand(this), null);
            reg("fly", new FlyCommand(this), new FlyCommand(this));
            var speedCommand = new SpeedCommand(this);
            reg("speed", speedCommand, speedCommand);
            // NOTE: GameModeShortcutCommand is intentionally NOT bound to "gm" here — current
            // already binds /gm to its own GameModeCommand (different 0-3 numbering and a
            // separate vetroforge.gamemode.others permission gate). See integration report.
            com.vetroforge.services.BuildModeService buildModeService = new com.vetroforge.services.BuildModeService(this);
            reg("build", buildModeService, buildModeService);
            new com.vetroforge.services.RegionDebugService(this);
            com.vetroforge.services.BuildBotService buildBotService = new com.vetroforge.services.BuildBotService(this);
            reg("buildbot", buildBotService, buildBotService);
            com.vetroforge.services.ExplosionToggleService explosionToggleService = new com.vetroforge.services.ExplosionToggleService(this);
            reg("explosions", explosionToggleService, explosionToggleService);
            var cnc = new CrateNpcCommand(this, crateNpcManager);
            reg("cratenpc", cnc, cnc);
            reg("spell", new SpellCommand(this), null);
            reg("story", new StoryCommand(this), null);
            reg("storyadmin", new StoryAdminCommand(this), null);
            reg("quest", new QuestCommand(this), null);
            reg("top", new TopGuiCommand(this), null);
            reg("ticket", new TicketCommand(this), null);
            reg("reward", new RewardCommand(this), null);
            reg("cratekey", new CrateKeyCommand(this), null);
            reg("cratehistory", new CrateHistoryCommand(this), null);
            reg("updater", new UpdaterCommand(this), null);
            getLogger().info("[Permissions] VetroPerms/VetroCore ist zuständig; Factions registriert kein /vetroperm.");
            reg("fmenu", new FMenuCommand(this), null);
            reg("help", new HelpCommand(this), null);
            var commandsCmd = new com.vetroforge.commands.CommandsCommand(this);
            reg("commands", commandsCmd, commandsCmd);
            AdminGuiCommand adminGuiCommand = new AdminGuiCommand(this);
            reg("admingui", adminGuiCommand, null);
            InvSeeCommand invSeeCommand = new InvSeeCommand(this);
            reg("invsee", invSeeCommand, invSeeCommand);
            EnderSeeCommand enderSeeCommand = new EnderSeeCommand(this);
            reg("endersee", enderSeeCommand, enderSeeCommand);
            safeRegister("guide", new GuideCommand(this));
            this.privateVaultService = new PrivateVaultService(this);
            var privateVaultCommand = new PrivateVaultCommand(privateVaultService);
            reg("pv", privateVaultCommand, privateVaultCommand);
            this.rankKitService = new RankKitService(this);
            register(new StarterKitJoinListener(rankKitService));
            var kitCommand = new RankKitCommand(rankKitService, null);
            reg("kit", kitCommand, kitCommand);
            safeRegister("starterkit", new RankKitCommand(rankKitService, RankKitService.KitType.STARTER));
            safeRegister("dailykit", new RankKitCommand(rankKitService, RankKitService.KitType.DAILY));
            safeRegister("weeklykit", new RankKitCommand(rankKitService, RankKitService.KitType.WEEKLY));
            safeRegister("monthlykit", new RankKitCommand(rankKitService, RankKitService.KitType.MONTHLY));
            this.dailyRewardService = new DailyRewardService(this);
            var rewardsCommand = new DailyRewardsCommand(dailyRewardService, false);
            reg("rewards", rewardsCommand, rewardsCommand);
            safeRegister("daily", new DailyRewardsCommand(dailyRewardService, true));
            register(new DailyRewardJoinListener(dailyRewardService));
            new BuildVersionJoinListener(this);
            var vetroForgeCommand = new VetroForgeCommand(this);
            reg("vetroforge", vetroForgeCommand, vetroForgeCommand);
            reg("wiki", new WikiCommand(this), null);
            reg("wikisearch", new WikiSearchCommand(this), null);
            reg("prestige", new PrestigeCommand(this), null);
            var pdebug = new PrestigeDebugCommand(this);
            reg("prestigedebug", pdebug, pdebug);
            reg("cosmetics", new CosmeticsCommand(this), null);
            reg("staffcosmetics", new StaffCosmetics(this), null);
            reg("errors", new ErrorsCommand(this), null);
            reg("broadcast", new BroadcastCommand(this), null);
            var kdr = new KdrCommand(this);
            reg("kdr", kdr, kdr);
            reg("slowchat", new SlowChatCommand(this), null);
            var statsCmd = new StatsCommand(this);
            reg("stats", statsCmd, statsCmd);
            safeRegister("vimage", new VImageCommand(this));
            var wzCmd = new WarzoneCommand(this);
            reg("warzone", wzCmd, wzCmd);
            var fwCmd = new FactionWorldCommand(this);
            reg("fworld", fwCmd, fwCmd);

            LanguageCommand langCmd = new LanguageCommand(this);
            PluginCommand langPc = getCommand("language");
            if (langPc != null) {
                langPc.setExecutor(langCmd);
                langPc.setTabCompleter(langCmd);
            } else {
                getLogger().warning("Command 'language' fehlt in plugin.yml!");
            }

            safeRegister("fshop", new FactionShopCommand(this));
            safeRegister("eloshop", new FactionUpgradeCommand(this));
            safeRegister("wzchestadmin", new WarzoneChestAdminCommand(this));
            if (!externalMaintenanceActive()) {
                safeRegister("maintenance", new com.vetroforge.maintenance.MaintenanceCommand(this));
            }
            auronAntiCheatService = new com.vetroforge.auron.AuronAntiCheatService(this);
            var auronCommand = new com.vetroforge.auron.AuronCommand(this, auronAntiCheatService);
            reg("auron", auronCommand, auronCommand);
            if (notifyManager == null) notifyManager = new com.vetroforge.notify.NotifyManager(this);
            safeRegister("report", new ReportCommand(this, notifyManager));
            safeRegister("optimize", new OptimizeCommand());
            prestigeManager.startAutosave();
            if (worldManager == null) worldManager = new WorldManager(this);
            if (lobbyManager == null) lobbyManager = new LobbyManager(this);
            if (factionShopManager == null) factionShopManager = new FactionShopManager(this);

        mysticStormService = new com.vetroforge.world.MysticStormService(this);
        new com.vetroforge.items.CustomItemSacrificeListener(this);
        var fusionBooksCommand = new FusionBooksAdminCommand(this);
        reg("fusionbooks", fusionBooksCommand, fusionBooksCommand);
        safeRegister("level", new LevelCommand(this));
        safeRegister("crateadmin", new CrateAdminCommand(this));
        safeRegister("loot", new LootCommand(this));
        safeRegister("ip", new IpCommand());
        safeRegister("onlinetime", new OnlineTimeCommand(this));
        safeRegister("playhider", new PlayHiderCommand(this));
        var fWorldAdmin = new FWorldAdminCommand(this);
        reg("fworldadmin", fWorldAdmin, fWorldAdmin);
        var wikiAdmin = new WikiAdminCommand(this);
        reg("wikiadmin", wikiAdmin, wikiAdmin);

        var lootUX = new com.vetroforge.lootdrop.LootChestUX(this);
        this.antiStallService = new com.vetroforge.raid.AntiStallService(this);
        captureZoneBuffService = new com.vetroforge.capture.CaptureZoneBuffService(this);
        captureZoneManager = new com.vetroforge.capture.CaptureZoneManager(this, captureZoneBuffService);
        safeRegister("admincapture", new AdminCaptureCommand(captureZoneManager));
        safeRegister("adminfactions", new com.vetroforge.commands.AdminFactionsCommand(this));
        var streaks = new com.vetroforge.social.LoginStreakService(this);
        new com.vetroforge.social.RewardNudgeService(this);
        getCommand("holo").setExecutor(new HoloCommand(holoService));
        getCommand("cifuse").setExecutor(new CustomItemFuseCommand(this));
        getCommand("givekey").setExecutor(new com.vetroforge.crates.cmd.GiveKeyCommand(this));
        KeyTraderCommand keyTraderCommand = new KeyTraderCommand(this);
        KeyTraderNpcService keyTraderNpcService = new KeyTraderNpcService(this, keyTraderCommand);
        keyTraderCommand.setNpcService(keyTraderNpcService);
        safeRegister("keytrader", keyTraderCommand);
        register(keyTraderCommand);
        register(keyTraderNpcService);
        getServer().getScheduler().runTaskLater(this, keyTraderNpcService::spawnConfigured, 40L);
        getServer().getScheduler().runTaskTimer(this, keyTraderNpcService::spawnConfigured, 20L * 30L, 20L * 30L);
        getCommand("coins").setExecutor(new CoinsCommand(this));
        getCommand("coins").setTabCompleter(new CoinsCommand(this));
        safeRegister("admincoins", new com.vetroforge.commands.AdminCoinsCommand(this));
        SkillCommand skillCmd = new SkillCommand(this);
        PluginCommand skillCommand = getCommand("skill");
        if (skillCommand != null) {
            skillCommand.setExecutor(skillCmd);
            skillCommand.setTabCompleter(skillCmd);

        }

        lootChestUX = new LootChestUX(this);
        timedLootChestService = new TimedLootChestService(this);
        if (getConfig().getBoolean("lootdrop.timed.enabled", true)) {
            timedLootChestService.start();
        }
        getServer().getPluginManager().registerEvents(new com.vetroforge.gui.RaidGuiListener(this), this);
        getServer().getPluginManager().registerEvents(
                raidBoundaryListener,
                this
        );

        raidBoundaryListener.startTask();
        new com.vetroforge.items.enchants.CustomEnchantListener(this);
        new com.vetroforge.items.enchants.CustomEnchantBookApplyListener(this);
        itemCooldownService = new com.vetroforge.items.ItemCooldownService(this);
        new com.vetroforge.items.abilities.DragonsBreathListener(this);
        customArmorSetService = new com.vetroforge.items.armor.CustomArmorSetService(this);
        itemProgressionService = new com.vetroforge.items.ItemProgressionService(this);
        bindCommand("item", itemProgressionService);
        factionWeeklyQuestService = new com.vetroforge.quests.FactionWeeklyQuestService(this);
        bindCommand("eventshop", new com.vetroforge.items.cmd.EventShopCommand(this));
        eventManager = new com.vetroforge.events.EventManager(this);
        eventsGui = new com.vetroforge.events.gui.EventsGui(this, eventManager);
        register(new com.vetroforge.events.gui.EventsGuiListener(eventsGui));
        var eventAdminGui = new com.vetroforge.events.gui.EventAdminGui(this, eventManager);
        register(new com.vetroforge.events.gui.EventAdminGuiListener(eventAdminGui));
        safeRegister("events", new com.vetroforge.events.commands.EventBoardCommand(eventsGui));
        safeRegister("eventadmin", new com.vetroforge.events.commands.EventAdminCommand(eventManager, eventAdminGui));
        safeRegister("eventadmingui", new com.vetroforge.events.commands.EventAdminGuiCommand(eventAdminGui));
        getCommand("loottest").setExecutor(new com.vetroforge.lootdrop.cmd.LootTestCommand(this));
        warzoneBossBar = new WarzoneBossBarManager(this);
        warzoneBossBar.start();
        warzoneMobService = new WarzoneMobService(this);
        warzoneMobService.start();
        safeRegister("adminboss", new com.vetroforge.commands.AdminBossCommand(warzoneMobService));
        initTraveler();
        chatChannelManager = new com.vetroforge.chat.ChatChannelManager(this);
        safeRegister("chat", new com.vetroforge.commands.ChatCommand(this));
        new com.vetroforge.chat.ChatListener(this);

        new com.vetroforge.lobby.LobbyProtectionListener(this);
        register(new com.vetroforge.warzone.WarzoneDamageRulesListener(this));
        register(new com.vetroforge.warzone.SpawnWarzoneTntListener(this));
        register(new com.vetroforge.raid.RaidLivesListener(this));
        register(new com.vetroforge.raid.RegenWallListener(this));
        register(new com.vetroforge.anticheat.MovementAntiCheatListener(this,
                new com.vetroforge.anticheat.MovementAntiCheatService(this)));
        this.combatTagService = new com.vetroforge.warzone.CombatTagService(this);
        register(this.combatTagService);
        playerVisibilityManager = new com.vetroforge.lobby.PlayerVisibilityManager(this);
        safeRegister("toggleplayers", new com.vetroforge.commands.TogglePlayersCommand(this));
        safeRegister("changelog", new com.vetroforge.commands.ChangelogCommand(this));

        contractsManager = new com.vetroforge.contracts.ContractsManager(this);
        safeRegister("contracts", new com.vetroforge.commands.ContractsCommand(this));

        try {
            var cmd = new com.vetroforge.commands.MotdAdminCommand(this, motdManager);
            Objects.requireNonNull(getCommand("motd")).setExecutor(cmd);
            Objects.requireNonNull(getCommand("motd")).setTabCompleter(cmd);
        } catch (Throwable t) {
            getLogger().warning("[MOTD] Command 'motd' fehlt in plugin.yml?");
        }

        if (this.notifyManager == null) this.notifyManager = new com.vetroforge.notify.NotifyManager(this);
        var notifyCmd = new com.vetroforge.commands.NotifyCommand(notifyManager);
        reg("notify", notifyCmd, notifyCmd);
        reg("autonotify", new AutoNotifyCommand(this), null);
        reg("tc", new TeamChatCommand(this), null);
        reg("linkweb", new LinkWebCommand(this), null);
        var staffProtect = new StaffProtectCommand(this);
        reg("staffprotect", staffProtect, staffProtect);
        safeRegister("staffgui", staffGuiService);
        var bossTrader = new com.vetroforge.commands.BossTraderCommand(this);
        bossTraderNpcService = new com.vetroforge.commands.BossTraderNpcService(this, bossTrader);
        bossTrader.setNpcService(bossTraderNpcService);
        reg("bosstrader", bossTrader, bossTrader);
        register(bossTraderNpcService);
        getServer().getScheduler().runTaskLater(this, bossTraderNpcService::spawnConfigured, 40L);
        getServer().getScheduler().runTaskTimer(this, bossTraderNpcService::spawnConfigured, 20L * 30L, 20L * 30L);

        new ScoreboardUpdateTask(this);
        new RaidActionBarTask(this);
        ensureCommandPermissions();

        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                if (legacyEntityCleaner != null) legacyEntityCleaner.start();
                crateNpcManager.spawnAll();
                if (shopNpcService != null) shopNpcService.spawnConfigured();
                if (auctionNpcService != null) auctionNpcService.spawnConfigured();
            } catch (Throwable t) {
                getLogger().warning("NPC spawnAll failed: " + t.getMessage());
            }
        }, 1L);

        getComponentLogger().info(
                Component.text("Vetro", NamedTextColor.RED)
                        .append(Component.text("Forge ", NamedTextColor.GOLD))
                        .append(Component.text("enabled ✓", NamedTextColor.GREEN))
        );


    }

    @Override
    public void onDisable() {
        if (factionsServerStatusTask != null) { factionsServerStatusTask.stop(); }
        if (presenceService != null) { presenceService.stop(); presenceService = null; }
        if (aiCommerceManager != null) aiCommerceManager.saveRuntime();
        if (prestigeProgressBarManager != null) prestigeProgressBarManager.stop();
        if (holoService != null) holoService.shutdown();
        if (nametag != null) try { nametag.shutdown(); } catch (Throwable ignored) {
        }
        if (raidBoundaryListener != null) { raidBoundaryListener.stopTask();
            raidBoundaryListener = null; }
        if (tabHeaderFooterTask != null) { tabHeaderFooterTask.stop(); tabHeaderFooterTask = null; }
        if (customArmorSetService != null) { customArmorSetService.shutdown(); customArmorSetService = null; }
        if (boosterManager != null) boosterManager.save();
        if (clearLagService != null) { clearLagService.stop(); clearLagService = null; }
        if (skillManager != null) skillManager.shutdown();
        if (eventsGui != null) { eventsGui.shutdown(); eventsGui = null; }
        if (eventManager != null) { eventManager.shutdown(); eventManager = null; }
        if (raidSessionService != null) raidSessionService.shutdown();
        if (raidManager != null) raidManager.shutdown();
        if (energyManager != null) energyManager.stop();
        if (captureZoneManager != null) captureZoneManager.shutdown();
        if (lootDropManager != null) lootDropManager.shutdown();
        if (warzoneActivityService != null) { warzoneActivityService.shutdown(); warzoneActivityService = null; }
        raidSessionService = null;
        raidJoinService = null;
        if (raidScoutingRestrictionListener != null) { raidScoutingRestrictionListener.stopTask(); raidScoutingRestrictionListener = null; }
        try { if (webApiManager != null) webApiManager.stop(); } catch (Throwable ignored) {}
        if (staffGuiService != null) {
            try {
                getServer().getMessenger().unregisterIncomingPluginChannel(
                        this,
                        com.vetroforge.staff.StaffGuiService.CHANNEL,
                        staffGuiService
                );
                getServer().getMessenger().unregisterOutgoingPluginChannel(
                        this,
                        com.vetroforge.staff.StaffGuiService.CHANNEL
                );
            } catch (Throwable ignored) {}
        }
        if (serverStatusWriterService != null) { serverStatusWriterService.stop(); }
        if (raidBoundaryListener != null) { raidBoundaryListener.stopTask(); raidBoundaryListener = null; }
        if (prestigeManager != null) prestigeManager.saveXp();
        try { if (warzoneBossBar != null) warzoneBossBar.stop(); } catch (Throwable ignored) {}
        try { if (warzoneMobService != null) warzoneMobService.stop(); } catch (Throwable ignored) {}
        try { if (travelerService != null) travelerService.stop(); } catch (Throwable ignored) {}
        try { if (tabHeaderFooterTask != null) tabHeaderFooterTask.stop(); } catch (Throwable ignored) {}
        try { if (prestigeManager != null) { prestigeManager.stopAutosave(); prestigeManager.saveXp(); } } catch (Throwable ignored) {}
        try { if (onlineTimeManager != null) onlineTimeManager.stop(); } catch (Throwable ignored) {}
        try {
            if (persistenceManager != null) {
                persistenceManager.stopAutosave();
                persistenceManager.saveAll();
            }
        } catch (Throwable ignored) {}
        try { if (factionRelationsManager != null) factionRelationsManager.save(); } catch (Throwable ignored) {}

        getComponentLogger().info(
                Component.text("Vetro", NamedTextColor.RED)
                        .append(Component.text("Forge ", NamedTextColor.GOLD))
                        .append(Component.text("disabled.", NamedTextColor.GRAY))
        );
    }

    public void onRankChanged(Player p) {
        if (nametagTabManager != null) nametagTabManager.updateTargetEverywhere(p);
        if (sidebarScoreboardManager != null) sidebarScoreboardManager.showLobbyBoard(p);
    }

    /* =================== Helpers =================== */
    private void register(Listener l) { getServer().getPluginManager().registerEvents(l, this); }

    private PluginCommand requireCommand(String name) {
        return Objects.requireNonNull(getCommand(name), "Missing '" + name + "' in plugin.yml");
    }

    private void registerIfPresent(String fqcn) {
        try {
            Class<?> cls = Class.forName(fqcn);
            Object inst = newInstancePossiblyWithPlugin(cls);
            if (inst instanceof Listener li) register(li);
        } catch (Throwable ignored) {}
    }

    private void registerWithArgsIfPresent(String fqcn, Object... args) {
        try {
            Class<?> cls = Class.forName(fqcn);
            for (Constructor<?> c : cls.getConstructors()) {
                if (c.getParameterCount() != args.length) continue;
                boolean match = true;
                Class<?>[] types = c.getParameterTypes();
                for (int i = 0; i < types.length; i++) {
                    if (args[i] != null && !types[i].isInstance(args[i])) { match = false; break; }
                }
                if (!match) continue;
                Object inst = c.newInstance(args);
                if (inst instanceof Listener li) register(li);
                return;
            }
        } catch (Throwable ignored) {}
    }
    private void registerCommand(String name, CommandExecutor exec, TabCompleter tab) {
        var pc = getCommand(name);
        if (pc == null) {
            getLogger().severe("Command '" + name + "' fehlt in plugin.yml!");
            return;
        }
        pc.setExecutor(exec);
        if (tab != null) pc.setTabCompleter(tab);
    }

    private void reg(String name, CommandExecutor exec, org.bukkit.command.TabCompleter tab) {
        PluginCommand pc = getCommand(name);
        if (pc == null) {
            getLogger().warning("Command '" + name + "' fehlt in plugin.yml!");
            return;
        }
        pc.setExecutor(exec);
        if (tab != null) pc.setTabCompleter(tab);
    }

    private void safeRegister(String name, CommandExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' fehlt in plugin.yml – Registrierung übersprungen.");
            return;
        }
        cmd.setExecutor(exec);
        if (exec instanceof org.bukkit.command.TabCompleter tc) cmd.setTabCompleter(tc);
    }

    /**
     * Commands ohne explizites "permission:" in plugin.yml UND ohne eigene interne
     * Berechtigungsprüfung im Handler. Nur Commands, die nachweislich reine
     * Spieler-Selfservice-Features sind (Info/Kosmetik/eigene Fortschritts-Anzeige),
     * dürfen hier für alle Spieler freigegeben sein. Alles andere fällt sicherheitshalber
     * auf PermissionDefault.OP zurück ("fail closed") statt für jeden Spieler offen zu sein.
     */
    private static final Set<String> PUBLIC_SELF_SERVICE_COMMANDS = Set.of(
            "cosmetics", "coins", "f", "raid", "raidjoin", "stats", "spawn", "hub", "help",
            "spell", "story", "top", "wiki", "wikisearch", "prestige", "reward", "fmenu",
            "ticket", "skill", "fshop", "key", "cratenpc", "iteminbox", "lobby", "fly",
            "pv", "kit", "starterkit", "dailykit", "weeklykit", "monthlykit", "apply",
            "kdr", "language", "quest", "rune", "crystals", "topraiders", "scout", "level",
            "fworld"
    );

    private void ensureCommandPermissions() {
        for (String name : getDescription().getCommands().keySet()) {
            PluginCommand command = getCommand(name);
            if (command == null || command.getPermission() != null) continue;

            String node = "vetroforge.command." + name.toLowerCase(Locale.ROOT);
            var manager = getServer().getPluginManager();
            if (manager.getPermission(node) == null) {
                boolean publicSelfService = PUBLIC_SELF_SERVICE_COMMANDS.contains(name.toLowerCase(Locale.ROOT));
                manager.addPermission(new org.bukkit.permissions.Permission(
                        node,
                        publicSelfService
                                ? org.bukkit.permissions.PermissionDefault.TRUE
                                : org.bukkit.permissions.PermissionDefault.OP
                ));
            }
            command.setPermission(node);
        }
    }

    private void safeRegisterIfPresent(String name, String fqcnExecutor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' fehlt in plugin.yml – Registrierung übersprungen.");
            return;
        }
        try {
            Class<?> cls = Class.forName(fqcnExecutor);
            Object inst = newInstancePossiblyWithPlugin(cls);
            if (!(inst instanceof CommandExecutor exec)) return;
            cmd.setExecutor(exec);
            if (inst instanceof org.bukkit.command.TabCompleter tc) cmd.setTabCompleter(tc);
        } catch (Throwable ignored) {}
    }

    private Object newInstancePossiblyWithPlugin(Class<?> cls) throws Exception {
        try {
            Constructor<?> c = cls.getConstructor(FactionsPlugin.class);
            return c.newInstance(this);
        } catch (NoSuchMethodException ignore) {
            return cls.getDeclaredConstructor().newInstance();
        }
    }

    private void trySaveResource(String path) {
        try {
            if (getResource(path) == null) return;
            java.io.File target = new java.io.File(getDataFolder(), path);
            if (target.exists()) return;
            saveResource(path, false);
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Konnte Resource nicht speichern: " + path + " -> " + t.getMessage());
        }
    }

    private AIService initAIService() {
        boolean enabled = getConfig().getBoolean("ai.enabled", true);
        String apiKey = configOrEnvironment("ai.key", "OPENAI_API_KEY").trim();
        if (!enabled || apiKey.isEmpty()) {
            getLogger().info("[VetroForge] AI: NOP");
            return new CachingAIService(new NopAIService());
        }
        try {
            Class<?> impl = Class.forName("com.vetroforge.ai.OpenAIServiceImpl");
            var ctor = impl.getConstructor(String.class);
            AIService real = (AIService) ctor.newInstance(apiKey);
            getLogger().info("[VetroForge] AI: OpenAI");
            return new CachingAIService(real);
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "[VetroForge] AI off (fallback to NOP): " + t.getMessage());
            return new CachingAIService(new NopAIService());
        }
    }

    public HoloLeaderboardService getHoloService(){ return holoService; }

    private void auditAndMergeConfig() {
        File file = new File(getDataFolder(), "config.yml");
        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            defaults.load(reader);
        } catch (Throwable t) {
            getLogger().warning("[ConfigDoctor] Konnte Default config.yml nicht laden: " + t.getMessage());
            reloadConfig();
            return;
        }

        int liveKeys = live.getKeys(true).size();
        int defaultKeys = defaults.getKeys(true).size();
        if (liveKeys < Math.max(40, defaultKeys / 3)) {
            getLogger().warning("[ConfigDoctor] config.yml wirkt unvollständig: " + liveKeys + " Keys, Default hat " + defaultKeys + ".");
        }

        int added = mergeMissingConfigValues(live, defaults, "");
        if (added > 0) {
            try {
                live.save(file);
                getLogger().warning("[ConfigDoctor] " + added + " fehlende Config-Werte aus Default ergänzt. Live-Werte wurden nicht überschrieben.");
            } catch (Throwable t) {
                getLogger().warning("[ConfigDoctor] Konnte gemergte config.yml nicht speichern: " + t.getMessage());
            }
        }

        reloadConfig();
        for (String required : new String[]{"mysql", "webapi", "redis", "shop", "lootdrop"}) {
            if (!getConfig().isConfigurationSection(required)) {
                getLogger().severe("[ConfigDoctor] Pflicht-Abschnitt fehlt weiterhin: " + required);
            }
        }
    }

    private int mergeMissingConfigValues(YamlConfiguration live, YamlConfiguration defaults, String path) {
        ConfigurationSection section = path.isEmpty() ? defaults : defaults.getConfigurationSection(path);
        if (section == null) return 0;

        int added = 0;
        for (String key : section.getKeys(false)) {
            String child = path.isEmpty() ? key : path + "." + key;
            if (defaults.isConfigurationSection(child)) {
                if (!live.isConfigurationSection(child)) {
                    live.createSection(child);
                    added++;
                }
                added += mergeMissingConfigValues(live, defaults, child);
            } else if (!live.isSet(child)) {
                live.set(child, defaults.get(child));
                added++;
            }
        }
        return added;
    }

    public String runDoctor() {
        StringBuilder out = new StringBuilder();
        out.append("§c§lVetroForge §8» §fDoctor\n");
        for (DoctorCheck check : runDoctorChecks()) {
            appendDoctor(out, check.label(), check.ok(), check.detail());
        }
        return out.toString();
    }

    public List<DoctorCheck> runDoctorChecks() {
        List<DoctorCheck> checks = new ArrayList<>();
        String statusUrl = firstConfig("status.public-url", "webapi.publicUrl", "webapi.public-url");
        checks.add(new DoctorCheck("Config mysql", getConfig().isConfigurationSection("mysql"), configSummary("mysql", "host", "database", "user")));
        checks.add(new DoctorCheck("Config webapi", getConfig().isConfigurationSection("webapi"), configSummary("webapi", "bind", "port", "publicUrl")));
        checks.add(new DoctorCheck("Config redis", getConfig().isConfigurationSection("redis"), configSummary("redis", "host", "port")));
        checks.add(new DoctorCheck("Config shop", getConfig().isConfigurationSection("shop.categories"), "categories=" + countKeys("shop.categories")));
        checks.add(new DoctorCheck("Config lootdrop", getConfig().isConfigurationSection("lootdrop"), "timed=" + getConfig().getBoolean("lootdrop.timed.enabled", false)));
        checks.add(new DoctorCheck("Config size", configLineCount() < 20000, configLineCount() + " Zeilen"));
        checks.add(new DoctorCheck("Level max", getConfig().getInt("level.maxLevel", 100) >= 100, "level.maxLevel=" + getConfig().getInt("level.maxLevel", 100)));
        checks.add(new DoctorCheck("Jar duplicates", !jarHasDuplicateArtifacts(), "2.class=" + (jarHasDuplicateArtifacts() ? "gefunden" : "keine")));
        checks.add(new DoctorCheck("Memory", runtimeMaxMemoryMb() >= 4096, runtimeMemorySummary()));
        checks.add(new DoctorCheck("Entities", totalEntityCount() < getConfig().getInt("doctor.entities.warnAt", 1500), "entities=" + totalEntityCount()));
        checks.add(new DoctorCheck("MySQL", testMysql(), getConfig().getString("mysql.user", "?") + "@" + getConfig().getString("mysql.host", "?") + "/" + getConfig().getString("mysql.database", "?")));
        checks.add(new DoctorCheck("Redis", testRedis(), getConfig().getString("redis.host", "127.0.0.1") + ":" + getConfig().getInt("redis.port", 6379)));
        checks.add(new DoctorCheck("WebAPI", webApiManager != null, "enabled=" + getConfig().getBoolean("webapi.enabled", false) + " port=" + getConfig().getInt("webapi.port", 8500)));
        checks.add(new DoctorCheck("SSL", statusUrl.startsWith("https://"), statusUrl.isBlank() ? "status.public-url fehlt" : statusUrl));
        checks.add(new DoctorCheck("Coins DB", networkDao != null, networkDao == null ? "fallback/local" : "NetworkDao aktiv"));
        return checks;
    }

    private long configLineCount() {
        try {
            File file = new File(getDataFolder(), "config.yml");
            if (!file.isFile()) return 0;
            return Files.lines(file.toPath()).count();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private boolean jarHasDuplicateArtifacts() {
        try (JarFile jar = new JarFile(getFile())) {
            return jar.stream().anyMatch(entry -> entry.getName().endsWith(" 2.class"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private long runtimeMaxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / 1024L / 1024L;
    }

    private String runtimeMemorySummary() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory() / 1024L / 1024L;
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        return "used=" + used + "MB max=" + max + "MB";
    }

    private int totalEntityCount() {
        int total = 0;
        try {
            for (org.bukkit.World world : Bukkit.getWorlds()) total += world.getEntities().size();
        } catch (Throwable ignored) {
        }
        return total;
    }

    public record DoctorCheck(String label, boolean ok, String detail) {}

    private void appendDoctor(StringBuilder out, String label, boolean ok, String detail) {
        out.append(ok ? "§a● " : "§c● ")
                .append("§f").append(label)
                .append(" §8- §7").append(detail == null ? "" : detail)
                .append("\n");
    }

    private String configSummary(String root, String... fields) {
        StringBuilder builder = new StringBuilder();
        for (String field : fields) {
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(field).append("=").append(getConfig().getString(root + "." + field, "-"));
        }
        return builder.toString();
    }

    private int countKeys(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        return section == null ? 0 : section.getKeys(false).size();
    }

    private boolean testMysql() {
        if (!getConfig().getBoolean("mysql.enabled", false)) return false;
        String db = firstConfig("mysql.database", "database.database", "database.name");
        String user = firstConfig("mysql.user", "mysql.username", "database.user", "database.username");
        String pass = configOrEnvironment("mysql.password", "VF_MYSQL_PASSWORD", "");
        String url = "jdbc:mysql://" + getConfig().getString("mysql.host", "127.0.0.1")
                + ":" + getConfig().getInt("mysql.port", 3306)
                + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection ignored = DriverManager.getConnection(url, user, pass)) {
            return true;
        } catch (Throwable t) {
            getLogger().warning("[Doctor] MySQL Check fehlgeschlagen: " + t.getMessage());
            return false;
        }
    }

    private boolean testRedis() {
        if (!getConfig().getBoolean("redis.enabled", false)) return false;
        try (redis.clients.jedis.Jedis jedis = new redis.clients.jedis.Jedis(
                getConfig().getString("redis.host", "127.0.0.1"),
                getConfig().getInt("redis.port", 6379))) {
            String password = getConfig().getString("redis.password", "");
            if (password != null && !password.isBlank()) jedis.auth(password);
            return "PONG".equalsIgnoreCase(jedis.ping());
        } catch (Throwable t) {
            getLogger().warning("[Doctor] Redis Check fehlgeschlagen: " + t.getMessage());
            return false;
        }
    }

    private String firstConfig(String... paths) {
        for (String path : paths) {
            String configured = getConfig().getString(path, "");
            if (configured != null && !configured.isBlank()) return configured;
        }
        return "";
    }

    private String configOrEnvironment(String path, String environment, String fallback) {
        String configured = getConfig().getString(path, "");
        if (configured != null && !configured.isBlank()) return configured;
        String value = System.getenv(environment);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String configOrEnvironment(String path, String environment) {
        String configured = getConfig().getString(path, "");
        if (configured != null && !configured.isBlank()) return configured;
        String value = System.getenv(environment);
        return value == null ? "" : value;
    }
    public AIService getAIService() { return aiService; }
    public AIBanManager getAIBanManager() { return aiBanManager; }
    public ReportService getReportService(){ return reportService; }
    public ChatLogService getChatLogService(){ return chatLogService; }
    public AntiCheatBridge getAntiCheatBridge(){ return antiCheatBridge; }
    public com.vetroforge.auron.AuronAntiCheatService getAuronAntiCheatService(){ return auronAntiCheatService; }
    public StaffProtectionService getStaffProtectionService() { return staffProtectionService; }
    public MaintenanceManager getMaintenanceManager() { return maintenanceManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public com.vetroforge.level.LevelProgressionService getLevelProgressionService() { return levelProgressionService; }
    public PrestigeManager getPrestigeManager() { return prestigeManager; }
    public com.vetroforge.gui.PrestigeGui getPrestigeGui() { return prestigeGui; }
    public FactionManager getFactionManager() { return factionManager; }
    public FactionRelationsManager getFactionRelationsManager() { return factionRelationsManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public com.vetroforge.punish.PunishmentHistoryManager getPunishmentHistory() { return punishmentHistory; }
    public com.vetroforge.punish.PunishmentHistoryManager getPunishmentHistoryManager() { return punishmentHistory; }
    public NotifyManager getNotifyManager() { return notifyManager; }
    public SeasonManager getSeasonManager() { return seasonManager; }
    public PvpKillListener getPvpKillListener() { return pvpKillListener; }
    public SpellManager getSpellManager(){ return spellManager; }
    public SidebarScoreboardManager getSidebarScoreboardManager() { return sidebarScoreboardManager; }
    public TabHeaderFooterTask getTabHeaderFooterTask() { return tabHeaderFooterTask; }
    public WarzoneRegionManager getWarzoneRegionManager() { return warzoneRegionManager; }
    public com.vetroforge.warzone.heatmap.WarzoneActivityService getWarzoneActivityService() { return warzoneActivityService; }
    public com.vetroforge.warzone.CombatTagService getCombatTagService() { return combatTagService; }
    public WarzoneMobService getWarzoneMobService() { return warzoneMobService; }

    public TravelerService getTravelerService() { return travelerService; }
    public RaidAnnouncements getRaidAnnouncements() { return raidAnnouncements; }
    public RaidManager getRaidManager() { return raidManager; }
    public ChestSpawnManager getChestSpawnManager() { return chestSpawnManager; }
    public KeyCrateManager getKeyCrateManager() { return keyCrateManager; }
    public GeneralShopManager getGeneralShopManager() { return generalShopManager; }
    public AuctionHouseManager getAuctionHouseManager() { return auctionHouseManager; }
    public CommerceNpcService getShopNpcService() { return shopNpcService; }
    public CommerceNpcService getAuctionNpcService() { return auctionNpcService; }
    public ShopAdminEditor getShopAdminEditor() { return shopAdminEditor; }
    public PersistenceManager getPersistenceManager() { return persistenceManager; }
    public WarzoneBossBarManager getWarzoneBossBar() { return warzoneBossBar; }
    public WebApiManager getWebApiManager() { return webApiManager; }
    public com.vetroforge.web.WebDeliveryService getWebDeliveryService() { return webDeliveryService; }
    public com.vetroforge.doctor.LegacyEntityCleaner getLegacyEntityCleaner() { return legacyEntityCleaner; }
    public VetroPermsManager getVetroPermsManager(){ return vetroPermsManager; }
    public TablistManager getTablistManager(){ return tablistManager; }
    public BalanceTuningManager getBalanceTuningManager() { return balanceTuningManager; }
    public StoryGuiManager getStoryGuiManager() { return storyGuiManager; }
    public TopGuiManager getTopGuiManager() { return topGuiManager; }
    public ChatLimiter getChatLimiter() { return chatLimiter; }
    public UpdateCheckerManager getUpdateCheckerManager() { return updateCheckerManager; }
    public ChestLootChanceManager getChestLootChanceManager() { return chestLootChanceManager; }
    public FactionMenuManager getFactionMenuManager() { return factionMenuManager; }
    public WikiManager getWikiManager() { return this.wikiManager; }
    public LanguageGui getLanguageGui() { return this.languageGui; }
    public BroadcastManager getBroadcastManager(){ return broadcastManager; }
    public CrateNpcManager getCrateNpcManager() { return crateNpcManager; }
    public NametagTabManager getNametagTabManager() { return nametagTabManager; }
    public WorldManager getWorldManager()       { return worldManager; }
    public com.vetroforge.manager.LobbyManager getLobbyManager() { return lobbyManager; }
    public FactionShopManager getFactionShopManager(){ return factionShopManager; }
    public I18nManager getI18n() { return i18n; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public com.vetroforge.manager.CosmeticsManager getCosmeticsManager() { return cosmeticsManager; }
    public RaidInstanceService getRaidInstanceService() { return raidInstanceService; }
    public com.vetroforge.raid.obsidian.ObsidianHealthManager getObsidianHealth(){ return obsidianHealthManager; }
    public com.vetroforge.raid.RaidCrystalManager getRaidCrystalManager(){ return raidCrystalManager; }
    public com.vetroforge.raid.scout.ScoutingService getScoutingService(){ return scoutingService; }
    public com.vetroforge.chat.ChatChannelManager getChatChannelManager(){ return chatChannelManager; }
    public com.vetroforge.lobby.PlayerVisibilityManager getPlayerVisibilityManager(){ return playerVisibilityManager; }
    public com.vetroforge.contracts.ContractsManager getContractsManager(){ return contractsManager; }
    public com.vetroforge.lootdrop.LootDropManager getLootDropManager(){ return lootDropManager; }
    public com.vetroforge.world.MysticStormService getMysticStormService(){ return mysticStormService; }
    public CrateBridge getCrateBridge(){ return crateBridge; }
    public TimedLootChestService getTimedLootChestService(){ return timedLootChestService; }
    public com.vetroforge.capture.CaptureZoneManager getCaptureZoneManager(){ return captureZoneManager; }
    public com.vetroforge.capture.CaptureZoneBuffService getCaptureZoneBuffService(){ return captureZoneBuffService; }
    public com.vetroforge.raid.energy.EnergyManager getEnergyManager() { return energyManager; }
    public com.vetroforge.raid.AntiStallService getAntiStallService() { return antiStallService; }
    public com.vetroforge.manager.SkillManager getSkillManager() { return skillManager; }
    public com.vetroforge.gui.SkillsGui getSkillsGui() { return skillsGui; }
    public com.vetroforge.gui.ForgeGui getForgeGui() { return forgeGui; }
    public com.vetroforge.gui.RunesGui getRunesGui() { return runesGui; }
    public com.vetroforge.warzone.PrestigeProgressBarManager getPrestigeProgressBar() { return prestigeProgressBarManager; }
    public void loadXp() { /* ... */ }
    public void saveXp() { /* ... */ }
    public com.vetroforge.gui.SkillTreeGui getSkillTreeGui() { return skillTreeGui; }
    public com.vetroforge.gui.LevelGui getLevelGui() { return levelGui; }
    public com.vetroforge.gui.CrateAdminGui getCrateAdminGui() { return crateAdminGui; }
    public StoryManager getStoryManager() { return storyManager; }
    public AICommerceManager getAiCommerceManager() { return aiCommerceManager; }
    public WarzoneEnterLeaveListener getWarzoneEnterLeaveListener() { return warzoneEnterLeaveListener; }
    public com.vetroforge.lobby.PlayHider getPlayHider() { return playHider; }
    public com.vetroforge.lobby.PlayerVisibilityGui getVisibilityGui() { return visibilityGui; }
    public com.vetroforge.lobby.LobbySettingsGui getLobbySettingsGui() { return lobbySettingsGui; }
    public com.vetroforge.lobby.LobbyNavigatorGui getLobbyNavigatorGui() { return lobbyNavigatorGui; }
    public RaidSpectatorService getRaidSpectatorService() { return raidSpectatorService; }
    public RaidTargetService getRaidTargetService() { return raidTargetService; }
    public RaidRewardService getRaidRewardService() { return raidRewardService; }
    public Object getWarzoneEnterListener() { return warzoneEnterLeaveListener; }
    public FactionCoinManager getFactionCoinManager() { return factionCoinManager; }
    public FactionBannerManager getFactionBannerManager() { return factionBannerManager; }
    public com.vetroforge.factions.FactionInboxService getFactionInboxService() { return factionInboxService; }
    public ItemInboxService getItemInboxService() { return itemInboxService; }
    public FactionUpgradeManager getFactionUpgradeManager() { return factionUpgradeManager; }
    public FactionBannerGui getFactionBannerGui() { return factionBannerGui; }
    public com.vetroforge.raid.shop.RaidShopGui getRaidShopGui() { return raidShopGui; }
    public CoinsManager getCoinsManager() { return coinsManager; }
    public com.vetroforge.items.armor.CustomArmorSetService getCustomArmorSetService() { return customArmorSetService; }
    public com.vetroforge.events.EventManager getEventManager() { return eventManager; }
    public com.vetroforge.items.ItemCooldownService getItemCooldownService() { return itemCooldownService; }
    public com.vetroforge.items.ItemProgressionService getItemProgressionService() { return itemProgressionService; }
    public com.vetroforge.quests.FactionWeeklyQuestService getFactionWeeklyQuestService() { return factionWeeklyQuestService; }
    public NetworkBridge getNetworkBridge() { return networkBridge; }
    public com.vetroforge.raid.energy.RaidSessionService getRaidSessionService() { return raidSessionService; }
    public com.vetroforge.raid.RaidJoinService getRaidJoinService() { return raidJoinService; }
    public FactionsDiscordBridgeService discordBridge() { return discordBridge; }
    public FactionDiscordSync factionDiscordSync() { return factionDiscordSync;
    }


    public synchronized TicketManager getTicketManager() {
        if (ticketManager == null) {
            try { ticketManager = new TicketManager(this); }
            catch (Throwable t) { getLogger().log(Level.WARNING, "TicketManager init fehlgeschlagen: " + t.getMessage()); }
        }
        return ticketManager;
    }
    public synchronized WebhookManager getWebhookManager() {
        if (webhookManager == null) {
            try { webhookManager = new WebhookManager(); }
            catch (Throwable t) { getLogger().log(Level.WARNING, "WebhookManager init fehlgeschlagen: " + t.getMessage()); }
        }
        return webhookManager;
    }


    private void bindCommand(String name, CommandExecutor exec) {
        PluginCommand pc = getCommand(name);
        if (pc == null) {
            getLogger().severe("Command '"+name+"' fehlt in plugin.yml");
            return;
        }
        pc.setExecutor(exec);
        if (exec instanceof TabCompleter tc) {
            pc.setTabCompleter(tc);
        }

    }

    // Vault
    public Economy getEconomy() { return (economyHook == null) ? null : economyHook.getEconomy(); }
    public EconomyHook getEconomyHook() { return economyHook; }
    public com.vetroforge.motd.MotdManager getMotdManager(){ return motdManager; }
    public BoosterManager getBoosterManager() { return boosterManager; }
    public com.vetroforge.services.ClearLagService getClearLagService() { return clearLagService; }
    public DailyRewardService getDailyRewardService() { return dailyRewardService; }

    /**
     * Vanguard+ perk: flight inside the player's own faction world, off everywhere else.
     * Denied while combat-tagged or while the faction is under an active raid, so it can't
     * be used to dodge fights or escape a base under attack.
     */
    public boolean canFlyInOwnFaction(org.bukkit.entity.Player player) {
        if (player == null) return false;
        if (!player.hasPermission("vetroforge.fly.faction") && !player.isOp() && !player.hasPermission("vetroforge.admin")) {
            return false;
        }
        FactionManager fm = getFactionManager();
        String faction = fm == null ? null : fm.getFactionOf(player.getUniqueId());
        if (faction == null) return false;
        org.bukkit.World world = player.getWorld();
        WorldManager wm = getWorldManager();
        if (world == null || wm == null || !world.getName().equalsIgnoreCase(wm.worldNameForFaction(faction))) {
            return false;
        }
        com.vetroforge.warzone.CombatTagService combat = getCombatTagService();
        if (combat != null && combat.isTagged(player.getUniqueId())) return false;
        com.vetroforge.raid.energy.RaidSessionService raid = getRaidSessionService();
        if (raid != null && raid.hasActiveRaidForFaction(faction)) return false;
        return true;
    }
}
