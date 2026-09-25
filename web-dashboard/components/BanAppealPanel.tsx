"use client";

import { useEffect, useState } from "react";
import { apiFetch, type SessionState } from "@/lib/api";

type Ban = {
  id: number;
  reason: string;
  staff: string;
  untilTime: number;
  createdAt: number;
};

type Appeal = {
  id: number;
  status: "open" | "approved" | "rejected";
  message: string;
  createdAt: number;
  reviewedBy?: string;
  reviewedAt?: number;
  reviewNote?: string;
};

type MineResponse = {
  ok: boolean;
  banned: boolean;
  ban: Ban | null;
  appeal: Appeal | null;
};

function formatDate(value?: number) {
  if (!value) return "unbekannt";
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(value);
}

function untilLabel(value: number) {
  if (value < 0) return "permanent";
  return formatDate(value);
}

export function BanAppealPanel({ session }: { session: SessionState }) {
  const [data, setData] = useState<MineResponse | null>(null);
  const [text, setText] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  async function load() {
    try {
      const result = await apiFetch<MineResponse>("/api/appeals/mine", session);
      setData(result);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Status konnte nicht geladen werden");
    }
  }

  useEffect(() => {
    if (session.token) void load();
  }, [session.token]);

  async function submit() {
    if (!text.trim()) return;
    setLoading(true);
    setMessage("");
    try {
      await apiFetch("/api/appeals/submit", session, {
        method: "POST",
        body: JSON.stringify({ message: text })
      });
      setText("");
      setMessage("Einspruch eingereicht. Das Staff-Team prueft ihn so schnell wie moeglich.");
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Einspruch konnte nicht gesendet werden");
    } finally {
      setLoading(false);
    }
  }

  if (!session.token) {
    return (
      <section className="card span-4 stack">
        <h2>Bann-Einspruch</h2>
        <p className="muted">Logge dich links ein, um deinen Bann-Status zu sehen.</p>
      </section>
    );
  }

  if (!data) {
    return (
      <section className="card span-4 stack">
        <h2>Bann-Einspruch</h2>
        <p className="muted">{message || "Lade..."}</p>
      </section>
    );
  }

  const canSubmit = data.banned && (!data.appeal || data.appeal.status === "rejected");

  return (
    <section className="card span-4 stack">
      <h2>Bann-Einspruch</h2>

      {!data.banned && <p className="pill ok">Du bist aktuell nicht gebannt.</p>}

      {data.banned && data.ban && (
        <div className="stack">
          <p className="pill warn">Gebannt</p>
          <p><strong>Grund:</strong> {data.ban.reason || "Kein Grund angegeben"}</p>
          <p className="muted">Von {data.ban.staff || "System"} · bis {untilLabel(data.ban.untilTime)}</p>
        </div>
      )}

      {canSubmit && (
        <div className="stack">
          <textarea
            className="input"
            style={{ minHeight: 100, resize: "vertical" }}
            placeholder="Warum sollte dein Bann aufgehoben werden?"
            value={text}
            onChange={(event) => setText(event.target.value)}
            maxLength={1000}
          />
          <button className="btn" onClick={submit} disabled={loading || !text.trim()}>
            {loading ? "Wird gesendet..." : "Einspruch einreichen"}
          </button>
        </div>
      )}

      {data.appeal && (
        <div className="stack">
          <p>
            Status:{" "}
            {data.appeal.status === "open" && <span className="pill warn">In Bearbeitung</span>}
            {data.appeal.status === "approved" && <span className="pill ok">Angenommen</span>}
            {data.appeal.status === "rejected" && <span className="pill">Abgelehnt</span>}
          </p>
          <p className="muted">Eingereicht: {formatDate(data.appeal.createdAt)}</p>
          <p>{data.appeal.message}</p>
          {data.appeal.status !== "open" && (
            <p className="muted">
              Von {data.appeal.reviewedBy || "Staff"} · {formatDate(data.appeal.reviewedAt)}
              {data.appeal.reviewNote ? ` · ${data.appeal.reviewNote}` : ""}
            </p>
          )}
        </div>
      )}

      {message && <p className="muted">{message}</p>}
    </section>
  );
}
