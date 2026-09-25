// src/main/java/com/vetroforge/motd/MotdRule.java
package com.vetroforge.motd;

import org.bukkit.configuration.ConfigurationSection;

import java.time.*;
import java.util.*;

public final class MotdRule {

    public final String targetProfile;          // set: <profile>
    public final Set<DayOfWeek> days;           // when.days: [SATURDAY, SUNDAY]
    public final LocalTime start;               // when.between: "HH:mm-HH:mm"
    public final LocalTime end;
    public final Integer onlineAtLeast;         // when.online_at_least: int

    public MotdRule(String targetProfile, Set<DayOfWeek> days, LocalTime start, LocalTime end, Integer onlineAtLeast) {
        this.targetProfile = targetProfile;
        this.days = days==null? Collections.emptySet():EnumSet.copyOf(days);
        this.start = start;
        this.end = end;
        this.onlineAtLeast = onlineAtLeast;
    }

    public static List<MotdRule> fromConfig(ConfigurationSection base) {
        List<MotdRule> out = new ArrayList<>();
        if (base == null) return out;
        for (String key : base.getKeys(false)) {
            ConfigurationSection r = base.getConfigurationSection(key);
            if (r == null) continue;
            String set = r.getString("set", "default");

            // days
            Set<DayOfWeek> dayset = EnumSet.noneOf(DayOfWeek.class);
            for (String d : r.getStringList("when.days")) {
                try { dayset.add(DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT))); } catch (Exception ignored) {}
            }

            // between
            LocalTime s=null,e=null;
            String between = r.getString("when.between", null);
            if (between != null && between.contains("-")) {
                String[] parts = between.split("-");
                try { s = LocalTime.parse(parts[0].trim()); } catch (Exception ignored) {}
                try { e = LocalTime.parse(parts[1].trim()); } catch (Exception ignored) {}
            }

            Integer minOnline = (r.isInt("when.online_at_least") ? r.getInt("when.online_at_least") : null);

            out.add(new MotdRule(set, dayset, s, e, minOnline));
        }
        return out;
    }

    public boolean matchesNow(int online, Clock clock) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!days.isEmpty() && !days.contains(now.getDayOfWeek())) return false;
        if (onlineAtLeast != null && online < onlineAtLeast) return false;

        if (start != null && end != null) {
            LocalTime t = now.toLocalTime();
            if (start.isBefore(end)) { // normal: 18:00-22:00
                if (t.isBefore(start) || t.isAfter(end)) return false;
            } else { // über Mitternacht: 22:00-03:00
                if (t.isAfter(end) && t.isBefore(start)) return false;
            }
        }
        return true;
    }
}