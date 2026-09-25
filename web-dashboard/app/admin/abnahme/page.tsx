"use client";

import { useEffect, useState } from "react";
import { RoleGate } from "@/components/RoleGate";
import { AbnahmeDashboard } from "@/components/AbnahmeDashboard";
import { persistSession, refreshMe, type SessionState, type UserRole } from "@/lib/api";

export default function AbnahmeAdminPage() {
  const [session, setSession] = useState<SessionState>({ token: "", apiKey: "", role: "public" });

  useEffect(() => {
    const storedRole = (localStorage.getItem("vf_role") ?? "public") as UserRole;
    const stored = {
      token: localStorage.getItem("vf_token") ?? "",
      apiKey: localStorage.getItem("vf_api_key") ?? "",
      role: ["public", "player", "staff", "admin"].includes(storedRole) ? storedRole : "public"
    } satisfies SessionState;
    setSession(stored);
    if (stored.token || stored.apiKey) {
      refreshMe(stored).then(setSession).catch(() => {
        const fallback = { ...stored, role: stored.token ? "player" as const : "public" as const };
        persistSession(fallback);
        setSession(fallback);
      });
    }
  }, []);

  return (
    <main className="page">
      <div className="shell">
        <header className="hero">
          <div>
            <div className="brand">Abnahme-Dashboard</div>
            <p className="subtitle">Gemeinsamer Fortschritt zwischen Claude und Codex, pro Feature-Bereich.</p>
          </div>
          <a className="btn secondary text-link" href="/">
            Zurueck zum Dashboard
          </a>
        </header>

        <RoleGate current={session.role} required="admin" title="Abnahme-Dashboard">
          <AbnahmeDashboard />
        </RoleGate>
      </div>
    </main>
  );
}
