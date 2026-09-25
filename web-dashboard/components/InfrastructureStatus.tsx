"use client";

import { useEffect, useState } from "react";
import { apiFetch, type SessionState } from "@/lib/api";

type WebStatus = {
  ok: boolean;
  apiUrl: string;
  mysqlReady: boolean;
  mysqlError: string;
  tables: Record<string, boolean>;
};

function StatePill({ ok, label }: { ok: boolean; label: string }) {
  return <span className={`status-pill ${ok ? "ok" : "warn"}`}>{ok ? "OK" : "FEHLT"} {label}</span>;
}

export function InfrastructureStatus({ session }: { session: SessionState }) {
  const [status, setStatus] = useState<WebStatus | null>(null);
  const [message, setMessage] = useState("Lade Infrastruktur...");

  async function load() {
    try {
      const result = await apiFetch<WebStatus>("/api/web/status", session);
      setStatus(result);
      setMessage("");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Status nicht erreichbar");
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const tablesReady = status ? Object.values(status.tables ?? {}).every(Boolean) : false;
  const severity = !status ? "warn" : status.mysqlReady && tablesReady ? "ok" : status.mysqlReady ? "warn" : "error";
  const headline = !status
    ? "Factions WebAPI wird geprüft"
    : status.mysqlReady && tablesReady
      ? "Factions WebAPI bereit"
      : status.mysqlReady
        ? "Factions WebAPI teilweise bereit"
        : "Factions WebAPI braucht MySQL";

  return (
    <section className={`status-banner ${severity} stack`}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>{headline}</h2>
          <p className="muted">{status?.apiUrl || "Wartet auf VF_FACTIONS_API_URL"}</p>
        </div>
        <button className="btn secondary" onClick={load}>Prüfen</button>
      </div>

      {message && <p className="muted">{message}</p>}
      {status && (
        <>
          <div className="row" style={{ flexWrap: "wrap" }}>
            <StatePill ok={Boolean(status.apiUrl)} label="VF_FACTIONS_API_URL" />
            <StatePill ok={status.mysqlReady} label="MySQL" />
            <StatePill ok={Boolean(status.tables.web_users)} label="web_users" />
            <StatePill ok={Boolean(status.tables.account_link_codes)} label="account_link_codes" />
            <StatePill ok={Boolean(status.tables.player_cache)} label="player_cache" />
            <StatePill ok={Boolean(status.tables.account_reset_codes)} label="account_reset_codes" />
          </div>
          {!status.mysqlReady && <p className="muted">MySQL Env noch nicht erreichbar: {status.mysqlError || "keine Details"}</p>}
        </>
      )}
    </section>
  );
}
