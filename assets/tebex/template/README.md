# VetroForge Tebex Template

Ziel: Der Tebex Store soll weniger nach Default-Theme aussehen und mehr wie ein VetroForge Premium Store wirken: Dark/Red, moderne Cards, bessere Buttons, klare Rank-Kommunikation.

## Dateien

- `custom-css.css`  
  Einfuegen unter `Webstore -> Appearance -> Custom CSS`.

- `homepage.html`  
  Einfuegen unter `Webstore -> Pages -> Homepage -> Edit`.

- `about-page.html`  
  Einfuegen unter `Webstore -> Pages -> About -> Edit`.

- `rank-descriptions.md`  
  Enthalt fertige Rank-Beschreibungen fuer Ember, Vanguard, Obsidian, Apex und Vetro+.

- `rank-package-description.html`  
  Generisches Template mit Platzhaltern fuer spaetere weitere Ranks.

## Empfohlene Tebex Settings

- `Webstore -> Sidebar`
  - Module Text ersetzen durch einen VetroForge Info-Block.
  - Unnoetige Default-Module entfernen, falls moeglich.

- `Webstore -> Appearance`
  - Header-Bild dunkel/rot halten.
  - Package image display aktivieren.
  - Rank-Bilder aus `assets/tebex/ranks/` pro Package hochladen.

- `Packages -> Categories -> Ranks`
  - Type: `Cumulative`
  - So funktionieren Upgrades automatisch ueber Differenzpreis.

## Sidebar Text

```html
<div class="vf-section">
  <h2>VetroForge</h2>
  <p class="vf-muted">Ranks sind fair: QoL, Cosmetics und Komfort statt Pay-to-win.</p>
  <div class="vf-pills">
    <span class="vf-pill">Cumulative Upgrades</span>
    <span class="vf-pill">Sofortige Lieferung</span>
    <span class="vf-pill">Support via vetroforge.net</span>
  </div>
</div>
```

## Nach dem Upload

Wenn du ein Rank-Bild in Tebex hochgeladen hast:

1. In `assets/tebex/media-status.json` das jeweilige `uploaded` auf `true` setzen.
2. Dasselbe in `web-dashboard/data/tebex-media-status.json` setzen.
3. Web-Dashboard neu deployen.
