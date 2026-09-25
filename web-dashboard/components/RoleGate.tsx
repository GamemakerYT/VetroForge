"use client";

import { canAccess, type UserRole } from "@/lib/api";

export function RoleGate({
  current,
  required,
  title,
  children
}: {
  current: UserRole;
  required: UserRole;
  title: string;
  children: React.ReactNode;
}) {
  if (canAccess(current, required)) return <>{children}</>;

  return (
    <section className="card span-12 role-locked">
      <p className="pill">Rolle benötigt: {required}</p>
      <h2>{title}</h2>
      <p className="muted">
        Dieser Bereich ist für deine aktuelle Rolle ausgeblendet. Stelle links die passende Rolle ein und hinterlege bei Staff/Admin den WebAPI-Key.
      </p>
    </section>
  );
}
