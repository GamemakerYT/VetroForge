"use client";

import { useEffect, useState } from "react";
import { apiFetch, money, type SessionState } from "@/lib/api";

type Summary = {
  ok: boolean;
  online: number;
  maxPlayers: number;
  tps: number;
  ramUsedMb: number;
  ramMaxMb: number;
  apiUptimeSeconds: number;
  reports24h: number;
  ahActiveListings: number;
  ahRiskListings: number;
  ahQuarantined: number;
  mysqlReady: boolean;
  webApiReady: boolean;
  lastErrors: string[];
};

function uptime(seconds: number) {
  if (!seconds) return "gerade gestartet";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h <= 0) return `${m}m`;
  return `${h}h ${m}m`;
}

function tpsClass(tps: number) {
  if (tps >= 19) return "ok";
  if (tps >= 17) return "warn";
  return "error";
}

export function AdminSummaryPanel({ session }: { session: SessionState }) {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [message, setMessage] = useState("");

  async function load() {
    setMessage("");
    try {
      setSummary(await apiFetch<Summary>("/api/admin/dashboard/summary", session));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Summary konnte nicht geladen werden");
    }
  }

  useEffect(() => {
    if (session.token || session.apiKey) void load();
  }, [session.token, session.apiKey]);

  return (
    <section className="card span-12 stack admin-summary">
      <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
        <div>
          <h2>Systemkarte</h2>
          <p className="muted">Live-Summary aus WebAPI, MySQL, AH und Logs.</p>
        </div>
        <button className="btn secondary" onClick={load}>Neu laden</button>
      </div>

      {message && <p className="muted">{message}</p>}

      <div className="summary-grid">
        <div className="summary-tile">
          <span>Spieler</span>
          <strong>{summary ? `${summary.online}/${summary.maxPlayers}` : "-"}</strong>
          <small>online Factions</small>
        </div>
        <div className="summary-tile">
          <span>TPS</span>
          <strong className={`summary-value ${summary ? tpsClass(summary.tps) : ""}`}>{summary ? summary.tps.toFixed(2) : "-"}</strong>
          <small>Live Performance</small>
        </div>
        <div className="summary-tile">
          <span>RAM</span>
          <strong>{summary ? `${money(summary.ramUsedMb)} MB` : "-"}</strong>
          <small>{summary ? `von ${money(summary.ramMaxMb)} MB` : "Server"}</small>
        </div>
        <div className="summary-tile">
          <span>Reports 24h</span>
          <strong>{summary ? summary.reports24h : "-"}</strong>
          <small>Staff Inbox</small>
        </div>
        <div className="summary-tile">
          <span>AH Risk</span>
          <strong>{summary ? summary.ahRiskListings : "-"}</strong>
          <small>{summary ? `${summary.ahQuarantined} eingefroren` : "Auktionshaus"}</small>
        </div>
        <div className="summary-tile">
          <span>API Uptime</span>
          <strong>{summary ? uptime(summary.apiUptimeSeconds) : "-"}</strong>
          <small>
            <span className={summary?.webApiReady ? "status-dot ok" : "status-dot"} /> WebAPI
            {" · "}
            <span className={summary?.mysqlReady ? "status-dot ok" : "status-dot"} /> MySQL
          </small>
        </div>
      </div>

      {summary?.lastErrors?.length ? (
        <div className="log-strip">
          <strong>Letzte Fehler</strong>
          {summary.lastErrors.map((line, index) => (
            <code key={`${index}-${line}`}>{line}</code>
          ))}
        </div>
      ) : (
        <p className="muted">Keine aktuellen Fehler in der Kurzansicht.</p>
      )}
    </section>
  );
}
