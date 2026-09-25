"use client";

import { useEffect, useMemo, useState } from "react";
import { apiFetch, type SessionState } from "@/lib/api";

type AppealEntry = {
  id: number;
  uuid: string;
  playerName?: string;
  username: string;
  message: string;
  status: "open" | "approved" | "rejected";
  createdAt: number;
  reviewedBy?: string;
  reviewedAt?: number;
  reviewNote?: string;
  banReason?: string;
  banStaff?: string;
  banUntil?: number;
};

type QueueResponse = {
  ok: boolean;
  items: AppealEntry[];
  page: number;
  pages: number;
  total: number;
};

const statuses = ["open", "approved", "rejected"] as const;

function formatDate(value?: number) {
  if (!value) return "unbekannt";
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(value);
}

export function BanAppealQueue({ session }: { session: SessionState }) {
  const [items, setItems] = useState<AppealEntry[]>([]);
  const [status, setStatus] = useState<(typeof statuses)[number]>("open");
  const [page, setPage] = useState(1);
  const [pages, setPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [message, setMessage] = useState("");
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState<number | null>(null);

  const path = useMemo(() => {
    const params = new URLSearchParams({ status, page: String(page), limit: "12" });
    return `/api/appeals/queue?${params.toString()}`;
  }, [status, page]);

  async function load() {
    setMessage("");
    try {
      const result = await apiFetch<QueueResponse>(path, session);
      setItems(result.items ?? []);
      setPages(result.pages ?? 1);
      setTotal(result.total ?? 0);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Appeals konnten nicht geladen werden");
    }
  }

  useEffect(() => {
    if (session.token || session.apiKey) void load();
  }, [path, session.token, session.apiKey]);

  async function act(entry: AppealEntry, action: "approve" | "reject") {
    setBusy(entry.id);
    setMessage("");
    try {
      await apiFetch("/api/appeals/action", session, {
        method: "POST",
        body: JSON.stringify({ id: entry.id, action, note: notes[entry.id] ?? "" })
      });
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Aktion fehlgeschlagen");
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="card span-12 stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>Ban-Appeals</h2>
          <p className="muted">Einsprueche gegen aktive Baenne. Genehmigen entbannt direkt.</p>
        </div>
        <span className="pill">{total} Eintraege</span>
      </div>

      <div className="filter-chips">
        {statuses.map((s) => (
          <button
            key={s}
            className={`filter-chip ${status === s ? "active" : ""}`}
            onClick={() => {
              setPage(1);
              setStatus(s);
            }}
          >
            {s === "open" ? "Offen" : s === "approved" ? "Angenommen" : "Abgelehnt"}
          </button>
        ))}
      </div>

      {message && <p className="muted">{message}</p>}

      <div style={{ overflowX: "auto" }}>
        <table className="table">
          <thead>
            <tr>
              <th>Case</th>
              <th>Spieler</th>
              <th>Bann-Grund</th>
              <th>Einspruch</th>
              <th>Eingereicht</th>
              <th>{status === "open" ? "Aktion" : "Entschieden"}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((entry) => (
              <tr key={entry.id}>
                <td>#{entry.id}</td>
                <td>
                  <strong>{entry.playerName || entry.username || "Unbekannt"}</strong>
                  <div className="mono muted">{entry.uuid.slice(0, 8)}...</div>
                </td>
                <td>
                  {entry.banReason || "Kein Grund"}
                  <div className="muted">von {entry.banStaff || "System"}</div>
                </td>
                <td style={{ maxWidth: 280, whiteSpace: "pre-wrap" }}>{entry.message}</td>
                <td>{formatDate(entry.createdAt)}</td>
                {entry.status === "open" ? (
                  <td>
                    <div className="stack">
                      <input
                        className="input compact"
                        placeholder="Notiz (optional)"
                        value={notes[entry.id] ?? ""}
                        onChange={(event) => setNotes((current) => ({ ...current, [entry.id]: event.target.value }))}
                      />
                      <div className="row">
                        <button className="btn" disabled={busy === entry.id} onClick={() => void act(entry, "approve")}>
                          Genehmigen
                        </button>
                        <button className="tiny-action danger" disabled={busy === entry.id} onClick={() => void act(entry, "reject")}>
                          Ablehnen
                        </button>
                      </div>
                    </div>
                  </td>
                ) : (
                  <td>
                    {entry.status === "approved" ? <span className="pill ok">Angenommen</span> : <span className="pill">Abgelehnt</span>}
                    <div className="muted">{entry.reviewedBy} · {formatDate(entry.reviewedAt)}</div>
                    {entry.reviewNote && <div className="muted">{entry.reviewNote}</div>}
                  </td>
                )}
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
