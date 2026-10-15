# SkinMotion

Animated Minecraft skins and official capes, configured from a web dashboard
([skin.thomas.md](https://skin.thomas.md)) and synced to your server in real time through the
SkinMotion API.

## Features

- **Animated skins**: players upload several frames on the dashboard; everyone else sees the
  animation, the player themselves keeps the first frame.
- **Official capes**: frames are signed by Mojang with the cape chosen on the dashboard.
- **Dashboard links**: `/skin link` (or on join) gives the player a personal, clickable dashboard link.
- **Real-time sync**: a WebSocket connection to the API applies dashboard changes instantly and
  reconnects automatically.
- **Offline persistence**: skins are cached in a local SQLite database (`plugins/SkinMotion/skins.db`)
  and applied immediately on join, then refreshed from the API.

## Supported Platforms

| Platform | Jar | Support |
|----------|-----|---------|
| Paper 1.20.5+ (tested 1.21.11, 26.2, 26.3) | `skinmotion-<version>.jar` | Full: skins, capes, animation, dashboard sync |
| Folia (tested 26.2) | `skinmotion-<version>.jar` | Full, using region/entity schedulers |
| Spigot | `skinmotion-<version>.jar` | Untested. Static first frame via reflection on versions whose authlib `GameProfile` still has mutable properties; no animation |
| Velocity 3.3+ (tested 3.5.1) | `skinmotion-velocity-<version>.jar` | Legacy `/cape` command only |
| BungeeCord | `skinmotion-bungee-<version>.jar` | Legacy `/cape` command only |

Animation sends per-viewer player-info packets through reflection on the Mojang-mapped server, so it
needs Paper or Folia 1.20.5 or newer (older Paper still gets the static first frame). Install the Bukkit jar on each backend server, not on the proxy.

## Requirements

- Java 17+ (Minecraft 1.21.x needs Java 21, 26.x needs Java 25)
- A `plugin_api_key` for the SkinMotion API (needed for dashboard links and real-time sync)

## Installation

1. Put the jar for your platform in the `plugins` folder.
2. Start the server once to generate `plugins/SkinMotion/config.yml`.
3. Set `api.plugin_api_key` (and `api.server_id` if you run several servers).
4. Restart, or run `/skin reload`.

## Commands (Paper/Folia/Spigot)

| Command | Description | Permission | Default | Console |
|---------|-------------|------------|---------|---------|
| `/skin` | Show help | `skinmotion.use` | everyone | yes |
| `/skin link` | Get a clickable dashboard link | `skinmotion.link` | everyone | no |
| `/skin refresh` | Re-fetch your skin from the API | `skinmotion.refresh` | everyone | no |
| `/skin status` | WebSocket, animation and skin status | `skinmotion.status` | everyone | yes |
| `/skin reload` | Reload `config.yml` and reconnect | `skinmotion.reload` | op | yes |

## Commands (Velocity/BungeeCord)

The proxy modules only ship the older cape command. A cape applied on the proxy is sent to the
backend on the player's next server connection.

| Command | Description | Permission |
|---------|-------------|------------|
| `/cape <type>` | Apply an official cape to your current skin | `customcapes.use` |
| `/cape list` | List cape types | `customcapes.use` |
| `/cape clear` | Restore your original skin | `customcapes.use` |
| `/cape reload` | Reload configuration | `customcapes.admin` |

Velocity also registers the `/capes` alias.

## Configuration

```yaml
api:
  url: https://ccapi.thomas.md   # SkinMotion API (REST + WebSocket)
  timeout_seconds: 30
  plugin_api_key: ''             # required for /skin link and real-time sync
  server_id: default             # sent with dashboard token requests
skin:
  send_link_on_join: true        # send the dashboard link when a player joins
messages:                        # used by the proxy /cape command
  prefix: '<gray>[<gold>Capes</gold>]</gray> '
  # ...
```

Keys added in newer versions are written into an existing `config.yml` on load; existing values
are kept. A malformed file is left untouched and defaults are used.

## How It Works

1. On join, the plugin applies the locally cached skin (if any), then fetches `GET /skins/{uuid}`.
2. The first frame becomes the player's real profile texture, so the player and newly arriving
   viewers see it immediately.
3. Every tick the animation task advances each animated player's frame (`loop`, `once` or
   `ping_pong`, `frame_duration_ticks` per frame). On a frame change, every player tracking the
   animated player gets the player hidden, re-announced with the frame's texture, and shown again.
4. When the dashboard saves, the API pushes `SkinUpdated`/`ConfigChanged` over the WebSocket and the
   plugin re-syncs that player.

## Building

```bash
./gradlew build
```

Outputs in `build/libs/`:

- `skinmotion-<version>.jar` (Paper, Folia, Spigot)
- `skinmotion-velocity-<version>.jar`
- `skinmotion-bungee-<version>.jar`

## Architecture

```
skinmotion/
├── skinmotion-core/     # API client, WebSocket client, config, models, animation timing
├── skinmotion-bukkit/   # Paper/Folia/Spigot plugin
├── skinmotion-bungee/   # BungeeCord /cape plugin
└── skinmotion-velocity/ # Velocity /cape plugin
```

## License

MIT
