"use client";

import { useState } from "react";
import mediaStatus from "@/data/tebex-media-status.json";

const ranks = [
  {
    id: "ember",
    name: "Ember",
    packageId: "7499369",
    price: "9,99 EUR",
    command: "vperms user {username} setrank ember",
    image: "/assets/tebex/ranks/ember.png",
    tebexUrl: "https://creator.tebex.io/packages/7499369"
  },
  {
    id: "vanguard",
    name: "Vanguard",
    packageId: "7499375",
    price: "24,99 EUR",
    command: "vperms user {username} setrank vanguard",
    image: "/assets/tebex/ranks/vanguard.png",
    tebexUrl: "https://creator.tebex.io/packages/7499375"
  },
  {
    id: "obsidian",
    name: "Obsidian",
    packageId: "7499376",
    price: "49,99 EUR",
    command: "vperms user {username} setrank obsidian",
    image: "/assets/tebex/ranks/obsidian.png",
    tebexUrl: "https://creator.tebex.io/packages/7499376"
  },
  {
    id: "apex",
    name: "Apex",
    packageId: "7499377",
    price: "79,99 EUR",
    command: "vperms user {username} setrank apex",
    image: "/assets/tebex/ranks/apex.png",
    tebexUrl: "https://creator.tebex.io/packages/7499377"
  },
  {
    id: "vetroplus",
    name: "Vetro+",
    packageId: "7499378",
    price: "119,99 EUR",
    command: "vperms user {username} setrank vetroplus",
    image: "/assets/tebex/ranks/vetroplus.png",
    tebexUrl: "https://creator.tebex.io/packages/7499378"
  }
];

export function TebexAdminPanel() {
  const [copied, setCopied] = useState("");

  async function copy(value: string, target: string) {
    await navigator.clipboard.writeText(value);
    setCopied(target);
    window.setTimeout(() => setCopied(""), 1600);
  }

  return (
    <section className="card span-12 stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>Tebex Ranks</h2>
          <p className="muted">
            Package-IDs, Commands, Preise und Bildstatus. Kategorie `Ranks` ist cumulative: Ember -&gt; Vanguard -&gt; Obsidian -&gt; Apex -&gt; Vetro+.
          </p>
          <div className="row" style={{ flexWrap: "wrap", marginTop: 10 }}>
            <span className={mediaStatus.category.cumulative ? "pill ok" : "pill"}>Upgrade-Pfad aktiv</span>
            <span className={mediaStatus.category.uploaded ? "pill ok" : "pill warn"}>Kategorie-Bild {mediaStatus.category.uploaded ? "hochgeladen" : "offen"}</span>
            <span className="pill">Status {mediaStatus.updatedAt}</span>
          </div>
        </div>
        <a className="btn secondary text-link" href="https://creator.tebex.io/categories/3354292" target="_blank" rel="noreferrer">
          Kategorie öffnen
        </a>
      </div>

      <div className="tebex-grid">
        {ranks.map((rank) => (
          <article className="tebex-card" key={rank.id}>
            <img src={rank.image} alt={`${rank.name} Rank`} />
            <div className="tebex-card-body">
              <div className="row" style={{ justifyContent: "space-between" }}>
                <strong>{rank.name}</strong>
                <span className={mediaStatus.packages[rank.id as keyof typeof mediaStatus.packages]?.uploaded ? "pill ok" : "pill warn"}>
                  Tebex Media {mediaStatus.packages[rank.id as keyof typeof mediaStatus.packages]?.uploaded ? "hochgeladen" : "offen"}
                </span>
              </div>
              <span className="muted">Package #{rank.packageId}</span>
              <span className="role-badge role-admin">{rank.price}</span>
              <code className="command-box">{rank.command}</code>
              <div className="row" style={{ flexWrap: "wrap" }}>
                <button className="tiny-action" onClick={() => void copy(rank.command, `${rank.id}:command`)}>
                  {copied === `${rank.id}:command` ? "Command kopiert" : "Command kopieren"}
                </button>
                <button className="tiny-action" onClick={() => void copy(rank.tebexUrl, `${rank.id}:link`)}>
                  {copied === `${rank.id}:link` ? "Link kopiert" : "Paket-Link kopieren"}
                </button>
                <button className="tiny-action" onClick={() => void copy(rank.packageId, `${rank.id}:id`)}>
                  {copied === `${rank.id}:id` ? "ID kopiert" : "ID kopieren"}
                </button>
              </div>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <span className="pill ok">Bild im Repo</span>
                <a className="btn ghost text-link" href={rank.tebexUrl} target="_blank" rel="noreferrer">
                  Paket öffnen
                </a>
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
