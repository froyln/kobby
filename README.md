<div align="center">

# Kobby

**Keep explored terrain in view beyond the server’s view distance.**

![Minecraft 1.12.2](https://img.shields.io/badge/Minecraft-1.12.2-62B47A)
![LiteLoader](https://img.shields.io/badge/Loader-LiteLoader-4C93D6)
![Java 8](https://img.shields.io/badge/Java-8-E68A2E)
[![GPLv3](https://img.shields.io/badge/License-GPLv3-blue)](LICENSE)

**[Documentation](docs/README.md) · [Compatibility](docs/COMPATIBILITY.md) · [Report an issue](https://github.com/froyln/kobby/issues)**

</div>

---

## What does it do?

Kobby saves terrain your client receives and keeps it available for rendering after the server unloads it. Revisit an area or reconnect, and previously cached terrain can load from disk.

Explore an area first, then move away. Set Kobby’s cached distance above Minecraft’s live distance to keep that terrain visible, including in singleplayer. Kobby displays only terrain already recorded or present in an enabled fallback world.

## Features

- **Persistent terrain cache** — keep explored terrain between sessions.
- **Live terrain takes priority** — newly received server data replaces cached terrain.
- **Separate world and dimension caches** — store snapshots outside your world saves.
- **Read-only fallback world** — optionally display terrain from a matching Minecraft 1.12.2 world copy.
- **Independent terrain distance** — display explored chunks beyond the live distance in singleplayer or multiplayer.
- **LiteOptimizer compatibility** — tested with Sodium’s terrain renderer enabled; see the compatibility record for the exact build.
- **In-game settings** — configure caching, distance, fallback and profiles through LiteLoader; an optional key toggles caching.
- **Background disk loading and saving** — keep file work off the client thread.

## Installation

Requires **Minecraft 1.12.2**, **LiteLoader**, and **Java 8**.

1. Set up a Minecraft 1.12.2 instance with LiteLoader.
2. Build the Kobby package using the instructions below, or download [Kobby 1.0.0](https://github.com/froyln/kobby/releases/download/v1.0.0/kobby-liteloader-1.12.2-1.0.0.litemod).
3. Copy it into your instance’s `minecraft/mods/1.12.2/` folder.
4. Launch Minecraft and explore to populate the cache.

When upgrading, replace the old mod file with Kobby and reassign your cache toggle in Controls. Kobby uses the existing config, terrain cache, and fallback world paths.

Install Kobby on the client. No server-side installation is needed.

## Controls and configuration

Open LiteLoader’s mod list from the chest tab on the main menu or pause menu (or **Ctrl+Shift+Tab** if the tab is hidden). Select **Kobby → Settings...** to change terrain caching, cached distance, the fallback world option, the singleplayer live distance, cached-terrain dimming, distant terrain LOD, or the profile for your next connection. Click **Save & Close**, or press **Escape**, to save and apply your changes.

Open **Options → Controls → Kobby** and assign a key to **Toggle Kobby terrain cache**. It starts unassigned to avoid key conflicts.

Press your assigned key in game to enable or disable Kobby. An on-screen message confirms the change, and the setting is saved. Disabling removes cached terrain from view and stops new caching; existing disk files remain available when you re-enable it.

You can also edit `liteconfig/common/bobby112.json` in your instance’s Minecraft folder:

```json
{
  "enabled": true,
  "cacheRenderDistance": 16,
  "integratedServerDistance": 0,
  "tintCachedTerrain": false,
  "distantTerrainLod": false,
  "fallbackEnabled": false,
  "cacheProfile": ""
}
```

`cacheRenderDistance` sets how far explored cached terrain can be displayed, independently of Minecraft’s render distance. It defaults to 16 chunks and accepts 2–128. Distances above 32 require **Distant terrain LOD** and a dimension with skylight. Shift-click the distance buttons to adjust by 16 chunks. The visible terrain range uses the larger of the two settings; Minecraft’s setting controls live chunk requests and singleplayer loading by default. For example, use Minecraft distance 6 and cache distance 16 to display explored terrain beyond the live area. Explore that terrain first: Kobby does not generate unseen chunks.

Enable **Distant terrain LOD** or set `distantTerrainLod` to `true` to simplify cached terrain from 12 chunks away in dimensions with skylight. The renderer uses flat material colors at one sample per 4×4 blocks through 32 chunks, then one sample per chunk out to the configured distance. Distant trees and buildings can lose detail, and terrain edges can show seams. Live chunks and nearby cached chunks keep their normal detail. The setting defaults to off.

Changes are reloaded while Minecraft is running. Disabling Kobby restores Minecraft’s normal terrain distance.

For a separate singleplayer live range, set **Singleplayer live distance** in Kobby’s settings, or `integratedServerDistance` in JSON. `0` (**Auto**) follows Minecraft; positive values are limited to 3–32 chunks. For example, keep Minecraft distance 8 and cached distance 16, then set the singleplayer live distance to 3. This changes the integrated server’s loading and tracking range for the host and LAN guests. Spawn chunks and other vanilla loading rules still apply. It takes effect on the next unpaused server tick; disabling Kobby restores Minecraft’s live distance. Dedicated servers keep their own distance.

Set **Tint cached terrain** or `tintCachedTerrain` to `true` to make cached terrain visibly distinct: rendered sky light drops by 3–10 levels depending on gamma, and rendered block light rises by up to 5. It defaults to off and does not change saved light data or live terrain.

## Cache profiles

Use a different `cacheProfile` name for each world behind a proxy address, or after a server reset. Select it in Kobby’s settings **before joining**. If you edit it while playing, disconnect and reconnect to apply it; changing dimension keeps the current connection’s profile.

Blank uses your existing cache. Named profiles start with separate recorded terrain. Select a previous name, or blank, to reopen its cache. Names are case-sensitive, support letters, numbers, spaces, dots, underscores and hyphens, and are limited to 64 characters. Surrounding spaces are trimmed.

Profile selection is manual; Kobby does not detect proxy world switches automatically.

## Fallback world

To fill areas missing from the recorded cache, copy a matching **Minecraft 1.12.2 Anvil world** into `saves/bobby-fallback` and set `fallbackEnabled` to `true`. Keep its `region/`, `DIM-1/region/` and `DIM1/region/` directories in their original layout.

Live chunks take priority, followed by recorded cache, then fallback terrain. The fallback files are opened read-only: Kobby does not generate, repair, convert or save that world. Entities, block entities and scheduled ticks are excluded. Modern world formats and symlinked sources are not supported.

The fallback is shared by this Minecraft instance, including all cache profiles. Enable it only while playing the matching world/server, and disable it when switching to an unrelated world. Changes to the option reload terrain automatically; after replacing fallback files, toggle the option off and on to refresh them.

## Cache storage

Terrain is stored under `.bobby112/v1/` inside the instance’s Minecraft folder, separately from `saves/`. The current disk budget is **2 GiB**, shared across cached worlds. Resident terrain follows the larger of your live and cached render distances, with two extra columns for meshing neighbors.

Cached chunks are visual snapshots. They may be outdated, and they do not keep farms running, simulate entities, or extend interaction distance.

## Build from source

With **JDK 8** selected:

```bash
git clone https://github.com/froyln/kobby.git
cd kobby
./gradlew build
```

Output: `build/libs/kobby-liteloader-1.12.2-1.0.0.litemod`.

The project uses the included Gradle wrapper, MCP stable_39 mappings, LiteLoader, and Python 3 to normalize the final archive. The `.litemod` revision is pinned in `gradle.properties` and should be increased for a new release. See [compatibility](docs/COMPATIBILITY.md) for inspected versions and runtime checks.

## License

This project is licensed under the [GNU General Public License version 3](LICENSE) (`GPL-3.0-only`).

## Current limitations

- Automatic proxy-world detection is not implemented; select a separate profile and reconnect for each world or reset.
- Cached entities and block entities are not rendered or simulated; cache merge/upgrade commands and modern world imports are not included.
- A dense distance-32 test retained 1,072 columns, but a fully populated maximum window and general gameplay performance at that distance remain unverified.
- Full upstream Bobby feature parity and broad modpack compatibility are not established. The tested LiteOptimizer build and local dedicated-server cases are listed in [Compatibility](docs/COMPATIBILITY.md); other server versions, plugins and proxy setups remain unverified.

When reporting a rendering issue, include your mod versions, dimension, client/server view distances, and whether the affected terrain was previously explored.

## Credits

Inspired by [Bobby](https://www.curseforge.com/minecraft/mc-mods/bobby). This is a separate Minecraft 1.12.2 LiteLoader project and is not affiliated with upstream Bobby.
