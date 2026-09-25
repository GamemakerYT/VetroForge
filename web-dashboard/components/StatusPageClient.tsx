"use client";

import { useEffect, useMemo, useState } from "react";

type WebStatus = {
  ok: boolean;
  overall: ServiceState;
  apiUrl: string;
  mysqlReady: boolean;
  mysqlError: string;
  services: Record<string, ServiceStatus>;
  tables: Record<string, boolean>;
};

type ServiceState = "ok" | "warn" | "error";

type ServiceStatus = {
  id: string;
  label: string;
  status: ServiceState;
  detail: string;
};

type Health = {
  ok: boolean;
  online: number;
  max: number;
};

type Warzone = {
  ok: boolean;
  count: number;
  players: string[];
};

type Lootdrop = {
  ok: boolean;
  running: boolean;
  etaSeconds: number;
  intervalSeconds: number;
};

type Leaderboards = {
  ok: boolean;
  factions?: Array<{ name: string; members: number }>;
  kills?: string[];
};

type StatusData = {
  web?: WebStatus;
  health?: Health;
  warzone?: Warzone;
  lootdrop?: Lootdrop;
  leaderboards?: Leaderboards;
  error?: string;
  checkedAt?: number;
};

async function getJson<T>(apiBase: string, path: string): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, { cache: "no-store" });
  const text = await response.text();
  let json: Record<string, unknown> = {};
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { err: text || "invalid json" };
  }
  if (!response.ok) throw new Error(String(json.err ?? response.statusText));
  return json as T;
}

async function loadOne<T>(apiBase: string, path: string): Promise<{ data?: T; error?: string }> {
  try {
    return { data: await getJson<T>(apiBase, path) };
  } catch (error) {
    return { error: error instanceof Error ? error.message : "nicht erreichbar" };
  }
}

function formatEta(seconds: number | undefined) {
  const safe = Math.max(0, seconds ?? 0);
  if (safe <= 0) return "bereit";
  const minutes = Math.floor(safe / 60);
  const rest = safe % 60;
  return minutes > 0 ? `${minutes}m ${rest}s` : `${rest}s`;
}

function StatusLight({ state }: { state: "ok" | "warn" | "error" }) {
  return <span className={`status-light ${state}`} />;
}

function MiniCard({
  title,
  value,
  detail,
  state
}: {
  title: string;
  value: string;
  detail: string;
  state: "ok" | "warn" | "error";
}) {
  return (
    <article className={`status-mini ${state}`}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <span className="muted">{title}</span>
        <StatusLight state={state} />
      </div>
      <strong>{value}</strong>
      <p className="muted">{detail}</p>
    </article>
  );
}

export function StatusPageClient({ initialApiBase }: { initialApiBase: string }) {
  const [apiBase, setApiBase] = useState(initialApiBase);
  const [data, setData] = useState<StatusData>({});
  const [loading, setLoading] = useState(false);

  async function load() {
    if (!apiBase) {
      setData({ error: "API URL fehlt", checkedAt: Date.now() });
      return;
    }
    setLoading(true);
    try {
      const [web, health, warzone, lootdrop, leaderboards] = await Promise.all([
        loadOne<WebStatus>(apiBase, "/api/web/status"),
        loadOne<Health>(apiBase, "/api/health"),
        loadOne<Warzone>(apiBase, "/api/warzone/live"),
        loadOne<Lootdrop>(apiBase, "/api/lootdrop/next"),
        loadOne<Leaderboards>(apiBase, "/api/leaderboards?limit=3")
      ]);
      const errors = [web.error, health.error, warzone.error, lootdrop.error, leaderboards.error].filter(Boolean);
      setData({
        web: web.data,
        health: health.data,
        warzone: warzone.data,
        lootdrop: lootdrop.data,
        leaderboards: leaderboards.data,
        error: errors.length >= 5 ? errors.join(", ") : undefined,
        checkedAt: Date.now()
      });
    } catch (error) {
      setData({ error: error instanceof Error ? error.message : "Status nicht erreichbar", checkedAt: Date.now() });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 30_000);
    return () => window.clearInterval(timer);
  }, [apiBase]);

  const tableOk = useMemo(() => {
    if (!data.web?.tables) return false;
    return Object.values(data.web.tables).every(Boolean);
  }, [data.web]);

  const overall = data.error ? "error" : data.health?.ok && data.web?.mysqlReady && tableOk ? "ok" : "warn";
  const apiOverall = data.web?.overall as ServiceState | undefined;
  const pageOverall = data.error ? "error" : apiOverall ?? overall;
  const headline = pageOverall === "ok" ? "Alle Systeme online" : pageOverall === "warn" ? "Teilweise online" : "Status rot";
  const services = data.web?.services
    ? ["lobby", "factions", "mysql", "redis", "webapi"]
        .map((key) => data.web?.services[key])
        .filter(Boolean) as ServiceStatus[]
    : [];

  return (
    <main className="page status-page">
      <section className={`status-hero ${pageOverall}`}>
        <div>
          <div className="brand">VetroForge</div>
          <h1>{headline}</h1>
          <p className="muted">
            Live-Status fuer Factions, WebAPI, MySQL, Lootdrops und Warzone.
          </p>
        </div>
        <div className="stack">
          <span className="pill">API {apiBase}</span>
          <span className="pill">Refresh 30s</span>
          {data.checkedAt && <span className="pill">Check {new Date(data.checkedAt).toLocaleTimeString("de-DE")}</span>}
        </div>
      </section>

      <section className="card stack">
        <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
          <div>
            <h2>Status Quelle</h2>
            <p className="muted">Falls du lokal testest, hier die WebAPI URL anpassen.</p>
          </div>
          <button className="btn" onClick={load} disabled={loading}>{loading ? "Pruefe..." : "Jetzt pruefen"}</button>
        </div>
        <input className="input" value={apiBase} onChange={(event) => setApiBase(event.target.value.trim())} />
        {data.error && <p className="status-error">Fehler: {data.error}</p>}
      </section>

      <section className="status-grid">
        {services.map((service) => (
          <MiniCard
            key={service.id}
            title={service.label}
            value={service.status === "ok" ? "Online" : service.status === "warn" ? "Achtung" : "Rot"}
            detail={service.detail}
            state={service.status}
          />
        ))}
      </section>

      <section className="status-grid">
        <MiniCard
          title="Minecraft Server"
          value={data.health?.ok ? `${data.health.online}/${data.health.max} online` : "Offline"}
          detail="Antwort von /api/health"
          state={data.health?.ok ? "ok" : "error"}
        />
        <MiniCard
          title="WebAPI"
          value={data.web?.ok ? "Erreichbar" : "Nicht erreichbar"}
          detail={data.web?.apiUrl || "Keine API URL gemeldet"}
          state={data.web?.ok ? "ok" : "error"}
        />
        <MiniCard
          title="MySQL"
          value={data.web?.mysqlReady ? "Verbunden" : "Nicht bereit"}
          detail={data.web?.mysqlReady ? "Web-Tabellen pruefbar" : data.web?.mysqlError || "keine Daten"}
          state={data.web?.mysqlReady ? "ok" : "warn"}
        />
        <MiniCard
          title="Lootdrop"
          value={formatEta(data.lootdrop?.etaSeconds)}
          detail={data.lootdrop?.running ? `Intervall ${data.lootdrop.intervalSeconds}s` : "Timed Lootdrop nicht aktiv"}
          state={data.lootdrop?.running ? "ok" : "warn"}
        />
        <MiniCard
          title="Warzone"
          value={`${data.warzone?.count ?? 0} Spieler`}
          detail={(data.warzone?.players ?? []).slice(0, 4).join(", ") || "Keine Spieler in der Warzone"}
          state={(data.warzone?.count ?? 0) > 0 ? "warn" : "ok"}
        />
        <MiniCard
          title="Top Faction"
          value={data.leaderboards?.factions?.[0]?.name ?? "-"}
          detail={data.leaderboards?.factions?.[0] ? `${data.leaderboards.factions[0].members} Mitglieder` : "Noch keine Daten"}
          state={data.leaderboards?.ok ? "ok" : "warn"}
        />
      </section>

      <section className="card stack">
        <h2>Web Tabellen</h2>
        <div className="row" style={{ flexWrap: "wrap" }}>
          {Object.entries(data.web?.tables ?? {}).map(([name, ok]) => (
            <span key={name} className={`status-pill ${ok ? "ok" : "warn"}`}>
              {ok ? "OK" : "FEHLT"} {name}
            </span>
          ))}
          {!data.web?.tables && <span className="muted">Noch keine Tabellendaten geladen.</span>}
        </div>
      </section>
    </main>
  );
}
