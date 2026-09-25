"use client";

import { useEffect, useState } from "react";
import { apiFetch, money, type SessionState } from "@/lib/api";

type HistoryEntry = {
  id: string;
  value: number;
  detail: string;
  created: number;
};

type FactionProfileResponse = {
  ok: boolean;
  name: string;
  display: string;
  leader: string;
  members: number;
  membersList: Array<{ uuid: string; name: string; role: string; online: boolean; kills: number; elo: number }>;
  coins: number;
  elo: number;
  crystals: number;
  ctfCaptures: number;
  ctfSeasonTop: Array<{ faction: string; captures: number }>;
  eloHistory: HistoryEntry[];
  raidHistory: HistoryEntry[];
};

function Bars({ rows }: { rows: Array<{ name: string; value: number }> }) {
  const max = Math.max(1, ...rows.map((row) => row.value));
  return (
    <svg className="chart hero-chart" viewBox="0 0 720 190" role="img">
      {rows.map((row, index) => {
        const height = (row.value / max) * 130;
        const x = 28 + index * 66;
        return (
          <g key={`${row.name}-${index}`}>
            <rect x={x} y={150 - height} width="42" height={height} rx="14" fill="url(#publicRed)" />
            <text x={x + 21} y="176" textAnchor="middle" fill="#9ca8ba" fontSize="12">{row.name.slice(0, 7)}</text>
            <text x={x + 21} y={140 - height} textAnchor="middle" fill="#fff" fontSize="12">{row.value}</text>
          </g>
        );
      })}
      <defs>
        <linearGradient id="publicRed" x1="0" x2="0" y1="0" y2="1">
          <stop stopColor="#ff304f" />
          <stop offset="1" stopColor="#7f1024" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function PublicFactionView({ name }: { name: string }) {
  const [profile, setProfile] = useState<FactionProfileResponse | null>(null);
  const [message, setMessage] = useState("Lade Faction...");

  useEffect(() => {
    const session: SessionState = { token: "", apiKey: "", role: "public" };
    apiFetch<FactionProfileResponse>(`/api/faction/profile?name=${encodeURIComponent(name)}`, session)
      .then((result) => {
        setProfile(result);
        setMessage("");
      })
      .catch((error) => setMessage(error instanceof Error ? error.message : "Faction nicht gefunden"));
  }, [name]);

  if (!profile) {
    return (
      <main className="page public-page">
        <section className="public-hero card">
          <p className="pill">Faction Profil</p>
          <h1>{name}</h1>
          <p className="muted">{message}</p>
        </section>
      </main>
    );
  }

  return (
    <main className="page public-page">
      <section className="public-hero card">
        <p className="pill">Öffentliches Faction Profil</p>
        <h1>{profile.display || profile.name}</h1>
        <div className="row public-stats">
          <span className="pill">Leader {profile.leader}</span>
          <span className="pill">Members {profile.members}</span>
          <span className="pill">Elo {profile.elo}</span>
          <span className="pill">Coins {money(profile.coins)}</span>
          <span className="pill">CTF {profile.ctfCaptures}</span>
        </div>
      </section>

      <section className="grid public-grid">
        <div className="card span-7">
          <h2>CTF Season</h2>
          <Bars rows={profile.ctfSeasonTop.map((row) => ({ name: row.faction, value: row.captures }))} />
        </div>
        <div className="card span-5">
          <h2>Faction Core</h2>
          <div className="stack">
            <span className="pill">Crystals {profile.crystals}</span>
            <span className="pill">Raid History {profile.raidHistory.length}</span>
            <span className="pill">Elo History {profile.eloHistory.length}</span>
          </div>
        </div>
        <div className="card span-12">
          <h2>Members</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Rolle</th>
                <th>Status</th>
                <th>Kills</th>
                <th>Elo</th>
              </tr>
            </thead>
            <tbody>
              {profile.membersList.map((member) => (
                <tr key={member.uuid}>
                  <td>{member.name}</td>
                  <td>{member.role}</td>
                  <td>{member.online ? "Online" : "Offline"}</td>
                  <td>{member.kills}</td>
                  <td>{member.elo}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  );
}
