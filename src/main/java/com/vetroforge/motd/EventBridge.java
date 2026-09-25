package com.vetroforge.motd;

import java.time.Instant;
import java.util.Optional;

public interface EventBridge {
    Optional<ActiveEvent> getActiveEvent();

    final class ActiveEvent {
        public final String name;
        public final long current;
        public final long goal;
        public final Instant endsAt; // optional (kann null sein)

        public ActiveEvent(String name, long current, long goal, Instant endsAt) {
            this.name = name;
            this.current = current;
            this.goal = goal;
            this.endsAt = endsAt;
        }

        public int progressPct() {
            if (goal <= 0) return 0;
            long pct = Math.round((current * 100.0) / goal);
            if (pct < 0) pct = 0;
            if (pct > 100) pct = 100;
            return (int) pct;
        }
    }
}
