"use client";

import { useEffect, useMemo, useState } from "react";
import { apiFetch, type SessionState } from "@/lib/api";

type PunishmentEntry = {
  id: number;
  uuid: string;
  playerName?: string;
  type: string;
  reason: string;
  staff: string;
  untilTime: number;
  active: boolean;
  createdAt: number;
};

type PunishmentResponse = {
  ok: boolean;
  items: PunishmentEntry[];
  page: number;
  pages: number;
  total: number;
};

const filters = ["all", "ban", "mute", "kick"] as const;

function typeClass(type: string) {
  const normalized = type.toLowerCase();
  if (normalized === "ban") return "critical";
  if (normalized === "mute") return "mid";
  if (normalized === "kick") return "high";
  return "low";
}

function formatDate(value: number) {
  if (!value) return "unbekannt";
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(value);
}

export function PunishmentHistoryPanel({ session }: { session: SessionState }) {
  const [items, setItems] = useState<PunishmentEntry[]>([]);
  const [query, setQuery] = useState("");
  const [type, setType] = useState<(typeof filters)[number]>("all");
  const [page, setPage] = useState(1);
  const [pages, setPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [message, setMessage] = useState("");
  const [copiedCase, setCopiedCase] = useState<number | null>(null);
  const [highlightCase, setHighlightCase] = useState<number | null>(null);

  const path = useMemo(() => {
    const params = new URLSearchParams({
      q: query,
      type,
      page: String(page),
      limit: "12"
    });
    return `/api/admin/punishments/history?${params.toString()}`;
  }, [query, type, page]);

  async function load() {
    setMessage("");
    try {
      const result = await apiFetch<PunishmentResponse>(path, session);
      setItems(result.items ?? []);
      setPages(result.pages ?? 1);
      setTotal(result.total ?? 0);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Punishments konnten nicht geladen werden");
    }
  }

  useEffect(() => {
    if (session.token || session.apiKey) void load();
  }, [path, session.token, session.apiKey]);

  useEffect(() => {
    const raw = new URLSearchParams(window.location.search).get("case");
    const parsed = raw ? Number.parseInt(raw, 10) : 0;
    if (Number.isFinite(parsed) && parsed > 0) setHighlightCase(parsed);
  }, []);

  async function copyCaseLink(entry: PunishmentEntry) {
    const url = `${window.location.origin}/?case=${entry.id}#punishments`;
    await navigator.clipboard.writeText(url);
    setCopiedCase(entry.id);
    window.setTimeout(() => setCopiedCase(null), 1800);
  }

  return (
    <section className="card span-12 stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>Staff Punishment History</h2>
          <p className="muted">Bans, Mutes und Kicks aus VetroCore/MySQL. Schnell filtern, prüfen, weiterklicken.</p>
        </div>
        <span className="pill">{total} Einträge</span>
      </div>

      <div className="filter-chips">
        {filters.map((filter) => (
          <button
            className={`filter-chip ${type === filter ? "active" : ""}`}
            key={filter}
            onClick={() => {
              setPage(1);
              setType(filter);
            }}
          >
            {filter === "all" ? "Alle" : filter.toUpperCase()}
          </button>
        ))}
      </div>

      <div className="row">
        <input
          className="input"
          placeholder="Spieler, UUID, Staff oder Grund suchen"
          value={query}
          onChange={(event) => {
            setPage(1);
            setQuery(event.target.value);
          }}
        />
        <button className="btn" onClick={load}>Neu laden</button>
      </div>

      {message && <p className="muted">{message}</p>}

      <div style={{ overflowX: "auto" }}>
        <table className="table">
          <thead>
            <tr>
              <th>Case</th>
              <th>Typ</th>
              <th>Ziel</th>
              <th>Grund</th>
              <th>Staff</th>
              <th>Status</th>
              <th>Zeit</th>
            </tr>
          </thead>
          <tbody>
            {items.map((entry) => (
              <tr key={entry.id} className={highlightCase === entry.id ? "highlight-row" : ""}>
                <td>#{entry.id}</td>
                <td className={`risk ${typeClass(entry.type)}`}>{entry.type}</td>
                <td>
                  <strong>{entry.playerName || "Unbekannt"}</strong>
                  <div className="mono muted">{entry.uuid.slice(0, 8)}...</div>
                  <a
                    className="tiny-action text-link inline-action"
                    href={`/profile/?player=${encodeURIComponent(entry.playerName || entry.uuid)}`}
                  >
                    Spieler öffnen
                  </a>
                </td>
                <td>{entry.reason || "Kein Grund"}</td>
                <td>{entry.staff || "System"}</td>
                <td>{entry.active ? <span className="pill ok">Aktiv</span> : <span className="pill">Inaktiv</span>}</td>
                <td>
                  <div>{formatDate(entry.createdAt)}</div>
                  <button className="tiny-action" onClick={() => void copyCaseLink(entry)}>
                    {copiedCase === entry.id ? "Kopiert" : "Case-Link"}
                  </button>
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
