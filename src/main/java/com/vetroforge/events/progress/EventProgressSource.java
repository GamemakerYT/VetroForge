// src/main/java/com/vetroforge/events/progress/EventProgressSource.java
package com.vetroforge.events.progress;

public interface EventProgressSource {
    /** Liefert den aktuellen Zählerstand für die laufende Eventmetrik. */
    long readCurrent();
}
