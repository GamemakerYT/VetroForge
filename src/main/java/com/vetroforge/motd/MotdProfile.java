// src/main/java/com/vetroforge/motd/MotdProfile.java
package com.vetroforge.motd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MotdProfile {
    public final String id;
    public final List<String> lines; // 1-2 Zeilen

    public MotdProfile(String id, List<String> lines) {
        this.id = id;
        List<String> copy = new ArrayList<>();
        if (lines != null) copy.addAll(lines);
        if (copy.isEmpty()) copy.add("§6VetroForge §8• §7Welcome");
        this.lines = Collections.unmodifiableList(copy);
    }
}