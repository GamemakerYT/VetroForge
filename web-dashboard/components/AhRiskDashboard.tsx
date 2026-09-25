"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { apiFetch, money, type SessionState } from "@/lib/api";

type RiskItem = {
  id: string;
  seller: string;
  sellerName: string;
  item: string;
  price: number;
  risk: number;
  quarantined?: boolean;
  reasons: string[];
};

type RiskResponse = {
  ok: boolean;
  items: RiskItem[];
};

function riskClass(risk: number) {
  if (risk >= 90) return "critical";
  if (risk >= 70) return "high";
  if (risk >= 40) return "mid";
  return "low";
}

export function AhRiskDashboard({ session }: { session: SessionState }) {
  const [items, setItems] = useState<RiskItem[]>([]);
  const [query, setQuery] = useState("");
  const [onlyQuarantine, setOnlyQuarantine] = useState(false);
  const [message, setMessage] = useState("");

  async function load() {
    setMessage("");
    try {
      const path = onlyQuarantine ? "/api/admin/auction/quarantine?limit=100" : "/api/admin/auction/risk?limit=100";
      const result = await apiFetch<RiskResponse>(path, session);
      setItems(result.items ?? []);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "AH konnte nicht geladen werden");
    }
  }

  useEffect(() => {
    if (session.apiKey) void load();
  }, [session.apiKey, onlyQuarantine]);

  async function action(actionName: "quarantine" | "unquarantine" | "delete", id: string) {
    await apiFetch("/api/admin/auction/action", session, {
      method: "POST",
      body: JSON.stringify({ action: actionName, id, actor: "web-dashboard" })
    });
    await load();
  }

  const filtered = useMemo(() => {
    const lower = query.toLowerCase();
    return items.filter((item) => `${item.id} ${item.sellerName} ${item.item}`.toLowerCase().includes(lower));
  }, [items, query]);

  return (
    <section className="card span-8 stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>AH Risk Dashboard</h2>
          <p className="muted">Verdächtige Auktionen, Quarantäne und schnelle Staff-Aktionen.</p>
        </div>
        <button className="btn secondary" onClick={() => setOnlyQuarantine(!onlyQuarantine)}>
          {onlyQuarantine ? "Risk anzeigen" : "Quarantäne Queue"}
        </button>
      </div>
      <div className="row">
        <input className="input" placeholder="Filtern nach Seller, Item, ID" value={query} onChange={(event) => setQuery(event.target.value)} />
        <button className="btn" onClick={load}>Neu laden</button>
      </div>
      {message && <p className="muted">{message}</p>}
      <div style={{ overflowX: "auto" }}>
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Item</th>
              <th>Seller</th>
              <th>Preis</th>
              <th>Risk</th>
              <th>Aktionen</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => (
              <tr key={item.id}>
                <td>#{item.id}</td>
                <td>
                  <strong>{item.item}</strong>
                  <div className="muted">{item.reasons.join(" • ")}</div>
                </td>
                <td>
                  <Link className="text-link" href={`/auction/seller/${item.seller}`}>
                    {item.sellerName || item.seller.slice(0, 8)}
                  </Link>
                </td>
                <td>{money(item.price)}</td>
                <td className={`risk ${riskClass(item.risk)}`}>{item.risk}/100</td>
                <td className="row">
                  <button className="btn secondary" onClick={() => action(item.quarantined ? "unquarantine" : "quarantine", item.id)}>
                    {item.quarantined ? "Freigeben" : "Freeze"}
                  </button>
                  <button className="btn ghost" onClick={() => action("delete", item.id)}>Löschen</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
