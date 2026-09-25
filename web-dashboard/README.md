# VetroForge Web Dashboard

Next/React dashboard for the VetroForge WebAPI.

## Run

```bash
npm install
npm run dev
```

Create `.env.local` if your API is not on the default host:

```bash
VF_FACTIONS_API_URL=http://45.146.252.240:8500
NEXT_PUBLIC_VETROFORGE_API=http://45.146.252.240:8500
NEXT_PUBLIC_SITE_URL=https://status.vetroforge.net
```

The dashboard stores the login session token and optional admin API key in `localStorage`.

## Pages

- `/` Admin dashboard with login, player directory, AH risk table, shop editor and faction lookup.
- `/status` Public live status page for WebAPI, Minecraft health, MySQL tables, Warzone and Lootdrops.
- `/profile?player=<name|uuid>` Player profile quick view from the Factions WebAPI.
- `/admin/tebex` Admin-only Tebex rank package overview with copy buttons and media status.
- `/factions/[name]` Public faction profile.
- `/auction/seller/[uuid]` Staff seller profile for AH risk review. Requires the WebAPI admin key.

## Public status domain

Point `status.vetroforge.net` to the host running this Next app and serve `/status` as the public status page. The plugin reports this URL through `status.public-url`.

## Discord alerts

In the plugin config:

```yaml
status:
  alerts:
    enabled: true
    webhookUrl: "https://discord.com/api/webhooks/..."
    cooldown-seconds: 300
```

If `webhookUrl` is empty, the plugin also checks `VF_STATUS_WEBHOOK_URL`.
