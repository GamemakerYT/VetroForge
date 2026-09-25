export const API_BASE = process.env.NEXT_PUBLIC_VETROFORGE_API
  ?? process.env.VF_FACTIONS_API_URL
  ?? "http://45.146.252.240:8500";

export type SessionState = {
  token: string;
  apiKey: string;
  role: UserRole;
};

export type UserRole = "public" | "player" | "staff" | "admin";

export type AuthMe = {
  ok: boolean;
  username: string;
  role: UserRole;
  minecraft?: string;
  uuid?: string;
  linked?: boolean;
};

export const roleRank: Record<UserRole, number> = {
  public: 0,
  player: 1,
  staff: 2,
  admin: 3
};

export function canAccess(current: UserRole, required: UserRole) {
  return roleRank[current] >= roleRank[required];
}

export function persistSession(session: SessionState) {
  if (typeof window === "undefined") return;
  localStorage.setItem("vf_token", session.token);
  localStorage.setItem("vf_api_key", session.apiKey);
  localStorage.setItem("vf_role", session.role);
  document.cookie = `vf_role=${session.role}; path=/; max-age=604800; samesite=lax`;
  if (session.token) document.cookie = `vf_token=${session.token}; path=/; max-age=604800; samesite=lax`;
}

export async function refreshMe(session: SessionState): Promise<SessionState> {
  const me = await apiFetch<AuthMe>("/api/auth/me", session);
  const next = { ...session, role: me.role ?? "public" };
  persistSession(next);
  return next;
}

export async function apiFetch<T>(
  path: string,
  session: SessionState,
  options: RequestInit = {}
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  if (session.token) {
    headers.set("Authorization", `Bearer ${session.token}`);
    headers.set("X-Session-Token", session.token);
  }
  if (session.apiKey) headers.set("X-API-Key", session.apiKey);

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    cache: "no-store"
  });
  const text = await response.text();
  let json: unknown = {};
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { ok: false, err: text };
  }
  if (!response.ok) {
    const err = typeof json === "object" && json && "err" in json ? String((json as { err: unknown }).err) : response.statusText;
    throw new Error(err);
  }
  return json as T;
}

export function money(value: number | undefined) {
  return new Intl.NumberFormat("de-DE", { maximumFractionDigits: 0 }).format(value ?? 0);
}
