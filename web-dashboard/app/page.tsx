"use client";

import { useEffect, useState } from "react";
import { AhRiskDashboard } from "@/components/AhRiskDashboard";
import { AdminSummaryPanel } from "@/components/AdminSummaryPanel";
import { BanAppealPanel } from "@/components/BanAppealPanel";
import { BanAppealQueue } from "@/components/BanAppealQueue";
import { FactionProfile } from "@/components/FactionProfile";
import { InfrastructureStatus } from "@/components/InfrastructureStatus";
import { LoginCard } from "@/components/LoginCard";
import { PlayerDirectoryPanel } from "@/components/PlayerDirectoryPanel";
import { PunishmentHistoryPanel } from "@/components/PunishmentHistoryPanel";
import { RoleGate } from "@/components/RoleGate";
import { RaidShopEditor } from "@/components/RaidShopEditor";
import { ShopEditor } from "@/components/ShopEditor";
import { UserRoleAdmin } from "@/components/UserRoleAdmin";
import { API_BASE, canAccess, persistSession, refreshMe, type SessionState, type UserRole } from "@/lib/api";

export default function Home() {
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
      <div className="admin-layout">
        <aside className="sidebar card">
          <div className="sidebar-logo">VF</div>
          <nav className="sidebar-nav">
            <a href="#overview">Overview</a>
            <a href="/status">Status</a>
            <a href="#player">Player</a>
            <a href="#appeal">Bann-Einspruch</a>
            {canAccess(session.role, "staff") && <a href="#players">Players</a>}
            {canAccess(session.role, "staff") && <a href="#auction">AH Risk</a>}
            {canAccess(session.role, "staff") && <a href="#punishments">Punishments</a>}
            {canAccess(session.role, "staff") && <a href="#appeals">Ban-Appeals</a>}
            {canAccess(session.role, "admin") && <a href="#shop">Shop</a>}
            {canAccess(session.role, "admin") && <a href="#raidshop">RaidShop</a>}
            {canAccess(session.role, "admin") && <a href="#users">Users</a>}
            {canAccess(session.role, "admin") && <a href="/admin/tebex">Tebex</a>}
            {canAccess(session.role, "admin") && <a href="/admin/abnahme">Abnahme</a>}
            <a href="#factions">Factions</a>
          </nav>
          <div className="sidebar-footer">
            <span className={`role-badge role-${session.role}`}>{session.role.toUpperCase()}</span>
            <span className="pill">{session.token ? "Session aktiv" : "Guest"}</span>
            {canAccess(session.role, "staff") && <span className="pill">{session.apiKey ? "API Key gesetzt" : "Kein API Key"}</span>}
          </div>
        </aside>

        <div className="shell admin-content">
        <header className="hero" id="overview">
          <div>
            <div className="brand">VetroForge</div>
            <p className="subtitle">
              Web-Dashboard fuer Staff, Shop, Auktionshaus und Factions. Modern, rot, schnell und ohne Spawn-Kram.
            </p>
          </div>
          <div className="stack">
            <span className="pill">API {API_BASE}</span>
            <span className={`role-badge role-${session.role}`}>{session.role.toUpperCase()}</span>
          </div>
        </header>

        <InfrastructureStatus session={session} />

        <div className="grid">
          <div id="summary" className="anchor span-12">
            <RoleGate current={session.role} required="staff" title="Systemkarte">
              <AdminSummaryPanel session={session} />
            </RoleGate>
          </div>
          <div id="player" className="anchor span-4">
            <LoginCard session={session} setSession={setSession} />
          </div>
          <div id="appeal" className="anchor span-4">
            <BanAppealPanel session={session} />
          </div>
          <div id="auction" className="anchor span-8">
            <RoleGate current={session.role} required="staff" title="AH Risk Dashboard">
              <AhRiskDashboard session={session} />
            </RoleGate>
          </div>
          <div id="players" className="anchor span-12">
            <RoleGate current={session.role} required="staff" title="Player Directory">
              <PlayerDirectoryPanel session={session} />
            </RoleGate>
          </div>
          <div id="users" className="anchor span-12">
            <RoleGate current={session.role} required="admin" title="Rollen Admin">
              <UserRoleAdmin session={session} />
            </RoleGate>
          </div>
          <div id="shop" className="anchor span-12">
            <RoleGate current={session.role} required="admin" title="Shop Webeditor">
              <ShopEditor session={session} />
            </RoleGate>
          </div>
          <div id="raidshop" className="anchor span-12">
            <RoleGate current={session.role} required="admin" title="RaidShop Webeditor">
              <RaidShopEditor session={session} />
            </RoleGate>
          </div>
          <div id="punishments" className="anchor span-12">
            <RoleGate current={session.role} required="staff" title="Punishment History">
              <PunishmentHistoryPanel session={session} />
            </RoleGate>
          </div>
          <div id="appeals" className="anchor span-12">
            <RoleGate current={session.role} required="staff" title="Ban-Appeals">
              <BanAppealQueue session={session} />
            </RoleGate>
          </div>
          <div id="factions" className="anchor span-12">
            <FactionProfile session={session} />
          </div>
        </div>
        </div>
      </div>
    </main>
  );
}
