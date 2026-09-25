# Abnahme-Status VetroForge

Gemeinsames Release-/Abnahme-Dashboard zwischen Claude und Codex, wie in
`CODEX-UEBERGABE-2026-09-24.md` (Abschnitt 28) und unabhaengig vom Nutzer
vorgeschlagen. Sechs Stufen pro Feature-Bereich: **geplant -> implementiert ->
getestet -> gebaut -> geladen -> ingame abgenommen**.

Maschinenlesbare Quelle: [`web-dashboard/data/abnahme-status.json`](../../web-dashboard/data/abnahme-status.json).
Live-Ansicht im Web-Dashboard: `/admin/abnahme` (Admin-Rolle erforderlich).

Stand: 2026-09-24. Diese Tabelle ist eine Momentaufnahme, kein automatischer
Sync -- bei neuen Aenderungen bitte JSON **und** diese Tabelle aktualisieren.

| Bereich | Repo | Verantwortlich | Geplant | Impl. | Getestet | Gebaut | Geladen | Ingame | Risiken |
|---|---|---|---|---|---|---|---|---|---|
| VPN-Exempt-Liste (`/vpnexempt`) | VetroCore | Claude | ok | ok | offen | ok | ok | offen | Kein echter Test mit /vpnexempt add/remove + VPN. |
| Report + Threat-Score-Verknuepfung | VetroCore | Claude | ok | ok | offen | ok | ok | offen | Threat-Score im Staff-Alert noch nicht an echtem Fall gesehen. |
| Ban-Appeal ueber Web | VetroForgeFinal + web-dashboard | Claude | ok | ok | teilweise | ok | ok | offen | Kein voller Appeal-Durchlauf live getestet. |
| `[item]` Chat-Token | VetroForgeFinal | Claude | ok | ok | offen | ok | ok | offen | Hover-Tooltip fuer echtes Custom-Item noch nicht gesehen. |
| Doppelte Ban/Mute-Eintraege (Fix) | VetroCore | Claude | ok | ok | teilweise | ok | ok | teilweise | Kein systematischer Vorher/Nachher-Test mit mehreren Bans. |
| VetroCore Boot-Fehler (Fix) | VetroCore | Claude | ok | ok | ok | ok | ok | ok | Keine offenen Punkte. |
| Faction-Welt-Wiederverwendungs-Exploit (Fix) | VetroForgeFinal | Claude | ok | ok | offen | ok | ok | offen | **Kritisch:** disband+recreate-Test noch offen. |
| Faction-Welt-Erstellung UX | VetroForgeFinal | Claude | ok | ok | offen | ok | ok | offen | Neues Loading-Design/Chat-Message noch nicht ingame gesehen. |
| Maintenance UX/Bugfixes | VetroCore + VetroForgeFinal | Claude | ok | ok | offen | ok | ok | offen | Unbegrenzte-Dauer + Systemzusammenlegung nicht live durchgespielt. |
| 31 wiederhergestellte Features (Recovery) | VetroForgeFinal | Claude (Quelle: Codex) | - | ok | teilweise | ok | ok | offen | Nur Compile-Check, keine Einzelfunktion ingame getestet. Groesster offener Block. |
| VetroPerms Deploy | VetroPerms | Claude | ok | - | offen | ok | ok | offen | Rechte/Ranks nicht einzeln nachgeprueft. |
| Next.js Sicherheitspatch | web-dashboard | Claude | ok | ok | ok | ok | ok | - | npm audit clean, laeuft stabil. |
| **SICHERHEIT:** Geleakter OpenAI-Key (`aikey`) | VetroForgeFinal + VetroForgeNET | Claude (Fund) / User (Rotation) | - | offen | - | - | - | - | **Key noch nicht rotiert.** Danach Datei entfernen + Historie bereinigen. |
| VetroMapBuilder Spawn-Redesign | Documents/VetroForge/map-builder | Codex | ok | ok | teilweise | ok | offen | offen | Live laeuft v3.0.0, neuester lokaler Preview ist 3.2.2 -- nicht deployt. |
| VetroBuild Warzone-Bauplan (2400x3000) | Desktop/VetroBuild | Codex/User | ok | offen | - | ok | ok | offen | `/vbuild status` zeigt keinen aktiven/abgeschlossenen Auftrag -- unklar ob je gebaut. Vermutlich Ursache fuer "Warzone sieht nicht mehr so geil aus". |
| `/errors` Fehleruebersicht | Documents/VetroForge (nicht integriert) | Codex | ok | ok | teilweise | ok | offen | offen | Von Codex selbst als nicht hochgeladen/live geprueft markiert. |
| Research/Vault/Trade/Auction/AURON-Anticheat | Documents/VetroForge/factions-repair (nicht integriert) | Codex | ok | ok | offen | offen | offen | offen | Nie im Sep-12-Live-Jar, bewusst ausserhalb der heutigen Recovery. |
| Website/Portal-Relaunch | Documents/VetroForge | Codex | ok | teilweise | offen | offen | offen | - | Grosser Scope, viele Einzelentscheidungen offen (siehe Handover Abschnitt 21). |

**Legende:** ok = erledigt, teilweise = teilweise/oberflaechlich geprueft,
offen = noch zu tun, "-" = nicht zutreffend.
