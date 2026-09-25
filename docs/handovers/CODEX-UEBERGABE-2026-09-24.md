# VetroForge: vollständige Projektübergabe für Claude

Stand: 24.09.2026, Europe/Berlin. Verfasser: Codex.

Diese Übergabe bündelt die hier besprochenen Anforderungen, Entscheidungen, bearbeiteten Bereiche, vorhandenen Implementierungen und offenen Abnahmen. Sie ist **keine Behauptung, dass alle genannten Funktionen bereits auf dem Live-Server funktionieren**, und kein wörtliches Chat-Transkript.

## 1. Zuerst lesen: aktueller Wiederherstellungsfall

- Der Nutzer meldete zuletzt: „Irgendwie ist alles weg“, und bat zunächst um ein Backup. Welche Welten, Daten oder Ansichten betroffen sind, wurde gegenüber Codex noch nicht konkretisiert.
- Danach stellte der Nutzer klar: **Claude kümmert sich aktuell um die Wiederherstellung.** Codex soll mit Claude Informationen über GitHub austauschen und damit anfangen.
- Gemeinsames, am 24.09.2026 als privat bestätigtes Repository: [GamemakerYT/VetroForgeNET](https://github.com/GamemakerYT/VetroForgeNET).
- Codex hat in diesem Wiederherstellungsfall **keinen Restore, keinen JAR-Austausch und keinen Neustart durchgeführt**. Diagnose war nur lesend.
- Der lesende SSH-Check am 24.09.2026 um 17:51 Uhr zeigte vorhandene Factions-, Velocity- und Website-Verzeichnisse sowie Screen-Sitzungen `Factions`, `Velocity` und `DevServer`. Das beweist weder intakte Spielerdaten noch einen korrekt geladenen Pluginstand.
- Ursache, Umfang des Verlusts und geeigneter Restore-Zeitpunkt sind **nicht festgestellt**. Keine Schuldzuweisung an einen bestimmten Build ohne Beleg.
- Zuerst mit Claude den aktuellen Recovery-Stand abstimmen. Nicht parallel denselben Server wiederherstellen, JARs tauschen oder Datenbanken zurücksetzen.

**Wichtig zur Quellbasis:** Das GitHub-Repository enthält bereits unter anderem `src`, `web-dashboard`, Deploy-Skripte und eine Tebex-Checkliste. Der lokale Codex-Arbeitsstand verwendet unter anderem `factions-repair` und `web-portal-live`. Eine Gleichheit dieser Stände ist nicht nachgewiesen. Nicht blind übereinanderkopieren.

## 2. Statusbegriffe und Arbeitsregeln

| Kennzeichnung | Bedeutung |
| --- | --- |
| Verbindlicher Wunsch | Vom Nutzer gewollt; sagt allein nichts über Implementierung aus. |
| Lokal vorhanden | Im aktuellen lokalen Quellcode oder Artefakt gefunden; nicht automatisch live. |
| Lokal geprüft | Konkrete Tests oder Build-Nachweise dokumentiert; deren Umfang und Datum beachten. |
| Historisch dokumentiert | Frühere Notiz meldet Umsetzung/Deployment; heutiger Zustand muss neu geprüft werden. |
| Abnahme offen | Echte Spieler-, Mehrspieler-, Berechtigungs- oder Produktionsprüfung fehlt. |
| Vorschlag | Noch keine freigegebene Produkt- oder Balanceentscheidung. |

Die älteren Projekt-, Abnahme- und Erinnerungsnotizen wurden als Wegweiser verwendet. Wo aktuelle Dateien ihnen widersprechen, ist das unten ausdrücklich korrigiert. Ein ungeprüftes Kontrollkästchen im Wiki kann auch „noch nicht verifiziert“ bedeuten, nicht zwingend „Code fehlt“.

- Erst vorhandenen Code, Konfiguration und Zuständigkeiten lesen. Kleine, passende Änderungen statt unkontrollierter Komplettumbauten.
- Keine Server-Neustarts, Löschungen oder Überschreibungen ohne ausdrückliche Freigabe des Nutzers. Der aktuelle Auftrag ist eine Übergabe, keine Deploy-Freigabe.
- Nutzeränderungen und Änderungen des anderen Assistenten nicht zurücksetzen.
- Abschlussberichte immer trennen: **implementiert, getestet, gebaut, hochgeladen, geladen, ingame abgenommen**.
- Ein HTTP 200, ein offener Prozess, ein Screenshot oder ein erfolgreicher Build allein ist keine End-to-End-Abnahme.
- Deutsche Spielertexte mit echten Umlauten. Kurze verständliche Lore, lesbare Farben, nachvollziehbare Statuskarten.
- Buttons müssen echte Funktionen ausführen. Unfertiges als „noch nicht verfügbar“ kennzeichnen.
- Keine Zugangsdaten, privaten Schlüssel, Datenbank-Dumps, Spielerbelege oder internen Moderationsdaten in GitHub-Nachrichten kopieren.
- GitHub ist das gemeinsame Protokoll, keine automatische Direktverbindung zwischen Claude und Codex. Beide müssen neue Einträge ausdrücklich lesen.

## 3. Projekt, Module und Fundstellen

VetroForge ist ein Minecraft-Factions-/Raid-/Economy-Netzwerk mit Website, Spielerportal, Adminbereich, Discord-Anbindung, Tebex-Shop und eigener Map-/Story-Welt.

Bekannte Zielbasis der jüngsten Plugin-Builds: **Paper 1.21.8, Java 21**, zusätzlich Velocity mit VetroCore. Serverversion und geladene Abhängigkeiten vor einem Deploy frisch ermitteln.

Lokale Projektwurzel: `/Users/castelijn/Documents/VetroForge`.

| Bereich | Lokaler Einstieg |
| --- | --- |
| Factions, Warzone, Raids, Wirtschaft, GUIs, Minecraft-WebAPI | `factions-repair/src/main/java/com/vetroforge/` |
| Jüngster bearbeiteter Core-Quellstand | `builds/2026-09-22-auron-config/core/` |
| Website und Portal | `web-portal-live/` |
| Eigenständiger MapBuilder | `map-builder/` |
| Ältere Lobby-Arbeiten | `lobby-repair/` |
| Prüfungen und Migrationen | `scripts/qa/`, `scripts/migrations/` |
| Jüngstes Gameplay-Paket | `builds/2026-09-23-gameplay-backlog/` |
| Projektwissen | `VetroForge-Projektwiki.md` und Obsidian-Vault `VetroForge` |

Die relativen Fundstellen in dieser Datei beziehen sich auf diese lokale Wurzel, nicht automatisch auf denselben Pfad im GitHub-Repository.

Bekannte Remote-Verzeichnisse: `/home/Mc/Factions`, `/home/Mc/Velocity`, `/var/www/vetroforge`, `/var/www/vetroforge/public`. Vor Nutzung die tatsächliche Installation prüfen. Keine Core-JAR in den Paper-Pluginordner legen.

Die ältere Architektur sieht VetroCore als zentrale Instanz für netzwerkweite Moderation und Rang-/Proxy-Funktionen vor. Factions enthält ebenfalls Moderations-/Bridge-Code. Vor Änderungen den tatsächlich registrierten Command und die wirksame Datenquelle verfolgen; nicht zwei konkurrierende Ban-, Help- oder Permission-Systeme schaffen.

## 4. Entscheidungen, die frühere Ideen ersetzen

1. **Anticheat und Moderationsmarke heißen AURON.** VetroSentinel wurde verworfen. Sichtbare Ban-, Mute- und Report-Texte sollen AURON verwenden.
2. **Kits enthalten Varyn-Crystals.** Crystals sind hier weder Kit-Kaufpreis noch Freischaltwährung. Das hat der Nutzer ausdrücklich klargestellt.
3. **Spawn: Frost vorne, Feuer/Lava hinten**, entlang der Laufrichtung. Die frühere Links-/Rechts-Aufteilung ist überholt.
4. **Keine schwebende VetroForge-/Factions-Schrift über dem Spawn.** Der frühere Wunsch nach einer solchen Beschriftung wurde später zurückgenommen.
5. Spawn soll erkundbar und spielerfreundlich sein, mit echten Räumen, Wegen, oberen Ebenen, einer Überführung und großer hinterer Torhalle mit Durchgang.
6. **AURON-Belege: automatische Löschung 90 Tage nach Fallabschluss.** Datenschutz- und Reopen-Verhalten gezielt prüfen; diese Entscheidung ist keine pauschale Löschfreigabe für andere Daten.
7. **Vetro+ darf den Rangverlauf Hellblau bis Orange haben. AURON AutoMute soll ausdrücklich keinen Gradient haben.**
8. GommeHD ist eine Stilreferenz für Status, News und Footer, keine Aufforderung zum ungeprüften Kopieren.
9. Die Antwort „Genauso“ auf die Frage nach Konvoi-Route/Rewards hat keine realen Koordinaten oder konkrete Belohnung geliefert. Keine erfundene Live-Route aktivieren.

## 5. Gesamtstand auf einen Blick

| Bereich | Belastbarer Stand dieser Übergabe |
| --- | --- |
| PvP, Raid-Respawn, Kit-Crystals, Rangfarben, Fehlerübersicht, Fly | Lokales Release vom 23.09. mit Abnahmeprotokoll; nicht von Codex live eingespielt. |
| Raid-Base-Kopie | Aktuell lokaler WorldEdit-Kopiercode vorhanden. Alte Aussage „nur TODO“ ist überholt; Live-Abnahme fehlt. |
| Forschung | Aktuell Punkte, Materialbuchung, Freischaltung und Effekthooks im lokalen Code. Alte „nur Vorschau“-Notiz ist überholt; Sicherheit, Balance und Live-Ablauf prüfen. |
| Item-/Coin-Tausch | Itemtausch historisch dokumentiert; inzwischen zusätzlich Coin-Angebote und exakte Coin-Abwicklung im lokalen Code. Keine neue End-to-End-Freigabe daraus ableiten. |
| AH-Kaufgesuche | `AuctionBuyOrderService` und Command-Anbindung lokal vorhanden; ältere „fehlt“-Notizen überholt. Live-, Crash- und Konkurrenztests offen. |
| Zustellfach/Team-Recovery | Spielerzustellung historisch dokumentiert; Adminaktionen inzwischen lokal vorhanden, mit Sperre bei begonnenem Inventar-Handoff. |
| Konvoi und Routenprofile | Lokale Umsetzung/Tests dokumentiert; reale Route, Rewards und Ingame-Abnahme offen. |
| Leaderboards/Season-Archiv | Mehrere konkrete Implementierungen vorhanden; unterschiedliche Metriken und Datenquellen, siehe Abschnitt 9. |
| AURON-Webabläufe, Permissions, Website-Ausbau | Viel besprochen und Quell-/Migrationsdateien vorhanden; kein vollständiger aktueller Live-Audit in dieser Übergabe. |
| MapBuilder | Eigenständige JAR 3.2.2-preview und Geometrie-Abnahme vorhanden; kein neuer Live-/Ingame-Nachweis. |
| Wiederherstellung | Claude zuständig; Ursache und Ergebnis gegenüber Codex noch unbestätigt. |

## 6. Warzone, PvP, Schutz und Flug

### Gewünschtes Spielerlebnis

- PvP in Warzone und Raid aktiv, Spawn geschützt. Gleichrangige Spieler dürfen nicht versehentlich durch kosmetische Scoreboard-Teams vom Kampf ausgeschlossen werden.
- Beim Betreten des Spawns automatische Flugberechtigung; Doppelleertaste muss normales Landen ermöglichen.
- Beim Eintritt in die Warzone Flug automatisch deaktivieren. Außerhalb der Warzone darf berechtigtes `/fly` wieder funktionieren.
- Beim Serverbeitritt am Spawn erscheinen, nicht am letzten Logout-Ort.
- Keine normalen Mobs im Spawn; Vanilla-Mob-Spawns in der Warzone kontrollieren/unterbinden. Custom-Mobs und NPCs getrennt behandeln.
- Disparity soll auch tagsüber entsprechend dem Konzept erscheinen. Spawnrate, Grenzen und tatsächliches Verhalten prüfen.
- Chest-Punkte, Lootdrops, Bosse und Kontrollpunkte sollen verständlich eingebunden sein.
- Vorgaben aus dem Wiki für Lootdrops: maximal drei, 30-Minuten-Takt, Despawn nach fünf Minuten. Vor Aktivierung aktuelle Config und Event-Ausnahmen prüfen.
- Seltene Lootpools, Crystals und Item-Rarity konsistent; Bezeichnungen „Mythic“/`MYSTIC` nicht ungeprüft als identische technische Enum behandeln.
- Anti-Cheese bei Spawn/Home/TPA/Warp, Elytra, Perlen, Chorus, Mounts, Portalen und ähnlichen Umgehungen berücksichtigen.

### Lokale Arbeit vom 23.09.

- Kosmetische Rangteams erlauben Friendly Fire wieder.
- PvP wird für die vorgesehenen Warzone-, Faction- und Raid-Welten aktiviert; Schutz anderer Systeme wird nicht pauschal überschrieben.
- Spawn-Fly setzt nicht bei jeder Bewegung erneut aktives Fliegen. Die Warzone-Prüfung berücksichtigt das Bewegungsziel.
- Creative/Spectator sind von dieser Survival-Flugregel ausgenommen; das ist keine allgemeine Freigabe aller Adminaktionen.

Fundstellen: `manager/LobbyManager.java`, `lobby/LobbyProtectionListener.java`, `warzone/WarzoneDamageRulesListener.java`, `display/NametagTabManager.java` unter dem Factions-Java-Paket.

**Abnahme:** zwei echte Spieler, gleiche und unterschiedliche Ränge, Spawn-/Warzone-Grenzen, Landen, Rejoin, Tod, Weltwechsel, Custom-Mobs versus Vanilla-Mobs und alle Lootdrop-Zeit-/Limitregeln.

## 7. Versorgungskonvoi und Schwarzmarkt

### Konvoi: dokumentierte lokale Umsetzung

- Phasen mit Ankündigung, Bewegung, Kontrolle, umkämpftem Punkt, Abschluss und Abbruch. Kein bloßes „wer klickt zuerst die Kiste“.
- `/convoy point`, `/convoy reward <Material> <Anzahl>`, `/convoy status`, `/convoy start confirm`, `/convoy abort`.
- Route mit 2 bis 16 Punkten in derselben geladenen Warzone-Welt, 2 bis 128 Blöcken Abstand. **Geradlinige Bewegung, keine automatische Hindernisnavigation.**
- Ohne gültige Route, Belohnung und erforderliche Datenbankverbindung bleibt der Start gesperrt.
- Dokumentierte Standardzeiten: 60 Sekunden Ankündigung, 30 Sekunden Kontrolle pro Punkt, höchstens 20 Minuten. Keine automatische Wiederholung in dieser Version.
- Nur geeignete aktive Survival-Teilnehmer zählen; Zuschauer, Flug-/Vanish-/Testfälle ausschließen.
- Belohnung für die Gewinner-Faction am letzten kontrollierten Punkt und dort berechtigte Teilnehmer. Keine ungeprüfte Belohnung an jeder Zwischenstation.
- Ergebnis und Event-/Spieler-ID dauerhaft speichern, Belohnungen über Zustellfach deduplizieren. Unklare Ausgaben nicht wiederholen.
- Aktiver Lauf wird bei Neustart abgebrochen; abgeschlossene, noch ausstehende Rewards bleiben nachvollziehbar.
- Darstellung über temporäre BlockDisplays/Bossbar, keine frei verteilten Weltblöcke oder unbeschränkten Chunkloads.

### Admin-GUI und Profile

- `/convoy gui` bzw. `/convoy admin`: Punktreihenfolge, Verschieben, aktuelle Position übernehmen, private Partikelvorschau und bestätigtes Entfernen.
- Neun Routenprofile; speichern, laden, Vorschau, umbenennen, überschreiben, löschen.
- Profile enthalten Route/Welt, nicht automatisch Reward und Zeitplan. Profil-Laden startet kein Event.
- Änderungen in alten Fenstern, parallele Adminänderungen, Rechteverlust und beschädigte Profildateien berücksichtigen.
- Dokumentation: `FEATURE_ROUND_MARKET_CONVOY_RESEARCH.md`; Code: `warzone/convoy/`.
- Noch prüfen: echte Welt/Koordinaten, freier Weg, Alliances. Die dokumentierte lokale Version zählt Allies separat; gewünschte Endregel muss bewusst freigegeben werden.

### Schwarzmarkt

Gewünscht: wechselnde Angebote gegen erspielte Fragmente, Vorschau und fester Wechselzeitpunkt in Spiel und Website. Vorhandener `traveler/TravelerService.java` arbeitet bereits mit einem reisenden Händler und Coin-Angeboten. Diesen nicht stillschweigend auf Fragmente umstellen.

Fragment-Konto, Quellen, Preise, Limits, Rotation, Kaufjournal und sichere Zustellung sind als eigener Produkt-/Sicherheitsblock zu behandeln. Fragmente, Raid-Shards, Crystals und Coins sind keine austauschbaren Währungen. Keine Live-Aktivierung allein aufgrund des Konzepts behaupten.

## 8. Factions, Chronik, Vault und Gemeinschaft

### Identität und Detailseiten

- Faction-Listen müssen echte Detailseiten öffnen.
- Stabile Faction-UUIDs für Links, Banner, Fans, Kommentare, Chronik, Forschung und andere Inhalte benutzen.
- Umbenennungen historisieren; bisherige Links und Inhalte dürfen nicht abbrechen.
- Frühere Namen „Team“ und „123“ gehören laut Nutzer zu heutigen Factions. Zuordnung anhand echter IDs/Verlauf prüfen, nicht anhand eines globalen geratenen Namens-Mappings.
- Profile mit Banner, echten Mitgliederskins, Rollen, Online-Status, Treasury, Raid-Shards und aktiven Perks.
- Laws/Regeln, Motto, Discord, Sprache, Aktivitätszeiten und Bewerbungsstatus pflegen.
- Faction-Fan ist nicht automatisch Mitglied. Web-Bewerbung muss zur tatsächlichen Ingame-Mitgliedschaft passen.

### Chronik

- Beitritt, Austritt, Beförderung, Degradierung, Leaderwechsel und Raid-Ergebnis korrekt erfassen.
- Akteur und betroffenen Spieler, Rollenwechsel, Zeit und stabile Faction-ID unterscheiden.
- Filter: Mitglieder, Rollen, Raids; 7 Tage, 30 Tage, alles. Spielersuche und direkte Profilverlinkung.
- Kleine Profilvorschau mit Kopf, Rangfarbe und Faction, auch per Tastatur bedienbar.
- Live-Aktualisierung ohne Verlust von Filtern oder Scrollposition.
- Lokaler Nachweis vom 23.09.: Regressionstests für gemeinsame Speicherung von Zustand/Ereignis, Austritt, Beförderung, Leaderwechsel, stabile IDs und Fehler-Rollback. Persistente Übertragungswarteschlange vorhanden.
- Bereits fehlende historische Ereignisse wurden nicht erfunden oder nachträglich als gesichert ergänzt. Live-Datenweg bis MySQL/Website noch prüfen.

### `/f` und Faction-Vault

- Neue Spieler ohne Faction: kleines, sauberes, farbiges Einstiegmenü mit Gründung und Beitrittsmöglichkeiten.
- Für Mitglieder: gruppiertes 54-Slot-Menü statt unsortierter Funktionssammlung. Bereiche Mitglieder, Rang/Rechte, Wirtschaft/Vault, Forschung, Raids und Historie.
- Reale Spieler-Skins statt Steve-Platzhalter. Header, sinnvolle Icons, kurze Lore, einheitliche Navigation.
- Gemeinsames Vault mit Rollenrechten, Ein-/Auszahlungsprotokoll, separatem Spendenfach und bestätigten Aktionen.
- Ziele wie Materialbedarf und Mitgliederbeiträge anzeigen; keine ungeschützte Entnahme zwischen Inventar und Vault.
- Vault-Pending-Zustände/Receipts bei Crash oder unklarer Übergabe nicht pauschal löschen.
- Private Spieler-Vaults (`/pv`) und gemeinsames Faction-Vault sind getrennte Systeme.

### Wochenziele und weitere Community-Funktionen

- Historisch dokumentiert: `/f weekly` und `/f quests`, Beiträge für Kills, Crystal-Einzahlungen, Onlinezeit und Lootdrops, Detailseiten und Abholjournal.
- Dokumentierte Grenzen der damaligen Version: kein automatischer Detailseiten-Refresh; generische Köpfe; Teile der Wochenstände noch namensbasiert; zusätzlicher Ally-/Wiederholungsschutz offen.
- Persönliche und gemeinsame Belohnung klar unterscheiden. `PROCESSING` bedeutet Team-Prüfung, nicht automatisch „fehlgeschlagen“; nicht doppelt auszahlen.
- Ausbauwünsche: rotierende Wochenziele, Abstimmung, gemeinsame Meilensteine, Kalender mit Zusagen, Rekrutierungsbörse mit Sprache/Aktivität/Spielstil/Rollen.
- Faction Operations Center: Wochenziele, Forschung, Kalender, Treasury und offene Raids an einem Ort.

Quellen: `factions-repair/FACTION_ID_LINKS.md`, `FACTION_CONTENT_IDS.md`, `FACTION_VAULT.md`, `WEEKLY_CONTRIBUTIONS.md`, `ITEM_SAFETY_AND_GOALS.md`; Website `faction/`, `leaderboards/`, `api/faction-history.php`.

## 9. Leaderboards, Elo, Prestige und Season-Archiv

**Nicht alle „Leaderboards“ zeigen dieselben Daten. Drei Ebenen auseinanderhalten.**

### A. Aktuelle Website-Ranglisten

Am 24.09. lokal in `web-portal-live/leaderboards/index.php` gelesen:

- Factions: sortieren nach **Elo, Mitgliederzahl oder Fans**.
- Spieler: sortieren nach **Prestige oder Coins**.
- Suche, zehn Einträge pro Seite, hervorgehobene Top 3, echte Faction-/Spielerlinks, Köpfe/Banner, eigene Faction und Community-Favoriten.
- Platzierung wird vor der Suchfilterung vergeben; Suchergebnisse bekommen nicht künstlich Platz 1.
- Faction-Daten kommen aus Minecraft, Fans aus dem Portal.
- Spieleransicht kombiniert verfügbare Top-/Cache-Spieler. **Keine vollständige Rangliste aller jemals registrierten Spieler und keine vollständige Season-Auswertung behaupten.**
- Coins stammen laut Seitenhinweis aus dem Spieler-Cache. Aktualität und verlässlichen Offline-/Stale-Zustand live prüfen.

### B. Ingame-/Hologramm-Ranglisten

Aktueller lokaler `leaderboard/LeaderboardService.java` enthält:

| Liste | Quelle/Metrik |
| --- | --- |
| Weekly Factions | Wochenziel-Fortschritt / Wochenpunkte |
| Weekly Spieler | Wochenbeiträge; bei fehlenden Beiträgen Fallback auf `weeklyScore` bzw. Aktivitätsformel |
| Season Factions | Faction-Elo |
| Season Spieler | PvP-Elo |

`holo/HoloLeaderboardService.java` enthält zusätzlich Hologramm-/Rotationslogik. Die Rotation hat einen konfigurierbaren Takt; lokale Vorgabe 15 Sekunden, Mindestwert fünf Sekunden.

**Konkrete Prüfstellen:** Der Weekly-Spieler-Fallback verwendet bei fehlendem Score `Kills * 12 + aktuelle Serie * 8 + Online-Minuten`; das ist nicht automatisch ein sauber abgegrenzter Wochenwert. Die Faction-Entries dieses Services verwenden aktuell Namen als ID, obwohl andere Bereiche UUIDs nutzen. Zeitraumdefinition und ID-Verwendung vor einem allgemeinen „synchronisiert/fertig“ prüfen.

### C. Dauerhaftes Season-Archiv

Historisch dokumentierte Umsetzung in `factions-repair/SEASON_ARCHIVE.md`:

- `/season archive` erzeugt eine Momentaufnahme, ohne Reset.
- Archiv mit Season-/Zeit-Metadaten, WarScore und sechs Ranglisten: **Kills, KDR, Prestige, Onlinezeit, Spieler-Elo, Faction-Elo**.
- Website `/seasons/`: finale Abschlüsse versus Zwischenstände, Suche, Seiten und Vergleiche.
- Spieler über UUID, Factions über stabile ID vergleichen. Fehlender Teilnehmer ist nicht „Wert 0“.
- Archivdateien `seasons/snapshot-<UUID>.yml`, Schema 2. Manuelles Archiv ist kein automatisch finaler Seasonabschluss.
- Soft-Reset archiviert zuerst und setzt die dokumentierten Kills/Tode/Serie/Onlinezeit zurück; Hard-Reset zusätzlich Prestige. Elo, WarScore, Coins, Inventare und Factions werden damit nicht pauschal zurückgesetzt.
- Vor Reset Originaldaten sichern und `season-reset.pending.yml` beachten. Bei Fehler kein automatisches „weiter so“, kein blindes Löschen des Markers und kein automatischer Restore.
- Alte Archiv-/Deploynachweise stammen aus früheren September-Arbeiten; aktuellen Runtime-Stand nach Recovery neu abnehmen.

### Fortschrittsbegriffe

XP-Level, Prestige-Stufe, Spieler-Elo, Faction-Elo, Evo und Forschungspunkte nicht vermischen. Der Hover soll zum Beispiel **Level 13 · Prestige Gold I** zeigen, nicht eine interne Prestige-Zahl als XP-Level. Shop-Voraussetzungen müssen zur tatsächlichen jeweiligen Fortschrittsquelle passen.

## 10. Forschung: aktueller Code ist weiter als die alte Vorschau

### Forschungsrat

- Zweige Wirtschaft, Verteidigung, Erkundung.
- Eine Stimme pro berechtigtem Mitglied, veränderbar/zurückziehbar bis zur Frist.
- Feste Teilnehmerbasis, Mindestbeteiligung und Frist; dokumentierte Voreinstellung 50 Prozent und 24 Stunden.
- Dokumentierte auswählbare Quoren 25/50/75/100 Prozent, Fristen 1/6/24/48/72 Stunden.
- Leader startet/bestätigt; delegierte Officer-Freigabe ist ein eigener kontrollierter Berechtigungspfad.
- Nach Ablauf und erreichtem Quorum endgültige Zweigwahl; bei Gleichstand explizite Auswahl und kurze Begründung.
- Ergebnis-Karte für Mitglieder, gespeichert mit Entscheider, Stimmenstand, Zeitpunkt und Begründung.
- Portal-Glocke: Opt-in-Erinnerung in den letzten 30 Minuten für noch nicht Abstimmende; separate Leader-Meldung bei Freigabebereitschaft.
- Diese dokumentierten Erinnerungen werden bei Portalaufrufen erzeugt, nicht durch einen bewiesenen unabhängigen Push-/Cron-Dienst.

### Jetzt lokal vorhandene Freischaltung

Am 24.09. in `FactionResearchMenu`, `FactionVaultService`, `FactionVaultStore`, `RaidRewardService`, `RaidCrystalManager`, `FactionUpgradeManager` und `research-plan.yml` geprüft:

- Forschungspunkte im Faction-Vault, Vergabe aus vollständigen Raidsiegen und geschützter Admin-Vergabe.
- Materialkosten aus dem Spendenfach; Punkte, Materialänderungen, Unlock und Audit im gemeinsamen Vault-Speicherpfad.
- Bestätigungsmenü, vorhandene Freigabe des Zweigs, Voraussetzungen und bereits aktive Knoten werden geprüft.
- Der aktuelle Unlock-Code erlaubt Leader und Officer. Das muss mit der feineren Delegationsregel des Forschungsrats und der gewünschten Rechtematrix abgeglichen werden.
- Effekthooks sind tatsächlich vorhanden, nicht mehr nur Beschreibungstexte.

Aktuell lokale Ausgangswerte, **keine nachgewiesene Live-Balancefreigabe**:

| Knoten | Punkte | Materialien | Effekt |
| --- | --- | --- | --- |
| Vorratslogistik I | 1 | 256 Eichenbretter, 128 Eisen | +9 Kapazität RaidShop-Shard-Bank |
| Vorratslogistik II | 2 | 32 Diamanten, 64 Smaragde | weitere +9 |
| Befestigung I | 1 | 256 Obsidian, 128 Eisen | +25 HP Obsidian-Ziel |
| Befestigung II | 2 | 512 Obsidian, 32 Diamanten | weitere +25 HP |
| Kartografie I | 1 | 256 Papier, 8 Kompasse | +1 Prozentpunkt Warzone-Control |
| Kartografie II | 2 | 512 Papier, 64 Smaragde | weitere +2 Prozentpunkte |

Planungsbudget und üblicher Punkte-Cap stehen lokal auf sechs. Prüfen, ob ein wieder auffüllbares Punktekonto tatsächlich die gewünschte dauerhafte Begrenzung auf bestimmte Zweige durchsetzt. Nicht aus der Zahl allein eine lebenslange Unlock-Grenze ableiten.

**Offen:** neue vollständige Funktions-/Crash-/Mehrspielerabnahme; einmalige Punktevergabe pro Sieg; kein Doppelverbrauch; Custom-/Favoritenitems schützen; Rechte bei geöffnetem Menü erneut prüfen; Wirkung nur einmal addieren; Web-/Ingame-Gleichstand; Season-Archivierung und Resetregeln.

## 11. Raids, Instanzen, Leben und Rewards

- Gewünschter Ablauf: Zielwahl, Scouting/Vorbereitung, berechtigte Teilnahme, Angreifer/Verteidiger, Leben, Kampf, Obsidian-/Basenziel, Ergebnis und eindeutige Belohnung.
- Echte gegnerische Base in der Raid-Instanz, nicht nur eine beliebige Welt.
- Schutz vor Teilnahme-/Teleport-Umgehungen, Leben-Reset durch Rejoin, mehrfacher Auszahlung und missverständlichem Siegerstatus.
- Raid-Historie auf der Website mit MVP, Schaden, Kills und Timeline gewünscht; vollständige Erfassung/Anzeige nicht durch diese Übergabe als live bestätigt.

### Base-Kopie: Korrektur der alten TODO-Aussage

`instance/RaidInstanceService.java` enthält aktuell eine WorldEdit-/FAWE-gestützte Bereichskopie rund um das Faction-Home, Volumenlimit, Zielposition, Einstieg und Fehlerbehandlung. Standardradius 24, 12 Blöcke unter und 28 über Home, Standard-Blocklimit 150.000. Das ist eine begrenzte Bereichskopie, **keine Garantie einer vollständigen Base beliebiger Größe**.

Noch prüfen: Container/Blockdaten, Quell-/Zielschutz, veraltete Instanzen und Invalidierung, sichere Einstiegspunkte, tatsächliche WorldEdit-Abhängigkeit, Performance und Wiederholung über mehrere Raids. Nicht erneut als „nur TODO“ planen, aber auch nicht ungeprüft live freigeben.

### Lokale Respawn-/Teilnahmefixes vom 23.09.

- Mit Restleben zurück zum gespeicherten Raid-Einstieg.
- Ohne Leben zum Spawn und aus dem Raid; auch Verteidiger sollen nicht über ihr Home wieder hineingeraten.
- Rejoin setzt Leben nicht zurück.
- Fehlgeschlagener Teleport erzeugt keine erfolgreiche Teilnahme.
- Verteidiger-Beitrittslistener wurde registriert.

### Live-Daten im Web

- Lokale API mit stabiler Raid-ID, Faction-IDs, Teilnehmern, Online-/Teilnahmestatus und Leben.
- Website aktualisiert alle fünf Sekunden, ohne komplettes Neuladen; Fokus/Teilnehmer-Scrollposition bleiben erhalten.
- Reward-Vorschau nutzt die Berechnung der vorhandenen Auszahlung, ist aber **keine Auszahlungsgarantie**. Booster/Limits können den Endwert verändern.
- Verbindungsfehler müssen alte Daten als veraltet anzeigen.

Quellen: `raid/RaidManager.java`, `RaidFightListener.java`, `RaidJoinService.java`, `RaidJoinListener.java`, `RaidRewardService.java`, `raid/energy/RaidSessionService.java`, `web/WebApiManager.java`, Website `api/raid-live.php`, `raid/index.php`, `assets/js/raid-live.js`.

## 12. TNT und ursprünglicher Weltuntergrund

Nutzerwunsch: TNT soll im Factions-Gebiet nur von Spielern gebaute Blöcke zerstören, nicht den ursprünglichen Weltuntergrund.

Lokale Umsetzung vom 23.09. in `world/PlacedBlockProtection.java`:

- Nach Aktivierung gesetzte Spielerblöcke werden in `faction_`-Welten persistent pro Chunk markiert.
- Explosionslisten und manuelle Obsidian-Schadenslogik berücksichtigen diese Herkunft.
- Platzieren, Abbauen, Verbrennen, natürliche Entstehung, Kolben und fallende Blöcke werden einbezogen.
- Zusätzliche bestehende Raid-/Höhenschutzregeln bleiben relevant.

**Kritische Grenze:** Nicht markierte alte Spielerhäuser werden wie unbekanntes Terrain geschützt. WorldEdit-/Plugin-Importe sind nicht automatisch als Spielerbauten markiert. Alte Bases nicht pauschal freigeben. Eine sichere Migration benötigt nachvollziehbare Terrain-/Bauherkunft und Zustimmung. Echte TNT-/Kolben-/Chunk-Neuladeprüfungen fehlen noch.

## 13. Items, Sets, Crystals, Fusionen und Kits

### Items und Crates

- Fünf Varyn-Crystal-Stufen mit echter Öffnung, korrektem Verbrauch und passendem Reward.
- Doppelklick-/Doppeltrigger-/leere-Hand-Fälle und Inventarvollstand prüfen.
- Fusion nur mit passender gleicher Rarity bei Rüstung und Waffen; FusionBooks dürfen keine Items duplizieren oder falsch verbrauchen.
- Einheitliche Set-, Level-, Rarity-, Enchant- und Fähigkeitsanzeige.
- Lieblingsitems vor Verkauf, Fusion, Tausch und anderen destruktiven Aktionen schützen.
- Crate-Vorschau nicht plünderbar; Item-Locks und Metadaten erhalten.
- Crate-Historie und Admin-Testfunktionen wurden als Diagnose-/Abnahmeausbau besprochen.

### Sets und gespeicherte Builds

- `/sets`: aktive Boni, fehlende Teile, Quellen und Verbesserungen verständlich darstellen.
- Historisch dokumentiert: `/sets builds` mit drei beschreibenden Ausrüstungs-Snapshots, vier Rüstungsteilen und Handitem. Keine Itemausgabe und kein automatisches Anziehen.
- Vergleich auf Grundlage der Itemdaten; beschädigte Speicherdaten nicht leer überschreiben.
- Weitere Wünsche: benannte Vorlagen, Vergleich von Level/Enchant/Haltbarkeit, Item-Codex mit Upgradewegen und Setvergleich. Details des heutigen Ausbaugrades separat prüfen.

### Kits: lokale Konfiguration vom 23.09.

| Kit | Enthaltene Varyn-Crystals | Zugang |
| --- | --- | --- |
| Starter | 2 Common | kostenlos, einmalig |
| Daily | 1 Common | ab Ember, bestehender Cooldown |
| Weekly | 2 Rare | ab Vanguard, bestehender Cooldown |
| Monthly | 2 Epic | ab Apex, bestehender Cooldown |

- Vorschau und Ausgabe verwenden dieselbe Itemzusammenstellung und echte KeyCrate-Items.
- Config: `rankKits.<kit>.crystals.<RARITY>`, optionale Rang-Overrides; Mengen 0 bis 64.
- Starter einmalig, danach ausblenden; Inventarvollstand darf keinen verlorenen Anspruch erzeugen.
- Starter konservativer: Common-Ausrüstung, Nahrung, vier Perlen, 32 Obsidian, kein TNT.
- Monthly keine standardmäßigen Legendary-/Mystic-Crystals; zwei Epic und vier goldene Äpfel als Ausgangspunkt.
- Alle Standard-Kits lokal bearbeitet; zusätzliche Live-Items/Commands und tatsächliche Economy-Auswirkungen noch nicht abschließend geprüft.

Quellen: `services/RankKitService.java`, `KitCrystalBalance.java`, `KitClaimStore.java`, `commands/RankKitCommand.java`, `crates/`, `items/`, `factions-repair/WIKI_AND_KITS.md`, `BUILDS_AND_COIN_SYNC.md`.

## 14. Economy, Handel, AH und Zustellung

### Handel

- Historisch abgesicherter Itemtausch: maximal acht Blöcke Abstand, gleicher Weltbereich, geeigneter Survival-Zustand, beide bestätigen, Änderungen setzen Bestätigung zurück.
- Nicht während Raid/Scouting; Abbruch bei Logout/Tod/Distanz/Zeitüberschreitung. Favoriten, Soulbound, Item-Locks und temporäre Raiditems schützen.
- Inventar erst nach vollständiger Planung ändern; voller Platz bedeutet keinen Teilverlust.
- Persistentes Trade-Journal und Inventar-Receipts für unklare Übergaben.
- **Aktueller lokaler Code enthält inzwischen `/trade coins <Betrag>`**, sichtbare Coin-Angebote und `exchangeExact`. Die ältere Dokumentation „nur Itemtausch, Coins fehlen“ ist nicht mehr der vollständige Quellstand.
- Das Vorhandensein dieser Methode beweist keine gemeinsame Crash-Transaktion für Inventare, SQL und Redis. Konkurrenzzugriffe und unklare Zahlungen neu prüfen; weder automatisch erstatten noch erneut übertragen.

### Auktionshaus

- Reale Gegenstände und vollständige Metadaten, korrekte Preise/Besitzprüfung, Kaufen/Anbieten/Abbrechen nachvollziehbar.
- **Aktuell lokal:** `auction/AuctionBuyOrderService.java`, eingebunden in `AuctionHouseCommand` und Plugin-Initialisierung; `/ah order create|list|fill|cancel`.
- Teilmengen, Coin-Reservierung, Restmenge, Auszahlung und Abbrucherstattung sind relevante bestehende Codepfade, keine bloße neue Idee mehr.
- Noch sicher abnehmen: gleichzeitige Erfüllungen, eigenes Gesuch, volles Inventar, Crash zwischen Entnahme/Zahlung/Zustellung, doppelte Anfrage, Redis-/Coin-Cache-Konsistenz und nachvollziehbarer Audit.
- Persönliche Handelshistorie, Watchlist und Preisverläufe wurden als Ausbauideen besprochen; nicht ohne Nachweis als fertig deklarieren.

### Zustellfach und Team-Zentrum

- `/deliveries` zeigt eigene Webbestellungen; Abholung ohne erneute Zahlung, nur wenn die gesamte Lieferung passt.
- Status unterscheiden: ausstehend, Übergabe läuft, Prüfung nötig, zugestellt, erstattet, fehlgeschlagen.
- MySQL-Journal mit Vorher-/Nachher-Inventar und dauerhaftem Spieler-Receipt; erst passende Bestätigung markiert Lieferung abgeschlossen.
- **Heute lokal vorhanden:** `DeliveriesCommand` mit Adminberechtigung `vetroforge.delivery.admin`, Adminliste und `WebDeliveryService.adminResolve` für geprüften Retry/Refund samt Audit.
- Automatische Korrektur ist bei begonnenem Inventar-Handoff gesperrt. Retry nur für passende REVIEW-Fälle; Refund nur für passende SHOP-Fälle vor Handoff. Das ist kein beliebiger „nochmal senden“-Knopf.
- Rückerstattung dort betrifft Ingame-Coins, nicht automatisch eine reale Tebex-Eurozahlung.
- Kein pauschales `DELIVERING -> PENDING`, kein Blind-Refund. Unklare Fälle mit Journal, Spielerdatei und Kontobelegen abstimmen.

### Weitere Economy-Punkte

- Coin-Sync über Redis wurde bearbeitet: eigene Echos vermeiden, bestätigte Balance synchronisieren. Das ersetzt kein gesamtes transaktionales Netzwerk-Ledger.
- Reward-, Vote-, Kit- und Daily-Menüs müssen echte Claim-/Cooldown-Dienste aufrufen, nicht nur dekorative Bestätigung anzeigen.
- Tebex-Rangbestellungen und Rechteänderungen müssen eindeutig, nachvollziehbar und ohne doppelte Vergabe im Spiel ankommen.
- Shop-/AH-Umsätze in Analytics nur aus echten Daten, ohne Vorschauwerte als Umsatz auszugeben.

Quellen: `trade/TradeService.java`, `trade/TradeJournal.java`, `auction/AuctionBuyOrderService.java`, `web/WebDeliveryService.java`, `web/DeliveriesCommand.java`, ältere `ITEM_TRADE.md`, `DELIVERIES_AND_GUIDE.md`, `BUILDS_AND_COIN_SYNC.md`.

## 15. AURON: Ingame-Moderation und Anticheat

### Marke und Report-UI

- Gewählter Name **AURON**; nicht VetroSentinel und nicht „VetroForge AutoMute“.
- Gewünschte Reportbestätigung: `AURON >> Report gesendet: <Spieler>. Das Team wurde benachrichtigt!`
- Reports mit tatsächlichem Kopf des gemeldeten Spielers, mittig angeordneten Gründen und farbigen verständlichen Überschriften.
- Ban/Mute/Kick/Report-Ausgaben konsistent unter AURON. Technische Paket-/Plugin-Namen nicht blind global ersetzen.
- Nutzer meldete `/ban`-Probleme und „Zu viele Aktionen“-Antworten. Command-Routing, Rang, Rate-Limiter und wirksame Core-Konfiguration prüfen; keine aktuelle Live-Reparatur nur aus Branding ableiten.

### AutoMute und Staff-Schutz

- AutoMute reagierte zu schnell: Schwellen, Zeitfenster und Eskalation konfigurierbar und anhand echter Chat-Tests kalibrieren.
- Lesbare, schöne Schrift ohne Gradient: **AURON AutoMute**.
- Teammitglieder sollen einen Bypass besitzen und nicht gemutet werden können. Schutz serverseitig aus verlässlichen Rechten/Rängen ableiten, nicht allein aus sichtbarem Prefix.
- LeonCBR ist laut Nutzer Admin; Rangdarstellung und wirksame Rechte müssen dazu passen.
- Automatische Moderation und manuelle Strafen haben unterschiedliche Kontrollpfade; Staff-Schutz in beiden prüfen, Ausnahmen nicht erfinden.

### Anticheat

- AURON auch als echtes Minecraft-Anticheat, mit Speed-, AutoClicker- und KillAura-Indikatoren.
- `/auron test speed GamemakerYT 1m` soll tatsächlich eine Minute diesen Spieler beobachten. Entsprechende Modi für andere Checks, nicht nur eine simulierte Einzelmeldung.
- Geschütztes Web-Dashboard, nachvollziehbare Test-/Beobachtungsdaten, Ping/TPS/legitime Bewegungsmechaniken berücksichtigen.
- Zunächst mehrere Tage Beobachtungsmodus vor automatischen Strafen; kein „jede Abweichung = Cheat“.
- Wartungs-/Testzustand klar markieren und nicht als echte Spieler-Verurteilung veröffentlichen.

## 16. AURON: geschützte Website-Fälle und Entbannung

Verbindlicher Funktionsumfang aus den bisherigen Iterationen; vorhandene Web-/API-/Migrationsdateien vor Fortsetzung prüfen. Diese Liste ist keine vollständige aktuelle Produktionsabnahme.

- Öffentliche AURON-Seite mit Fairplay-Wegweiser, erlaubten Mods, Regelwerk, Spieler melden und Entbannungsantrag.
- Geschützte Webmeldungen nur für berechtigte/verknüpfte Konten, mit Belegen und persönlichem Status.
- Begonnene Meldungen als Entwurf erhalten; geschützte Screenshotvorschau statt öffentlicher Dateilinks.
- Status: eingegangen, in Prüfung, Rückfrage offen, abgeschlossen.
- Spieler dürfen im eigenen Fall antworten und weitere Belege ergänzen.
- Teamantworten erzeugen persönliche Portal-Glockenmeldungen.
- **Interne Teamnotizen strikt von spielersichtbaren Antworten trennen.**
- Spielerhistorie mit internen Notizen für die vorgesehenen Supporter-/Moderator-/Adminrechte; Fallnummer verlinken.
- Filter nach Fall-ID, Verfasser und Zeitraum; Schnellzeiträume heute, sieben und 30 Tage.
- 90 Tage nach Abschluss Belege automatisch löschen; Löschlauf, Reopen und Einspruchfristen ausdrücklich nachvollziehbar halten.
- Verknüpfter gebannter Spieler sieht seinen tatsächlichen Ban im Portal und kann genau dazu einen Antrag stellen. Andere Spieler dürfen diesen privaten Datensatz nicht sehen.
- „Antrag angenommen“ ist nicht automatisch „Minecraft-Ban entfernt“. Zweistufige Aktion **Annehmen und entbannen** nur mit bestätigtem Backend-Ergebnis.
- Vier-Augen-Freigabe für permanente Cheating-Bans bzw. deren kritische Entscheidungsabläufe; ein zweites, anderes Teammitglied muss zustimmen. Exakten Geltungsbereich im aktuellen Code prüfen.
- Einspruch gegen eine abgeschlossene Entscheidung mit Begründung und anderer zuständiger Person.
- Vollständige Timeline aus Ban, Rückfragen, Belegen, Entscheidung und Audit; private Auditdetails nicht ungefiltert an Spieler ausliefern.
- Feste Fallzuweisung, sichtbare Bearbeitungsfrist, SLA-Erinnerungen bei sechs/zwei/null Stunden, Qualitätsprüfung zufällig ausgewählter abgeschlossener Fälle.
- AURON Analytics: SLA-Quote, Bearbeitungsdauer, Fälle je Bearbeiter, QA-Abweichungen; Zugriff begrenzen und Zahlen ehrlich definieren.
- Öffentlich: Gesamtzahl gebannter Spieler und anonymisierte Sanktionskurve, Zeitfilter sieben/30/90 Tage, Regelnummern direkt ins Regelwerk verlinken. Keine Veröffentlichung sensibler Teamdaten oder privater Belege.

Fundstellen unter anderem `web-portal-live/auron/`, `appeal/`, `staff/` soweit im jeweiligen Stand vorhanden, `includes/auron-*.php`, `scripts/migrations/auron-*.sql`, `scripts/auron-retention.php`, Factions `punish/` und WebAPI sowie VetroCore. Vor Änderungen den konkreten Pfad auflösen.

## 17. Ränge, Permissions, Commands und Chat

### Festgelegte Rangfarben

| Rang | Lokale Palette des Releases 23.09. |
| --- | --- |
| Kein Rang / Spieler | Grau `#aaaaaa` |
| Admin | Helles Rot `#ff5555`, nicht dunkles `§4` |
| Moderator | Türkis `#55ffff` |
| Supporter | Grün `#22cc77` |
| Vanguard | Orange `#ff8a3d` |
| Apex | Pink `#ff55ff` |
| Ember | Hellgrün `#a8ef75` |
| Vetro+ | Hellblau `#7fd4ff` bis Orange `#ff8a3d` |
| Obsidian | Lila `#aa55dd` |
| Developer | Dunkelblau `#355cbb` |
| Builder | Goldgelb `#ffcc55`, unterscheidbar von Vanguard |

Owner gehört zur zu prüfenden Rechtematrix; keine hier nicht festgelegte neue Owner-Farbe erfinden. Farbdarstellung allein darf keine Moderationsrechte verleihen. Im lokalen Core bleibt Developer trotz erkannter Darstellung ohne neue implizite Moderationspriorität.

### Permissions

- Vollständige Soll-Matrix für Owner, Admin, Moderator, Supporter, Developer, Builder und Spielerränge.
- Vererbung, alte Einzelrechte und wirkliche effektive Rechte prüfen; keine pauschalen Wildcards als „Reparatur“.
- Webmatrix soll ihre Daten aus VetroPerms/API beziehen. Das verhindert nicht automatisch jeden Cache-/Versionsfehler; Aktualität sichtbar machen.
- `/vp doctor`-GUI: Spieler wählen, effektive Rechte, Soll-Rang, Abweichungen, kontrollierte Reparatur.
- Online-Spieler-Gesamtscan mit Warnung bei Abweichungen.
- Zweistufig bestätigter Reparaturknopf mit Audit und Vorher-/Nachher-Vergleich.
- Audits nach Spieler, Bearbeiter, Zeitraum filtern. Privilegierte und normale Konten, nicht nur OP, testen.

### Commands und Anwesenheit

- `/spawn` direkt, nicht nur `/lobby spawn`.
- `/fly`, `/gm 1`, `/gm 2`, `/gm 3`, `/day`, `/night`, `/rain`, `/weather clear|rain|donner` mit passenden Rechten.
- Join/Leave-Meldungen nur für Teammitglieder erzeugen; normale Spieler sollen keine allgemeinen Join-/Leave-Meldungen verursachen. Empfängerkreis der Teamhinweise mit der tatsächlichen Konfiguration abgleichen.
- Umlaute und sichtbare Texte über die relevanten Menüs/Commands hinweg vereinheitlichen.

### Chat-Hover: genauer Nutzerauftrag

```text
[Admin] GamemakerYT
Level 13 · Prestige Gold I
──────────────
Faction: Team · Leader
Elo: 940
PvP: K/D 0,00 (0 Kills · 20 Tode)
Serie: 5 Kills
──────────────
▸ Klick: Nachricht senden
```

- XP-Level zeigen; Prestige als Stufenname, nicht als interne numerische Prestigehöhe.
- Beschriftungen grau, Werte weiß, Rang hell und lesbar; Prestige in Tierfarbe, Gold zum Beispiel `#ffaa00`.
- Serie erst ab drei Kills; sonst ganze Zeile weglassen. Frostakzent `#7fd4ff`, Feuer `#ff8a3d` passend zur Faction, sonst Frost.
- K/D deutsch mit Komma und zwei Nachkommastellen; bei null Toden K/D = Kills, keine Division durch null.
- Fehlende Werte `–`; ohne Faction `Faction: –` ohne angehängte Rolle.
- Bestehende Klickaktion für `/msg` erhalten.
- Texte, Farben, Templates konfigurierbar. Beide Trennlinien abschaltbar, standardmäßig an. Tooltiprahmen/-hintergrund sind clientseitig.
- Nicht nebenbei Permissions, Economy oder äußeres Chatformat umbauen.
- Abnahmefälle: Admin mit Serie fünf, Spieler ohne Faction, Spieler mit null Toden.

## 18. Einheitliches Ingame-GUI-Design

Nutzerbrief für Elo-/Perk-Shop und `/f`: klassische Chest-GUIs mit **9 Spalten und 6 Reihen, 54 Slots**. Cyan/Türkis auf Grau, bewusster blauer Rahmen.

- Nicht verschiedene Funktionen wahllos nebeneinanderstreuen.
- Kategorie-Tab-/Kopfzeile, zusammenhängende Kategorieblöcke, farblich konsistente Rahmen/Trenner.
- Pro Block ein klarer Header, beispielsweise Banner mit Glow und erklärender Lore.
- Bestehende Fachkategorien übernehmen: RaidShop Angriff, Verteidigung, Mobilität, Hilfsmittel; tatsächliche Elo-Shop-Kategorien wie Defense/Identify im Code prüfen.
- Semantische Icons: Schild für Schutz, Schwert für Angriff, Kompass/Fernrohr für Erkundung/Identifikation, Feder/Stiefel für Bewegung, Truhe für Lager, Buch für Chronik.
- Tooltipfolge: farbiger Name, kurze Funktion, Voraussetzungen, Preis mit richtiger Währung, Zustand aktiv/gekauft/gesperrt, klare Aktion.
- Echte Spieler-Köpfe aus UUID-/Skinprofil; dekorative Köpfe mit geprüfter eigener Textur. Keine leeren Steve-Skulls als endgültige UI.
- Header/Status-/Zurück-Positionen einheitlich; Vorschaukarten niemals als echte herausnehmbare Items.
- Klickschutz umfasst Shift, Zifferntasten, Doppelklick und Drag; Rechte und Besitz beim Ausführen erneut prüfen.
- Der Nutzer wollte ein konkretes Slotlayout, Icons, Lore-Schema, technische Skin-Erklärung und ein Mockup. Diese Wünsche nicht mit einem bloßen Farbwechsel als erledigt betrachten.

## 19. MapBuilder, Spawn und Architektur

### Grundidee und Bildreferenzen

- Feuer-/Lava- und Frostwelt mit Wegen, Brücken, Festungen, Häusern, Vulkan, Eislandschaft und Frostal-Kristall.
- Frühere Gesamtplanung circa 2400 × 3000 Blöcke. Tatsächliche Generatorgrenzen/Importdaten vor Verwendung prüfen.
- Der Nutzer kann nicht selbst bauen und möchte automatischen, wiederholbaren Aufbau über ein eigenes Plugin/JAR.
- Detaillierte Referenzen ernst nehmen, aber keine blockgenaue 1:1-Rekonstruktion aus einem KI-Perspektivbild versprechen. Verdeckte Geometrie, Licht und Maßstab sind daraus nicht eindeutig bestimmbar.
- Tatsächlich erzeugte Blockgeometrie rendern und prüfen. Konzeptbild, lokale Welt, JAR, Serverimport und Ingame-Abnahme getrennt behandeln.
- Modular speichern/aktualisieren, damit einzelne Gebiete nicht die gesamte Welt oder Spielerbauten überschreiben.

### Aktuell verbindlicher Spawn

- Frost vorne, Feuer hinten. Keine Links-/Rechts-Aufteilung mehr.
- Keine schwebende VetroForge-Schrift.
- Breite gut erkennbare Wege und Durchgänge, erkundbare Räume und obere Ebenen.
- Verschiedene Gebäude/Funktionen, nicht nur identische Hallen: etwa Schmiede, Gasthaus, Bibliothek/Galerie, Gilden-/Besprechungsräume und kleine Entdeckungen.
- Hinteres großes Gebäude mit riesiger Eingangshalle und offenem Durchgang.
- Obere Überführung/Galerie über die bestehenden Wehrgänge erreichbar, mit Brüstung und ausreichender Kopffreiheit.
- Eigenständige Feuer-/Frostarchitektur, Banner, Köpfe, passende Beleuchtung, Feuerdetails, Pflanzen und Möblierung.

### Vulkan und Warzone-Deko

- Sichtbare Lava, die vom Vulkan herunterläuft.
- Begehbares Vulkaninnere mit Lavasee/-fluss und fünf Kisten.
- Gewünschter Zugang zu den Kisten nur durch Enderperlen-Überquerung; andere Umgehungen und Belohnungsbalance live prüfen.
- Schönere Brücken, Pfade und Festungen; Knochen-/Skelettdekoration und ein großes liegendes Dinosaurierskelett waren konkrete Dekowünsche.
- Feuer und Lava dürfen Spielerwege, Spawn-Sicherheit und Performance nicht unbeabsichtigt zerstören.

### Vorhandene eigenständige JAR

- `map-builder/releases/VetroMapBuilder-3.2.2-preview.jar` ist lokal vorhanden; SHA-256 am 24.09. erneut geprüft, siehe Abschnitt 25.
- Abnahmebericht `map-builder/verification/ABNAHME-3.2.2-preview.md`: hintere Torhalle 59 Blöcke breit, Durchgang 17 Blöcke, obere Galerie und Brücke.
- Dokumentierte Geometrieprüfung: 16 Ziele erreichbar, zwei Blöcke Kopffreiheit, Hauptdurchgang, Chunkgrenzen und Migration von Version 2.
- Spawn-Version 3 für diese Erweiterung; ältere Vorlage bleibt erhalten. Umbau per `/mapsetup spawn` und `/mapsetup confirm`, vorher Weltbackup.
- Undo-Pfad und alte Generatorvorlagen nicht entfernen. Kein Live-Deployment und keine neue Ingame-Abnahme in diesem Preview-Bericht.
- Echte Vorschau: `map-builder/verification/spawn-v6/spawn.png`. Frühere Nutzerreferenzen liegen unter `verification/spawn-v4/`, `spawn-v5/` und dem Desktop-Mapordner.
- Admin-Draufsicht mit gebauten Modulen, Grenzen und Kristallzustand wurde als gewünschter Ausbau besprochen; API-/UI-Anschluss separat prüfen.

## 20. Story: The Frostal Ice

- Eigenständige VetroForge-Story um **The Frostal Ice**, nicht nur austauschbare Eventtexte.
- Riesiger Frostkristall in einer Eishöhle als Story-Ort, Wahrzeichen, Warzone-Hotspot und Bossarena.
- Welt soll Season-Fortschritt zeigen: Kristallzustände **schlafend, erwacht, gebrochen**.
- `/mapsetup frostal` als vereinfachter Platzierungs-/Setupablauf war gewünscht; Standort nur aus geprüfter Grundkarten-/Modulkonfiguration ableiten.
- Saisonaler Boss, dessen Phasen durch Disparity und Warzone-Kontrolle beeinflusst werden.
- Bedeutende Raids, erste Bosskills und wichtige Forschungsentscheidungen sollen automatisch in Story und Season-Archiv erscheinen.
- Season Story mit Gewinnern, Rekorden, größten Raids und seltensten Items.
- Ereignisse brauchen stabile IDs, nachvollziehbare Quelle und Deduplizierung. Kein nachträglich erfundener „erster Kill“ aus unvollständigen Logs.
- Öffentliche Story und private Team-/Spielerdaten getrennt halten. Nicht alle genannten Story-Automatismen sind durch diese Übergabe als fertig bestätigt.

## 21. Website, Portal, Community und Branding

### Gestaltung und Einstieg

- Moderne, übersichtliche Website; stimmige Einrückungen und Abstände, keine abgetrennten Buchstaben oder fehlerhaften Hintergrundflächen.
- Einheitliches VetroForge-Design für Favicon, VF-Logo, Footer, Statusseite, News und Portal.
- Neues Logo auf **VF** aufgebaut statt des ungeliebten alten Vetro/Forge-Schriftzugs.
- Vote-Banner in **150 × 100** und **400 × 60** wurden angefragt. Vor Verwendung endgültige Assets und Lesbarkeit prüfen.
- News für Spieler: Events, Seasons, Patch Notes, Gewinner. MySQL-/Backend-/Auditdetails in separaten Dev-Log.
- Nur Fertiges als verfügbar ankündigen, echte Termine, passende unterschiedliche Bilder.
- Hero: IP kopieren als Hauptaktion, Discord als zweite; Version/Java-Bedrock-Unterstützung tatsächlich prüfen und nennen; Beitritt in drei Schritten erklären.
- Keine internen Begriffe wie „Profile im Cache“ als Werbe-Statistik. Leere/nicht aussagekräftige Statistikblöcke sinnvoll ersetzen, keine Zahlen erfinden.
- Warzone nicht gleichzeitig im Hero als verfügbar versprechen und im Spielebereich „bald“ nennen.
- Shop nicht an jeder Stelle wiederholen; kompakte, mobile bedienbare Marktplatzkarten.
- Navbar vor jedem Deploy auf Desktop, Tablet und Mobil prüfen, einschließlich angemeldeter Admin-/Spieleransicht.

### Spielerprofil und Benachrichtigungen

- Minecraft-Kopf aus UUID/Skin, Ingame-Name und Rangfarbe zuverlässig; Discord-Avatar daneben.
- Minecraft-Verknüpfung per Ingame-Code und Discord-Linkcode mit tatsächlicher Rollen-Synchronisation.
- Letzter Sync und verständlicher Fehlerstatus, nicht bloß ein dekorativer „verbunden“-Text.
- Einstellungen nach Profil, Discord, Datenschutz und Benachrichtigungen gliedern.
- Portal-Glocke für Verkäufe, Raids, Faction-Ereignisse, Wartungen, Forschung und AURON-Teamantworten.
- Datenschutz, Besitzprüfung, Opt-ins, Deduplizierung und veraltete/erledigte Meldungen berücksichtigen.
- Angemeldetes Spielerportal soll relevante Identität und Aktionen statt erneut generischem Werbe-Hero zeigen; frühere UI-Fertigmeldungen separat live verifizieren.

### Community-Inhalte und Admin

- Forum: oben rechts Plus/„Post veröffentlichen“, Entwürfe sichern.
- Wiki, Regelwerk, Story, AURON, Items, Factions und Spielhilfen sinnvoll verlinken.
- Adminbereich kompakt mit Luft zwischen Modulen, dezenten Animationen, einheitlichen Buttons, Skeletons, Fehler- und Offline-Zuständen.
- Admin-Startseite mit verschiebbaren/ausblendbaren Karten.
- Analytics mit echten Shopumsätzen, AH-Verkäufen, Raids und aktiven Spielern. Zeiträume/Metriken klar definieren.
- Topdown-Warzonekarte mit Zoom, Verschieben, Legende und separaten Filtern für Kills, Chests, Lootdrops, Bosse und Disparity.
- Sensible aktuelle Spielerpositionen ausschließlich im berechtigten Adminbereich, nicht in öffentlichen Karten-/Status-APIs.
- Statuspunkt animiert bei gesundem Betrieb, Ausfall rot, unbekannt neutral; keine fehlenden Daten als „alles online“ darstellen.

### Tebex und Mail

- Shop: Ränge Ember, Vanguard, Obsidian, Apex, Vetro+. Produkttexte, Vorteile, Reihenfolge, Preise, Rangzuweisung und tatsächliche Ingame-Leistung abgleichen.
- Ein später gesendeter Shop-Screenshot wurde vom Nutzer als versehentlich eingeordnet; daraus nicht stillschweigend einen neuen Komplettumbau ableiten.
- Die vorhandene GitHub-Datei `TEBEX_RANK_CHECKLIST.md` ist ein zusätzlicher, hier nicht vollständig geprüfter Arbeitsstand.
- Aus älteren Projektnotizen: feste administrativ verwaltete Mailadressen, Webmail nur für Team/Admin; keine Wiederbelebung freier Spieler-Mailbox-Erstellung ohne neue Entscheidung. Aktuelle Mail-Konfiguration und externer Versand/Empfang bleiben separat zu verifizieren.

## 22. Wartungsmodus, Kalender und Status-Synchronisation

- Gemeinsame Wartungsplanung für Minecraft/MOTD, Website, Discord und Portal-Glocke.
- Kalender, Vorlagen für Update/Hotfix/Neustart, Vorschau und Änderungshistorie.
- Testmodus veröffentlicht eine gekennzeichnete Vorschau, ohne Spieler zu sperren.
- Countdown bei 30, zehn, fünf und einer Minute.
- Maintenance-Modus und sichere Freigabe im Adminpanel; Rechte, Ausnahmen, Bestätigung und Audit.
- Geplante, aktive, verlängerte und abgeschlossene Wartung unterscheiden; Zeit und Zeitzone konsistent.
- Discord-Status-Nachrichten nach Möglichkeit aktualisieren, nicht bei jeder Änderung neue Nachrichten spammen.
- Externer Status-Fallback unabhängig vom Minecraft-Root, damit ein Root-Ausfall weiterhin erkennbar ist.
- Vorhandene `maintenance/`, `motd/`, Core-Module, Web-Statusdienste und Discord-Worker zuerst zusammenführen; keinen zweiten widersprüchlichen Wartungszustand erfinden.

## 23. Gemeinsame Fehlerübersicht `/errors`

Nutzerproblem: Die alte Chat-Ausgabe zeigte „Keine Fehler protokolliert“, obwohl echte Probleme bestanden, und war gestalterisch unbrauchbar.

### Lokal implementiertes Release vom 23.09.

- Paper-WARN/ERROR aus `logs/latest.log`, inklusive Rotation und dauerhaftem Lesefortschritt.
- Persistentes Journal mit bis zu 100 aggregierten Einträgen, Zeitpunkt und Wiederholungen.
- Häufige Credential-Muster werden maskiert. Das ist keine Garantie, dass jeder beliebige Freitext frei von Geheimnissen ist.
- PHP-Fehler-Outbox außerhalb des Webroots unter `../storage/system-errors`, bis zu 1.000 Einträge; Verzeichnis 0700, Dateien 0600.
- Wiederholbare Übertragung mit Meldungs-ID; erst passende dauerhafte API-Bestätigung entfernt eine Meldung.
- Versand wird durch Website-Aufrufe angestoßen, höchstens alle zehn Sekunden, maximal fünf Meldungen je Durchlauf. **Kein eigenständiger Hintergrundversand ohne Aufrufe.**
- `/errors`: geschützte 54-Slot-GUI mit Quelle, Schweregrad, Seiten, Zeiten und Details.
- Web-Admin: Quellen-/Schweregradfilter, Suche, Aktualisierung alle zehn Sekunden, Offline-/Warteschlangenzustand.
- Ingame `vetroforge.admin`, Website Web-Admin; Minecraft-API verweigert bei fehlender Auth-Konfiguration den Zugriff.

Grenzen: Browser-JS-, nginx- und PHP-Startfehler vor dem gemeinsamen Bootstrap werden damit nicht automatisch erfasst. Journalhistorie ist nicht gleich aktueller Gesundheitsstatus. Nach Recovery reale kontrollierte Testfehler beider Quellen sowie Zugriff als normaler Spieler prüfen.

Fundstellen: `manager/ErrorHandlerManager.java`, `ServerErrorLogReader.java`, `DiagnosticText.java`, `commands/ErrorsCommand.java`, `web/WebApiManager.java`, Website `includes/system-errors.php`, `api/admin-errors.php`, `assets/js/admin-errors.js`.

## 24. API, MySQL, Redis, Backups und Betriebsrisiken

- Website und Minecraft-WebAPI stabil verbinden; Authentifizierung, Timeouts, Wiederholungsstrategie und verständliche Offlinezustände.
- MySQL-Verbindung, Migrationen, Wiederanlauf, Datenintegrität und Backups verifizieren, nicht nur erfolgreiche Verbindung.
- Redis-/Coin-Sync, Ranking-Cache und Portal-Cache nicht mit autoritativer Datenbank gleichsetzen.
- Lokalen Tunnel zuverlässig erneuern oder abgesicherten HTTPS-Proxy betreiben; öffentlich keine ungeschützte Admin-API.
- Externe Statusseite unabhängig vom Minecraft-Root; DNS/TLS/Proxy und Dienstabhängigkeiten beim Recovery prüfen.
- Vollständiges Backup umfasst passende Welt-, Plugin-, Config-, Spieler- und Datenbankstände. Eine JAR oder das selektive Webpaket ist **kein Serverbackup**.
- Bei Zahlungen/Items keine alte Datenbank blind über neuere Inventare legen und umgekehrt; sonst Verlust oder Duplikation möglich.
- Bestehende Trade-/Delivery-/Vault-/Weekly-/Season-Pending-Journale aufbewahren und vor Wiederholung auswerten.
- Alte Deploy-Skripte können Upload, Remap-Bereinigung und Neustarts enthalten. Nicht zur Diagnose ausführen, ohne sie zu lesen und Freigabe zu haben.
- Historische Backup-Pfade aus Notizen sind Suchhinweise, keine geprüften heutigen Restore-Kandidaten: beispielsweise `/var/backups/vetroforge-deliveries-20260913-012525`, `/var/backups/vetroforge-weekly-20260913-014751`, `/var/backups/vetroforge-trade-20260913-021825`.
- Der lokale Git-Status zeigte beim Übergabecheck unversionierte Projektdateien. Nicht `git add .` ausführen: hier liegen auch Daten, Artefakte, alte Kopien und potenziell sensible Konfigurationen.
- Im GitHub-Verzeichnis wurde ein Dateiname `aikey` gesehen. **Inhalt nicht geöffnet, kein bestätigter Secret-Fund.** Vor weiterem Teilen prüfen; falls ein echter Schlüssel enthalten ist, separat sperren/rotieren und die Historie kontrolliert bereinigen. Privatheit des Repositories ersetzt keine Geheimnisverwaltung.

## 25. Greifbare Artefakte und nachgewiesene Testgrenzen

### Gameplay-Release vom 23.09.2026

Lokaler Ordner: `/Users/castelijn/Documents/VetroForge/builds/2026-09-23-gameplay-backlog/`.

| Artefakt | Zweck |
| --- | --- |
| `VetroForge-Factions-2026-09-23.jar` | Paper-Factions-Plugin, vorhandene Abhängigkeiten erforderlich |
| `VetroCore-2026-09-23.jar` | Velocity-Core, nicht Paper |
| `VetroForge-Web-2026-09-23.tar.gz` | Nur neun betroffene PHP-/CSS-/JS-Dateien, kein vollständiges Webbackup |
| `config-ergaenzungen.yml` | Selektive Kit-/Hover-Ergänzungen, keine komplette Ersatz-Config |
| `ABNAHME.md` | Details, Grenzen, Tests, offene Live-Prüfungen |
| `SHA256SUMS` | Prüfsummen |

Die JARs enthalten den damaligen gesamten lokalen Modulstand samt vorherigen Änderungen, **keine isolierten Hotfix-Patches gegen die ältere Live-JAR**. Ob heutiger Quellstand, GitHub und JARs übereinstimmen, vor Weiterarbeit vergleichen.

Am 24.09.2026 erneut berechnete SHA-256:

```text
VetroForge-Factions-2026-09-23.jar
2d34ded5bbabf89ca74028877d95da507bbd76ffafbbcdea0578d996b69f7279

VetroCore-2026-09-23.jar
931ea4ec9970c09f67da586e676ddbb3a76a3414e70dbbd10441fcf812482864

VetroForge-Web-2026-09-23.tar.gz
97d2c3fb7abfa0653f1154e2597adb2da2a4e58c89ebe82f0b7f5296ffc73b71

map-builder/releases/VetroMapBuilder-3.2.2-preview.jar
878761d1786d76af6ff1f72465a55d6d82b2c04c56e6f40934310b615312b279
```

Dokumentierte Tests des Gameplay-Releases, **nicht heute vollständig erneut ausgeführt**:

- 24 Factions-Java-Tests und 13 Core-Java-Tests erfolgreich.
- 89 isolierte PHP-Chronikprüfungen und zwölf PHP-Fehler-Outbox-Prüfungen, zusammen 101.
- Syntaxprüfung der sechs geänderten PHP-Dateien und beiden neuen JavaScript-Dateien.
- Lokale Browser-Fixtures mit ausdrücklich künstlichen Daten: Filter, Suche, Refresh, Offline/Wiederverbindung, Raid-Leben und Reward-Vorschau.
- Kein horizontaler Überlauf bei 1280 und 390 CSS-Pixeln in diesen Fixtures; keine vollständige angemeldete Live-Adminabnahme.
- Artefaktklassen zusätzlich geprüft. **Upload, Live-Laden und Ingame-Abnahme des Pakets wurden von Codex nicht durchgeführt.**

Die Zahlen gelten nur für diesen dokumentierten Prüfumfang. Sie belegen nicht automatisch vollständige Sicherheit aller lokalen Research-/Trade-/AH-Erweiterungen.

### Weitere wichtige Lesepfade

- `VetroForge-Projektwiki.md`: Gesamtwünsche und früherer Planungsstand, Stand 21.09.
- `FEATURE_ROUND_MARKET_CONVOY_RESEARCH.md`: Konvoi/Forschungsrat; Forschungskapitel teilweise vom aktuellen Code überholt.
- `factions-repair/SEASON_ARCHIVE.md`: Archivschema, Reset-Schutz und historische Nachweise.
- `factions-repair/ITEM_TRADE.md`, `DELIVERIES_AND_GUIDE.md`, `WEEKLY_CONTRIBUTIONS.md`, `FACTION_VAULT.md`: frühere Funktionsgrenzen; spätere Erweiterungen dagegen prüfen.
- `map-builder/README.md` und `map-builder/verification/ABNAHME-*.md`: Generatorversionen und echte Geometrieprüfung.
- `LIVE_DEPLOY_RUNBOOK.md`: Betriebsgrundlage, vor Anwendung auf heutigen Stand prüfen.
- `MAY_JUNE_FEATURE_PARITY.md` / `MAY_PARITY_AUDIT.md`: historische Bestandsaufnahme, keine aktuelle offene Aufgabenliste ohne Abgleich.
- `scripts/qa/`: vorhandene isolierte Prüfungen; Browser-Fixtures nicht produktiv deployen.

## 26. Obsidian und gemeinsames Projektwissen

- Gewünscht ist ein gemeinsames VetroForge-Wiki für Plugins, Map, Website, AURON und offene Aufgaben, das Claude und Codex lesen können.
- Bestehende Obsidian-Notizen nicht durch eine neue Zusammenfassung ersetzen. Diese Übergabe ergänzend verlinken.
- Bekannte Bereiche/Notizen: VetroForge Website, Factions/Gameplay, Permissions, AURON, Abnahme sowie „VetroForge Startseite - Änderungsliste“.
- Aufgaben nach Priorität und Bereich zusammenfassen; P1 in Chronik, Admin-Redesign, Content und andere konkrete Arbeitsblöcke mit Reihenfolge aufteilen.
- Ein Dashboard soll offene Aufgaben automatisch zeigen. Nicht alle Checkboxen auf „fertig“ setzen, wenn nur Planung oder lokaler Build vorliegt.
- Jede wichtige Entscheidung mit Datum und eventueller ersetzter Vorgabe festhalten, insbesondere Kit-Crystals, Spawn-Ausrichtung, AURON und Löschfrist.

## 27. Reihenfolge nach Abschluss der Wiederherstellung

Vorschlag zur Arbeitsaufteilung, keine automatische Deploy-/Restore-Freigabe:

1. **P0: Recovery mit Claude abschließen.** Betroffene Daten, Ursache, aktiven Restorestand und erhaltene Journale festhalten; keine parallelen Schreibaktionen.
2. **P0: Quellen und laufende Artefakte zuordnen.** GitHub, lokale Module, JAR-Hashes, Configs und aktuelle Ladeprotokolle vergleichen; bekannte gute Basis markieren.
3. **P0: Daten-/Zugriffssicherheit.** Zahlungen, Inventare, Rechte, Admin-APIs, private AURON-Fälle und mögliche Geheimnisse prüfen.
4. **P1: Kernspiel mit echten Konten abnehmen.** Join/Spawn/Fly, Warzone-PvP, vollständiger Raid mit Leben/Respawn/Basekopie, TNT-Untergrundschutz.
5. **P1: Economy-Prüfungen.** Starterkit/Crystals, Handel mit Coins, Teilmengen-Kaufgesuche, Zustellfach, voller Inventarfall, Rejoin, Konkurrenz und Crashzustände.
6. **P1: Faction-Datenweg.** Gründung/Beitritt/Austritt/Beförderung/Leaderwechsel/Umbenennung bis Chronik, Website, Forschung und Ranglisten.
7. **P1: Moderation und Rechte.** AURON-Namen, wirkliche Ban-/Mute-Funktion, Team-Bypass, Reports, eigene Bans, Antrag und bestätigte Entbannung mit zwei Prüfern.
8. **P1: Fehler-/Statuskette.** Kontrollierte Spiel-/PHP-Fehler, API-Ausfall, Admin-/Spielerzugriff, Wartungskanäle und unabhängiger Status.
9. **P2: Konvoi/Forschung/Map.** Echte Route und Rewards freigeben, Forschungsspielbarkeit/Balance prüfen, Map-Module zunächst in Testwelt abnehmen.
10. **P2: Politur und neue Inhalte.** Menüs, Portal, News, Operations Center, Rekrutierung, Season Story und Analytics anhand der stabilen Datenquellen ausbauen.

## 28. Vorschlag für laufende Übergaben zwischen Claude und Codex

Ein gemeinsames GitHub-Issue als Abstimmungsprotokoll, getrennte Arbeitszweige pro Änderung, keine gegenseitigen ungeprüften Deploys.

Für jeden Arbeitsblock knapp eintragen:

```text
Bereich / Verantwortlich:
Ausgangsstand / Commit / JAR-Hash:
Geänderte Dateien:
Umsetzung:
Automatische Tests:
Build-Artefakte:
Upload: nein / wann und wohin
Geladen: nein / frischer Lognachweis
Ingame-Abnahme: offen / konkrete Fälle und Ergebnis
Datenmigration / Backup / Rückweg:
Offene Risiken / nächster Schritt:
```

**Nächste sinnvolle Verbesserung:** ein gemeinsames Release- und Abnahme-Dashboard, das pro Bereich diese sechs Stufen zeigt: geplant, implementiert, getestet, gebaut, geladen, mit echten Spielern abgenommen. Damit werden weder alte lokale Stände für live gehalten noch bereits vorhandene Funktionen erneut von Grund auf gebaut.
