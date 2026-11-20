# Capture Point — Fabric 26.3

A server-authoritative two-team sector breakthrough mode for Minecraft Java 26.3.

## Target toolchain

- Minecraft: `26.3`
- Fabric Loader: `0.20.1`
- Fabric API: `0.166.2+26.3`
- Fabric Loom: `1.19-SNAPSHOT`
- Gradle: `9.6.0`
- Java: `25`

Minecraft 26.3 uses Java 25 and the unobfuscated Mojang class names. This project therefore uses the non-remapping Loom plugin (`net.fabricmc.fabric-loom`) and does not declare Yarn mappings.

## Build

Install JDK 25 and Gradle 9.6.0, then run from this directory:

```bash
gradle build
```

The mod jar will be written to `build/libs/`.

> The current authoring environment only has Java 21 and has no network access, so dependency resolution and a local Gradle build could not be run here. This is a source project, not a verified compiled jar. Build it with the toolchain above before putting it on a live server.

## Install

1. Install Fabric Loader 0.20.1 for Minecraft 26.3.
2. Install Fabric API `0.166.2+26.3` on the client and server.
3. Build the project and put `capturepoint-0.1.0.jar` from `build/libs/` into the `mods` folder.
4. Install the same mod version on all clients and the server. The gameplay state is server-authoritative.

## Administrator panel

Default key: **J**. Change it in **Options → Controls → Key Binds → Capture Point**. The 26.3 input backend uses SDL and the mod registers its key with Minecraft’s `InputConstants`/Fabric key-mapping API.

Only operators whose server command tree exposes `/cappoint` can open the panel. Every action is also checked server-side with the Minecraft 26.3 `ADMINS` command permission (OP level 3 or higher).

The panel is split into tabs:

- **Round:** start, pause, resume, stop, status, and point list.
- **Sectors:** create a point at the administrator's current block position, set `pos1` and `pos2` (X, Y and Z), choose the central point, reorder or remove points.
- **Teams:** randomly distribute online players between BLUE and RED with a maximum team-size difference of one, manually assign a player, and inspect the roster.
- **Settings:** set capture and hold durations.
- **Saved rounds:** save, load, and list named round configurations.

The default opening key is **J**. It can be changed in Minecraft's Controls menu. The client checks for an administrator-only command in the server command tree before opening the panel, and the server independently authorizes every command.

## Point setup

The GUI is the main interface. Commands are also available for setup and server-console administration:

```text
/cappoint point create <name>
/cappoint point pos1 <name>
/cappoint point pos2 <name>
/cappoint point center <name>
/cappoint point move <name> <1-based-index>
/cappoint point remove <name>
/cappoint list
/cappoint time capture <seconds>
/cappoint time hold <seconds>
/cappoint team random
/cappoint team set <player> <blue|red>
/cappoint save <round-name>
/cappoint load <round-name>
/cappoint start
/cappoint pause
/cappoint resume
/cappoint stop
/cappoint status
```

Create points in the order from **BLUE base to RED base**. Choose the neutral starting sector as the central point. RED advances toward lower indexes; BLUE advances toward higher indexes. After a sector is captured and held, the next active sector is the immediately adjacent point in the winner's direction. If the other team takes that point, the front reverses to the adjacent point just fought over. No sectors are skipped.

A newly created point starts as a one-block region at the administrator's position. Set both corners to define the full 3D cuboid. The point's dimension is recorded when its corners are set.

## Current implementation notes

Implemented in this first source build:

- Server-side team membership and random balanced assignment.
- 3D cuboid point detection, including Y.
- Neutral initial point and ordered, bidirectional frontline movement.
- Capture and hold timers, equal-number pause, capture reset to zero on a team switch, and hold progress continuing when the point is empty.
- Counter-capture of previously owned points when the frontline returns to them.
- Vanilla boss bar, colored boundary particles, chat announcements, and four language files (`en_us`, `ru_ru`, `de_de`, `uk_ua`).
- Persistent current round, named round files, and team assignments under `config/capturepoint/`.

The visual bar currently uses vanilla boss-bar colors (the paused state switches to white) rather than a custom alpha-blended HUD. The final-round notification is currently a chat announcement and completed boss bar; a timed title overlay and a fully custom translucent HUD are follow-up polish items. The admin screen is a functional first dashboard backed by server commands; richer live lists, dropdowns, map preview, and delete-saved-round controls remain planned improvements.

## Data files

```text
config/capturepoint/
  current-round.json
  teams.json
  rounds/
    <round-name>.json
```

## License

MIT. See `LICENSE`.

## Automated build (GitHub Actions)

The repository includes `.github/workflows/build.yml`. After pushing this project to a GitHub repository, the workflow runs on pushes, pull requests, and manual dispatch. It provisions Temurin JDK 25 and Gradle 9.6.0, runs `gradle clean build`, and uploads the resulting JARs as a workflow artifact.

To download a build: open the repository's **Actions** tab, select **Build Capture Point**, open a successful run, and download the `capturepoint-build-*` artifact. The JAR is inside the downloaded ZIP under `build/libs/`.

A successful CI compile does not replace in-game testing on Minecraft 26.3 with the matching Fabric Loader and Fabric API.


## Interface update
The administrator panel now opens on a simplified home screen with dedicated Points, Teams, Round, and Saved Rounds sections.

The home screen now focuses on the three primary areas: Points, Teams, and Round.

Build note: corrected locale JSON output and tightened admin menu layout.

UI update: added an explicit remove-from-team action and reorganized point setup into create, bounds, ordering, center/removal, and list steps.

Retry build for team removal and point setup UI fixes.

Retry: robust insertion for team unassign command.

Build retry: fixed newline handling and roster command insertion.

Retry build for team unassign action.

Build retry: register team remove as an additional merged command branch.

Build retry: team unassign branch attached at dispatcher root.
