// src/main/java/com/vetroforge/lootdrop/DropLogger.java
package com.vetroforge.lootdrop;

import com.vetroforge.lootdrop.model.ActiveDrop;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class DropLogger implements Closeable {
    private final File file;
    private PrintWriter out;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public DropLogger(File f) {
        this.file = f;
        try {
            f.getParentFile().mkdirs();
            out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), java.nio.charset.StandardCharsets.UTF_8), true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void logSpawn(ActiveDrop d, Location l){
        write("SPAWN " + d.meteorType().name() + "/" + d.tier().name() + " @ " + pos(l) + " id=" + d.id());
    }
    public void logOpen(ActiveDrop d, Player p, List<ItemStack> items){
        StringBuilder sb = new StringBuilder("OPEN id=").append(d.id()).append(" by=").append(p.getName()).append(" items=[");
        boolean first = true;
        for (ItemStack it : items) {
            if (it == null) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append(it.getAmount()).append("x ").append(it.getType());
        }
        sb.append("]");
        write(sb.toString());
    }
    public void logExpire(ActiveDrop d){
        write("EXPIRE id=" + d.id());
    }

    private void write(String s){
        String line = "["+fmt.format(new Date())+"] " + s;
        System.out.println("[Lootdrop] " + line);
        if (out != null) out.println(line);
    }

    private String pos(Location l){ return l.getWorld().getName()+" "+l.getBlockX()+"/"+l.getBlockY()+"/"+l.getBlockZ(); }
    @Override public void close(){ try { if (out != null) out.close(); } catch (Throwable ignored) {} }
}
