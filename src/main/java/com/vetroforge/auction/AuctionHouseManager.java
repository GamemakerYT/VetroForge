package com.vetroforge.auction;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

public final class AuctionHouseManager implements Listener {

    private static final String TITLE = "§8✦ §6§lAuktionshaus §8• §fMarkt";
    private static final String ADMIN_TITLE = "§8✦ §c§lAH Admin §8• §fKontrolle";
    private static final String ADMIN_SUSPICIOUS_TITLE = "§8✦ §c§lAH Admin §8• §fPrüfung";
    private static final String TITLE_PREFIX = TITLE;
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final FactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final NamespacedKey keyAuctionId;
    private final NamespacedKey keyAuctionFilter;
    private final NamespacedKey keyAuctionSeller;
    private final Map<UUID, SearchContext> pendingSearch = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingBid = new ConcurrentHashMap<>();

    public AuctionHouseManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auction-house.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.keyAuctionId = new NamespacedKey(plugin, "auction_id");
        this.keyAuctionFilter = new NamespacedKey(plugin, "auction_filter");
        this.keyAuctionSeller = new NamespacedKey(plugin, "auction_seller");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startBidResolver();
    }

    private void startBidResolver() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::resolveExpiredBids, 20L * 30L, 20L * 30L);
    }

    public void open(Player player) {
        open(player, null, null, 0);
    }

    public void search(Player player, String query) {
        open(player, query, null, 0);
    }

    public void category(Player player, String category) {
        open(player, null, category, 0);
    }

    public void openAdmin(Player player) {
        openAdmin(player, false, 0, null, false);
    }

    private void openAdmin(Player player, boolean suspiciousOnly) {
        openAdmin(player, suspiciousOnly, 0, null, false);
    }

    private void openAdmin(Player player, boolean suspiciousOnly, int page) {
        openAdmin(player, suspiciousOnly, page, null, false);
    }

    private void openAdmin(Player player, boolean suspiciousOnly, int page, UUID sellerFilter) {
        openAdmin(player, suspiciousOnly, page, sellerFilter, false);
    }

    private void openAdmin(Player player, boolean suspiciousOnly, int page, UUID sellerFilter, boolean confirmSellerDelete) {
        if (!player.hasPermission("vetroforge.auction.admin")) {
            player.sendMessage("§cKeine Rechte.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, suspiciousOnly ? ADMIN_SUSPICIOUS_TITLE : ADMIN_TITLE);
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " "));

        List<Listing> listings = listings();
        if (suspiciousOnly) listings.removeIf(listing -> suspiciousReasons(listing).isEmpty());
        if (sellerFilter != null) listings.removeIf(listing -> !listing.seller().equals(sellerFilter));
        listings.sort(Comparator.comparingLong(Listing::created).reversed());
        int maxPage = Math.max(0, (listings.size() - 1) / SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * SLOTS.length;
        int end = Math.min(listings.size(), start + SLOTS.length);
        int slotIndex = 0;
        for (int i = start; i < end; i++) {
            Listing listing = listings.get(i);
            ItemStack item = display(listing, false);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            List<String> reasons = suspiciousReasons(listing);
            lore.add(" ");
            if (!reasons.isEmpty()) {
                int risk = riskScore(reasons);
                lore.add(riskColor(risk) + "Risk: §f" + risk + "§8/§f100");
                lore.add("§cVerdächtig:");
                for (String reason : reasons) lore.add("§8• §c" + reason);
                lore.add(" ");
            }
            if (isQuarantined(listing.id())) {
                lore.add("§4Quarantäne: §cAktiv");
                lore.add(" ");
            }
            lore.add("§cShift-Klick: Auktion löschen");
            lore.add(isQuarantined(listing.id()) ? "§aDrop/Q: Quarantäne lösen" : "§cDrop/Q: Quarantäne setzen");
            lore.add("§eKlick: Seller filtern");
            lore.add("§7Seller UUID: §8" + listing.seller());
            if (meta != null) {
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(keyAuctionSeller, org.bukkit.persistence.PersistentDataType.STRING, listing.seller().toString());
                item.setItemMeta(meta);
            }
            inv.setItem(SLOTS[slotIndex++], item);
        }

        double feePct = plugin.getConfig().getDouble("auction.listingFeePct", 0.03);
        double taxPct = plugin.getConfig().getDouble("auction.salesTaxPct", 0.05);
        int lowValueMax = plugin.getConfig().getInt("auction.suspicious.lowValueMaxPrice", 1_000);
        int commonMax = plugin.getConfig().getInt("auction.suspicious.commonMaxPrice", 10_000);
        inv.setItem(35, adminButton(Material.REDSTONE, "§cListing Fee -1%", "fee:-0.01:" + modeTag(suspiciousOnly, safePage, sellerFilter), "§7Aktuell: §f" + percent(feePct)));
        inv.setItem(36, adminButton(Material.EMERALD, "§aListing Fee +1%", "fee:0.01:" + modeTag(suspiciousOnly, safePage, sellerFilter), "§7Aktuell: §f" + percent(feePct)));
        inv.setItem(26, adminButton(Material.REDSTONE_BLOCK, "§cSales Tax -1%", "tax:-0.01:" + modeTag(suspiciousOnly, safePage, sellerFilter), "§7Aktuell: §f" + percent(taxPct)));
        inv.setItem(44, adminButton(Material.EMERALD_BLOCK, "§aSales Tax +1%", "tax:0.01:" + modeTag(suspiciousOnly, safePage, sellerFilter), "§7Aktuell: §f" + percent(taxPct)));
        inv.setItem(45, adminButton(Material.ARROW, "§eVorherige Seite", "page:" + (safePage - 1) + ":" + modeTag(suspiciousOnly, safePage, sellerFilter),
                "§7Seite §f" + (safePage + 1) + "§8/§f" + (maxPage + 1)));
        inv.setItem(46, adminButton(Material.ARROW, "§eNächste Seite", "page:" + (safePage + 1) + ":" + modeTag(suspiciousOnly, safePage, sellerFilter),
                "§7Seite §f" + (safePage + 1) + "§8/§f" + (maxPage + 1)));
        inv.setItem(47, adminButton(Material.DIRT, "§cLow-Value Max -100", "suspicious-low:-100:" + modeTag(suspiciousOnly, safePage, sellerFilter),
                "§7Aktuell: §6" + lowValueMax));
        inv.setItem(48, adminButton(Material.COARSE_DIRT, "§aLow-Value Max +100", "suspicious-low:100:" + modeTag(suspiciousOnly, safePage, sellerFilter),
                "§7Aktuell: §6" + lowValueMax));
        inv.setItem(49, item(Material.NETHER_STAR, "§c§lAH Admin §8• §fÜbersicht", List.of(
                "§7Listings: §f" + listings.size(),
                "§7Seite: §f" + (safePage + 1) + "§8/§f" + (maxPage + 1),
                suspiciousOnly ? "§cModus: Verdächtige Auktionen" : "§7Modus: Alle Auktionen",
                sellerFilter == null ? "§7Seller-Filter: §fAus" : "§7Seller-Filter: §f" + sellerFilter,
                "§7Common Max: §6" + commonMax,
                "§7Low-Value Max: §6" + lowValueMax,
                "§7Shift-Klick auf Listing löscht es.",
                "§7Seller sieht man in der Lore."
        )));
        inv.setItem(50, adminButton(Material.PAPER, "§cCommon Max -1000", "suspicious-common:-1000:" + modeTag(suspiciousOnly, safePage, sellerFilter),
                "§7Aktuell: §6" + commonMax));
        inv.setItem(51, sellerFilter == null
                ? adminButton(Material.MAP, "§aCommon Max +1000", "suspicious-common:1000:" + modeTag(suspiciousOnly, safePage, null),
                    "§7Aktuell: §6" + commonMax)
                : adminButton(confirmSellerDelete ? Material.REDSTONE_BLOCK : Material.TNT,
                    confirmSellerDelete ? "§4BESTÄTIGEN: Seller-Auktionen löschen" : "§cAlle Auktionen dieses Sellers löschen",
                    (confirmSellerDelete ? "seller-confirm:" : "seller-delete:") + modeTag(suspiciousOnly, safePage, sellerFilter),
                    "§7Seller: §f" + sellerFilter,
                    confirmSellerDelete ? "§cKlick löscht wirklich alle Auktionen dieses Sellers." : "§7Erfordert einen zweiten Bestätigungsklick."));
        if (sellerFilter == null) {
            inv.setItem(25, adminButton(Material.NETHER_STAR, "§cTop Risk Dashboard", "risk:" + modeTag(true, 0, null),
                    "§7Sortiert verdächtige Auktionen nach Risiko."));
        }
        inv.setItem(52, sellerFilter == null
                ? adminButton(suspiciousOnly ? Material.CHEST : Material.TNT,
                    suspiciousOnly ? "§fAlle Auktionen" : "§cVerdächtige Auktionen",
                    suspiciousOnly ? "all:0" : "suspicious:0",
                    suspiciousOnly ? "§7Zur normalen Admin-Ansicht" : "§7Unter Mindestpreis / absurde Common-Preise")
                : adminButton(Material.MILK_BUCKET, "§fSeller-Filter löschen", "seller-clear:" + modeTag(suspiciousOnly, 0, null),
                    "§7Zeigt wieder alle Seller."));
        inv.setItem(53, adminButton(Material.BARRIER, "§cSchließen", "close", "§7GUI schließen"));
        player.openInventory(inv);
    }

    private void open(Player player, String query, String category, int page) {
        open(player, query, category, page, false);
    }

    private void open(Player player, String query, String category, int page, boolean mineOnly) {
        String suffix = query != null && !query.isBlank()
                ? " §8Suche: " + query
                : category != null && !category.isBlank() ? " §8" + categoryName(category) : "";
        if (mineOnly) suffix += " §8Meine";
        Inventory inv = Bukkit.createInventory(null, 54, TITLE + suffix);
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(4, item(Material.NETHER_STAR, "§6§lAuktionshaus §8• §fSpielermarkt", List.of(
                "§7Sofortkauf: §f/ah sell <preis>",
                "§7Bid-Auktion: §f/ah auction <startpreis> <minuten>",
                "§7Suche gezielt mit §f/ah search <item>",
                "§7Klicke ein Listing, um zu kaufen oder zu bieten.",
                "",
                query == null ? "§8Suche: §7-" : "§8Suche: §f" + query,
                category == null ? "§8Kategorie: §7Alle" : "§8Kategorie: §f" + categoryName(category),
                mineOnly ? "§8Ansicht: §bMeine Auktionen" : "§8Ansicht: §7Alle Auktionen"
        )));
        inv.setItem(45, filterButton(Material.GRASS_BLOCK, "§aBlöcke", "blocks"));
        inv.setItem(46, filterButton(Material.DIAMOND_SWORD, "§cCombat", "combat"));
        inv.setItem(47, filterButton(Material.NETHERITE_CHESTPLATE, "§bGear", "gear"));
        inv.setItem(48, filterButton(Material.NETHER_STAR, "§6Crystals", "keys"));
        inv.setItem(49, filterButton(Material.CHEST, "§fAlle", "all"));
        inv.setItem(50, filterButton(Material.NETHER_STAR, "§5Mystic", "mystic"));

        List<Listing> listings = listings();
        if (mineOnly) listings.removeIf(listing -> !listing.seller().equals(player.getUniqueId()));
        listings.removeIf(listing -> !matches(listing, query, category));
        listings.sort(Comparator.comparingLong(Listing::created).reversed());

        int maxPage = Math.max(0, (listings.size() - 1) / SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * SLOTS.length;
        int end = Math.min(listings.size(), start + SLOTS.length);
        int index = 0;
        for (int i = start; i < end; i++) {
            Listing listing = listings.get(i);
            inv.setItem(SLOTS[index++], display(listing, listing.seller().equals(player.getUniqueId())));
        }
        inv.setItem(44, mineOnly ? filterButton(Material.CHEST, "§fAlle Auktionen",
                        mineTag(false, query, category),
                        "§7Zeigt wieder den gesamten Markt.",
                        "§7Aktuelle Treffer: §f" + listings.size())
                : myAuctionsButton(player, mineTag(true, query, category), listings.size()));
        inv.setItem(51, filterButton(Material.ARROW, "§e← Vorherige Seite", pageTag(safePage - 1, query, category, mineOnly),
                "§7Seite §f" + (safePage + 1) + "§8/§f" + (maxPage + 1)));
        inv.setItem(52, filterButton(Material.ARROW, "§eNächste Seite →", pageTag(safePage + 1, query, category, mineOnly),
                "§7Seite §f" + (safePage + 1) + "§8/§f" + (maxPage + 1)));
        inv.setItem(53, filterButton(Material.OAK_SIGN, "§fSuche", searchTag(category, mineOnly),
                "§7Klick und schreibe danach",
                "§7deinen Suchbegriff in den Chat.",
                "§7Schreibe §fcancel §7zum Abbrechen.",
                "§7Aktuelle Treffer: §f" + listings.size(),
                "§7Seite: §f" + (safePage + 1) + "§8/§f" + (maxPage + 1)));
        player.openInventory(inv);
    }

    public synchronized boolean sell(Player player, int price) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§cHalte ein Item in der Hand.");
            return false;
        }
        int min = plugin.getConfig().getInt("auction.minPrice", 1);
        int max = plugin.getConfig().getInt("auction.maxPrice", 10_000_000);
        if (price < min || price > max) {
            player.sendMessage("§cPreis muss zwischen " + min + " und " + max + " Coins liegen.");
            return false;
        }

        ItemStack listed = hand.clone();
        int minByRarity = minimumPriceFor(listed);
        if (price < minByRarity) {
            player.sendMessage("§cDieses Item ist zu selten. Mindestpreis: §6" + minByRarity + " Coins§c.");
            return false;
        }

        int fee = fee(price, plugin.getConfig().getDouble("auction.listingFeePct", 0.03));
        if (plugin.getCoinsManager() == null || plugin.getCoinsManager().getCoins(player.getUniqueId()) < fee) {
            player.sendMessage("§cNicht genug Coins für die Listing-Gebühr (§6" + fee + " Coins§c).");
            return false;
        }
        if (fee > 0) plugin.getCoinsManager().removeCoins(player.getUniqueId(), fee);

        player.getInventory().setItemInMainHand(null);

        String id = String.valueOf(nextId());
        String root = "listings." + id;
        yaml.set(root + ".seller", player.getUniqueId().toString());
        yaml.set(root + ".sellerName", player.getName());
        yaml.set(root + ".price", price);
        yaml.set(root + ".created", Instant.now().toEpochMilli());
        yaml.set(root + ".item", listed);
        yaml.set(root + ".type", "BIN");
        save();
        autoFlagIfRisky(id);

        player.sendMessage("§aAuktion erstellt: §f#" + id + " §7für §6" + price
                + " Coins§7. Fee verbrannt: §6" + fee + " Coins§7.");
        return true;
    }

    /** BID-Auktion: Startpreis, läuft ab, Höchstbietender gewinnt (wie Hypixel Skyblock BID). */
    public synchronized boolean sellBid(Player player, int startPrice, int minutes) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§cHalte ein Item in der Hand.");
            return false;
        }
        int min = plugin.getConfig().getInt("auction.minPrice", 1);
        int max = plugin.getConfig().getInt("auction.maxPrice", 10_000_000);
        if (startPrice < min || startPrice > max) {
            player.sendMessage("§cStartpreis muss zwischen " + min + " und " + max + " Coins liegen.");
            return false;
        }

        ItemStack listed = hand.clone();
        int minByRarity = minimumPriceFor(listed);
        if (startPrice < minByRarity) {
            player.sendMessage("§cDieses Item ist zu selten. Mindest-Startpreis: §6" + minByRarity + " Coins§c.");
            return false;
        }

        int minMinutes = plugin.getConfig().getInt("auction.bid.minMinutes", 10);
        int maxMinutes = plugin.getConfig().getInt("auction.bid.maxMinutes", 2880);
        if (minutes < minMinutes || minutes > maxMinutes) {
            player.sendMessage("§cDauer muss zwischen " + minMinutes + " und " + maxMinutes + " Minuten liegen.");
            return false;
        }

        int fee = fee(startPrice, plugin.getConfig().getDouble("auction.listingFeePct", 0.03));
        if (plugin.getCoinsManager() == null || plugin.getCoinsManager().getCoins(player.getUniqueId()) < fee) {
            player.sendMessage("§cNicht genug Coins für die Listing-Gebühr (§6" + fee + " Coins§c).");
            return false;
        }
        if (fee > 0) plugin.getCoinsManager().removeCoins(player.getUniqueId(), fee);

        player.getInventory().setItemInMainHand(null);

        String id = String.valueOf(nextId());
        String root = "listings." + id;
        yaml.set(root + ".seller", player.getUniqueId().toString());
        yaml.set(root + ".sellerName", player.getName());
        yaml.set(root + ".price", startPrice);
        yaml.set(root + ".created", Instant.now().toEpochMilli());
        yaml.set(root + ".item", listed);
        yaml.set(root + ".type", "BID");
        yaml.set(root + ".endsAt", System.currentTimeMillis() + minutes * 60_000L);
        save();
        autoFlagIfRisky(id);

        player.sendMessage("§aBid-Auktion erstellt: §f#" + id + " §7Startpreis §6" + startPrice
                + " Coins§7, Dauer §f" + minutes + "m§7. Fee verbrannt: §6" + fee + " Coins§7.");
        return true;
    }

    /** Gebot auf eine BID-Auktion. Vorheriges Höchstgebot wird sofort zurückerstattet. */
    public synchronized boolean bid(Player bidder, String id, int amount) {
        Listing listing = listing(id);
        if (listing == null) {
            bidder.sendMessage("§cDiese Auktion existiert nicht mehr.");
            return false;
        }
        if (!listing.isBid()) {
            bidder.sendMessage("§cDas ist keine Bid-Auktion.");
            return false;
        }
        if (isQuarantined(id)) {
            bidder.sendMessage("§cDiese Auktion ist aktuell in Staff-Quarantäne.");
            return false;
        }
        if (listing.endsAt() <= System.currentTimeMillis()) {
            bidder.sendMessage("§cDiese Auktion ist bereits beendet.");
            return false;
        }
        if (listing.seller().equals(bidder.getUniqueId())) {
            bidder.sendMessage("§cDu kannst nicht auf deine eigene Auktion bieten.");
            return false;
        }

        int max = plugin.getConfig().getInt("auction.maxPrice", 10_000_000);
        if (amount <= 0 || amount > max) {
            bidder.sendMessage("§cGebot muss zwischen 1 und " + max + " Coins liegen.");
            return false;
        }

        int minNextBid = minNextBid(listing);
        if (amount < minNextBid) {
            bidder.sendMessage("§cGebot zu niedrig. Mindestgebot: §6" + minNextBid + " Coins§c.");
            return false;
        }
        if (plugin.getCoinsManager() == null || plugin.getCoinsManager().getCoins(bidder.getUniqueId()) < amount) {
            bidder.sendMessage("§cNicht genug Coins.");
            return false;
        }

        if (listing.currentBidder() != null) {
            plugin.getCoinsManager().addCoins(listing.currentBidder(), listing.currentBid());
            Player outbid = Bukkit.getPlayer(listing.currentBidder());
            if (outbid != null) {
                outbid.sendMessage("§6Auktionshaus §8» §7Du wurdest bei Auktion §f#" + id
                        + " §7überboten. §6+" + listing.currentBid() + " Coins §7zurückerstattet.");
            }
        }

        plugin.getCoinsManager().removeCoins(bidder.getUniqueId(), amount);

        String root = "listings." + id;
        yaml.set(root + ".currentBid", amount);
        yaml.set(root + ".currentBidder", bidder.getUniqueId().toString());
        yaml.set(root + ".currentBidderName", bidder.getName());
        save();

        bidder.sendMessage("§aGebot abgegeben: §f#" + id + " §7für §6" + amount + " Coins§7.");
        Player sellerOnline = Bukkit.getPlayer(listing.seller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage("§6Auktionshaus §8» §f" + bidder.getName() + " §7hat §6" + amount
                    + " Coins §7auf deine Auktion §f#" + id + " §7geboten.");
        }
        return true;
    }

    private int minNextBid(Listing listing) {
        int currentEffective = listing.currentBid() > 0 ? listing.currentBid() : listing.price();
        if (listing.currentBid() <= 0) return listing.price();
        int minIncrement = Math.max(1, (int) Math.ceil(currentEffective * plugin.getConfig().getDouble("auction.bid.minIncrementPct", 0.02)));
        return currentEffective + minIncrement;
    }

    /** Läuft periodisch: schließt abgelaufene BID-Auktionen ab (Auszahlung/Lieferung oder Rückgabe ohne Gebote). */
    private synchronized void resolveExpiredBids() {
        long now = System.currentTimeMillis();
        for (Listing listing : listings()) {
            if (!listing.isBid() || listing.endsAt() <= 0 || listing.endsAt() > now) continue;
            // Erst persistieren, dann ausliefern: ein Crash zwischen beiden Schritten darf höchstens
            // eine Auslieferung verlieren (manuell nachholbar), niemals eine doppelt auszahlen.
            yaml.set("listings." + listing.id(), null);
            save();

            if (listing.currentBidder() != null) {
                int tax = fee(listing.currentBid(), plugin.getConfig().getDouble("auction.salesTaxPct", 0.05));
                int payout = Math.max(0, listing.currentBid() - tax);
                if (plugin.getCoinsManager() != null) plugin.getCoinsManager().addCoins(listing.seller(), payout);
                returnToSeller(listing.currentBidder(), listing.item());
                recordSale(listing.item(), listing.currentBid());

                Player winner = Bukkit.getPlayer(listing.currentBidder());
                if (winner != null) {
                    winner.sendMessage("§aAuktion gewonnen: §f#" + listing.id() + " §7für §6" + listing.currentBid() + " Coins§7.");
                }
                Player sellerOnline = Bukkit.getPlayer(listing.seller());
                if (sellerOnline != null) {
                    sellerOnline.sendMessage("§6Auktionshaus §8» §7Deine Auktion §f#" + listing.id()
                            + " §7wurde für §6" + listing.currentBid() + " Coins §7verkauft. Auszahlung: §6"
                            + payout + " Coins §8(Steuer: " + tax + ")");
                }
            } else {
                returnToSeller(listing.seller(), listing.item());
                Player sellerOnline = Bukkit.getPlayer(listing.seller());
                if (sellerOnline != null) {
                    sellerOnline.sendMessage("§6Auktionshaus §8» §7Deine Auktion §f#" + listing.id()
                            + " §7ist ohne Gebote ausgelaufen. Item wurde zurückgegeben.");
                }
            }
        }
    }

    public synchronized boolean cancel(Player player, String id) {
        Listing listing = listing(id);
        if (listing == null) {
            player.sendMessage("§cAuktion nicht gefunden.");
            return false;
        }
        if (!listing.seller().equals(player.getUniqueId()) && !player.hasPermission("vetroforge.auction.admin")) {
            player.sendMessage("§cDas ist nicht deine Auktion.");
            return false;
        }
        if (listing.isBid() && listing.currentBidder() != null) {
            player.sendMessage("§cDiese Bid-Auktion hat bereits ein Gebot und kann nicht mehr zurückgezogen werden.");
            return false;
        }
        returnToSeller(listing.seller(), listing.item());
        yaml.set("listings." + id, null);
        save();
        player.sendMessage("§aAuktion #" + id + " zurückgenommen.");
        if (!listing.seller().equals(player.getUniqueId())) {
            Player sellerOnline = Bukkit.getPlayer(listing.seller());
            if (sellerOnline != null) {
                sellerOnline.sendMessage("§6Auktionshaus §8» §7Deine Auktion §f#" + id + " §7wurde von einem Admin zurückgezogen. Das Item wurde dir zurückgegeben.");
            }
        }
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle().startsWith(ADMIN_TITLE)
                || event.getView().getTitle().startsWith(ADMIN_SUSPICIOUS_TITLE)) {
            event.setCancelled(true);
            if (!player.hasPermission("vetroforge.auction.admin")) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;
            String filter = clicked.getItemMeta().getPersistentDataContainer()
                    .get(keyAuctionFilter, org.bukkit.persistence.PersistentDataType.STRING);
            if (filter != null) {
                handleAdminButton(player, filter);
                return;
            }
            String id = clicked.getItemMeta().getPersistentDataContainer()
                    .get(keyAuctionId, org.bukkit.persistence.PersistentDataType.STRING);
            if (id != null && event.isShiftClick()) {
                adminDelete(player, id);
                return;
            }
            if (id != null && (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP)) {
                setQuarantine(player, id, !isQuarantined(id));
                return;
            }
            String sellerRaw = clicked.getItemMeta().getPersistentDataContainer()
                    .get(keyAuctionSeller, org.bukkit.persistence.PersistentDataType.STRING);
            if (sellerRaw != null) {
                openAdmin(player, event.getView().getTitle().equals(ADMIN_SUSPICIOUS_TITLE), 0, UUID.fromString(sellerRaw));
                return;
            }
            return;
        }
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;
        String filter = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyAuctionFilter, org.bukkit.persistence.PersistentDataType.STRING);
        if (filter != null) {
            if (filter.startsWith("search|")) {
                pendingBid.remove(player.getUniqueId());
                pendingSearch.put(player.getUniqueId(), new SearchContext(decoded(filter, 1), booleanFromPipeTag(filter, 2)));
                player.closeInventory();
                player.sendMessage("§6Auktionshaus §8» §7Schreibe deinen Suchbegriff in den Chat. §8(cancel zum Abbrechen)");
                return;
            }
            if (filter.startsWith("page|")) {
                open(player, decoded(filter, 3), decoded(filter, 2), pageFromPipeTag(filter), booleanFromPipeTag(filter, 4));
                return;
            }
            if (filter.startsWith("mine|")) {
                open(player, decoded(filter, 3), decoded(filter, 2), 0, booleanFromPipeTag(filter, 1));
                return;
            }
            if (filter.equals("all")) open(player);
            else category(player, filter);
            return;
        }
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyAuctionId, org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null) return;
        Listing listing = listing(id);
        if (listing == null) return;
        if (listing.seller().equals(player.getUniqueId())) {
            cancel(player, id);
            open(player);
            return;
        }
        if (listing.isBid()) {
            pendingSearch.remove(player.getUniqueId());
            pendingBid.put(player.getUniqueId(), id);
            player.closeInventory();
            player.sendMessage("§6Auktionshaus §8» §7Schreibe dein Gebot (mind. §6" + minNextBid(listing)
                    + " Coins§7) in den Chat. §8(cancel zum Abbrechen)");
            return;
        }
        buy(player, id);
    }

    @EventHandler
    public void onBidChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String id = pendingBid.remove(uuid);
        if (id == null) return;
        pendingSearch.remove(uuid);
        event.setCancelled(true);
        String raw = event.getMessage() == null ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("abbrechen")) {
                player.sendMessage("§6Auktionshaus §8» §7Gebot abgebrochen.");
                open(player);
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                player.sendMessage("§cBitte gib eine Zahl ein.");
                open(player);
                return;
            }
            bid(player, id, amount);
            open(player);
        });
    }

    @EventHandler
    public void onSearchChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        SearchContext context = pendingSearch.remove(uuid);
        if (context == null) return;
        pendingBid.remove(uuid);
        event.setCancelled(true);
        String query = event.getMessage() == null ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            if (query.equalsIgnoreCase("cancel") || query.equalsIgnoreCase("abbrechen")) {
                player.sendMessage("§6Auktionshaus §8» §7Suche abgebrochen.");
                open(player, null, context.category(), 0, context.mineOnly());
                return;
            }
            if (query.isBlank()) {
                player.sendMessage("§cBitte gib einen Suchbegriff ein.");
                open(player, null, context.category(), 0, context.mineOnly());
                return;
            }
            open(player, query, context.category(), 0, context.mineOnly());
        });
    }

    public synchronized boolean buy(Player buyer, String id) {
        Listing listing = listing(id);
        if (listing == null) {
            buyer.sendMessage("§cDiese Auktion existiert nicht mehr.");
            open(buyer);
            return false;
        }
        if (isQuarantined(id)) {
            buyer.sendMessage("§cDiese Auktion ist aktuell in Staff-Quarantäne.");
            open(buyer);
            return false;
        }
        if (listing.seller().equals(buyer.getUniqueId())) {
            buyer.sendMessage("§cDu kannst deine eigene Auktion nicht kaufen.");
            return false;
        }
        if (plugin.getCoinsManager() == null || plugin.getCoinsManager().getCoins(buyer.getUniqueId()) < listing.price()) {
            buyer.sendMessage("§cNicht genug Coins.");
            return false;
        }

        yaml.set("listings." + id, null);
        save();

        int tax = fee(listing.price(), plugin.getConfig().getDouble("auction.salesTaxPct", 0.05));
        int payout = Math.max(0, listing.price() - tax);
        plugin.getCoinsManager().removeCoins(buyer.getUniqueId(), listing.price());
        plugin.getCoinsManager().addCoins(listing.seller(), payout);
        giveOrDrop(buyer, listing.item());
        recordSale(listing.item(), listing.price());

        buyer.sendMessage("§aGekauft: §f#" + id + " §7für §6" + listing.price() + " Coins§7.");
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller());
        Player sellerOnline = seller.getPlayer();
        if (sellerOnline != null) {
            sellerOnline.sendMessage("§6Auktionshaus §8» §f" + buyer.getName()
                    + " §7hat deine Auktion §f#" + id + " §7gekauft. Auszahlung: §6"
                    + payout + " Coins §8(Steuer: " + tax + ")");
        }
        open(buyer);
        return true;
    }

    private synchronized long nextId() {
        long id = yaml.getLong("lastId", 0L) + 1L;
        yaml.set("lastId", id);
        return id;
    }

    private synchronized Listing listing(String id) {
        String root = "listings." + id;
        if (!yaml.isConfigurationSection(root)) return null;
        String sellerRaw = yaml.getString(root + ".seller");
        ItemStack item = yaml.getItemStack(root + ".item");
        if (sellerRaw == null || item == null) return null;
        String bidderRaw = yaml.getString(root + ".currentBidder");
        return new Listing(
                id,
                UUID.fromString(sellerRaw),
                yaml.getString(root + ".sellerName", "Unknown"),
                yaml.getInt(root + ".price", 1),
                yaml.getLong(root + ".created", 0L),
                item,
                yaml.getString(root + ".type", "BIN"),
                yaml.getLong(root + ".endsAt", 0L),
                yaml.getInt(root + ".currentBid", 0),
                bidderRaw == null ? null : UUID.fromString(bidderRaw),
                yaml.getString(root + ".currentBidderName", null)
        );
    }

    private synchronized List<Listing> listings() {
        ConfigurationSection section = yaml.getConfigurationSection("listings");
        if (section == null) return new ArrayList<>();
        List<Listing> out = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            Listing listing = listing(id);
            if (listing != null) out.add(listing);
        }
        return out;
    }

    private ItemStack display(Listing listing, boolean own) {
        ItemStack item = listing.item().clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        int amount = Math.max(1, listing.item().getAmount());
        int effectivePrice;
        lore.add(" ");
        if (listing.isBid()) {
            boolean hasBid = listing.currentBid() > 0;
            effectivePrice = hasBid ? listing.currentBid() : listing.price();
            lore.add("§7Typ: §d§lBID-Auktion");
            lore.add("§7" + (hasBid ? "Aktuelles Gebot" : "Startpreis") + ": §6§l" + effectivePrice + " Coins");
            if (hasBid) lore.add("§7Höchstbieter: §f" + listing.currentBidderName());
            lore.add("§7Endet in: §f" + timeLeft(listing.endsAt()));
        } else {
            effectivePrice = listing.price();
            lore.add("§7Preis: §6§l" + effectivePrice + " Coins");
        }
        if (amount > 1) {
            lore.add("§8Pro Stück: §7" + Math.round(effectivePrice / (double) amount) + " Coins §8(" + amount + "x)");
        }
        PriceHistory history = priceHistory(listing.item());
        if (history != null) {
            lore.add("§8Ø Verkaufspreis: §7" + history.avgPerUnit() + " Coins/Stück §8("
                    + history.sales() + (history.sales() == 1 ? " Verkauf)" : " Verkäufe)"));
        }
        lore.add("§7Seller: §f" + listing.sellerName());
        lore.add("§8Auktion #" + listing.id());
        lore.add("");
        lore.add(own ? "§cKlick §8» §fZurückziehen" : listing.isBid() ? "§eKlick §8» §fBieten" : "§eKlick §8» §fKaufen");
        if (meta != null) {
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(keyAuctionId, org.bukkit.persistence.PersistentDataType.STRING, listing.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean matches(Listing listing, String query, String category) {
        if (query != null && !query.isBlank()) {
            String needle = query.toLowerCase(Locale.ROOT);
            String haystack = (displayName(listing.item()) + " " + listing.item().getType().name() + " " + listing.sellerName())
                    .toLowerCase(Locale.ROOT);
            if (!haystack.contains(needle)) return false;
        }
        if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) return true;
        if (category.equalsIgnoreCase("mystic")) return rarityOf(listing.item()) == Rarity.MYSTIC;
        return categoryOf(listing.item()).equalsIgnoreCase(category);
    }

    private String categoryOf(ItemStack item) {
        Material material = item.getType();
        String name = material.name();
        if (name.contains("KEY") || name.contains("CRYSTAL") || material == Material.NETHER_STAR || material == Material.END_CRYSTAL) return "keys";
        if (name.contains("SWORD") || name.contains("AXE") || name.contains("BOW")
                || material == Material.TNT || material == Material.ENDER_PEARL) return "combat";
        if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS")
                || name.contains("BOOTS") || rarityOf(item).ordinal() >= Rarity.RARE.ordinal()) return "gear";
        if (material.isBlock()) return "blocks";
        return "misc";
    }

    private String categoryName(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "blocks" -> "Blöcke";
            case "combat" -> "Combat";
            case "gear" -> "Gear";
            case "keys" -> "Keys";
            case "mystic" -> "Mystic";
            default -> "Alle";
        };
    }

    private ItemStack filterButton(Material material, String name, String filter) {
        return filterButton(material, name, filter,
                "§7Kategorie öffnen",
                "§8Filter: §f" + categoryName(filter),
                "",
                "§eKlick §8» §fAnzeigen");
    }

    private ItemStack filterButton(Material material, String name, String filter, String... lore) {
        ItemStack item = item(material, name, List.of(lore));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyAuctionFilter, org.bukkit.persistence.PersistentDataType.STRING, filter);
        item.setItemMeta(meta);
        return item;
    }

    /** "Meine Auktionen"-Filter mit dem echten Skin des Spielers statt eines generischen Steve-Kopfes. */
    private ItemStack myAuctionsButton(Player player, String filter, int matchCount) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName("§bMeine Auktionen");
        meta.setLore(List.of(
                "§7Zeigt nur deine aktiven Listings.",
                "§7Aktuelle Treffer: §f" + matchCount
        ));
        meta.getPersistentDataContainer().set(keyAuctionFilter, org.bukkit.persistence.PersistentDataType.STRING, filter);
        item.setItemMeta(meta);
        return item;
    }

    private String pageTag(int page, String query, String category, boolean mineOnly) {
        return "page|" + Math.max(0, page) + "|" + encoded(category) + "|" + encoded(query) + "|" + mineOnly;
    }

    private String searchTag(String category, boolean mineOnly) {
        return "search|" + encoded(category) + "|" + mineOnly;
    }

    private String mineTag(boolean mineOnly, String query, String category) {
        return "mine|" + mineOnly + "|" + encoded(category) + "|" + encoded(query);
    }

    private int pageFromPipeTag(String tag) {
        String[] parts = tag.split("\\|", -1);
        if (parts.length < 2) return 0;
        return parseInt(parts[1], 0);
    }

    private boolean booleanFromPipeTag(String tag, int index) {
        String[] parts = tag.split("\\|", -1);
        return parts.length > index && Boolean.parseBoolean(parts[index]);
    }

    private String encoded(String value) {
        if (value == null || value.isBlank()) return "-";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decoded(String tag, int index) {
        String[] parts = tag.split("\\|", -1);
        if (parts.length <= index || parts[index].equals("-")) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(parts[index]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record SearchContext(String category, boolean mineOnly) {}

    private ItemStack adminButton(Material material, String name, String tag, String... lore) {
        ItemStack item = item(material, name, List.of(lore));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyAuctionFilter, org.bukkit.persistence.PersistentDataType.STRING, tag);
        item.setItemMeta(meta);
        return item;
    }

    private void handleAdminButton(Player player, String tag) {
        if ("close".equals(tag)) {
            player.closeInventory();
            return;
        }
        if (tag.startsWith("suspicious:")) {
            openAdmin(player, true, pageFromTag(tag, 1), sellerFromTag(tag, 2));
            return;
        }
        if (tag.startsWith("all:")) {
            openAdmin(player, false, pageFromTag(tag, 1), sellerFromTag(tag, 2));
            return;
        }
        if (tag.startsWith("seller-clear:")) {
            openAdmin(player, booleanFromTag(tag, 1), pageFromTag(tag, 2), null);
            return;
        }
        if (tag.startsWith("seller-delete:")) {
            openAdmin(player, booleanFromTag(tag, 1), pageFromTag(tag, 2), sellerFromTag(tag, 3), true);
            return;
        }
        if (tag.startsWith("seller-confirm:")) {
            adminDeleteSeller(player, sellerFromTag(tag, 3), booleanFromTag(tag, 1));
            return;
        }
        if (tag.startsWith("risk:")) {
            openAdmin(player, true, pageFromTag(tag, 2), sellerFromTag(tag, 3));
            return;
        }
        if (tag.startsWith("quarantine:")) {
            setQuarantine(player, valuePart(tag, 1), true);
            return;
        }
        if (tag.startsWith("unquarantine:")) {
            setQuarantine(player, valuePart(tag, 1), false);
            return;
        }
        if (tag.startsWith("page:")) {
            openAdmin(player, booleanFromTag(tag, 2), pageFromTag(tag, 1), sellerFromTag(tag, 4));
            return;
        }
        if (tag.startsWith("suspicious-low:")) {
            adjustInt(player, "auction.suspicious.lowValueMaxPrice", tag, 1, 100, 1_000_000);
            reopenFromTag(player, tag);
            return;
        }
        if (tag.startsWith("suspicious-common:")) {
            adjustInt(player, "auction.suspicious.commonMaxPrice", tag, 1, 100, 10_000_000);
            reopenFromTag(player, tag);
            return;
        }
        if (tag.startsWith("fee:")) {
            adjustDouble(player, "auction.listingFeePct", valuePart(tag, 1));
            reopenFromTag(player, tag);
            return;
        }
        if (tag.startsWith("tax:")) {
            adjustDouble(player, "auction.salesTaxPct", valuePart(tag, 1));
            reopenFromTag(player, tag);
        }
    }

    private void reopenFromTag(Player player, String tag) {
        openAdmin(player, booleanFromTag(tag, 2), pageFromTag(tag, 3), sellerFromTag(tag, 4));
    }

    private void adjustDouble(Player player, String path, String deltaRaw) {
        double delta;
        try { delta = Double.parseDouble(deltaRaw); } catch (NumberFormatException ignored) { return; }
        double next = Math.max(0.0, Math.min(0.50, plugin.getConfig().getDouble(path, 0.0) + delta));
        plugin.getConfig().set(path, Math.round(next * 100.0) / 100.0);
        plugin.saveConfig();
        player.sendMessage("§6AH Admin §8» §7" + path + " = §f" + percent(next));
    }

    private void adjustInt(Player player, String path, String tag, int deltaIndex, int min, int max) {
        int delta = parseInt(valuePart(tag, deltaIndex), 0);
        if (delta == 0) return;
        int next = Math.max(min, Math.min(max, plugin.getConfig().getInt(path, min) + delta));
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        player.sendMessage("§6AH Admin §8» §7" + path + " = §6" + next);
    }

    private String modeTag(boolean suspiciousOnly, int page, UUID sellerFilter) {
        return suspiciousOnly + ":" + page + ":" + (sellerFilter == null ? "none" : sellerFilter);
    }

    private String valuePart(String tag, int index) {
        String[] parts = tag.split(":");
        return parts.length > index ? parts[index] : "0";
    }

    private int pageFromTag(String tag, int index) {
        return parseInt(valuePart(tag, index), 0);
    }

    private boolean booleanFromTag(String tag, int index) {
        return Boolean.parseBoolean(valuePart(tag, index));
    }

    private UUID sellerFromTag(String tag, int index) {
        String raw = valuePart(tag, index);
        if (raw == null || raw.isBlank() || raw.equals("none") || raw.equals("0")) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Erstattet ein evtl. eskrowiertes Höchstgebot, bevor eine BID-Auktion ohne reguläre Auflösung entfernt wird. */
    private void refundBidderIfAny(Listing listing) {
        if (!listing.isBid() || listing.currentBidder() == null || listing.currentBid() <= 0) return;
        if (plugin.getCoinsManager() != null) {
            plugin.getCoinsManager().addCoins(listing.currentBidder(), listing.currentBid());
        }
        Player bidder = Bukkit.getPlayer(listing.currentBidder());
        if (bidder != null) {
            bidder.sendMessage("§6Auktionshaus §8» §7Auktion §f#" + listing.id() + " §7wurde entfernt. §6+"
                    + listing.currentBid() + " Coins §7zurückerstattet.");
        }
    }

    private synchronized void adminDelete(Player player, String id) {
        Listing listing = listing(id);
        if (listing == null) {
            player.sendMessage("§cAuktion nicht gefunden.");
            openAdmin(player);
            return;
        }
        refundBidderIfAny(listing);
        returnToSeller(listing.seller(), listing.item());
        Player sellerOnline = Bukkit.getPlayer(listing.seller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage("§cAH §8» §7Deine Auktion §f#" + id + " §7wurde von einem Admin entfernt. Das Item wurde dir zurückgegeben.");
        }
        yaml.set("listings." + id, null);
        save();
        audit(player, "delete-listing", "listing=" + id + ",seller=" + listing.seller());
        player.sendMessage("§cAH Admin §8» §7Auktion §f#" + id + " §7gelöscht. Item wurde an den Verkäufer zurückgegeben.");
        openAdmin(player);
    }

    private synchronized void adminDeleteSeller(Player player, UUID seller, boolean suspiciousOnly) {
        if (seller == null) {
            player.sendMessage("§cSeller nicht gefunden.");
            openAdmin(player, suspiciousOnly, 0, null);
            return;
        }
        int deleted = 0;
        for (Listing listing : listings()) {
            if (!listing.seller().equals(seller)) continue;
            refundBidderIfAny(listing);
            returnToSeller(seller, listing.item());
            yaml.set("listings." + listing.id(), null);
            deleted++;
        }
        save();
        audit(player, "delete-seller", "seller=" + seller + ",deleted=" + deleted);
        Player sellerOnline = Bukkit.getPlayer(seller);
        if (sellerOnline != null && deleted > 0) {
            sellerOnline.sendMessage("§cAH §8» §7" + deleted + " deiner Auktionen wurden von einem Admin entfernt. Die Items wurden dir zurückgegeben.");
        }
        player.sendMessage("§cAH Admin §8» §7" + deleted + " Auktionen von Seller §f" + seller + " §7gelöscht. Items wurden zurückgegeben.");
        openAdmin(player, suspiciousOnly, 0, null);
    }

    private synchronized void setQuarantine(Player player, String id, boolean quarantined) {
        Listing listing = listing(id);
        if (listing == null) {
            player.sendMessage("§cAuktion nicht gefunden.");
            openAdmin(player);
            return;
        }
        yaml.set("listings." + id + ".quarantined", quarantined);
        save();
        audit(player, quarantined ? "quarantine" : "unquarantine", "listing=" + id + ",seller=" + listing.seller());
        player.sendMessage("§6AH Admin §8» §7Auktion §f#" + id + (quarantined ? " §cwurde eingefroren." : " §awurde freigegeben."));
        openAdmin(player);
    }

    private String percent(double value) {
        return String.format(Locale.US, "%.0f%%", value * 100.0);
    }

    private String timeLeft(long endsAt) {
        long remaining = Math.max(0L, endsAt - System.currentTimeMillis());
        long minutes = remaining / 60_000L;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        return (hours / 24) + "d " + (hours % 24) + "h";
    }

    private List<String> suspiciousReasons(Listing listing) {
        List<String> reasons = new ArrayList<>();
        int min = minimumPriceFor(listing.item());
        if (listing.price() < min) {
            reasons.add("Unter Mindestpreis (" + listing.price() + " < " + min + ")");
        }

        Rarity rarity = rarityOf(listing.item());
        Material material = listing.item().getType();
        int commonMax = plugin.getConfig().getInt("auction.suspicious.commonMaxPrice", 10_000);
        int lowValueMax = plugin.getConfig().getInt("auction.suspicious.lowValueMaxPrice", 1_000);
        if (rarity == Rarity.COMMON && listing.price() > commonMax) {
            reasons.add("Common-Item extrem teuer (>" + commonMax + ")");
        }
        if (isLowValue(material) && listing.price() > lowValueMax) {
            reasons.add("Low-Value Item teuer (>" + lowValueMax + ")");
        }
        if (min > 0 && listing.price() > Math.max(commonMax, min * 100)) {
            reasons.add("Preis stark über Referenz");
        }
        int sellerLowValueLimit = plugin.getConfig().getInt("auction.suspicious.sellerLowValueCount", 20);
        if (sellerLowValueLimit > 0 && isLowValue(material) && listing.price() > lowValueMax) {
            long expensiveLowValueBySeller = listings().stream()
                    .filter(other -> other.seller().equals(listing.seller()))
                    .filter(other -> isLowValue(other.item().getType()))
                    .filter(other -> other.price() > lowValueMax)
                    .count();
            if (expensiveLowValueBySeller >= sellerLowValueLimit) {
                reasons.add("Seller hat " + expensiveLowValueBySeller + " teure Low-Value Listings");
            }
        }
        return reasons;
    }

    private int riskScore(List<String> reasons) {
        int score = 0;
        for (String reason : reasons) {
            if (reason.contains("Unter Mindestpreis")) score += 35;
            else if (reason.contains("Seller hat")) score += 45;
            else if (reason.contains("Low-Value")) score += 30;
            else if (reason.contains("Common-Item")) score += 25;
            else if (reason.contains("Referenz")) score += 20;
            else score += 15;
        }
        return Math.min(100, score);
    }

    private String riskColor(int risk) {
        if (risk >= 90) return "§4";
        if (risk >= 70) return "§6";
        if (risk >= 40) return "§e";
        return "§a";
    }

    private synchronized boolean isQuarantined(String id) {
        return yaml.getBoolean("listings." + id + ".quarantined", false);
    }

    private synchronized void autoFlagIfRisky(String id) {
        Listing listing = listing(id);
        if (listing == null) return;
        int risk = riskScore(suspiciousReasons(listing));
        if (risk < plugin.getConfig().getInt("auction.suspicious.autoFlagRisk", 90)) return;
        yaml.set("listings." + id + ".autoFlagged", true);
        if (plugin.getConfig().getBoolean("auction.suspicious.autoQuarantine", false)) {
            yaml.set("listings." + id + ".quarantined", true);
        }
        save();
        try {
            if (plugin.getNotifyManager() != null) {
                plugin.getNotifyManager().broadcast("cheat",
                        "&cAH Auto-Flag &8» &f#" + id + " &7Risk &c" + risk + "/100 &8(" + listing.sellerName() + ")",
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        "vetroforge.auction.admin");
            }
        } catch (Throwable ignored) {}
    }

    private void audit(Player actor, String action, String detail) {
        audit(actor.getName() + "/" + actor.getUniqueId(), action, detail);
    }

    private void audit(String actor, String action, String detail) {
        try {
            File dir = new File(plugin.getDataFolder(), "data");
            if (!dir.exists()) dir.mkdirs();
            File audit = new File(dir, "auction-admin-audit.log");
            String line = Instant.now() + " actor=" + actor + " action=" + action + " " + detail + System.lineSeparator();
            try (FileWriter writer = new FileWriter(audit, true)) {
                writer.write(line);
            }
        } catch (IOException ignored) {}
    }

    public synchronized boolean webSetQuarantine(String id, boolean quarantined, String actor) {
        Listing listing = listing(id);
        if (listing == null) return false;
        yaml.set("listings." + id + ".quarantined", quarantined);
        save();
        audit(actor, quarantined ? "web-quarantine" : "web-unquarantine", "listing=" + id + ",seller=" + listing.seller());
        return true;
    }

    public synchronized boolean webDeleteListing(String id, String actor) {
        Listing listing = listing(id);
        if (listing == null) return false;
        yaml.set("listings." + id, null);
        save();
        audit(actor, "web-delete-listing", "listing=" + id + ",seller=" + listing.seller());
        Bukkit.getScheduler().runTask(plugin, () -> {
            refundBidderIfAny(listing);
            returnToSeller(listing.seller(), listing.item());
        });
        return true;
    }

    public synchronized int webDeleteSeller(UUID seller, String actor) {
        if (seller == null) return 0;
        int deleted = 0;
        List<Listing> removed = new ArrayList<>();
        for (Listing listing : listings()) {
            if (!listing.seller().equals(seller)) continue;
            removed.add(listing);
            yaml.set("listings." + listing.id(), null);
            deleted++;
        }
        save();
        if (!removed.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Listing listing : removed) {
                    refundBidderIfAny(listing);
                    returnToSeller(seller, listing.item());
                }
            });
        }
        audit(actor, "web-delete-seller", "seller=" + seller + ",deleted=" + deleted);
        return deleted;
    }

    public boolean webSetThreshold(String key, String value, String actor) {
        String path = switch (key == null ? "" : key) {
            case "lowValueMaxPrice" -> "auction.suspicious.lowValueMaxPrice";
            case "commonMaxPrice" -> "auction.suspicious.commonMaxPrice";
            case "sellerLowValueCount" -> "auction.suspicious.sellerLowValueCount";
            case "autoFlagRisk" -> "auction.suspicious.autoFlagRisk";
            case "autoQuarantine" -> "auction.suspicious.autoQuarantine";
            default -> null;
        };
        if (path == null) return false;
        if ("autoQuarantine".equals(key)) plugin.getConfig().set(path, Boolean.parseBoolean(value));
        else plugin.getConfig().set(path, parseInt(value, plugin.getConfig().getInt(path, 0)));
        plugin.saveConfig();
        audit(actor, "web-threshold", key + "=" + value);
        return true;
    }

    public String riskDashboardJson(int limit) {
        List<Listing> rows = listings();
        rows.sort((a, b) -> Integer.compare(
                riskScore(suspiciousReasons(b)),
                riskScore(suspiciousReasons(a))
        ));
        int max = Math.max(1, Math.min(100, limit));
        StringBuilder json = new StringBuilder("{\"ok\":true,\"items\":[");
        int count = 0;
        for (Listing listing : rows) {
            List<String> reasons = suspiciousReasons(listing);
            int risk = riskScore(reasons);
            if (risk <= 0) continue;
            if (count++ > 0) json.append(',');
            json.append('{')
                    .append("\"id\":\"").append(jsonEsc(listing.id())).append("\",")
                    .append("\"seller\":\"").append(listing.seller()).append("\",")
                    .append("\"sellerName\":\"").append(jsonEsc(listing.sellerName())).append("\",")
                    .append("\"item\":\"").append(jsonEsc(displayName(listing.item()))).append("\",")
                    .append("\"material\":\"").append(jsonEsc(listing.item().getType().name())).append("\",")
                    .append("\"price\":").append(listing.price()).append(',')
                    .append("\"risk\":").append(risk).append(',')
                    .append("\"quarantined\":").append(isQuarantined(listing.id())).append(',')
                    .append("\"reasons\":[");
            for (int i = 0; i < reasons.size(); i++) {
                if (i > 0) json.append(',');
                json.append('"').append(jsonEsc(reasons.get(i))).append('"');
            }
            json.append("]}");
            if (count >= max) break;
        }
        return json.append("]}").toString();
    }

    public int activeListingCount() {
        return listings().size();
    }

    public int quarantineCount() {
        int count = 0;
        for (Listing listing : listings()) {
            if (isQuarantined(listing.id())) count++;
        }
        return count;
    }

    public int riskyListingCount(int minRisk) {
        int count = 0;
        for (Listing listing : listings()) {
            if (riskScore(suspiciousReasons(listing)) >= minRisk) count++;
        }
        return count;
    }

    public String quarantineQueueJson(int limit) {
        List<Listing> rows = new ArrayList<>();
        for (Listing listing : listings()) {
            if (isQuarantined(listing.id())) rows.add(listing);
        }
        rows.sort(Comparator.comparingLong(Listing::created).reversed());
        int max = Math.max(1, Math.min(100, limit));
        StringBuilder json = new StringBuilder("{\"ok\":true,\"items\":[");
        int count = 0;
        for (Listing listing : rows) {
            if (count >= max) break;
            List<String> reasons = suspiciousReasons(listing);
            if (count++ > 0) json.append(',');
            json.append('{')
                    .append("\"id\":\"").append(jsonEsc(listing.id())).append("\",")
                    .append("\"seller\":\"").append(listing.seller()).append("\",")
                    .append("\"sellerName\":\"").append(jsonEsc(listing.sellerName())).append("\",")
                    .append("\"item\":\"").append(jsonEsc(displayName(listing.item()))).append("\",")
                    .append("\"material\":\"").append(jsonEsc(listing.item().getType().name())).append("\",")
                    .append("\"price\":").append(listing.price()).append(',')
                    .append("\"risk\":").append(riskScore(reasons)).append(',')
                    .append("\"created\":").append(listing.created()).append(',')
                    .append("\"reasons\":[");
            for (int i = 0; i < reasons.size(); i++) {
                if (i > 0) json.append(',');
                json.append('"').append(jsonEsc(reasons.get(i))).append('"');
            }
            json.append("]}");
        }
        return json.append("]}").toString();
    }

    public String sellerProfileJson(UUID seller) {
        if (seller == null) return "{\"ok\":false,\"err\":\"seller missing\"}";
        List<Listing> sellerListings = new ArrayList<>();
        int riskSum = 0;
        int suspicious = 0;
        for (Listing listing : listings()) {
            if (!listing.seller().equals(seller)) continue;
            sellerListings.add(listing);
            int risk = riskScore(suspiciousReasons(listing));
            riskSum += risk;
            if (risk > 0) suspicious++;
        }
        int avgRisk = sellerListings.isEmpty() ? 0 : Math.round((float) riskSum / sellerListings.size());
        int deleted = auditDeleteCount(seller);
        String sellerName = sellerListings.isEmpty() ? Bukkit.getOfflinePlayer(seller).getName() : sellerListings.get(0).sellerName();
        StringBuilder json = new StringBuilder("{\"ok\":true");
        json.append(",\"seller\":\"").append(seller).append("\"")
                .append(",\"sellerName\":\"").append(jsonEsc(sellerName == null ? "" : sellerName)).append("\"")
                .append(",\"activeListings\":").append(sellerListings.size())
                .append(",\"suspiciousListings\":").append(suspicious)
                .append(",\"averageRisk\":").append(avgRisk)
                .append(",\"deletedCount\":").append(deleted)
                .append(",\"listings\":[");
        for (int i = 0; i < sellerListings.size(); i++) {
            Listing listing = sellerListings.get(i);
            if (i > 0) json.append(',');
            json.append('{')
                    .append("\"id\":\"").append(jsonEsc(listing.id())).append("\",")
                    .append("\"item\":\"").append(jsonEsc(displayName(listing.item()))).append("\",")
                    .append("\"material\":\"").append(jsonEsc(listing.item().getType().name())).append("\",")
                    .append("\"price\":").append(listing.price()).append(',')
                    .append("\"risk\":").append(riskScore(suspiciousReasons(listing))).append(',')
                    .append("\"quarantined\":").append(isQuarantined(listing.id()))
                    .append('}');
        }
        return json.append("]}").toString();
    }

    private int auditDeleteCount(UUID seller) {
        File audit = new File(new File(plugin.getDataFolder(), "data"), "auction-admin-audit.log");
        if (!audit.exists()) return 0;
        int count = 0;
        try {
            for (String line : java.nio.file.Files.readAllLines(audit.toPath())) {
                if (line.contains("seller=" + seller) && line.contains("delete")) count++;
            }
        } catch (IOException ignored) {}
        return count;
    }

    private String jsonEsc(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isLowValue(Material material) {
        return material == Material.DIRT
                || material == Material.COARSE_DIRT
                || material == Material.COBBLESTONE
                || material == Material.STONE
                || material == Material.SAND
                || material == Material.GRAVEL
                || material == Material.OAK_LOG
                || material == Material.CACTUS
                || material == Material.ROTTEN_FLESH;
    }

    /** Identifiziert einen Item-"Typ" für die Preis-Historie (Custom-Item+Rarity, sonst Material). */
    private String historyKey(ItemStack item) {
        String base = CustomItemUtil.baseId(plugin, item);
        if (base != null) return "ci:" + base.toLowerCase(Locale.ROOT) + ":" + rarityOf(item).name();
        return "mat:" + item.getType().name();
    }

    /** Protokolliert einen abgeschlossenen Verkauf (BIN-Kauf oder gewonnenes BID) für die Preis-Historie. */
    private synchronized void recordSale(ItemStack item, int totalPrice) {
        if (item == null || totalPrice <= 0) return;
        String path = "history." + historyKey(item);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList(path)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (var e : raw.entrySet()) copy.put(String.valueOf(e.getKey()), e.getValue());
            entries.add(copy);
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("price", totalPrice);
        entry.put("qty", Math.max(1, item.getAmount()));
        entry.put("time", System.currentTimeMillis());
        entries.add(entry);
        int cap = Math.max(1, plugin.getConfig().getInt("auction.history.maxEntriesPerItem", 20));
        while (entries.size() > cap) entries.remove(0);
        yaml.set(path, entries);
        save();
    }

    private record PriceHistory(int avgPerUnit, int sales) {}

    /** Durchschnittlicher Stückpreis der letzten protokollierten Verkäufe dieses Item-Typs, oder null ohne Historie. */
    private PriceHistory priceHistory(ItemStack item) {
        if (item == null) return null;
        List<Map<?, ?>> entries = yaml.getMapList("history." + historyKey(item));
        if (entries.isEmpty()) return null;
        double sumPerUnit = 0;
        int count = 0;
        for (Map<?, ?> raw : entries) {
            if (!(raw.get("price") instanceof Number priceNum) || !(raw.get("qty") instanceof Number qtyNum)) continue;
            sumPerUnit += priceNum.doubleValue() / Math.max(1, qtyNum.intValue());
            count++;
        }
        if (count == 0) return null;
        return new PriceHistory((int) Math.round(sumPerUnit / count), count);
    }

    private int fee(int price, double percent) {
        if (percent <= 0) return 0;
        return Math.max(1, (int) Math.ceil(price * percent));
    }

    private int minimumPriceFor(ItemStack item) {
        Rarity rarity = rarityOf(item);
        return switch (rarity) {
            case RARE -> plugin.getConfig().getInt("auction.minPrices.RARE", 500);
            case EPIC -> plugin.getConfig().getInt("auction.minPrices.EPIC", 1500);
            case LEGENDARY -> plugin.getConfig().getInt("auction.minPrices.LEGENDARY", 5000);
            case MYSTIC -> plugin.getConfig().getInt("auction.minPrices.MYSTIC", 10000);
            default -> plugin.getConfig().getInt("auction.minPrice", 1);
        };
    }

    private Rarity rarityOf(ItemStack item) {
        Rarity rarity = CustomItemUtil.rarityOf(plugin, item);
        if (rarity != Rarity.COMMON) return rarity;
        String plain = (displayName(item) + " " + item.getType().name()).toLowerCase(Locale.ROOT);
        if (plain.contains("mystic") || plain.contains("mythic")) return Rarity.MYSTIC;
        if (plain.contains("legendary")) return Rarity.LEGENDARY;
        if (plain.contains("epic")) return Rarity.EPIC;
        if (plain.contains("rare")) return Rarity.RARE;
        return Rarity.COMMON;
    }

    private String displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return ChatColor.stripColor(meta.getDisplayName());
        return item.getType().name();
    }

    private void giveOrDrop(Player player, ItemStack item) {
        var left = player.getInventory().addItem(item);
        left.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    /** Liefert ein Item an eine UUID aus -- online direkt, offline über die Item-Inbox (Rückgabe an Seller oder Auslieferung an BID-Gewinner). */
    private void returnToSeller(UUID seller, ItemStack item) {
        if (seller == null || item == null) return;
        Player online = Bukkit.getPlayer(seller);
        if (online != null && plugin.getItemInboxService() != null) {
            plugin.getItemInboxService().giveOrStore(online, item, "auctionhouse-admin-return");
        } else if (online != null) {
            giveOrDrop(online, item);
        } else if (plugin.getItemInboxService() != null) {
            plugin.getItemInboxService().storeForOffline(seller, item);
        }
    }

    private ItemStack pane(Material material, String name) {
        return item(material, name, null);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv, ItemStack item) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, item);
    }

    private synchronized void save() {
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    private record Listing(String id, UUID seller, String sellerName, int price, long created, ItemStack item,
                            String type, long endsAt, int currentBid, UUID currentBidder, String currentBidderName) {
        boolean isBid() { return "BID".equals(type); }
    }
}
