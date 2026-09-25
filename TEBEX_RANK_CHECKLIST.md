# VetroForge Tebex Rank Checklist

Diese Checkliste ist fuer Tebex `VetroForge Factions` gedacht. Preise sind Vorschlaege/aktueller Stand aus dem Rank-Konzept.

## Globale Tebex-Settings

- Store: `VetroForge Factions`
- Waehrung: EUR
- Kategorie: `Ranks` (`https://creator.tebex.io/categories/3354292`)
- Typ: `Cumulative`, damit Upgrade-Pfade automatisch nur die Preisdifferenz berechnen
- Minecraft Username required: ja
- Delivery: Command on purchase
- Refund-Hinweis: digitale Inhalte, serverseitige Freischaltung
- Beschreibung immer mit `Keine Pay-to-win Kampfwerte, Fokus auf QoL/Cosmetics/Komfort.`

## Aktueller Tebex-Stand

- Kategorie `Ranks` ist angelegt und auf `Cumulative` gesetzt.
- Upgrade-Pfad laeuft damit automatisch: `Ember -> Vanguard -> Obsidian -> Apex -> Vetro+`.
- Paketbilder wurden dauerhaft im Repo unter `assets/tebex/ranks/` abgelegt.
- Fuer das Web-Dashboard liegen dieselben Bilder unter `web-dashboard/public/assets/tebex/ranks/`.
- Upload-Status wird in `assets/tebex/media-status.json` gepflegt und fuer `/admin/tebex` nach `web-dashboard/data/tebex-media-status.json` gespiegelt.
- Der Datei-Upload im eingebetteten Browser nimmt aktuell keine automatischen File-Dialog-Eingaben an. Falls die Warnsymbole in Tebex bleiben, die PNGs manuell im jeweiligen Paket unter `Media -> Choose File` hochladen und `Update` klicken.
- Nach manuellem Tebex-Upload pro Paket `uploaded` in beiden JSON-Dateien auf `true` setzen.

| Rank | Tebex Package | Bild |
| --- | --- | --- |
| Ember | `https://creator.tebex.io/packages/7499369` | `assets/tebex/ranks/ember.png` |
| Vanguard | `https://creator.tebex.io/packages/7499375` | `assets/tebex/ranks/vanguard.png` |
| Obsidian | `https://creator.tebex.io/packages/7499376` | `assets/tebex/ranks/obsidian.png` |
| Apex | `https://creator.tebex.io/packages/7499377` | `assets/tebex/ranks/apex.png` |
| Vetro+ | `https://creator.tebex.io/packages/7499378` | `assets/tebex/ranks/vetroplus.png` |

## Permission-System

Die Ranks werden ueber VetroPerms/VetroCore gepflegt. Tebex soll nicht einzelne 20 Permissions verteilen, sondern den Rang setzen.

Empfohlener Tebex-Ausfuehrungscommand:

```text
vperms user {username} setrank <rank>
```

Fallback, falls VetroPerms-Command anders heisst:

```text
lp user {username} parent set <rank>
```

## Rank Pakete

### Ember - 9,99 EUR

Tebex Name:

```text
Ember Rank
```

Command:

```text
vperms user {username} setrank ember
booster give {username} coin 1
booster give {username} raid 1
```

Bei den beiden `booster give` Zeilen in Tebex "Only execute when player is online" aktivieren - der Befehl schlaegt sonst fehl, Tebex liefert ihn dann automatisch nach, sobald der Spieler online kommt.

Permission Nodes:

```text
vetroforge.rank.ember
vetroforge.pv
vetroforge.kit.daily
vetroforge.homes.extra.1
vetroforge.cosmetics.small
```

Inhalt:

- Daily Kit Ember
- `/pv 1` mit 8 nutzbaren Slots
- Chat-Prefix `Ember`
- 1 extra Home
- kleine Cosmetics
- 1x Coin Booster + 1x Raid Booster einmalig bei Kauf

Tebex Kurzbeschreibung:

```text
Starte mit Ember: Daily Kit, erster Private Vault, Chat-Prefix und kleine Cosmetics.
```

### Vanguard - 24,99 EUR

Tebex Name:

```text
Vanguard Rank
```

Command:

```text
vperms user {username} setrank vanguard
```

Permission Nodes:

```text
vetroforge.rank.vanguard
vetroforge.pv
vetroforge.kit.daily
vetroforge.kit.weekly
vetroforge.kit.monthly
vetroforge.homes.extra.2
vetroforge.cosmetics.joinparticles
vetroforge.shop.discount.qol
vetroforge.fly.faction
```

Inhalt:

- Daily + Weekly Kit
- `/pv 1-2` mit je 18 nutzbaren Slots
- 2 extra Homes
- Join-Partikel
- kleine QoL/Shop-Rabatte ohne Kampf-Vorteil
- `/fly` in der eigenen Fraktionswelt (aus im Kampf, aus wenn die Fraktion geraidet wird, aus außerhalb der eigenen Welt - Details unten unter "Fly ab Vanguard")
- Monthly Kit ab jetzt auch fuer Vanguard, gibt 1x Coin Booster

Tebex Kurzbeschreibung:

```text
Mehr Komfort fuer aktive Spieler: Weekly Kit, zwei Vaults, Join-Partikel und QoL-Boni.
```

### Obsidian - 49,99 EUR

Tebex Name:

```text
Obsidian Rank
```

Command:

```text
vperms user {username} setrank obsidian
```

Permission Nodes:

```text
vetroforge.rank.obsidian
vetroforge.pv
vetroforge.kit.daily
vetroforge.kit.weekly
vetroforge.kit.monthly
vetroforge.homes.extra.3
vetroforge.ah.slots.obsidian
vetroforge.profile.badge.obsidian
vetroforge.cosmetics.premium
vetroforge.fly.faction
```

Inhalt:

- Daily + Weekly Kit
- `/pv 1-3` mit je 27 nutzbaren Slots
- 3 extra Homes
- mehr AH-Slots
- Obsidian Profil-Badge
- Premium Cosmetics
- `/fly` in der eigenen Fraktionswelt (gleiche Regeln wie Vanguard)
- Monthly Kit: 1x Coin Booster + 1x Raid Booster

Tebex Kurzbeschreibung:

```text
Obsidian gibt dir mehr Lagerplatz, mehr AH-Komfort und ein hochwertiges Profil-Badge.
```

### Apex - 79,99 EUR

Tebex Name:

```text
Apex Rank
```

Command:

```text
vperms user {username} setrank apex
```

Permission Nodes:

```text
vetroforge.rank.apex
vetroforge.pv
vetroforge.kit.daily
vetroforge.kit.weekly
vetroforge.kit.monthly
vetroforge.homes.extra.4
vetroforge.ah.slots.apex
vetroforge.profile.badge.apex
vetroforge.cosmetics.trails
vetroforge.cosmetics.wings
vetroforge.fly.faction
```

Inhalt:

- Daily + Weekly + Monthly Kit
- `/pv 1-4` mit je 36 nutzbaren Slots
- exklusive Trails/Wings
- mehr AH-Slots
- Apex Profil-Badge
- `/fly` in der eigenen Fraktionswelt (gleiche Regeln wie Vanguard)
- Monthly Kit: 2x Epic Key, 1x Legendary Key, 2x Coin Booster, 1x Raid Booster

Tebex Kurzbeschreibung:

```text
Apex ist fuer Spieler, die langfristig spielen: Monthly Kit, starke Cosmetics und mehr Komfort.
```

### Vetro+ - 119,99 EUR

Tebex Name:

```text
Vetro+ Rank
```

Command:

```text
vperms user {username} setrank vetroplus
```

Permission Nodes:

```text
vetroforge.rank.vetroplus
vetroforge.pv
vetroforge.kit.daily
vetroforge.kit.weekly
vetroforge.kit.monthly
vetroforge.homes.extra.5
vetroforge.ah.slots.vetroplus
vetroforge.profile.badge.vetroplus
vetroforge.joinmessage.custom
vetroforge.cosmetics.premiumplus
vetroforge.portal.earlyaccess
vetroforge.fly.faction
```

Inhalt:

- `/pv 1-5` mit je 54 Slots
- Daily + Weekly + Monthly Kit
- Premium Cosmetics
- eigene Join-Nachricht
- monatliches Cosmetic/Crystal Bundle
- frueher Zugang zu neuen Portal-Features
- `/fly` in der eigenen Fraktionswelt (gleiche Regeln wie Vanguard)
- Monthly Kit: 2x Epic Key, 1x Legendary Key, 1x Mystic Key, 2x Coin Booster, 2x Raid Booster

Tebex Kurzbeschreibung:

```text
Das komplette VetroForge Premium-Paket: maximale Vaults, Monthly Bundle, Premium Cosmetics und Early Access.
```

## Tebex Einrichtung pro Rank

1. `Packages` oeffnen.
2. Kategorie `Ranks` erstellen, falls sie fehlt.
3. `Create Package`.
4. Name, Preis, Beschreibung setzen.
5. `Commands` hinzufuegen:
   - `vperms user {username} setrank <rank>`
6. Testing:
   - Testkauf oder manuelles Command ausfuehren.
   - Danach ingame pruefen:
     - `/kit`
     - `/pv 1`
     - Tab/Prefix
     - `/help`

## Fly ab Vanguard

Neue Permission `vetroforge.fly.faction` (in `plugin.yml` deklariert, Default `false`). Muss in VetroPerms selbst noch den Gruppen Vanguard/Obsidian/Apex/Vetro+ zugewiesen werden - das liegt außerhalb dieses Repos.

Verhalten (`FlyCommand`, `LobbyProtectionListener`, `FactionsPlugin#canFlyInOwnFaction`):

- `/fly` funktioniert fuer Inhaber der Permission zusaetzlich zum bisherigen Spawn-Bereich auch in der eigenen Fraktionswelt (`faction_<name>`).
- Automatisch aus bei: Verlassen der eigenen Fraktionswelt, aktivem Combat-Tag, laufendem Raid auf die eigene Fraktion. Damit bleibt es ein QoL-Perk und kein Kampf- oder Flucht-Vorteil.
- Fremde Fraktionswelten und Warzone bleiben ohne Fly, genau wie bisher.
- Erledigt: Die Tebex-Produktbeschreibungen von Vanguard, Obsidian, Apex und Vetro+ haben jetzt einen eigenen Abschnitt "Dein Fly-Zugang" mit den beiden Bulletpoints (`/fly` in eigener Fraktionswelt, automatisch aus bei Kampf/Raid). Bei allen vier Paketen gespeichert und ueber "Package successfully updated" bestaetigt.

## Booster & Crystal Pakete

Neue Kategorien in Tebex: `Boosters` und `Crystals`, gleiches Setup wie bei Ranks (Type `Default`, kein Cumulative noetig, da unabhaengige Einzelkaeufe).

Booster-Commands brauchen einen online Spieler. In Tebex bei jedem `booster give` / `give` Command "Only execute when player is online" aktivieren, sonst kommt der Kauf nie an.

Alle Booster/Crystal-Paketbilder sind selbst gezeichnete Minecraft-Style SVGs (per QuickLook zu PNG gerendert) und bereits in die jeweils leeren Media-Slots hochgeladen.

### Coin Booster - 1,99 EUR

```text
booster give {username} coin 1
```

Bild: goldene Muenzen mit Stern-Emblem.

### Raid Booster - 1,99 EUR

```text
booster give {username} raid 1
```

Bild: isometrischer TNT-Block mit Zuendschnur.

### Raid Bundle - 7,99 EUR (Vorschlag)

```text
booster give {username} coin 1
booster give {username} raid 1
give {username} minecraft:tnt 64
give {username} minecraft:ender_pearl 16
give {username} minecraft:dispenser 6
give {username} minecraft:sand 64
```

Sand-Menge war nicht spezifiziert, 1 Stack (64) angenommen - bei Bedarf anpassen. Kein eigenes Bild angefragt/gesetzt.

### Epic Crystal - 4,99 EUR (Vorschlag)

```text
givekey {username} epic 1
```

Bild: facettierter Kristall, violettes Kernglühen.

### Legendary Crystal - 6,99 EUR

```text
givekey {username} legendary 1
```

Bild: gleiche Kristallform, oranges Kernglühen.

### Mystic Crystal - 19,99 EUR (Vorschlag)

```text
givekey {username} mystic 1
```

Bild: gleiche Kristallform, violettes Kernglühen plus violette Partikel/Sparkles rundherum.

Bild-Update: Die erste Version (glatter Vektor-Edelstein) hat nicht wie ein echtes Minecraft-Item ausgesehen. Finale Bilder sind vom Nutzer bereitgestellte, sauber geshadete isometrische Renders (transparenter Hintergrund) - fuer alle 5 Booster/Crystal-Pakete durch die finalen Versionen ersetzt: Coin Booster (Goldmuenze mit $-Symbol), Raid Booster (TNT-Wuerfel), Epic/Mystic Crystal (lila Kreuz-Kern), Legendary Crystal (orange), Mystic Crystal zusaetzlich mit schwebenden lila/orangen Partikel-Splittern drumherum. Alle 5 hochgeladen und gespeichert.

Hinweis: "Crystals" heissen im Code-Backend `Crate Keys` (Rarity Epic/Legendary/Mystic, Nether-Star-Item). Es gibt daneben noch eine komplett andere, unabhaengige Raid-Waehrung `/crystals`, die hier NICHT gemeint ist und aktuell einen eigenen Bug hat (siehe unten).

## Storefront (Tebex Webstore Builder)

Projekt: `VetroForge Shop` (`https://creator.tebex.io/storefront`, Editor: `https://builder.tebex.io/_storefront/edit?projectId=1667947&storefrontId=3aeaa64e-0280-4cb3-8b31-92bb4da3f2ca`). Live-Domain: `https://vetroforgenet.tebex.store`.

- Nach einem Tebex-seitigen Reset (zeitgleich mit einem Nutzer-Upgrade, das Custom-HTML/CSS erst freigeschaltet hat) waren mehrere native Bloecke (Carousel-Bild, Info-Cards, Feature, 2x Custom HTML), das Theme und Header-Logo/Menue geloescht. Entscheidung: nicht den Tebex-Bug weiter untersuchen, sondern alles mit selbst geschriebenen Custom-HTML-Bloecken neu aufbauen (robuster, kein Abhaengen von nativen Widgets) und zusaetzlich lokale Backups fuehren.
- Kanonische Backups aller Custom-HTML-Bloecke liegen unter `assets/tebex/storefront-blocks/*.html` (01-hero, 02-warum-vetroforge, 03-vetroplus-spotlight, 04-discord-banner, 05-cumulative-upgrade, 06-faq, 07-category-intro) - bei jeder Live-Aenderung mitpflegen.
- Theme: Preset `Midnight` (violet `#8b5cf6` / cyan `#22d3ee` auf `#0b0b12`), bewusst weg vom urspruenglichen orangen `Ember`-Preset ("muss modern aussehen und nicht orange braun").
- Home-Layout: Header -> Hero (Custom HTML) -> Trust-Bar (Custom HTML, siehe unten) -> Category List -> Category (nativ) -> Warum-VetroForge (Custom HTML) -> Vetro+-Spotlight (Custom HTML) -> Cumulative-Upgrade (Custom HTML) -> Discord-Banner (Custom HTML) -> FAQ (Custom HTML) -> Creator Code -> Footer.
- Neuer Block `assets/tebex/storefront-blocks/09-trust-bar.html`: kompakte 4-Punkt-Vertrauensleiste direkt unter dem Hero ("Online seit 2025", "Kein Pay2Win - nur Komfort & Cosmetics", "Sofortige Freischaltung nach Kauf", "Aktive Discord-Community"). Bewusst OHNE erfundene/prazise Zahlen (z.B. exakte Discord-Mitgliederzahl oder verkaufte Ranks) - Nutzer hat nur eine grobe Groessenordnung (unter 500 Mitglieder) bestaetigt, dafuer keine belastbare exakte Zahl fuer eine oeffentliche Marketing-Aussage. Falls spaeter eine echte Zahl (Mitglieder, verkaufte Ranks) genannt wird, kann sie hier als fuenfter Punkt ergaenzt werden.
- "Category page"-Template (gemeinsame Vorlage fuer `/category/{id}`, also Ranks/Boosters/Crystals/Packages teilen sich EINE Vorlage): Header -> Category-Intro (Custom HTML, generischer Kein-Pay2Win-Hinweis) -> Category (nativ) -> Footer.
- **JS-Test durchgefuehrt (25.09.2026): JavaScript in Custom-HTML-Bloecken funktioniert NICHT.** Test: temporärer Block mit `<div id="vf-js-test">JS lief NICHT</div><script>...textContent = 'JS lief JA'</script>`, published und im echten Tebex-Preview (`_storefront/preview`, selbe Rendering-Pipeline wie die Live-Domain) neu geladen - Text blieb bei "JS lief NICHT". DOM-Check zeigt: jeder Custom-HTML-Block wird serverseitig in ein eigenes `<iframe sandbox="allow-scripts" srcdoc="...">` gerendert; das Skript hatte trotz `allow-scripts` keine sichtbare Wirkung (Sanitizer entfernt vermutlich `<script>`-Inhalte vor dem Rendern). Test-Block wurde geloescht und der Ursprungszustand neu published.
- **Wichtiger Folgefund (25.09.2026): `<a href>`-Links innerhalb von Custom-HTML-Bloecken funktionieren ebenfalls NICHT als echte Seiten-Navigation.** Grund: das sandboxed Iframe (`sandbox="allow-scripts"`, ohne `allow-top-navigation`/`allow-top-navigation-by-user-activation`) darf das Top-Level-Fenster nicht navigieren - ein Klick auf einen internen Link (`href="/category/ranks"` o.ae.) navigiert stattdessen nur das isolierte Iframe selbst, was im Tebex-Preview als "builder.tebex.io hat die Verbindung abgelehnt" sichtbar wird. Auch `target="_top"` wurde getestet und bringt keine Abhilfe (vom Browser still blockiert, da das Sandbox-Attribut es nicht erlaubt). Betroffen und **wieder entfernt**: die probeweise gebauten eigenen Rank-Karten mit Farb-Glow auf `/category/ranks` (durch das native `Category`-Element mit funktionierendem "Buy Now" ersetzt), die 3 Hero-CTA-Buttons auf der Startseite ("Ranks ansehen"/"Boosters"/"Crystals", ersatzlos entfernt - die native Category-List direkt darunter deckt die Navigation weiterhin ab) und der "Vetro+ ansehen"-Button im Vetro+-Spotlight-Block (ersatzlos entfernt).
  - **Konsequenz fuer die Zukunft:** In Custom-HTML-Bloecken duerfen keine `<a href>`-Links zu anderen Shop-Seiten mehr eingebaut werden, egal wie harmlos sie aussehen - sie sehen im Editor-Screenshot korrekt aus, sind aber im echten Preview/Live-Betrieb tote Links. Navigation zu anderen Seiten (Kategorien, Pakete) funktioniert nur ueber native Bloecke (Header, Category, Category List, Footer-Links).
  - **Selbes Problem, auch externe Links betroffen:** Der Discord-Button im Discord-Banner (`<a href="https://discord.gg/vetroforge" target="_blank">`) war ebenfalls tot - `target="_blank"` wird ohne `allow-popups` im Sandbox-Iframe komplett stillschweigend blockiert (kein Klick-Effekt, nicht mal ein sichtbarer Fehler wie beim internen Link). **Geloest:** Der native Header-Block unterstuetzt unter "Layout -> + -> Link" eigene Navigations-Links mit frei waehlbarer URL (Page ODER externe URL) und einem "Open in new tab"-Schalter - das ist ein natives, nicht-sandboxed Element und funktioniert. Neuer Header-Link "Discord" -> `https://discord.gg/vetroforge` (Open in new tab: an) hinzugefuegt und live getestet (oeffnet echten neuen Tab zu discord.com/invite/vetroforge). Der tote Button im Discord-Banner-Block wurde entfernt, der Banner zeigt jetzt nur noch Text/Infos ohne Button.
  - **Konsequenz:** Keine bedingte/dynamische Logik in Custom-HTML-Bloecken moeglich (kein `document.querySelector`, kein URL-basiertes Ein-/Ausblenden). Die geplante Ranks-spezifische Vergleichstabelle kann NICHT ueber JS auf der gemeinsamen "Category page"-Vorlage nach Kategorie unterschieden werden.
  - **Geloest:** Tebex bietet ueber "Pages -> + -> Category page" die Moeglichkeit, eine Seite an EINE bestimmte Kategorie zu binden (Pfad z.B. `/category/ranks`), die dann Vorrang vor der generischen "Category page"-Vorlage hat. Neue Seite "Category: Ranks" angelegt (gebunden an Kategorie `Ranks`), aktuelles Layout: Header -> Category-Intro (Custom HTML, gleicher Block wie generische Seite) -> Category (**nativ**, mit funktionierendem "Buy Now") -> Rang-Vergleich (Custom-HTML-Block, siehe unten) -> Footer. Boosters/Crystals/Packages laufen weiterhin unveraendert ueber die generische "Category page"-Vorlage (per Preview verifiziert: `/category/boosters` zeigt weiterhin "Default category page").
  - Neuer Block `assets/tebex/storefront-blocks/08-ranks-comparison.html`: vollstaendige Feature-Vergleichstabelle (Kits, Extra Homes, Private Vault Slots, Fly, AH-Slots, Profil-Badge, Cosmetics-Stufe, eigene Join-Nachricht, Early Access) ueber alle 5 Raenge, Vetro+-Spalte farblich hervorgehoben. Reine Datentabelle ohne Links, daher von der `<a href>`-Einschraenkung oben nicht betroffen. Nur auf der neuen "Category: Ranks"-Seite eingebunden, nicht auf der generischen Vorlage. Live verifiziert unter `/category/ranks` und published.
  - **Verworfen:** `assets/tebex/storefront-blocks/10-rank-cards.html` - probeweise gebaute eigene Rank-Karten mit Hover-Glow in der jeweiligen Rang-Farbe (Ember=orange, Vanguard=gold, Obsidian=violett, Apex=blau, Vetro+=violett) als Ersatz fuer das native `Category`-Element. Optisch einwandfrei, aber die Karten-Links (`<a href="/package/{id}">`) waren wegen der Iframe-Sandbox tote Links (siehe Folgefund oben) - die Seite haette dadurch komplett ohne Kaufmoeglichkeit dagestanden. Block wurde wieder durch das native `Category`-Element ersetzt und aus der Seite entfernt; die Datei bleibt nur als Referenz im Repo liegen, ist aber nirgends mehr eingebunden.
- **Ursache fuer den 503 "Store unavailable" auf `https://vetroforgenet.tebex.store` gefunden (25.09.2026):** Der Store wurde noch nie bei Tebex zur Pruefung eingereicht. Unter `https://creator.tebex.io/get-started` ("Continue Setup") sind alle 5 Pflicht-Schritte bereits gruen abgehakt (Configure product delivery, Create a package, Confirm customer support contact, Link Wallet, Create your storefront - "Pending Steps 0/5"), aber der Store bleibt trotzdem offline, bis der Button "Submit for Review" bewusst geklickt und von Tebex genehmigt wurde. Das ist der normale Ablauf bei einem neuen Tebex-Store, kein Fehler auf unserer Seite.
  - **Erledigt (25.09.2026): Auf ausdruecklichen Wunsch des Nutzers eingereicht.** Store-Status ist jetzt "Pending Review": "PENDING REVIEW - PLEASE ALLOW UP TO 2 WORKING DAYS FOR YOUR STORE TO BE REVIEWED". Bis zur Freigabe durch Tebex bleibt `https://vetroforgenet.tebex.store` weiterhin mit 503 offline; der Builder-eigene Preview-Link (`_storefront/preview?...`) funktioniert davon unabhaengig schon jetzt und wurde fuer alle Tests in diesem Dokument verwendet. Nach Ablauf der ~2 Werktage kurz pruefen, ob die Live-Domain erreichbar ist.

## Offene Punkte

- **Erledigt (25.09.2026): Ablauf-Ranks.** Entscheidung: Ranks bleiben dauerhaft (kein Abo-Modell). Kein Tebex-Expiry-Command noetig.
- **Erledigt (25.09.2026): Upgrade-Pfade.** Entscheidung: kein zusaetzliches, eigens benanntes Sprungpaket (z.B. "Ember zu Vanguard") - die Ranks-Kategorie ist bereits auf `Cumulative` gestellt, das automatische Preisdifferenz-Upgrade existiert also schon technisch. Stattdessen soll es nur sichtbarer gemacht werden. Umsetzung: den bestehenden "Cumulative Upgrade"-Erklaerblock zusaetzlich auf der "Category: Ranks"-Seite selbst eingebunden (vorher nur auf der Startseite), direkt zwischen den Rang-Karten und der Rang-Vergleichstabelle - genau dort, wo ein Kunde tatsaechlich eine Kaufentscheidung trifft. Neuer Block `assets/tebex/storefront-blocks/08b-ranks-cumulative-upgrade.html` (identischer Inhalt wie `05-cumulative-upgrade.html`). Live verifiziert unter `/category/ranks` und published. Beim Eintippen kam es einmal zu einem einzelnen ueberschuessigen `<`-Zeichen nach "Vetro+" (Editor-Glitch, sichtbar als kleiner Darstellungsfehler) - ueber direktes Auslesen des Editor-Inhalts per JavaScript (Shadow-DOM `.CodeEditorPanel__editor`) gefunden und exakt korrigiert.
- **Erledigt (25.09.2026): Web-Portal-Sync geprueft.**
  - **Wichtige Preis-Klarstellung:** `web-dashboard/components/TebexAdminPanel.tsx` zeigte weiterhin 9,99/24,99/49,99/79,99/119,99 EUR fuer die Ranks - das ist **korrekt und bereits synchron**, keine Aenderung noetig. Das sind die in Tebex tatsaechlich konfigurierten Netto-Paketpreise (per `creator.tebex.io/packages/<id>` Preisfeld verifiziert), identisch mit den Preisen im Abschnitt "Rank Pakete" oben. Die "€11.89" etc., die auf der Live-Storefront angezeigt werden, sind KEIN eigener Preis, sondern Tebex' automatischer Bruttopreis inkl. 19% MwSt. fuer EU-Kunden (9,99 × 1,19 ≈ 11,89 usw.). Vorsicht in kuenftigen Sessions: nicht die Brutto-Storefront-Zahl mit dem konfigurierten Preis verwechseln.
  - **Bild-Fund und Fix:** `web-dashboard/public/assets/tebex/ranks/*.png` und die Root-Kopie `assets/tebex/ranks/*.png` zeigten noch die alten Platzhalterbilder vom 13. Juni (1200x675, "billig" wirkende Vektor-Variante). Ersetzt durch die tatsaechlich auf Tebex live geschalteten Icons (1254x1254, direkt von der Tebex-CDN heruntergeladen: `dunb17ur4ymx4.cloudfront.net/packages/images/...`).
    - **Vorsicht, Fallstrick entdeckt:** Die Dateien `tebex-ranks/{ember,vanguard,obsidian,apex,vetroplus}-final.png` im Repo-Root (Sep 24, ~2MB, ebenfalls 1254x1254) sehen auf den ersten Blick wie "die finalen Bilder" aus, zeigen aber tatsaechlich ein KOMPLETT ANDERES Motiv (aufwaendige Fantasy-Schloss-Szene im Hintergrund) als das, was wirklich auf der Tebex-Storefront haengt (schlichtes Kristall-Icon auf hellem Hintergrund, ohne Szenerie). Nicht verwenden fuer Paket-/Karten-Icons - Ursprung/Zweck dieser Dateien ist unklar, evtl. eine verworfene fruehere Bildvariante. Die tatsaechlichen Live-Icons kommen nur direkt von der Tebex-CDN (URLs oben, bzw. per DOM-Scrape von `/category/ranks` extrahierbar).
  - Keine Beschreibungstexte im Dashboard vorhanden (Karten zeigen nur Name/Preis/Bild/Command) - nichts zu synchronisieren, da kein Text-Feld existiert.
  - Booster/Crystal-Pakete haben aktuell gar keine Karten im Web-Dashboard (nur Ranks). Nicht ergaenzt, da nicht explizit angefragt - bei Bedarf spaeter nachziehen.
- **Erledigt:** Coin/Raid Booster wirken jetzt tatsaechlich im Spiel. `BoosterManager.applyCoinBoost(uuid, amount)` ist vor jeder persoenlichen Coin-Vergabe verdrahtet: `WarzoneMobService.onDeath()` (Mob-Kill-Coins), `CaptureZoneManager.rewardCapture()` (CTF-Coins), `ChestSpawnManager.markOpened()` (Prospector-Set-Bonus), `DailyRewardService.claim()` (Daily-Coins). `BoosterManager.applyRaidXp(player, amount)` ist in `RaidRewardService.rewardProgress()` vor der persoenlichen Raid-Crystal-Vergabe verdrahtet (stackt mit dem bestehenden Rune-Bonus).
  - Bewusst NICHT geboostet: die faction-weiten Coins/Crystals in `RaidRewardService` (`addCoinsToFaction`, `FactionCoinManager.addCoins`, `addForFaction`) - ein einzelner Booster-Kauf soll nicht die ganze Fraktion boosten. Ebenso nicht geboostet: RAIDER-Skill-XP (`SkillType.RAIDER` in WarzoneMobService/ChestSpawnManager) - das ist ein separates Skill-Progressionssystem, kein "Raid-XP" im Sinn des Booster-Namens. Falls das anders gewuenscht ist, bitte Bescheid geben.
  - Mit `mvn compile` erfolgreich gebaut, aber noch NICHT auf den Live-Server deployed (siehe unten).
- **Preise final bestaetigt (25.09.2026):** Coin Booster 1,99 EUR, Raid Booster 1,99 EUR, Legendary Crystal 6,99 EUR, Raid Bundle 7,99 EUR, Epic Crystal 4,99 EUR, Mystic Crystal 19,99 EUR. Alle bereits so in Tebex konfiguriert (per `creator.tebex.io/packages` verifiziert) - keine "Vorschlag"-Markierung mehr noetig.
- Bekannter Bug (nicht durch dieses Update verursacht, noch offen): `/crystals give` schreibt auf das persoenliche Guthaben, aber mit `useFactionBank: true` liest jede Ausgabe die Fraktionskasse - darueber vergebene Crystals waeren aktuell nicht nutzbar. Betrifft NICHT die neuen Crate-Key-"Crystals" oben.
- In `.claude/worktrees/youthful-pare-432f20/` liegt ein separater, unfertiger Branch mit einer neueren Crystal-Refinery-Logik (`CrystalCommand.java`, `CrystalRefineryManager.java`). Nicht angefasst, aber vor weiterem Crystal-Ausbau gegenchecken, ob das zusammengefuehrt werden soll.
- Noch offen: Java-Plugin-Aenderungen (Fly-Permission, `RankKitService` Booster-Vergabe, Booster-Gameplay-Verdrahtung) kompilieren sauber (`mvn compile`), sind aber noch nicht auf den Live-Server deployed/neu gestartet. `vetroforge.fly.faction` muss ausserdem noch in VetroPerms den Gruppen Vanguard/Obsidian/Apex/Vetro+ zugewiesen werden.
