// src/main/java/com/vetroforge/events/EventService.java
package com.vetroforge.events;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface EventService {

    Optional<RunningEvent> getActiveEvent();

    Collection<EventTemplate> getTemplates();

    void reloadTemplates();

    void start(String templateId) throws IllegalArgumentException;

    void stop(boolean silent);

    void pause();

    void resume();

    void setProgress(long absolute);

    void addProgress(long delta);

    /* ===== DTOs ===== */

    final class RunningEvent {
        public final String id;
        public final String name;
        public final long current;
        public final long goal;
        public final Instant startedAt;
        public final Instant endsAt; // optional (kann null sein)
        public final boolean paused;

        public RunningEvent(String id, String name, long current, long goal,
                            Instant startedAt, Instant endsAt, boolean paused) {
            this.id = id; this.name = name; this.current = current; this.goal = goal;
            this.startedAt = startedAt; this.endsAt = endsAt; this.paused = paused;
        }

        public int pct() {
            if (goal <= 0) return 0;
            long p = Math.round((current * 100.0) / goal);
            if (p < 0) p = 0; if (p > 100) p = 100;
            return (int) p;
        }
    }
}
