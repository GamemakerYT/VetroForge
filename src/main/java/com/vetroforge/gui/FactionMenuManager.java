package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FactionMenuManager implements Listener {

    private static final int MEMBER_PAGE_SIZE = 5;

    private final FactionsPlugin plugin;
    private final NamespacedKey actionKey;

    public FactionMenuManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "fmenu_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player player) {
        if (plugin.getFactionManager() == null || plugin.getRaidCrystalManager() == null) {
            player.sendMessage(tr(player, "message.unavailable"));
            return;
        }

        String faction = factionOf(player);
        if (faction == null) {
            openCreatePrompt(player);
            return;
        }
        int members = plugin.getFactionManager().getMembers(faction).size();
        int crystals = plugin.getRaidCrystalManager().getAvailable(player);
        int crystalCap = plugin.getRaidCrystalManager().getAvailableCap(player);

        Inventory inventory = Bukkit.createInventory(new MainHolder(), 45,
                "§6§lFaction §8» §f" + displayFaction(faction));
        VetroForgeUtil.fill(inventory, Material.BLACK_STAINED_GLASS_PANE);

        inventory.setItem(13, profileHead(player, faction, members, crystals, crystalCap));
        inventory.setItem(20, factionBanner(player, faction, members, crystals, crystalCap));
        inventory.setItem(22, action(Material.PLAYER_HEAD, tr(player, "members.name"), "members",
                tr(player, "members.lore", "members", members)));
        inventory.setItem(24, action(Material.NETHERITE_SWORD, tr(player, "raid.name"), "raid",
                tr(player, "raid.lore")));
        inventory.setItem(28, action(Material.LOOM, tr(player, "banner_design.name"), "banner",
                tr(player, "banner_design.lore")));
        inventory.setItem(30, action(Material.PAPER, tr(player, "stats.name"), "stats",
                tr(player, "stats.lore")));
        inventory.setItem(34, action(Material.NETHER_STAR, tr(player, "raid_shop.name"), "raid_shop",
                tr(player, "raid_shop.lore")));
        inventory.setItem(36, action(Material.ANVIL, tr(player, "upgrades.name"), "upgrades",
                tr(player, "upgrades.lore")));
        inventory.setItem(38, action(Material.COMPARATOR, "§b§lRollenrechte", "roleperms",
                "§7Bearbeite Rechte pro Faction-Rolle.",
                "§7Invite, Kick, Home, Raid und Bank.",
                "",
                "§eKlick §8» §fEditor öffnen"));
        inventory.setItem(40, item(Material.BOOK, tr(player, "help.name"), tr(player, "help.ready")));
        inventory.setItem(42, action(Material.ENDER_PEARL, "§b§lFaction Home", "home",
                "§7Teleportiert dich zum Faction-Home.",
                "§8/f home"));
        inventory.setItem(44, action(Material.RESPAWN_ANCHOR, "§d§lHome setzen", "sethome",
                "§7Setzt das Faction-Home an deiner Position.",
                "§8/f sethome"));

        player.openInventory(inventory);
        sound(player);
    }

    /** Zentrierter Einstieg fuer Spieler ohne Faction, statt direkt das volle (dann leere) Hauptmenue zu zeigen. */
    public void openCreatePrompt(Player player) {
        Inventory inventory = Bukkit.createInventory(new NoFactionHolder(), 27, "§6§lFaction §8» §7Neu");
        VetroForgeUtil.fill(inventory, Material.BLACK_STAINED_GLASS_PANE);

        int requiredLevel = plugin.getConfig().getInt("factions.create.requiredLevel", 4);
        int cost = plugin.getConfig().getInt("factions.create.costCoins", 1000);

        inventory.setItem(13, item(Material.NETHER_STAR, "§a§lFaction gründen",
                "§7Du bist noch in keiner Faction.",
                "",
                "§7Voraussetzung: §fLevel " + requiredLevel + ", §f" + cost + " Coins",
                "§7Nur im Spawn möglich.",
                "",
                "§eTippe: §f/f create <name>"));
        inventory.setItem(15, item(Material.WRITABLE_BOOK, "§b§lFaction beitreten",
                "§7Ein Faction-Leader lädt dich ein.",
                "§7Einladung annehmen mit:",
                "",
                "§eTippe: §f/f accept <name>"));

        player.openInventory(inventory);
        sound(player);
    }

    public void openMembers(Player player, int page) {
        String faction = factionOf(player);
        if (faction == null) {
            player.sendMessage(tr(player, "message.no_faction"));
            return;
        }

        List<UUID> members = new ArrayList<>(plugin.getFactionManager().getMembers(faction));
        members.sort(Comparator
                .comparing((UUID uuid) -> Bukkit.getPlayer(uuid) == null)
                .thenComparing(this::name, String.CASE_INSENSITIVE_ORDER));

        int lastPage = Math.max(1, (int) Math.ceil(members.size() / (double) MEMBER_PAGE_SIZE));
        int safePage = Math.max(1, Math.min(page, lastPage));
        int start = (safePage - 1) * MEMBER_PAGE_SIZE;
        int end = Math.min(members.size(), start + MEMBER_PAGE_SIZE);

        Inventory inventory = Bukkit.createInventory(new MembersHolder(safePage), 54,
                tr(player, "members.title", "page", safePage, "pages", lastPage));
        VetroForgeUtil.fill(inventory, Material.BLACK_STAINED_GLASS_PANE);

        for (int index = start; index < end; index++) {
            int row = index - start;
            UUID member = members.get(index);
            int baseSlot = row * 9;
            inventory.setItem(baseSlot, memberHead(player, member));
            inventory.setItem(baseSlot + 2, memberButton(Material.BOOK, "§eStats öffnen", "memberstats:" + member,
                    "§7Öffnet das Spielerprofil.", "§8" + name(member)));
            inventory.setItem(baseSlot + 4, roleButton(player, member));
            inventory.setItem(baseSlot + 6, kickButton(player, member));
        }

        inventory.setItem(45, action(Material.ARROW, tr(player, "back"), "back"));
        if (safePage > 1) inventory.setItem(48, action(Material.ARROW, tr(player, "previous"), "members:" + (safePage - 1)));
        inventory.setItem(49, item(Material.PAPER, tr(player, "members.page", "page", safePage, "pages", lastPage)));
        if (safePage < lastPage) inventory.setItem(50, action(Material.ARROW, tr(player, "next"), "members:" + (safePage + 1)));

        player.openInventory(inventory);
        sound(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MainHolder || holder instanceof NoFactionHolder || holder instanceof MembersHolder || holder instanceof RolePermHolder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        if (action.equals("back")) {
            openMain(player);
        } else if (action.startsWith("member:")) {
            handleMemberAction(player, holder instanceof MembersHolder mh ? mh.page() : 1, action.substring("member:".length()), "stats");
        } else if (action.startsWith("memberstats:")) {
            handleMemberAction(player, holder instanceof MembersHolder mh ? mh.page() : 1, action.substring("memberstats:".length()), "stats");
        } else if (action.startsWith("memberpromote:")) {
            handleMemberAction(player, holder instanceof MembersHolder mh ? mh.page() : 1, action.substring("memberpromote:".length()), "promote");
        } else if (action.startsWith("memberdemote:")) {
            handleMemberAction(player, holder instanceof MembersHolder mh ? mh.page() : 1, action.substring("memberdemote:".length()), "demote");
        } else if (action.startsWith("memberkick:")) {
            handleMemberAction(player, holder instanceof MembersHolder mh ? mh.page() : 1, action.substring("memberkick:".length()), "kick");
        } else if (action.startsWith("memberkickconfirm:")) {
            handleMemberAction(player, holder instanceof MembersHolder mh ? mh.page() : 1, action.substring("memberkickconfirm:".length()), "kick-confirm");
        } else if (action.equals("members") || action.startsWith("members:")) {
            int page = action.contains(":") ? Integer.parseInt(action.substring(action.indexOf(':') + 1)) : 1;
            openMembers(player, page);
        } else if (action.equals("roleperms")) {
            openRolePermissions(player, com.vetroforge.factions.FactionManager.Role.OFFICER);
        } else if (action.startsWith("roleperms:")) {
            openRolePermissions(player, parseRole(action.substring("roleperms:".length())));
        } else if (action.startsWith("roleperm:")) {
            handleRolePermissionToggle(player, action);
        } else if (action.startsWith("combatsetting:")) {
            handleCombatSettingToggle(player, action.substring("combatsetting:".length()));
        } else if (action.equals("raid")) {
            new RaidGui(plugin).open(player);
        } else if (action.equals("stats")) {
            player.closeInventory();
            player.performCommand("stats");
        } else if (action.equals("raid_shop")) {
            plugin.getRaidShopGui().open(player);
        } else if (action.equals("banner")) {
            plugin.getFactionBannerGui().open(player);
        } else if (action.equals("upgrades")) {
            plugin.getFactionUpgradeManager().open(player);
        } else if (action.equals("home")) {
            player.closeInventory();
            player.performCommand("f home");
        } else if (action.equals("sethome")) {
            player.closeInventory();
            player.performCommand("f sethome");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MainHolder || holder instanceof NoFactionHolder || holder instanceof MembersHolder || holder instanceof RolePermHolder) event.setCancelled(true);
    }

    private ItemStack profileHead(Player player, String faction, int members, int crystals, int cap) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName("§b§l" + player.getName());
        meta.setLore(List.of(
                tr(player, "profile.faction", "faction", displayFaction(faction)),
                tr(player, "profile.role", "role", role(player, player.getUniqueId())),
                tr(player, "profile.members", "members", members),
                tr(player, plugin.getRaidCrystalManager().useFactionSource()
                                ? "profile.crystals_faction"
                                : "profile.crystals_player",
                        "crystals", crystals, "cap", cap)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack factionBanner(Player player, String faction, int members, int crystals, int cap) {
        Material banner = faction == null || plugin.getFactionBannerManager() == null
                ? Material.GRAY_BANNER
                : plugin.getFactionBannerManager().getBanner(faction);
        ItemStack item = action(banner, tr(player, "banner.name"), "banner",
                tr(player, "banner.faction", "faction", displayFaction(faction)),
                tr(player, "banner.members", "members", members),
                tr(player, plugin.getRaidCrystalManager().useFactionSource()
                                ? "banner.crystals_faction"
                                : "banner.crystals_player",
                        "crystals", crystals, "cap", cap),
                faction == null ? tr(player, "banner.none") : tr(player, "banner.ready"));
        return item;
    }

    private ItemStack memberHead(Player viewer, UUID uuid) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        Player online = Bukkit.getPlayer(uuid);
        meta.setOwningPlayer(offline);
        meta.setDisplayName((online == null ? "§c" : "§a") + name(uuid));
        List<String> lore = new ArrayList<>();
        lore.add("§8" + uuid);
        lore.add("§7Rolle: §f" + role(viewer, uuid));
        lore.add("§7Status: " + (online == null ? "§cOffline" : "§aOnline"));
        lore.add("§7Zuletzt online: §f" + lastSeen(offline));
        lore.add("");
        lore.add("§7Nutze die Buttons rechts.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "member:" + uuid);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack roleButton(Player viewer, UUID uuid) {
        var role = plugin.getFactionManager().getRole(uuid);
        if (!canPromoteDemote(viewer, uuid)) {
            return memberButton(Material.GRAY_DYE, "§7Rolle gesperrt", "noop",
                    "§7Du kannst diese Rolle nicht ändern.", "§8" + role.name());
        }
        if (role == com.vetroforge.factions.FactionManager.Role.MEMBER) {
            return memberButton(Material.LIME_DYE, "§aPromote", "memberpromote:" + uuid,
                    "§7Befördert zum Officer.", "§8" + name(uuid));
        }
        return memberButton(Material.YELLOW_DYE, "§eDemote", "memberdemote:" + uuid,
                "§7Degradiert zum Member.", "§8" + name(uuid));
    }

    private ItemStack kickButton(Player viewer, UUID uuid) {
        if (!canManage(viewer, uuid)) {
            return memberButton(Material.GRAY_DYE, "§7Kick gesperrt", "noop",
                    "§7Dieses Mitglied kann nicht entfernt werden.", "§8" + name(uuid));
        }
        return memberButton(Material.RED_DYE, "§cEntfernen", "memberkick:" + uuid,
                "§7Öffnet eine Bestätigung.", "§8" + name(uuid));
    }

    private ItemStack memberButton(Material material, String name, String action, String... lore) {
        return action(material, name, action, lore);
    }

    private void handleMemberAction(Player viewer, int page, String uuidText, String action) {
        UUID targetId;
        try {
            targetId = UUID.fromString(uuidText);
        } catch (IllegalArgumentException ex) {
            viewer.sendMessage("§cUngültiges Mitglied.");
            return;
        }

        if ("kick".equals(action)) {
            if (!canManage(viewer, targetId)) {
                viewer.sendMessage("§cDu darfst dieses Mitglied nicht entfernen.");
                return;
            }
            openKickConfirm(viewer, page, targetId);
            return;
        }

        if ("kick-confirm".equals(action)) {
            if (!canManage(viewer, targetId)) {
                viewer.sendMessage("§cDu darfst dieses Mitglied nicht entfernen.");
                return;
            }
            boolean removed = plugin.getFactionManager().kick(viewer.getUniqueId(), targetId);
            viewer.sendMessage(removed
                    ? "§a" + name(targetId) + " wurde aus der Faction entfernt."
                    : "§cMitglied konnte nicht entfernt werden.");
            if (removed) {
                try {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null) target.sendMessage("§cDu wurdest aus deiner Faction entfernt.");
                } catch (Throwable ignored) {}
            }
            openMembers(viewer, page);
            return;
        }

        if ("promote".equals(action) || "demote".equals(action)) {
            if (!canPromoteDemote(viewer, targetId)) {
                viewer.sendMessage("§cDu darfst diese Rolle nicht ändern.");
                return;
            }
            var role = plugin.getFactionManager().getRole(targetId);
            boolean changed;
            if ("promote".equals(action) && role == com.vetroforge.factions.FactionManager.Role.MEMBER) {
                changed = plugin.getFactionManager().promote(viewer.getUniqueId(), targetId);
                viewer.sendMessage(changed
                        ? "§a" + name(targetId) + " ist jetzt Officer."
                        : "§cRolle konnte nicht geändert werden.");
            } else if ("demote".equals(action) && role == com.vetroforge.factions.FactionManager.Role.OFFICER) {
                changed = plugin.getFactionManager().demote(viewer.getUniqueId(), targetId);
                viewer.sendMessage(changed
                        ? "§e" + name(targetId) + " ist jetzt Member."
                        : "§cRolle konnte nicht geändert werden.");
            } else {
                changed = false;
                viewer.sendMessage("§cLeader können hier nicht geändert werden.");
            }
            if (changed) {
                try {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null) target.sendMessage("§6Faction §8» §7Deine Rolle wurde aktualisiert.");
                } catch (Throwable ignored) {}
            }
            openMembers(viewer, page);
            return;
        }

        viewer.closeInventory();
        viewer.performCommand("stats " + name(targetId));
    }

    private void openKickConfirm(Player viewer, int page, UUID targetId) {
        Inventory inventory = Bukkit.createInventory(new MembersHolder(page), 27,
                "§8✦ §c§lKick bestätigen §8• §f" + name(targetId));
        VetroForgeUtil.fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(11, memberHead(viewer, targetId));
        inventory.setItem(13, memberButton(Material.REDSTONE_BLOCK, "§4§lWirklich entfernen",
                "memberkickconfirm:" + targetId,
                "§7Entfernt §f" + name(targetId) + " §7aus der Faction.",
                "§cDieser Klick führt die Aktion aus."));
        inventory.setItem(15, action(Material.BARRIER, "§aAbbrechen", "members:" + page,
                "§7Zur Memberliste zurück."));
        viewer.openInventory(inventory);
        sound(viewer);
    }

    private void openRolePermissions(Player player, com.vetroforge.factions.FactionManager.Role role) {
        String faction = factionOf(player);
        if (faction == null) {
            player.sendMessage(tr(player, "message.no_faction"));
            return;
        }
        if (!plugin.getFactionManager().isLeader(player.getUniqueId())) {
            player.sendMessage("§cNur der Leader kann Rollenrechte bearbeiten.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(new RolePermHolder(role), 54,
                "§8✦ §b§lRollenrechte §8• §f" + roleLabel(role));
        VetroForgeUtil.fill(inventory, Material.BLACK_STAINED_GLASS_PANE);

        inventory.setItem(4, item(Material.COMPARATOR, "§b§lRollenrechte §8• §f" + displayFaction(faction),
                "§7Rechte werden pro Faction gespeichert.",
                "§7Leader-Rechte sind fest aktiv.",
                "",
                "§8Ausgewählt: §f" + roleLabel(role)));

        inventory.setItem(11, roleTab(com.vetroforge.factions.FactionManager.Role.MEMBER, role));
        inventory.setItem(13, roleTab(com.vetroforge.factions.FactionManager.Role.OFFICER, role));
        inventory.setItem(15, roleTab(com.vetroforge.factions.FactionManager.Role.LEADER, role));

        int[] slots = {28, 29, 30, 31, 32, 33};
        List<RolePermission> permissions = rolePermissions();
        for (int i = 0; i < permissions.size() && i < slots.length; i++) {
            RolePermission permission = permissions.get(i);
            boolean enabled = rolePermissionEnabled(faction, role, permission.key());
            boolean locked = role == com.vetroforge.factions.FactionManager.Role.LEADER;
            inventory.setItem(slots[i], action(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    (enabled ? "§a§l" : "§7§l") + permission.name(),
                    locked ? "noop" : "roleperm:" + role.name() + ":" + permission.key(),
                    permission.description(),
                    "",
                    locked ? "§8Leader: immer aktiv" : (enabled ? "§eKlick §8» §cDeaktivieren" : "§eKlick §8» §aAktivieren")));
        }

        if (role == com.vetroforge.factions.FactionManager.Role.LEADER) {
            boolean friendlyFire = factionCombatSetting(faction, "friendlyFire");
            boolean allyFire = factionCombatSetting(faction, "allyFire");
            inventory.setItem(40, action(friendlyFire ? Material.REDSTONE : Material.GRAY_DYE,
                    (friendlyFire ? "§c§l" : "§7§l") + "Team-Kill erlauben",
                    "combatsetting:friendlyFire",
                    "§7Eigene Faction-Mitglieder koennen",
                    "§7sich gegenseitig angreifen.",
                    "",
                    friendlyFire ? "§eKlick §8» §cDeaktivieren" : "§eKlick §8» §aAktivieren"));
            inventory.setItem(42, action(allyFire ? Material.REDSTONE : Material.GRAY_DYE,
                    (allyFire ? "§c§l" : "§7§l") + "Allianzen angreifbar",
                    "combatsetting:allyFire",
                    "§7Verbuendete Factions koennen",
                    "§7sich gegenseitig angreifen.",
                    "",
                    allyFire ? "§eKlick §8» §cDeaktivieren" : "§eKlick §8» §aAktivieren"));
        }

        inventory.setItem(45, action(Material.ARROW, tr(player, "back"), "back"));
        inventory.setItem(49, action(Material.PLAYER_HEAD, "§fMemberliste", "members",
                "§7Zurück zu den Mitgliedern."));
        player.openInventory(inventory);
        sound(player);
    }

    private ItemStack roleTab(com.vetroforge.factions.FactionManager.Role role, com.vetroforge.factions.FactionManager.Role selected) {
        Material material = role == selected ? Material.NETHER_STAR : Material.PAPER;
        return action(material, (role == selected ? "§b§l" : "§f") + roleLabel(role),
                "roleperms:" + role.name(),
                role == selected ? "§7Diese Rolle ist ausgewählt." : "§7Klick zum Bearbeiten.");
    }

    private void handleRolePermissionToggle(Player player, String action) {
        String faction = factionOf(player);
        if (faction == null) {
            player.sendMessage(tr(player, "message.no_faction"));
            return;
        }
        if (!plugin.getFactionManager().isLeader(player.getUniqueId())) {
            player.sendMessage("§cNur der Leader kann Rollenrechte bearbeiten.");
            return;
        }

        String[] parts = action.split(":", 3);
        if (parts.length < 3) return;
        com.vetroforge.factions.FactionManager.Role role = parseRole(parts[1]);
        if (role == com.vetroforge.factions.FactionManager.Role.LEADER) {
            openRolePermissions(player, role);
            return;
        }
        String key = parts[2];
        boolean next = !rolePermissionEnabled(faction, role, key);
        plugin.getConfig().set(rolePermissionPath(faction, role, key), next);
        plugin.saveConfig();
        player.sendMessage("§bFaction §8» §7Recht §f" + key + " §7für §f" + roleLabel(role)
                + " §7ist jetzt " + (next ? "§aaktiv" : "§cdeaktiviert") + "§7.");
        openRolePermissions(player, role);
    }

    private boolean rolePermissionEnabled(String faction, com.vetroforge.factions.FactionManager.Role role, String key) {
        if (role == com.vetroforge.factions.FactionManager.Role.LEADER) return true;
        String path = rolePermissionPath(faction, role, key);
        if (plugin.getConfig().isSet(path)) return plugin.getConfig().getBoolean(path);
        return defaultRolePermission(role, key);
    }

    private boolean defaultRolePermission(com.vetroforge.factions.FactionManager.Role role, String key) {
        if (role == com.vetroforge.factions.FactionManager.Role.OFFICER) {
            return key.equals("invite") || key.equals("kick") || key.equals("raid") || key.equals("chests");
        }
        return key.equals("chests");
    }

    private String rolePermissionPath(String faction, com.vetroforge.factions.FactionManager.Role role, String key) {
        String safeFaction = faction == null ? "unknown" : faction.toLowerCase(Locale.ROOT).replace(".", "_");
        return "factions.rolePermissions." + safeFaction + "." + role.name().toLowerCase(Locale.ROOT) + "." + key;
    }

    /** Faction-weite Kampfregeln, vom Leader gesetzt -- gelesen von WarzoneDamageRulesListener. */
    public static String combatSettingPath(String faction, String key) {
        String safeFaction = faction == null ? "unknown" : faction.toLowerCase(Locale.ROOT).replace(".", "_");
        return "factions.combatSettings." + safeFaction + "." + key;
    }

    private boolean factionCombatSetting(String faction, String key) {
        return plugin.getConfig().getBoolean(combatSettingPath(faction, key), false);
    }

    private void handleCombatSettingToggle(Player player, String key) {
        String faction = factionOf(player);
        if (faction == null) {
            player.sendMessage(tr(player, "message.no_faction"));
            return;
        }
        if (!plugin.getFactionManager().isLeader(player.getUniqueId())) {
            player.sendMessage("§cNur der Leader kann Kampfregeln bearbeiten.");
            return;
        }
        boolean next = !factionCombatSetting(faction, key);
        plugin.getConfig().set(combatSettingPath(faction, key), next);
        plugin.saveConfig();
        player.sendMessage("§bFaction §8» §7Kampfregel §f" + key + " §7ist jetzt " + (next ? "§aaktiv" : "§cdeaktiviert") + "§7.");
        openRolePermissions(player, com.vetroforge.factions.FactionManager.Role.LEADER);
    }

    private List<RolePermission> rolePermissions() {
        return List.of(
                new RolePermission("invite", "Einladen", "§7Darf Spieler in die Faction einladen."),
                new RolePermission("kick", "Kicken", "§7Darf normale Mitglieder entfernen."),
                new RolePermission("sethome", "Home setzen", "§7Darf das Faction-Home verwalten."),
                new RolePermission("raid", "Raid starten", "§7Darf Raids vorbereiten oder starten."),
                new RolePermission("chests", "Chests nutzen", "§7Darf Faction- und Warzone-Tools nutzen."),
                new RolePermission("treasury", "Bank nutzen", "§7Darf Faction-Bank/Treasury bedienen.")
        );
    }

    private com.vetroforge.factions.FactionManager.Role parseRole(String raw) {
        try {
            return com.vetroforge.factions.FactionManager.Role.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return com.vetroforge.factions.FactionManager.Role.MEMBER;
        }
    }

    private String roleLabel(com.vetroforge.factions.FactionManager.Role role) {
        return switch (role) {
            case LEADER -> "Leader";
            case OFFICER -> "Officer";
            default -> "Member";
        };
    }

    private ItemStack action(Material material, String name, String action, String... lore) {
        ItemStack item = item(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, String... lore) {
        return VetroForgeUtil.item(material, name, lore);
    }

    private String factionOf(Player player) {
        return plugin.getFactionManager().getFactionOf(player.getUniqueId());
    }

    private String displayFaction(String faction) {
        return faction == null ? "-" : plugin.getFactionManager().getDisplayName(faction);
    }

    private String name(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    private boolean canManage(Player viewer, UUID target) {
        if (viewer == null || target == null || viewer.getUniqueId().equals(target)) return false;
        String faction = factionOf(viewer);
        if (faction == null || !faction.equals(plugin.getFactionManager().getFactionOf(target))) return false;
        var viewerRole = plugin.getFactionManager().getRole(viewer.getUniqueId());
        var targetRole = plugin.getFactionManager().getRole(target);
        if (targetRole == com.vetroforge.factions.FactionManager.Role.LEADER) return false;
        if (viewerRole == com.vetroforge.factions.FactionManager.Role.LEADER) return true;
        return viewerRole == com.vetroforge.factions.FactionManager.Role.OFFICER
                && targetRole == com.vetroforge.factions.FactionManager.Role.MEMBER;
    }

    private boolean canPromoteDemote(Player viewer, UUID target) {
        if (viewer == null || target == null || viewer.getUniqueId().equals(target)) return false;
        String faction = factionOf(viewer);
        if (faction == null || !faction.equals(plugin.getFactionManager().getFactionOf(target))) return false;
        if (!plugin.getFactionManager().isLeader(viewer.getUniqueId())) return false;
        var targetRole = plugin.getFactionManager().getRole(target);
        return targetRole == com.vetroforge.factions.FactionManager.Role.MEMBER
                || targetRole == com.vetroforge.factions.FactionManager.Role.OFFICER;
    }

    private String lastSeen(OfflinePlayer player) {
        if (player == null) return "-";
        if (player.isOnline()) return "Jetzt";
        long last = player.getLastPlayed();
        if (last <= 0L) return "Nie";
        long diff = Math.max(0L, System.currentTimeMillis() - last);
        long minutes = diff / 60000L;
        if (minutes < 1) return "gerade eben";
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60L;
        if (hours < 24) return hours + "h";
        long days = hours / 24L;
        if (days < 30) return days + "d";
        return String.format(Locale.ROOT, "%.1f Monate", days / 30.0D);
    }

    private String role(Player viewer, UUID uuid) {
        return tr(viewer, "profile.roles." + plugin.getFactionManager().getRole(uuid).name().toLowerCase());
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "fmenu." + key, VetroForgeUtil.vars(values));
    }

    private void sound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private static final class MainHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class NoFactionHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record MembersHolder(int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record RolePermHolder(com.vetroforge.factions.FactionManager.Role role) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record RolePermission(String key, String name, String description) {}
}
