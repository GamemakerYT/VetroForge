"use client";

import { useEffect, useState } from "react";
import { apiFetch, type SessionState, type UserRole } from "@/lib/api";

type WebUser = {
  username: string;
  minecraft: string;
  uuid: string;
  role: UserRole;
  store: string;
};

type UsersResponse = {
  ok: boolean;
  users: WebUser[];
  store: string;
};

const roles: UserRole[] = ["player", "staff", "admin"];

export function UserRoleAdmin({ session }: { session: SessionState }) {
  const [users, setUsers] = useState<WebUser[]>([]);
  const [message, setMessage] = useState("Lade Web-User...");

  async function load() {
    try {
      const response = await apiFetch<UsersResponse>("/api/admin/users", session);
      setUsers(response.users ?? []);
      setMessage(response.users?.length ? `Quelle: ${response.store}` : "Noch keine Web-User vorhanden.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "User konnten nicht geladen werden.");
    }
  }

  async function setRole(username: string, role: UserRole) {
    try {
      await apiFetch("/api/admin/users", session, {
        method: "POST",
        body: JSON.stringify({ username, role })
      });
      setUsers((current) => current.map((user) => user.username === username ? { ...user, role } : user));
      setMessage(`${username} ist jetzt ${role}.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Rolle konnte nicht gespeichert werden.");
    }
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <section className="card stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h2>Rollen Admin</h2>
          <p className="muted">Web-User direkt auf Player, Staff oder Admin setzen.</p>
        </div>
        <button className="btn secondary" onClick={load}>Neu laden</button>
      </div>

      <p className="muted">{message}</p>

      <table className="table">
        <thead>
          <tr>
            <th>User</th>
            <th>Minecraft</th>
            <th>Rolle</th>
            <th>Setzen</th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <tr key={user.username}>
              <td>
                <strong>{user.username}</strong>
                <div className="muted">{user.store}</div>
              </td>
              <td>
                {user.minecraft || "-"}
                <div className="muted">{user.uuid || "nicht gelinkt"}</div>
              </td>
              <td><span className={`role-badge role-${user.role}`}>{user.role.toUpperCase()}</span></td>
              <td>
                <div className="row" style={{ flexWrap: "wrap" }}>
                  {roles.map((role) => (
                    <button
                      key={role}
                      className={`btn ${user.role === role ? "" : "secondary"}`}
                      onClick={() => setRole(user.username, role)}
                    >
                      {role}
                    </button>
                  ))}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
