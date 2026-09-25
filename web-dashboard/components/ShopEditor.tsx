"use client";

import { useEffect, useMemo, useState } from "react";
import { apiFetch, money, type SessionState } from "@/lib/api";
import { MaterialIcon } from "@/components/MaterialIcon";

type ShopItem = {
  id: string;
  name: string;
  material: string;
  amount: number;
  buy: number;
  sell: number;
};

type ShopCategory = {
  id: string;
  name: string;
  icon: string;
  items: ShopItem[];
};

type ShopResponse = {
  ok: boolean;
  categories: ShopCategory[];
  history: Array<{
    id: string;
    actor: string;
    action: string;
    item: string;
    oldBuy: number;
    oldSell: number;
    newBuy: number;
    newSell: number;
    created: number;
  }>;
};

export function ShopEditor({ session }: { session: SessionState }) {
  const [data, setData] = useState<ShopResponse>({ ok: false, categories: [], history: [] });
  const [selected, setSelected] = useState<ShopItem | null>(null);
  const [category, setCategory] = useState("all");
  const [search, setSearch] = useState("");
  const [message, setMessage] = useState("");

  async function load() {
    setMessage("");
    try {
      setData(await apiFetch<ShopResponse>("/api/admin/shop/editor", session));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Shop konnte nicht geladen werden");
    }
  }

  useEffect(() => {
    if (session.apiKey) void load();
  }, [session.apiKey]);

  const allItems = useMemo(() => {
    const items = data.categories.flatMap((cat) => cat.items.map((item) => ({ ...item, category: cat.id })));
    const lower = search.toLowerCase();
    return items.filter((item) => {
      if (category !== "all" && item.category !== category) return false;
      return `${item.id} ${item.name} ${item.material}`.toLowerCase().includes(lower);
    });
  }, [data, category, search]);

  async function setPrice(item: ShopItem, buy: number, sell: number) {
    await apiFetch("/api/admin/shop/action", session, {
      method: "POST",
      body: JSON.stringify({ action: "setPrice", id: item.id, buy, sell, actor: "web-dashboard" })
    });
    await load();
  }

  async function moveItem(id: string, target: string) {
    await apiFetch("/api/admin/shop/action", session, {
      method: "POST",
      body: JSON.stringify({ action: "moveItem", id, category: target, actor: "web-dashboard" })
    });
    await load();
  }

  async function bulk(action: "bulkPercent" | "bulkFlat", value: number) {
    const body = action === "bulkPercent"
      ? { action, percent: value, category, actor: "web-dashboard" }
      : { action, delta: value, category, actor: "web-dashboard" };
    await apiFetch("/api/admin/shop/action", session, { method: "POST", body: JSON.stringify(body) });
    await load();
  }

  return (
    <section className="card span-12 stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>Shop Webeditor</h2>
          <p className="muted">Drag-and-drop Kategorien, Preise und Bulk-Änderungen.</p>
        </div>
        <button className="btn" onClick={load}>Neu laden</button>
      </div>
      {message && <p className="muted">{message}</p>}

      <div className="row">
        <select className="input" value={category} onChange={(event) => setCategory(event.target.value)}>
          <option value="all">Alle Kategorien</option>
          {data.categories.map((cat) => <option key={cat.id} value={cat.id}>{cat.name.replaceAll("&", "")}</option>)}
        </select>
        <input className="input" placeholder="Item suchen" value={search} onChange={(event) => setSearch(event.target.value)} />
      </div>

      <div className="row">
        <button className="btn secondary" onClick={() => bulk("bulkPercent", 0.1)}>+10%</button>
        <button className="btn secondary" onClick={() => bulk("bulkPercent", -0.1)}>-10%</button>
        <button className="btn secondary" onClick={() => bulk("bulkFlat", 100)}>+100</button>
        <button className="btn secondary" onClick={() => bulk("bulkFlat", -100)}>-100</button>
      </div>

      <div className="shop-board">
        <div className="stack">
          {data.categories.map((cat) => (
            <div
              className={`category ${category === cat.id ? "active" : ""}`}
              key={cat.id}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                const id = event.dataTransfer.getData("text/plain");
                if (id) void moveItem(id, cat.id);
              }}
            >
              <strong>{cat.name.replaceAll("&", "")}</strong>
              <div className="muted">{cat.items.length} Items</div>
            </div>
          ))}
        </div>

        <div className="stack">
          {allItems.map((item) => (
            <div
              className="item-chip"
              key={item.id}
              draggable
              onDragStart={(event) => event.dataTransfer.setData("text/plain", item.id)}
              onClick={() => setSelected(item)}
            >
              <span className="row">
                <MaterialIcon material={item.material} />
                <span>
                  <strong>{item.name.replaceAll("&", "")}</strong>
                  <span className="muted"> · {item.material} · {item.id}</span>
                </span>
              </span>
              <span className="muted">Buy {money(item.buy)} / Sell {money(item.sell)}</span>
            </div>
          ))}
        </div>
      </div>

      {selected && (
        <div className="card stack">
          <div className="row">
            <MaterialIcon material={selected.material} size="lg" />
            <h2>{selected.name.replaceAll("&", "")}</h2>
          </div>
          <div className="row">
            <button className="btn secondary" onClick={() => setPrice(selected, selected.buy + 10, selected.sell)}>Buy +10</button>
            <button className="btn secondary" onClick={() => setPrice(selected, selected.buy - 10, selected.sell)}>Buy -10</button>
            <button className="btn secondary" onClick={() => setPrice(selected, selected.buy, selected.sell + 10)}>Sell +10</button>
            <button className="btn secondary" onClick={() => setPrice(selected, selected.buy, selected.sell - 10)}>Sell -10</button>
          </div>
        </div>
      )}

      <div>
        <h2>Preis-History</h2>
        <div className="row" style={{ flexWrap: "wrap" }}>
          {data.history.slice(0, 12).map((entry) => (
            <span className="pill" key={entry.id}>
              {entry.action} {entry.item || entry.id}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}
