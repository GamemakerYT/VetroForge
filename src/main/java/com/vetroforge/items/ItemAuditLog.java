package com.vetroforge.items;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class ItemAuditLog {

    private final FactionsPlugin plugin;
    private final File file;
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ItemAuditLog(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "logs/custom-items.log");
    }

    public synchronized void write(String action, Player player, ItemStack item, String details) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            out.println("[" + format.format(new Date()) + "] action=" + action
                    + " player=" + (player == null ? "console" : player.getName() + "/" + player.getUniqueId())
                    + " item=" + CustomItemUtil.baseId(plugin, item)
                    + " rarity=" + CustomItemUtil.rarityOf(plugin, item)
                    + " power=" + CustomItemUtil.powerScore(plugin, item)
                    + (details == null || details.isBlank() ? "" : " " + details));
        } catch (Exception e) {
            plugin.getLogger().warning("[ItemAudit] Schreiben fehlgeschlagen: " + e.getMessage());
        }
    }
}
