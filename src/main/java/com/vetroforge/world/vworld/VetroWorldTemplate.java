// src/main/java/com/vetroforge/world/vworld/VetroWorldTemplate.java
package com.vetroforge.world.vworld;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class VetroWorldTemplate {

    private final String name;

    private String schematic;

    private VetroPoint pasteOrigin;

    private final Map<VetroPointType, VetroPoint> points = new EnumMap<>(VetroPointType.class);
    private final Map<VetroRegionType, VetroRegion> regions = new EnumMap<>(VetroRegionType.class);

    public VetroWorldTemplate(String name) {
        this.name = name;
        this.schematic = name + ".schem";
        this.pasteOrigin = new VetroPoint(0.0, 64.0, 0.0, 0.0f, 0.0f);
    }

    public String name() {
        return name;
    }

    public String schematic() {
        return schematic;
    }

    public void setSchematic(String schematic) {
        if (schematic == null || schematic.isBlank()) {
            this.schematic = name + ".schem";
            return;
        }

        this.schematic = schematic;
    }

    public VetroPoint pasteOrigin() {
        return pasteOrigin;
    }

    public void setPasteOrigin(VetroPoint pasteOrigin) {
        if (pasteOrigin == null) return;
        this.pasteOrigin = pasteOrigin;
        this.points.put(VetroPointType.PASTEORIGIN, pasteOrigin);
    }

    public void setPoint(VetroPointType type, VetroPoint point) {
        if (type == null || point == null) return;

        points.put(type, point);

        if (type == VetroPointType.PASTEORIGIN) {
            pasteOrigin = point;
        }
    }

    public VetroPoint getPoint(VetroPointType type) {
        if (type == null) return null;
        return points.get(type);
    }

    public Map<VetroPointType, VetroPoint> points() {
        return Collections.unmodifiableMap(points);
    }

    public void setRegion(VetroRegionType type, VetroRegion region) {
        if (type == null || region == null) return;
        regions.put(type, region);
    }

    public VetroRegion getRegion(VetroRegionType type) {
        if (type == null) return null;
        return regions.get(type);
    }

    public Map<VetroRegionType, VetroRegion> regions() {
        return Collections.unmodifiableMap(regions);
    }
}