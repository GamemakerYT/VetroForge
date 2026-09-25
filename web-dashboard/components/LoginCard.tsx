"use client";

import { useState } from "react";
import { apiFetch, persistSession, refreshMe, type SessionState } from "@/lib/api";

type AuthResponse = {
  ok: boolean;
  token?: string;
  code?: string;
  command?: string;
  expiresInSeconds?: number;
  role?: SessionState["role"];
};

export function LoginCard({
  session,
  setSession
}: {
  session: SessionState;
  setSession: (session: SessionState) => void;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [resetCode, setResetCode] = useState("");
  const [mode, setMode] = useState<"login" | "register">("login");
  const [message, setMessage] = useState("");

  async function submit() {
    setMessage("");
    try {
      const path = mode === "login" ? "/api/auth/login" : "/api/auth/register";
      const result = await apiFetch<AuthResponse>(path, session, {
        method: "POST",
        body: JSON.stringify({ username, password })
      });
      if (result.token) {
        const next = await refreshMe({ ...session, token: result.token, role: result.role ?? "player" });
        setSession(next);
        setMessage(`Eingeloggt als ${next.role}. Session gespeichert.`);
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Login fehlgeschlagen");
    }
  }

  async function createLinkCode() {
    setMessage("");
    try {
      const result = await apiFetch<AuthResponse>("/api/auth/link-code", session, { method: "POST" });
      setMessage(result.command ?? `Code: ${result.code}`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Code fehlgeschlagen");
    }
  }

  async function requestReset() {
    setMessage("");
    try {
      const result = await apiFetch<AuthResponse>("/api/auth/reset/request", session, {
        method: "POST",
        body: JSON.stringify({ username })
      });
      if (result.code) setResetCode(result.code);
      setMessage(result.code
        ? `Reset-Code: ${result.code} (${result.expiresInSeconds ?? 900}s gueltig)`
        : "Falls der Account existiert, wurde ein Reset-Code vorbereitet.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Reset-Code fehlgeschlagen");
    }
  }

  async function confirmReset() {
    setMessage("");
    try {
      const result = await apiFetch<AuthResponse>("/api/auth/reset/confirm", session, {
        method: "POST",
        body: JSON.stringify({ username, code: resetCode, password })
      });
      if (result.token) {
        const next = await refreshMe({ ...session, token: result.token, role: result.role ?? "player" });
        setSession(next);
        setMessage(`Passwort gesetzt. Eingeloggt als ${next.role}.`);
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Passwort-Reset fehlgeschlagen");
    }
  }

  return (
    <section className="card span-4 stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Login</h2>
        <button className="btn ghost" onClick={() => setMode(mode === "login" ? "register" : "login")}>
          {mode === "login" ? "Registrieren" : "Einloggen"}
        </button>
      </div>

      <input className="input" placeholder="Benutzername" value={username} onChange={(event) => setUsername(event.target.value)} />
      <input
        className="input"
        placeholder="Passwort"
        type="password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
      />
      <button className="btn" onClick={submit}>{mode === "login" ? "Einloggen" : "Account erstellen"}</button>

      <div className="role-identity">
        <span className={`role-badge role-${session.role}`}>{session.role.toUpperCase()}</span>
        <span className="muted">Rolle kommt jetzt vom Server über /api/auth/me.</span>
      </div>

      <label className="stack">
        <span className="muted">Staff/Admin API Key für AH/Shop</span>
        <input
          className="input"
          placeholder="webapi.apiKey"
          value={session.apiKey}
          onChange={(event) => {
            const next = { ...session, apiKey: event.target.value };
            persistSession(next);
            setSession(next);
          }}
        />
      </label>

      <button
        className="btn ghost"
        onClick={async () => {
          const next = await refreshMe(session);
          setSession(next);
          setMessage(`Rolle aktualisiert: ${next.role}`);
        }}
      >
        Rolle aktualisieren
      </button>

      <button className="btn secondary" onClick={createLinkCode} disabled={!session.token}>
        Minecraft-Link-Code erstellen
      </button>
      <div className="stack">
        <button className="btn ghost" onClick={requestReset} disabled={!username}>
          Passwort-Reset-Code
        </button>
        <input
          className="input"
          placeholder="Reset-Code"
          value={resetCode}
          onChange={(event) => setResetCode(event.target.value)}
        />
        <button className="btn secondary" onClick={confirmReset} disabled={!username || !resetCode || password.length < 6}>
          Neues Passwort setzen
        </button>
      </div>
      <p className="muted">{message || "Nach dem Login: Code erzeugen und ingame /linkweb <code> nutzen."}</p>
    </section>
  );
}
