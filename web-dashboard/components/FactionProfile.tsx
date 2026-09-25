"use client";

import { useState } from "react";
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

function MiniBars({ rows, label }: { rows: Array<{ name: string; value: number }>; label: string }) {
  const max = Math.max(1, ...rows.map((row) => row.value));
  return (
    <div>
      <p className="muted">{label}</p>
      <svg className="chart" viewBox="0 0 600 180" role="img">
        {rows.map((row, index) => {
          const height = (row.value / max) * 120;
          const x = 24 + index * 54;
          return (
            <g key={`${row.name}-${index}`}>
              <rect x={x} y={140 - height} width="34" height={height} rx="10" fill="url(#vfRed)" />
              <text x={x + 17} y="164" textAnchor="middle" fill="#9ca8ba" fontSize="11">{row.name.slice(0, 5)}</text>
              <text x={x + 17} y={132 - height} textAnchor="middle" fill="#f5f7fb" fontSize="11">{row.value}</text>
            </g>
          );
        })}
        <defs>
          <linearGradient id="vfRed" x1="0" x2="0" y1="0" y2="1">
            <stop stopColor="#ff304f" />
            <stop offset="1" stopColor="#9f1230" />
          </linearGradient>
        </defs>
      </svg>
    </div>
  );
}

export function FactionProfile({ session }: { session: SessionState }) {
  const [name, setName] = useState("");
  const [profile, setProfile] = useState<FactionProfileResponse | null>(null);
  const [message, setMessage] = useState("");

  async function load() {
    setMessage("");
    try {
      setProfile(await apiFetch<FactionProfileResponse>(`/api/faction/profile?name=${encodeURIComponent(name)}`, session));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Faction nicht gefunden");
    }
  }

  return (
    <section className="card span-12 stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>Faction Profil</h2>
          <p className="muted">Members, Elo, CTF Season und History für die Website.</p>
        </div>
        <div className="row">
          <input className="input" placeholder="Faction Name" value={name} onChange={(event) => setName(event.target.value)} />
          <button className="btn" onClick={load}>Laden</button>
        </div>
      </div>
      {message && <p className="muted">{message}</p>}
      {profile && (
        <>
          <div className="row" style={{ flexWrap: "wrap" }}>
            <span className="pill">Leader {profile.leader}</span>
            <span className="pill">Members {profile.members}</span>
            <span className="pill">Coins {money(profile.coins)}</span>
            <span className="pill">Elo {profile.elo}</span>
            <span className="pill">CTF {profile.ctfCaptures}</span>
            <span className="pill">Crystals {profile.crystals}</span>
          </div>
          <div className="grid">
            <div className="card span-5">
              <MiniBars
                label="CTF Season Top"
                rows={profile.ctfSeasonTop.map((row) => ({ name: row.faction, value: row.captures }))}
              />
            </div>
            <div className="card span-7">
              <MiniBars
                label="Elo History"
                rows={(profile.eloHistory.length ? profile.eloHistory : [{ id: "now", value: profile.elo, detail: "", created: Date.now() }])
                  .slice(0, 10)
                  .map((row, index) => ({ name: String(index + 1), value: row.value }))}
              />
            </div>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Member</th>
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
        </>
      )}
    </section>
  );
}
