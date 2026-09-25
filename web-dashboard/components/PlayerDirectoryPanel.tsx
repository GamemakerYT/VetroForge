"use client";

import { useEffect, useMemo, useState } from "react";
import { apiFetch, canAccess, money, type SessionState } from "@/lib/api";

type PlayerRow = {
  uuid: string;
  name: string;
  faction: string;
  role: string;
  coins: number;
  lastSeen: number;
  online: boolean;
};

type PlayerResponse = {
  ok: boolean;
  players: PlayerRow[];
  page: number;
  pages: number;
  total: number;
  days: number;
  ranks: string[];
};

function formatLastSeen(value: number) {
  if (!value) return "unbekannt";
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(value);
}

export function PlayerDirectoryPanel({ session }: { session: SessionState }) {
  const [players, setPlayers] = useState<PlayerRow[]>([]);
  const [query, setQuery] = useState("");
  const [days, setDays] = useState("14");
  const [page, setPage] = useState(1);
  const [pages, setPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [message, setMessage] = useState("");
  const [ranks, setRanks] = useState<string[]>([]);
  const [selectedRanks, setSelectedRanks] = useState<Record<string, string>>({});

  const path = useMemo(() => {
    const params = new URLSearchParams({
      q: query,
      days,
      page: String(page),
      limit: "16"
    });
    return `/api/admin/players/recent?${params.toString()}`;
  }, [query, days, page]);

  async function load() {
    setMessage("");
    try {
      const result = await apiFetch<PlayerResponse>(path, session);
      setPlayers(result.players ?? []);
      setPages(result.pages ?? 1);
      setTotal(result.total ?? 0);
      setRanks(result.ranks ?? []);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Spieler konnten nicht geladen werden");
    }
  }

  async function changeRank(player: PlayerRow) {
    const rank = selectedRanks[player.uuid] || player.role || "Member";
    setMessage("");
    try {
      await apiFetch("/api/admin/players/rank", session, {
        method: "POST",
        body: JSON.stringify({ uuid: player.uuid, name: player.name, rank })
      });
      setMessage(`Rank von ${player.name || player.uuid} wurde auf ${rank} gesetzt.`);
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Rank konnte nicht gesetzt werden");
    }
  }

  useEffect(() => {
    if (session.token || session.apiKey) void load();
  }, [path, session.token, session.apiKey]);

  return (
    <section className="card span-12 stack">
      <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
        <div>
          <h2>Player Directory</h2>
          <p className="muted">Letzte Spieler aus MySQL/player_cache mit Profil-Shortcut und Online-Status.</p>
        </div>
        <span className="pill">{total} Spieler</span>
      </div>

      <div className="row">
        <input
          className="input"
          placeholder="Name, UUID, Faction oder Rank suchen"
          value={query}
          onChange={(event) => {
            setPage(1);
            setQuery(event.target.value);
          }}
        />
        <select
          className="input compact"
          value={days}
          onChange={(event) => {
            setPage(1);
            setDays(event.target.value);
          }}
        >
          <option value="1">24h</option>
          <option value="7">7 Tage</option>
          <option value="14">14 Tage</option>
          <option value="30">30 Tage</option>
          <option value="0">Alle</option>
        </select>
        <button className="btn" onClick={load}>Neu laden</button>
      </div>

      {message && <p className="muted">{message}</p>}

      <div style={{ overflowX: "auto" }}>
        <table className="table">
          <thead>
            <tr>
              <th>Spieler</th>
              <th>Status</th>
              <th>Faction</th>
              <th>Rank</th>
              <th>Coins</th>
              <th>Last Seen</th>
              <th>Aktionen</th>
            </tr>
          </thead>
          <tbody>
            {players.map((player) => (
              <tr key={player.uuid}>
                <td>
                  <strong>{player.name || "Unbekannt"}</strong>
                  <div className="mono muted">{player.uuid.slice(0, 8)}...</div>
                </td>
                <td>
                  <span className={player.online ? "status-dot ok" : "status-dot"} />
                  {player.online ? " Online" : " Offline"}
                </td>
                <td>{player.faction || "-"}</td>
                <td>{player.role || "player"}</td>
                <td>{money(player.coins)}</td>
                <td>{formatLastSeen(player.lastSeen)}</td>
                <td>
                  <div className="row" style={{ flexWrap: "wrap" }}>
                    <a className="tiny-action text-link inline-action" href={`/profile/?player=${encodeURIComponent(player.name || player.uuid)}`}>
                      Profil öffnen
                    </a>
                    {canAccess(session.role, "admin") && (
                      <>
                        <select
                          className="input compact mini-select"
                          value={selectedRanks[player.uuid] ?? player.role ?? "Member"}
                          onChange={(event) => setSelectedRanks((current) => ({ ...current, [player.uuid]: event.target.value }))}
                        >
                          {(ranks.length ? ranks : ["Member", "Ember", "Vanguard", "Obsidian", "Apex", "Vetro+", "Support", "Builder", "Mod", "Admin"]).map((rank) => (
                            <option key={rank} value={rank}>{rank}</option>
                          ))}
                        </select>
                        <button className="tiny-action" onClick={() => changeRank(player)}>Rank ändern</button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="row" style={{ justifyContent: "space-between" }}>
        <button className="btn secondary" disabled={page <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>
          Zurück
        </button>
        <span className="muted">Seite {page} / {pages}</span>
        <button className="btn secondary" disabled={page >= pages} onClick={() => setPage((value) => Math.min(pages, value + 1))}>
          Weiter
        </button>
      </div>
    </section>
  );
}
