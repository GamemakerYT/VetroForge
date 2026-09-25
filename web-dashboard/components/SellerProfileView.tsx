"use client";

import { useEffect, useState } from "react";
import { apiFetch, money } from "@/lib/api";
import { MaterialIcon } from "@/components/MaterialIcon";

type SellerProfile = {
  ok: boolean;
  seller: string;
  sellerName: string;
  activeListings: number;
  suspiciousListings: number;
  averageRisk: number;
  deletedCount: number;
  listings: Array<{
    id: string;
    item: string;
    material?: string;
    price: number;
    risk: number;
    quarantined: boolean;
  }>;
};

function riskClass(risk: number) {
  if (risk >= 90) return "critical";
  if (risk >= 70) return "high";
  if (risk >= 40) return "mid";
  return "low";
}

export function SellerProfileView({ uuid }: { uuid: string }) {
  const [apiKey, setApiKey] = useState("");
  const [profile, setProfile] = useState<SellerProfile | null>(null);
  const [message, setMessage] = useState("Admin API-Key benötigt.");

  async function load(nextKey = apiKey) {
    setMessage("");
    try {
      const result = await apiFetch<SellerProfile>(`/api/admin/auction/seller?seller=${encodeURIComponent(uuid)}`, {
        token: "",
        apiKey: nextKey,
        role: "admin"
      });
      setProfile(result);
      localStorage.setItem("vf_api_key", nextKey);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Seller konnte nicht geladen werden");
    }
  }

  async function action(actionName: "quarantine" | "unquarantine" | "delete", id: string) {
    setMessage("");
    try {
      await apiFetch("/api/admin/auction/action", {
        token: "",
        apiKey,
        role: "admin"
      }, {
        method: "POST",
        body: JSON.stringify({ action: actionName, id, actor: "web-seller-profile" })
      });
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Aktion fehlgeschlagen");
    }
  }

  useEffect(() => {
    const stored = localStorage.getItem("vf_api_key") ?? "";
    setApiKey(stored);
    if (stored) void load(stored);
  }, [uuid]);

  return (
    <main className="page public-page">
      <section className="public-hero card">
        <p className="pill">AH Seller Profil</p>
        <h1>{profile?.sellerName || uuid.slice(0, 8)}</h1>
        <div className="row">
          <input className="input" placeholder="Admin API Key" value={apiKey} onChange={(event) => setApiKey(event.target.value)} />
          <button className="btn" onClick={() => load()}>Laden</button>
        </div>
        {message && <p className="muted">{message}</p>}
        {profile && (
          <div className="row public-stats">
            <span className="pill">Aktiv {profile.activeListings}</span>
            <span className="pill">Suspicious {profile.suspiciousListings}</span>
            <span className={`pill risk ${riskClass(profile.averageRisk)}`}>Risk {profile.averageRisk}/100</span>
            <span className="pill">Gelöscht {profile.deletedCount}</span>
          </div>
        )}
      </section>

      {profile && (
        <section className="card">
          <h2>Aktive Auktionen</h2>
          <div className="seller-list">
            {profile.listings.map((listing) => (
              <article className="seller-listing" key={listing.id}>
                <MaterialIcon material={listing.material || listing.item} size="lg" />
                <div>
                  <strong>#{listing.id} {listing.item}</strong>
                  <p className="muted">Preis {money(listing.price)} Coins</p>
                </div>
                <span className={`risk ${riskClass(listing.risk)}`}>{listing.risk}/100</span>
                <div className="row seller-actions">
                  <button className="btn secondary" onClick={() => action(listing.quarantined ? "unquarantine" : "quarantine", listing.id)}>
                    {listing.quarantined ? "Freigeben" : "Quarantäne"}
                  </button>
                  <button className="btn ghost" onClick={() => action("delete", listing.id)}>Löschen</button>
                </div>
              </article>
            ))}
          </div>
        </section>
      )}
    </main>
  );
}
