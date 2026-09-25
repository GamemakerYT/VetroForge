"use client";

import { useEffect, useMemo, useState } from "react";
import { apiFetch, money, type SessionState } from "@/lib/api";

type PlayerProfile = {
  ok: boolean;
  uuid: string;
  name: string;
  rank: string;
  faction: string;
  coins: number;
  kills: number;
  deaths: number;
  kd: number;
  elo: number;
  level: number;
  prestige: number;
  prestigeXp: number;
  nextPrestige: string;
  xpToNext: number;
  raidWins: number;
  warzoneChests: number;
  bossKills: number;
};

export default function PlayerProfilePage() {
  const [session, setSession] = useState<SessionState>({ token: "", apiKey: "", role: "public" });
  const [profile, setProfile] = useState<PlayerProfile | null>(null);
  const [message, setMessage] = useState("Lade Spielerprofil...");

  const player = useMemo(() => {
    if (typeof window === "undefined") return "";
    return new URLSearchParams(window.location.search).get("player") ?? "";
  }, []);

  useEffect(() => {
    const stored: SessionState = {
      token: localStorage.getItem("vf_token") ?? "",
      apiKey: localStorage.getItem("vf_api_key") ?? "",
      role: "public"
    };
    setSession(stored);
  }, []);

  useEffect(() => {
    if (!player) {
      setMessage("Kein Spieler angegeben.");
      return;
    }
    const isUuid = /^[0-9a-f-]{36}$/i.test(player);
    const param = isUuid ? `uuid=${encodeURIComponent(player)}` : `name=${encodeURIComponent(player)}`;
    apiFetch<PlayerProfile>(`/api/profile?${param}`, session)
      .then((result) => {
        setProfile(result);
        setMessage("");
      })
      .catch((error) => setMessage(error instanceof Error ? error.message : "Profil konnte nicht geladen werden"));
  }, [player, session.apiKey, session.token]);

  return (
    <main className="page">
      <div className="shell">
        <header className="hero">
          <div>
            <div className="brand">Profile</div>
            <p className="subtitle">Spielerprofil aus der Factions WebAPI.</p>
          </div>
          <a className="btn secondary text-link" href="/#punishments">
            Zurueck
          </a>
        </header>

        {!profile && <section className="card"><p className="muted">{message}</p></section>}

        {profile && (
          <section className="card stack">
            <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
              <div>
                <h2>{profile.name}</h2>
                <p className="muted mono">{profile.uuid}</p>
              </div>
              <span className="role-badge role-admin">{profile.rank || "Member"}</span>
            </div>

            <div className="profile-stat-grid">
              <span className="pill">Faction {profile.faction || "-"}</span>
              <span className="pill">Coins {money(profile.coins)}</span>
              <span className="pill">Elo {profile.elo}</span>
              <span className="pill">Level {profile.level}</span>
              <span className="pill">Prestige {profile.prestige}</span>
              <span className="pill">XP {money(profile.prestigeXp)}</span>
              <span className="pill">Kills {profile.kills}</span>
              <span className="pill">Deaths {profile.deaths}</span>
              <span className="pill">K/D {profile.kd}</span>
              <span className="pill">Raid Wins {profile.raidWins}</span>
              <span className="pill">WZ Chests {profile.warzoneChests}</span>
              <span className="pill">Boss Kills {profile.bossKills}</span>
            </div>

            <div className="card">
              <h2>Nächster Prestige Schritt</h2>
              <p className="muted">{profile.nextPrestige} benötigt noch {money(profile.xpToNext)} XP.</p>
            </div>
          </section>
        )}
      </div>
    </main>
  );
}
