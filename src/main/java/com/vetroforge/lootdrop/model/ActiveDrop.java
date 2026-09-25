// src/main/java/com/vetroforge/lootdrop/model/ActiveDrop.java
package com.vetroforge.lootdrop.model;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;

import java.util.UUID;

public class ActiveDrop {

    private static final String META = "vetro_lootdrop";

    private final FactionsPlugin plugin;     // für removeMetadata(...)
    private final UUID id = UUID.randomUUID();

    private final Location baseCenter;       // Blockzentrum der Kiste
    private final ArmorStand label;          // schwebender Name direkt darüber
    private final Tier tier;
    private final MeteorType meteorType;
    private final int interdictionRadius;
    private final long unlockAtMs;
    private String exclusiveFaction;
    private long factionExclusiveUntilMs;

    private final long expireAtMs;           // NEU: absoluter Ablaufzeitpunkt
    private int secLeft;
    private boolean consumed = false;
    private UUID claimer = null;

    private Block chestBlock;                // tatsächlicher Kisten-Block

    private ActiveDrop(FactionsPlugin plugin,
                       Location baseCenter,
                       ArmorStand label,
                       Tier tier,
                       MeteorType meteorType,
                       int lifetimeSec,
                       int interdictionRadius) {
        this.plugin = plugin;
        this.baseCenter = baseCenter;
        this.label = label;
        this.tier = tier;
        this.meteorType = meteorType;
        this.secLeft = lifetimeSec;
        this.interdictionRadius = interdictionRadius;
        this.expireAtMs = System.currentTimeMillis() + (long) lifetimeSec * 1000L;
        this.unlockAtMs = System.currentTimeMillis() + (long) meteorType.lockSeconds() * 1000L;
    }

    /** Spawnt den Label-ArmorStand exakt über dem Kistenblock (zentriert). */
    public static ActiveDrop spawn(FactionsPlugin pl, Location at, Tier tier, MeteorType meteorType, int lifetimeSec, int interdictionRadius){
        Block block = at.getBlock();
        Location center   = block.getLocation().add(0.5, 0.0, 0.5);
        Location labelLoc = center.clone().add(0.0, 1.25, 0.0);

        World w = labelLoc.getWorld();
        if (w == null) throw new IllegalStateException("World is null for lootdrop label");

        ArmorStand as = w.spawn(labelLoc, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setSmall(false);
            s.setCollidable(false);
            s.customName(LegacyComponentSerializer.legacySection().deserialize(
                    "§c§lLootdrop §8» " + tier.displayName()));
            s.setCustomNameVisible(true);
            s.setGlowing(true);
        });

        w.spawnParticle(Particle.ENCHANTED_HIT, labelLoc, 24, 0.25, 0.12, 0.25, 0.01);

        return new ActiveDrop(pl, center, as, tier, meteorType, lifetimeSec, interdictionRadius);
    }

    /** 1× pro Sekunde vom Manager aufgerufen. */
    public boolean tick(){
        if (consumed) return false;
        if (--secLeft <= 0) { despawn(); return false; }

        World w = baseCenter.getWorld();
        if (w != null) {
            String state = unlocked() ? "§aOffen" : "§eLockpick " + unlockSecondsLeft() + "s";
            label.customName(LegacyComponentSerializer.legacySection().deserialize(
                    "§c§lLootdrop §8» " + tier.displayName() + " §8• " + state
            ));
            Location l = baseCenter.clone().add(0, 0.2, 0);
            w.spawnParticle(Particle.ENCHANT, l, 6, 0.25, 0.15, 0.25, 0.0);
            for (int y = 1; y <= 18; y += 2) {
                w.spawnParticle(Particle.END_ROD, baseCenter.clone().add(0, y, 0), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
        return true;
    }

    /** Spieler hat gelootet → Effekt + Aufräumen. */
    public void finish(){
        consumed = true;
        World w = baseCenter.getWorld();
        if (w != null) w.playSound(baseCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        despawn();
    }

    /** Entfernt Titel + Kiste (falls noch vorhanden) sauber. */
    public void despawn(){
        try { if (label != null && !label.isDead()) label.remove(); } catch (Throwable ignored) {}

        try {
            if (chestBlock != null && chestBlock.getType() == Material.CHEST) {
                // Meta entfernen + Block wegräumen
                if (chestBlock.hasMetadata(META)) chestBlock.removeMetadata(META, plugin);

                // Inventar leeren (kein Refill / keine Dupes)
                if (chestBlock.getState() instanceof Chest ch) {
                    ch.getBlockInventory().clear();
                }
                chestBlock.setType(Material.AIR, false);
            }
        } catch (Throwable ignored) {}
    }

    /** One-Open Gate. */
    public boolean claimFirst(UUID player){
        if (claimer != null) return claimer.equals(player);
        claimer = player;
        return true;
    }

    /* ===== Kisten-Block ===== */
    public void setChestBlock(Block b){ this.chestBlock = b; }
    public Block getChestBlock(){ return chestBlock; }

    /* ===== Accessors (kompatibel für Reflection in der Sidebar) ===== */
    public UUID id(){ return id; }
    public UUID getId(){ return id; }

    public Location location(){ return baseCenter.clone(); }
    public Location getLocation(){ return baseCenter.clone(); }

    public boolean consumed(){ return consumed; }
    public boolean isConsumed(){ return consumed; }

    public Tier tier(){ return tier; }
    public Tier getTier(){ return tier; }
    public String getTierName(){ return tier.displayName(); } // farbiger Name als String
    public MeteorType meteorType(){ return meteorType; }
    public MeteorType getMeteorType(){ return meteorType; }
    public boolean unlocked(){ return System.currentTimeMillis() >= unlockAtMs; }
    public int unlockSecondsLeft(){ return (int) Math.max(0L, Math.ceil((unlockAtMs - System.currentTimeMillis()) / 1000.0)); }
    public void claimFaction(String faction, int seconds) {
        if (exclusiveFaction != null || faction == null || faction.isBlank()) return;
        exclusiveFaction = faction;
        factionExclusiveUntilMs = System.currentTimeMillis() + seconds * 1000L;
    }
    public boolean canFactionOpen(String faction) {
        return exclusiveFaction == null || System.currentTimeMillis() >= factionExclusiveUntilMs
                || exclusiveFaction.equalsIgnoreCase(faction);
    }
    public String exclusiveFaction(){ return exclusiveFaction; }

    public int secondsLeft(){ return secLeft; }
    public int getSecondsLeft(){ return secLeft; }

    public int interdictionRadius(){ return interdictionRadius; }
    public int getInterdictionRadius(){ return interdictionRadius; }

    /** NEU: absolute Ablaufzeit (Millis seit Epoch). Mehrere Alias-Namen für Reflection. */
    public long getExpireAt(){ return expireAtMs; }
    public long getExpiresAt(){ return expireAtMs; }
    public long getEndAt(){ return expireAtMs; }
    public long getTimeoutAt(){ return expireAtMs; }
}
