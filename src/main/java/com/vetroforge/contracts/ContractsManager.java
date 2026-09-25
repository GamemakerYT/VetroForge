package com.vetroforge.contracts;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ContractsManager {
    public static final class Contract {
        public final String id, name, type; // z.B. type: KILLS, CRYSTALS, RAID_WIN
        public final int goal, reward;
        public Contract(String id, String name, String type, int goal, int reward){
            this.id=id; this.name=name; this.type=type; this.goal=goal; this.reward=reward;
        }
    }

    private final FactionsPlugin plugin;
    private final Map<String, Contract> daily = new LinkedHashMap<>();
    private final Map<String, Contract> weekly= new LinkedHashMap<>();

    private final File dataDir;

    public ContractsManager(FactionsPlugin plugin){
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "contracts");
        if (!dataDir.exists()) dataDir.mkdirs();
        reload();
    }

    public void reload(){
        daily.clear(); weekly.clear();
        var secD = plugin.getConfig().getConfigurationSection("contracts.daily");
        var secW = plugin.getConfig().getConfigurationSection("contracts.weekly");
        if (secD!=null){
            for (String k : secD.getKeys(false)){
                daily.put(k, new Contract(k,
                        secD.getString(k+".name", k),
                        secD.getString(k+".type", "KILLS"),
                        secD.getInt(k+".goal", 10),
                        secD.getInt(k+".reward", 100)));
            }
        }
        if (secW!=null){
            for (String k : secW.getKeys(false)){
                weekly.put(k, new Contract(k,
                        secW.getString(k+".name", k),
                        secW.getString(k+".type", "RAID_WIN"),
                        secW.getInt(k+".goal", 3),
                        secW.getInt(k+".reward", 500)));
            }
        }
    }

    private YamlConfiguration file(UUID u){
        return YamlConfiguration.loadConfiguration(new File(dataDir, u.toString()+".yml"));
    }
    private void save(UUID u, YamlConfiguration y){
        try { y.save(new File(dataDir, u.toString()+".yml")); } catch (IOException ignored){}
    }

    public int getProgress(UUID u, String id){
        var y = file(u);
        return y.getInt("progress."+id, 0);
    }
    public void addProgress(UUID u, String id, int amount){
        var y = file(u);
        y.set("progress."+id, getProgress(u,id)+amount);
        save(u,y);
    }
    public boolean claim(UUID u, Contract c){
        var y = file(u);
        int prog = getProgress(u, c.id);
        if (prog < c.goal) return false;
        if (y.getBoolean("claimed."+c.id, false)) return false;
        y.set("claimed."+c.id, true);
        save(u,y);
        // reward: du kannst hier Crystal/Coins/Commands vergeben
        return true;
    }

    public Collection<Contract> daily(){ return daily.values(); }
    public Collection<Contract> weekly(){ return weekly.values(); }
}