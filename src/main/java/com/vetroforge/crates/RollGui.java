// src/main/java/com/vetroforge/crates/RollGui.java
package com.vetroforge.crates;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Animiertes 27-Slot-Reel. Sichtfenster: Slots 10..16, Gewinn-Slot: 13
 */
public class RollGui implements Listener {

    public interface RollResult {
        void onFinish(ItemStack finalItem);
        void onAbort();
    }

    private final FactionsPlugin plugin;
    /** Aktive Rolls: Inventory -> Callback. Solange ein Eintrag hier drin ist, darf der Spieler
     *  das Inventar nicht schliessen (siehe onRollClose) -- der Crystal ist bereits verbraucht,
     *  das Ergebnis muss ausgespielt werden statt durch ESC umgangen werden zu koennen. */
    private final Map<Inventory, RollResult> activeRolls = new WeakHashMap<>();

    public RollGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Startet ein Roll mit zufälligem Ergebnis aus reelItems. */
    public void start(Player p, List<ItemStack> reelItems, RollResult cb) {
        start(p, reelItems, cb, null);
    }

    /**
     * Startet ein Roll; wenn forcedFinal != null, landet das Roll garantiert auf diesem Item (sofern im Reel vorhanden).
     * Andernfalls zufälliges Ergebnis.
     */
    public void start(Player p, List<ItemStack> reelItems, RollResult cb, ItemStack forcedFinal) {
        if (p == null || !p.isOnline() || reelItems == null || reelItems.isEmpty()) return;

        // Arbeitskopie + minimale Vielfalt sicherstellen
        List<ItemStack> base = new ArrayList<>();
        for (ItemStack it : reelItems) if (it != null) base.add(it.clone());
        if (base.isEmpty()) return;

        // Inventar vorbereiten
        final Inventory inv = Bukkit.createInventory(null, 27, ChatColor.AQUA + "✦ " + ChatColor.WHITE + "VetroForge Crate" + ChatColor.AQUA + " ✦");
        activeRolls.put(inv, cb);
        decorate(inv);

        // Reel (Deque) füllen
        final Deque<ItemStack> reel = new ArrayDeque<>(base);
        // Anfangs sichtbare 7 Items setzen
        for (int i = 10; i <= 16; i++) {
            ItemStack it = reel.pollFirst();
            reel.offerLast(it);
            inv.setItem(i, it);
        }

        // optional: Ergebnis erzwingen -> wir rotieren den Deque so, dass Slot 13 später darauf landet
        if (forcedFinal != null) {
            alignForcedResult(inv, reel, forcedFinal);
        }

        p.openInventory(inv);

        // Easing-Animation über steigenden Delay: anfangs schnell, später langsamer
        final int totalShifts = 60;          // Anzahl Schrittwechsel (Dauer)
        final int minDelay    = 2;           // Start: alle 2 Ticks schieben
        final int maxDelay    = 6;           // Ende: alle 6 Ticks schieben
        final float startPitch = 1.3f;
        final float endPitch   = 0.7f;

        final int[] tick = {0};
        final int[] delay = {minDelay};
        final int[] counter = {0};
        final BukkitTask[] taskRef = new BukkitTask[1];

        taskRef[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                // Einziger Abbruchgrund: Spieler ist offline. Ein Schliessen der GUI (ESC) fuehrt
                // NICHT zum Abbruch -- onRollClose erzwingt ein Wiederoeffnen, solange die Rolle
                // noch laeuft (der Crystal ist schon verbraucht, das Ergebnis muss ausgespielt werden).
                if (!p.isOnline()) {
                    cancel(); if (cb != null) cb.onAbort(); return;
                }
                tick[0]++;

                // nur bei "delay"-Takt verschieben (Easing)
                counter[0]++;
                if (counter[0] < delay[0]) return;
                counter[0] = 0;

                shiftOnce(inv, reel);

                // Sound mit Pitch-Ramp
                float t = Math.min(1f, tick[0] / (float) totalShifts);
                float pitch = startPitch + (endPitch - startPitch) * t;
                try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, pitch); } catch (Throwable ignored) {}

                // Delay linear erhöhen (Easing)
                int newDelay = minDelay + Math.round((maxDelay - minDelay) * t);
                delay[0] = Math.max(minDelay, Math.min(maxDelay, newDelay));

                // Ende?
                if (tick[0] >= totalShifts) {
                    ItemStack finalItem = safeClone(inv.getItem(13));
                    try { p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f); } catch (Throwable ignored) {}
                    cancel();
                    if (cb != null) cb.onFinish(finalItem);
                }
            }

            private void cancel() {
                activeRolls.remove(inv);
                if (taskRef[0] != null) {
                    try { taskRef[0].cancel(); } catch (Throwable ignored) {}
                    taskRef[0] = null;
                }
            }
        }, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRollClick(InventoryClickEvent event) {
        if (activeRolls.containsKey(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRollDrag(InventoryDragEvent event) {
        if (activeRolls.containsKey(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    /** Verhindert, dass ein Spieler sich per ESC/Schliessen vor dem Ergebnis drueckt: der
     *  Crystal ist beim Oeffnen der Rolle bereits verbraucht, also wird die GUI erzwungen
     *  wieder geoeffnet, solange die Rolle noch laeuft. Nur beim Verlassen des Servers wird
     *  tatsaechlich abgebrochen (naechster Tick des Timers ruft dann onAbort). */
    @EventHandler
    public void onRollClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!activeRolls.containsKey(inv)) return;

        if (!(event.getPlayer() instanceof Player player) || !player.isOnline()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && activeRolls.containsKey(inv)) {
                player.openInventory(inv);
            }
        });
    }

    /* ================= Optik / Deko ================= */

    private void decorate(Inventory inv) {
        ItemStack ice = named(new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE), " ");
        ItemStack frost = named(new ItemStack(Material.CYAN_STAINED_GLASS_PANE), " ");
        ItemStack snow = named(new ItemStack(Material.WHITE_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ice);
        for (int s : new int[]{0, 1, 7, 8, 9, 17, 18, 19, 25, 26}) inv.setItem(s, frost);
        for (int s : new int[]{2, 3, 5, 6, 20, 21, 23, 24}) inv.setItem(s, snow);

        // Sichtfenster „freilegen“
        for (int s = 10; s <= 16; s++) inv.setItem(s, null);

        // Gewinn-Slot (13) visuell hervorheben
        inv.setItem(4, named(new ItemStack(Material.PACKED_ICE), ChatColor.AQUA + "" + ChatColor.BOLD + "GEWINN"));
        inv.setItem(22, named(new ItemStack(Material.SEA_LANTERN), ChatColor.WHITE + "▲ " + ChatColor.AQUA + "Stop-Marker" + ChatColor.WHITE + " ▲"));
    }

    /* ================= Animation ================= */

    private void shiftOnce(Inventory inv, Deque<ItemStack> reel) {
        // Fenster 10..16, wir schieben nach rechts; 10 bekommt das nächste vom Reel
        for (int i = 16; i >= 11; i--) {
            inv.setItem(i, inv.getItem(i - 1));
        }
        ItemStack next = reel.pollFirst();
        reel.offerLast(next);
        inv.setItem(10, next);
    }

    private void alignForcedResult(Inventory inv, Deque<ItemStack> reel, ItemStack target) {
        // Wenn target im Reel vorkommt, rotieren bis wir es als nächsten „Fenster-Durchlauf“ in Slot 13 haben.
        // Heuristik: wir setzen beim Start bereits 10..16; wir drehen die Deque so, dass die Rollfolge dort liegt.
        if (target == null) return;
        // grober Versuch: Wir suchen 3 Schritte weiter (13 ist 3 Schritte von 10 aus).
        final int distanceToCenter = 3;
        for (int tries = 0; tries < reel.size(); tries++) {
            ItemStack peek = getNth(reel, distanceToCenter);
            if (similar(peek, target)) break;
            // rotiere um 1
            ItemStack it = reel.pollFirst();
            reel.offerLast(it);
        }
    }

    private ItemStack getNth(Deque<ItemStack> dq, int n) {
        int i = 0;
        for (ItemStack it : dq) {
            if (i == n) return it;
            i++;
        }
        return dq.peekFirst();
    }

    private boolean similar(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        ItemMeta am = a.getItemMeta(), bm = b.getItemMeta();
        String an = (am != null && am.hasDisplayName()) ? am.getDisplayName() : "";
        String bn = (bm != null && bm.hasDisplayName()) ? bm.getDisplayName() : "";
        return an.equals(bn);
    }

    /* ================= Utils ================= */

    private ItemStack named(ItemStack base, String name) {
        ItemMeta im = base.getItemMeta();
        im.setDisplayName(name == null ? "" : name);
        base.setItemMeta(im);
        return base;
    }

    private ItemStack safeClone(ItemStack it) { return it == null ? null : it.clone(); }
}
