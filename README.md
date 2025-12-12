# Custom Capes Plugin

A multi-platform Minecraft plugin that allows players to apply custom capes via commands by integrating with the Custom Capes API.

## Features

- **Multi-platform support**: Works on Paper, Spigot, BungeeCord, and Velocity
- **Dynamic cape availability**: Fetches available capes from API on startup
- **Simple commands**: `/cape <type>`, `/cape list`, `/cape clear`
- **Automatic skin detection**: Extracts player's current skin URL automatically
- **Cape caching**: API caches generated textures for faster subsequent requests

## Supported Platforms

| Platform | Support |
|----------|---------|
| Paper | Full native support via PlayerProfile API |
| Spigot | Reflection-based support |
| BungeeCord | Reflection-based proxy support |
| Velocity | Native GameProfile API support |

## Requirements

- Java 17+
- A running instance of the [Custom Capes API](../minecraft_custom_capes_api)
- Minecraft server/proxy running 1.20+

## Installation

1. Download the latest release or build from source
2. Place the JAR in your server's `plugins` folder
3. Start the server to generate the default configuration
4. Configure the API URL in `config.yml`
5. Restart or reload the plugin

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/cape <type>` | Apply a cape | `customcapes.use` |
| `/cape list` | List available capes | `customcapes.use` |
| `/cape clear` | Remove your cape | `customcapes.use` |
| `/cape reload` | Reload configuration | `customcapes.admin` |

## Available Cape Types

The plugin fetches available capes from the API on startup. Use `/cape list` to see which capes are currently available.

Possible cape types (availability depends on API backend):

- `vanilla` - No cape
- `minecon_2011` - Minecon 2011
- `minecon_2012` - Minecon 2012
- `minecon_2013` - Minecon 2013
- `minecon_2015` - Minecon 2015
- `minecon_2016` - Minecon 2016
- `mojang` - Mojang Cape
- `mojang_classic` - Mojang Classic
- `mojang_studios` - Mojang Studios
- `realms_mapmaker` - Realms Mapmaker
- `cobalt` - Cobalt
- `scrolls` - Scrolls
- `translator` - Translator
- `millionth_customer` - Millionth Customer
- `prismarine` - Prismarine
- `birthday` - Birthday
- `migrator` - Migrator
- `cherry_blossom` - Cherry Blossom
- `anniversary_15th` - 15th Anniversary

## Configuration

```yaml
api:
  url: "https://ccapi.thomas.md"  # URL to the Custom Capes API
  timeout_seconds: 30           # Request timeout

messages:
  prefix: "<gray>[<gold>Capes</gold>]</gray> "
  cape_applied: "<green>Cape applied successfully!"
  cape_cleared: "<green>Cape removed."
  cape_not_found: "<red>Cape type not found: <white>%cape%"
  error: "<red>Failed to apply cape: <white>%error%"
  no_permission: "<red>You don't have permission to do that."
  player_only: "<red>This command can only be used by players."
  usage: "<yellow>Usage: <white>/cape <list|clear|<cape_type>>"
  list_header: "<gold>Available capes:"
  list_entry: "<gray>- <white>%cape%"
  applying: "<yellow>Applying cape, please wait..."
```

## Building

```bash
# Clone the repository
cd customcapes

# Build with Gradle
./gradlew build

# The output JAR will be in build/libs/customcapes-<version>.jar
```

## Architecture

```
customcapes/
├── customcapes-core/     # Shared logic, API client, models
├── customcapes-bukkit/   # Paper/Spigot support
├── customcapes-bungee/   # BungeeCord support
└── customcapes-velocity/ # Velocity support
```

## How It Works

1. Player runs `/cape <type>`
2. Plugin extracts the player's current skin URL from their profile
3. Plugin sends a request to the Custom Capes API with the skin URL and desired cape type
4. API uploads the skin to a Minecraft account with that cape and retrieves signed texture data
5. Plugin applies the signed texture data to the player's profile
6. Other players are refreshed to see the change

## License

MIT

