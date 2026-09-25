"use client";

import { useMemo, useState } from "react";
import data from "@/data/abnahme-status.json";

type Stage = "geplant" | "implementiert" | "getestet" | "gebaut" | "geladen" | "ingame_abgenommen";

type Item = {
  id: string;
  bereich: string;
  repo: string;
  verantwortlich: string;
  dateien: string[];
  geplant: string;
  implementiert: string;
  getestet: string;
  gebaut: string;
  geladen: string;
  ingame_abgenommen: string;
  risiken: string;
  aktualisiert: string;
};

const stageLabels: Record<Stage, string> = {
  geplant: "Geplant",
  implementiert: "Implementiert",
  getestet: "Getestet",
  gebaut: "Gebaut",
  geladen: "Geladen",
  ingame_abgenommen: "Ingame-Abnahme"
};

const stages: Stage[] = ["geplant", "implementiert", "getestet", "gebaut", "geladen", "ingame_abgenommen"];

function pillClass(value: string) {
  if (value === "erledigt") return "pill ok";
  if (value === "teilweise") return "pill warn";
  return "pill";
}

function StagePill({ value }: { value: string }) {
  if (value === "-") return <span className="muted">–</span>;
  return <span className={pillClass(value)}>{value}</span>;
}

export function AbnahmeDashboard() {
  const items = data.items as Item[];
  const [filter, setFilter] = useState<"alle" | "offen">("alle");

  const visible = useMemo(
    () => (filter === "alle" ? items : items.filter((i) => i.ingame_abgenommen === "offen")),
    [items, filter]
  );

  const ingameDone = items.filter((i) => i.ingame_abgenommen === "erledigt").length;
  const ingameOpen = items.filter((i) => i.ingame_abgenommen === "offen").length;

  return (
    <section className="card span-12 stack">
      <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
        <div>
          <h2>Abnahme-Dashboard</h2>
          <p className="muted">
            Gemeinsamer Status zwischen Claude und Codex: geplant → implementiert → getestet → gebaut → geladen → ingame
            abgenommen. Quelle: <code>data/abnahme-status.json</code>, Stand {data.updated}.
          </p>
          <div className="row" style={{ flexWrap: "wrap", marginTop: 10 }}>
            <span className="pill ok">{ingameDone} ingame abgenommen</span>
            <span className="pill">{ingameOpen} noch offen</span>
            <span className="pill">{items.length} Bereiche insgesamt</span>
          </div>
        </div>
        <div className="row" style={{ gap: 8 }}>
          <button className={filter === "alle" ? "btn" : "btn secondary"} onClick={() => setFilter("alle")}>
            Alle
          </button>
          <button className={filter === "offen" ? "btn" : "btn secondary"} onClick={() => setFilter("offen")}>
            Nur ingame offen
          </button>
        </div>
      </div>

      <div style={{ overflowX: "auto" }}>
        <table className="table">
          <thead>
            <tr>
              <th>Bereich</th>
              <th>Repo / Verantwortlich</th>
              {stages.map((s) => (
                <th key={s}>{stageLabels[s]}</th>
              ))}
              <th>Offene Risiken</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((item) => (
              <tr key={item.id}>
                <td style={{ minWidth: 220 }}>
                  <strong>{item.bereich}</strong>
                  {item.dateien.length > 0 && item.dateien[0] !== "-" && (
                    <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
                      {item.dateien.join(", ")}
                    </div>
                  )}
                </td>
                <td style={{ minWidth: 160 }}>
                  <div>{item.repo}</div>
                  <div className="muted" style={{ fontSize: 12 }}>{item.verantwortlich}</div>
                </td>
                {stages.map((s) => (
                  <td key={s}>
                    <StagePill value={item[s]} />
                  </td>
                ))}
                <td style={{ minWidth: 240 }} className="muted">
                  {item.risiken}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
