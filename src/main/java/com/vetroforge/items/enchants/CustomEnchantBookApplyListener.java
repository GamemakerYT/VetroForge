package com.vetroforge.items.enchants;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomEnchant;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CustomEnchantBookApplyListener implements Listener {
    private final FactionsPlugin plugin;
    private final Map<UUID, PendingApply> pending = new HashMap<>();
    private final Map<UUID, Long> rarityFusionLock = new HashMap<>();

    public CustomEnchantBookApplyListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onApplyBook(InventoryClickEvent event) {
        if (com.vetroforge.items.ItemSafety.readOnly(event.getView().getTopInventory())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack book = event.getCursor();
        if ((CustomItemUtil.enchantFromBook(plugin, book) != null || CustomItemUtil.rarityFusionFrom(plugin, book) != null)
                && com.vetroforge.items.ItemSafety.deny(player, book, event.getCurrentItem())) { event.setCancelled(true); return; }
        if (tryApplyRarityFusionBook(player, book, event)) return;

        CustomEnchant enchant = CustomItemUtil.enchantFromBook(plugin, book);
        if (enchant == null) return;

        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType().isAir()) return;

        event.setCancelled(true);

        int level = CustomItemUtil.enchantBookLevel(plugin, book);
        if (level <= 0) {
            player.sendMessage("§cDieses Apply-Buch ist beschädigt.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f);
            return;
        }

        if (!canApply(enchant, target)) {
            player.sendMessage("§c" + pretty(enchant) + " passt nicht auf dieses Item.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }

        if (requiresCustomWeapon(enchant) && CustomItemUtil.baseId(plugin, target) == null) {
            player.sendMessage("§cDieses Enchant funktioniert nur auf VetroForge Custom-Waffen.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }

        int current = CustomItemUtil.getEnchant(plugin, target, enchant);
        if (current >= level) {
            player.sendMessage("§eDieses Item hat bereits §f" + pretty(enchant) + " " + roman(current) + "§e.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.75f);
            return;
        }

        String signature = signature(target, enchant, level, current);
        PendingApply previous = pending.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (previous == null || !previous.matches(signature, now)) {
            pending.put(player.getUniqueId(), new PendingApply(signature, now + 10_000L));
            player.sendMessage("§c§lApply §8» §7Preview: §f" + pretty(enchant) + " " + roman(level)
                    + " §8(aktuell " + (current <= 0 ? "nicht vorhanden" : roman(current)) + ") §7auf §f" + target.getType().name());
            player.sendMessage("§c§lApply §8» §eLege das Buch nochmal auf dasselbe Item, um zu bestätigen.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.35f);
            return;
        }
        pending.remove(player.getUniqueId());

        CustomItemUtil.setEnchant(plugin, target, enchant, level);
        event.setCurrentItem(target);
        consumeOne(event, book);

        player.sendMessage("§a" + pretty(enchant) + " " + roman(level) + " §7wurde auf dein Item angewendet.");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.25f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.6f);
    }

    private boolean tryApplyRarityFusionBook(Player player, ItemStack book, InventoryClickEvent event) {
        Rarity from = CustomItemUtil.rarityFusionFrom(plugin, book);
        Rarity to = CustomItemUtil.rarityFusionTo(plugin, book);
        if (from == null || to == null) return false;

        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType().isAir()) return false;

        event.setCancelled(true);

        long fusionAttempt = System.nanoTime();
        long previousUse = rarityFusionLock.getOrDefault(player.getUniqueId(), 0L);
        if (fusionAttempt - previousUse < 250_000_000L) return true;

        if (event.getClickedInventory() == null || event.getClickedInventory() != player.getInventory()) {
            player.sendMessage("§cLege Fusion Books nur direkt in deinem Inventar auf ein Item.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return true;
        }

        if (target.getAmount() != 1) {
            player.sendMessage("§cDieses Item darf beim Upgrade nicht gestackt sein.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return true;
        }

        if (CustomItemUtil.baseId(plugin, target) == null) {
            player.sendMessage("§cFusion Books funktionieren nur auf VetroForge Custom-Items.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return true;
        }

        Rarity current = CustomItemUtil.rarityOf(plugin, target);
        if (current != from) {
            player.sendMessage("§cDieses Buch braucht ein " + from.colored() + " §cItem. Aktuell: " + current.colored());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.85f);
            return true;
        }

        if (to.ordinal() <= current.ordinal()) {
            player.sendMessage("§cDieses Fusion Book ist beschädigt.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f);
            return true;
        }

        rarityFusionLock.put(player.getUniqueId(), fusionAttempt);

        boolean reroll = plugin.getConfig().getBoolean("customitems.rarityFusion.rerollOnUpgrade", true);
        ItemStack upgraded = target.clone();
        upgraded.setAmount(1);
        CustomItemUtil.setRarity(plugin, upgraded, to, reroll);
        event.setCurrentItem(upgraded);
        consumeOne(event, book);

        player.sendMessage("§6§lFusion §8» §aRarity erhöht: " + from.colored() + " §8→ " + to.colored());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65f, 1.75f);
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
        return true;
    }

    private void consumeOne(InventoryClickEvent event, ItemStack book) {
        if (book.getAmount() <= 1) {
            event.setCursor(null);
            return;
        }
        ItemStack remaining = book.clone();
        remaining.setAmount(book.getAmount() - 1);
        event.setCursor(remaining);
    }

    private boolean canApply(CustomEnchant enchant, ItemStack target) {
        Material type = target.getType();
        return switch (enchant) {
            case GUARD -> isArmor(type);
            case LIFESTEAL, BLEED, RAGE -> isMeleeWeapon(type);
            case SHATTER -> isMeleeWeapon(type) || isTool(type);
        };
    }

    private boolean requiresCustomWeapon(CustomEnchant enchant) {
        return enchant != CustomEnchant.GUARD;
    }

    private boolean isArmor(Material type) {
        String name = type.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    private boolean isMeleeWeapon(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_HOE")
                || type == Material.TRIDENT;
    }

    private boolean isTool(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_AXE");
    }

    private String pretty(CustomEnchant enchant) {
        return switch (enchant) {
            case LIFESTEAL -> "Lifesteal";
            case BLEED -> "Bleed";
            case RAGE -> "Rage";
            case SHATTER -> "Shatter";
            case GUARD -> "Guard";
        };
    }

    private String signature(ItemStack target, CustomEnchant enchant, int level, int current) {
        String base = CustomItemUtil.baseId(plugin, target);
        return target.getType().name() + "|" + (base == null ? "vanilla" : base) + "|" + enchant.name() + "|" + level + "|" + current;
    }

    private String raritySignature(ItemStack target, Rarity from, Rarity to) {
        String base = CustomItemUtil.baseId(plugin, target);
        return "rarity|" + target.getType().name() + "|" + (base == null ? "vanilla" : base) + "|" + from.name() + "|" + to.name();
    }

    private String roman(int n) {
        return switch (Math.max(1, Math.min(10, n))) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }

    private record PendingApply(String signature, long expiresAt) {
        private boolean matches(String other, long now) {
            return now <= expiresAt && signature.equals(other);
        }
    }
}
