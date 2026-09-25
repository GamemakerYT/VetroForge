"use client";

import { useEffect, useMemo, useState } from "react";
import { apiFetch, type SessionState } from "@/lib/api";
import { MaterialIcon } from "@/components/MaterialIcon";

type RaidShopOffer = {
  id: string;
  category: string;
  material: string;
  amount: number;
  rewardType: string;
  rewardValue: string;
  custom: boolean;
  price: number;
  enabled: boolean;
};

type RaidShopCategory = {
  id: string;
  items: RaidShopOffer[];
};

type RaidShopResponse = {
  ok: boolean;
  mysql: boolean;
  source: string;
  categories: RaidShopCategory[];
};

type RaidShopAuditEntry = {
  time: number;
  uuid: string;
  player: string;
  offer: string;
  price: number;
  balanceAfter: number;
};

type RaidShopAuditResponse = {
  ok: boolean;
  items: RaidShopAuditEntry[];
};

const categories = ["ATTACK", "DEFENSE", "MOBILITY", "UTILITY"];
const rewardTypes = ["ITEM", "POTION", "BREACH_CHARGE", "BUNDLE", "ENERGY", "FACTION_ELO", "COMMAND", "KIT"];

export function RaidShopEditor({ session }: { session: SessionState }) {
  const [data, setData] = useState<RaidShopResponse>({ ok: false, mysql: false, source: "", categories: [] });
  const [audit, setAudit] = useState<RaidShopAuditEntry[]>([]);
  const [category, setCategory] = useState("ATTACK");
  const [message, setMessage] = useState("");
  const [draft, setDraft] = useState({
    id: "",
    material: "TNT",
    amount: 1,
    price: 3,
    category: "UTILITY",
    rewardType: "ITEM",
    rewardValue: ""
  });

  async function load() {
    setMessage("");
    try {
      const [editor, purchases] = await Promise.all([
        apiFetch<RaidShopResponse>("/api/admin/raidshop/editor", session),
        apiFetch<RaidShopAuditResponse>("/api/admin/raidshop/audit?limit=30", session)
      ]);
      setData(editor);
      setAudit(purchases.items ?? []);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "RaidShop konnte nicht geladen werden");
    }
  }

  useEffect(() => {
    if (session.apiKey) void load();
  }, [session.apiKey]);

  const items = useMemo(() => {
    return data.categories.find((entry) => entry.id === category)?.items ?? [];
  }, [data, category]);

  async function setPrice(item: RaidShopOffer, delta: number) {
    await apiFetch("/api/admin/raidshop/action", session, {
      method: "POST",
      body: JSON.stringify({ action: "setPrice", id: item.id, price: Math.max(1, item.price + delta), actor: "web-dashboard" })
    });
    await load();
  }

  async function setEnabled(item: RaidShopOffer, enabled: boolean) {
    await apiFetch("/api/admin/raidshop/action", session, {
      method: "POST",
      body: JSON.stringify({ action: "setEnabled", id: item.id, enabled, actor: "web-dashboard" })
    });
    await load();
  }

  async function createOffer() {
    if (!draft.id.trim()) {
      setMessage("Bitte eine Offer-ID eingeben.");
      return;
    }
    await apiFetch("/api/admin/raidshop/action", session, {
      method: "POST",
      body: JSON.stringify({ action: "createOffer", ...draft, actor: "web-dashboard" })
    });
    setDraft({ id: "", material: "TNT", amount: 1, price: 3, category: draft.category, rewardType: draft.rewardType, rewardValue: "" });
    await load();
  }

  async function deleteOffer(item: RaidShopOffer) {
    await apiFetch("/api/admin/raidshop/action", session, {
      method: "POST",
      body: JSON.stringify({ action: "deleteOffer", id: item.id, actor: "web-dashboard" })
    });
    await load();
  }

  return (
    <section className="card span-12 stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>RaidShop Editor</h2>
          <p className="muted">Raid-Angebote, Shard-Preise und Aktiv-Status direkt aus dem Webpanel bearbeiten.</p>
        </div>
        <div className="row">
          <span className={`pill ${data.mysql ? "ok" : ""}`}>MySQL {data.mysql ? "Mirror aktiv" : "Config aktiv"}</span>
          <button className="btn" onClick={load}>Neu laden</button>
        </div>
      </div>
      {message && <p className="muted">{message}</p>}

      <div className="row">
        {categories.map((cat) => (
          <button className={`btn ${category === cat ? "" : "secondary"}`} key={cat} onClick={() => setCategory(cat)}>
            {cat}
          </button>
        ))}
      </div>

      <div className="shop-board">
        <div className="stack">
          <div className="category active">
            <strong>{category}</strong>
            <div className="muted">{items.length} Angebote</div>
          </div>
          <div className="category">
            <strong>Quelle</strong>
            <div className="muted">{data.source || "Config"}</div>
          </div>
        </div>

        <div className="stack">
          {items.map((item) => (
            <div className={`item-chip ${item.enabled ? "" : "disabled"}`} key={`${item.category}-${item.id}`}>
              <span className="row">
                <MaterialIcon material={item.material} />
                <span>
                  <strong>{item.id}</strong>
                  <span className="muted"> · {item.material} x{item.amount}</span>
                  <span className="muted"> · {item.rewardType}{item.rewardValue ? `: ${item.rewardValue}` : ""}</span>
                </span>
              </span>
              <span className="row">
                <button className="tiny-action" onClick={() => setPrice(item, -10)}>-10</button>
                <button className="tiny-action" onClick={() => setPrice(item, -1)}>-1</button>
                <span className="pill">{item.price} Shards</span>
                <button className="tiny-action" onClick={() => setPrice(item, 1)}>+1</button>
                <button className="tiny-action" onClick={() => setPrice(item, 10)}>+10</button>
                <button className="tiny-action" onClick={() => setEnabled(item, !item.enabled)}>
                  {item.enabled ? "Deaktivieren" : "Aktivieren"}
                </button>
                {item.custom && (
                  <button className="tiny-action danger" onClick={() => deleteOffer(item)}>
                    Löschen
                  </button>
                )}
              </span>
            </div>
          ))}
        </div>
      </div>

      <div className="card stack">
        <h2>Neues Angebot</h2>
        <div className="row">
          <input className="input" placeholder="offer_id" value={draft.id} onChange={(event) => setDraft({ ...draft, id: event.target.value })} />
          <select className="input" value={draft.category} onChange={(event) => setDraft({ ...draft, category: event.target.value })}>
            {categories.map((cat) => <option key={cat} value={cat}>{cat}</option>)}
          </select>
          <input className="input" placeholder="Material" value={draft.material} onChange={(event) => setDraft({ ...draft, material: event.target.value.toUpperCase() })} />
          <input className="input" type="number" min={1} value={draft.amount} onChange={(event) => setDraft({ ...draft, amount: Number(event.target.value) })} />
          <input className="input" type="number" min={1} value={draft.price} onChange={(event) => setDraft({ ...draft, price: Number(event.target.value) })} />
          <select className="input" value={draft.rewardType} onChange={(event) => setDraft({ ...draft, rewardType: event.target.value })}>
            {rewardTypes.map((type) => <option key={type} value={type}>{type}</option>)}
          </select>
          <input
            className="input"
            placeholder="Reward Value / Command"
            value={draft.rewardValue}
            onChange={(event) => setDraft({ ...draft, rewardValue: event.target.value })}
          />
          <button className="btn" onClick={() => void createOffer()}>Erstellen</button>
        </div>
      </div>

      <div className="card stack">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h2>RaidShop Audit</h2>
          <span className="pill">{audit.length} letzte Käufe</span>
        </div>
        <div className="stack">
          {audit.length === 0 && <p className="muted">Noch keine RaidShop-Käufe im Audit.</p>}
          {audit.map((entry) => (
            <div className="item-chip" key={`${entry.time}-${entry.uuid}-${entry.offer}`}>
              <span>
                <strong>{entry.player}</strong>
                <span className="muted"> kaufte {entry.offer}</span>
              </span>
              <span className="row">
                <span className="pill">{entry.price} Shards</span>
                <span className="muted">Bank danach {entry.balanceAfter}</span>
                <span className="muted">{new Date(entry.time).toLocaleString("de-DE")}</span>
              </span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
