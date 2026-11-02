# Releases

Claude Opus 5.5, 2026-11-02. How a release is built, what's in it and why, the version scheme, the licence
obligations and how each is met, and the steps to publish. The first release is the Pure edition for Windows
(roadmap phase 7).

## Names

Players see **Pure Faithful** (user, 2026-11-02): everything the Pure edition is made of is Pure Faithful's publicly,
and Old Faithful (the Fixed edition, later) will be Pure Faithful plus its fix mods. So public names say
purefaithful: the pack (`PureFaithful-<v>.zip`, the instance it imports as), mod ids and jars (`purefaithful-saves`
and so on, from the project folder's name in `ModPlugin`), the config files (`config/purefaithful-*.properties`), the
log (`logs/purefaithful/`), log line prefixes, the title line, the component's coordinates
(`org.studioquire.purefaithful:purefaithful-lwjgl`), release asset names, the repository (`studio-quire/pure-faithful`)
and the page (`studioquire.org/pure-faithful/`). Internal names stay `oldfaithful`: the project folder and private
repository, Gradle projects and tasks, Java packages, mixin config names and handler prefixes, system properties, the
harness's own mod and lines, and our natives' Maven coordinates (`org.studioquire.oldfaithful.natives`, versions
`+oldfaithful.N`; they show only in the pack's `libraries/` file names and Prism's library cache). The words come from
`release_name`, `release_file_name` and `release_tagline` in `gradle.properties`.

## Building one

    ./gw release                 # everything into local/release/<version>/, with SHA256SUMS.txt
    python tools/publish.py --check

`release` publishes nothing. What it writes, with the names the release page uses (GitHub rewrites `+` in asset
names, so assets have none):

| File | What it is |
|---|---|
| `PureFaithful-<v>.zip` | The pack: a Prism instance to import (below) |
| `purefaithful-{hardening,saves,speed,display}-<v>.jar` | The mods on their own (Modrinth, existing instances) |
| `org.lwjgl.json` | Our LWJGL component for an existing instance, with each library's URL on this release, SHA-1 and size |
| `purefaithful-lwjgl-<v>.jar`, `purefaithful-lwjgl3-…-natives-windows.jar`, `purefaithful-sdl3-…-natives-windows.jar`, `purefaithful-openal-soft-…-natives-windows-lwjgl3.jar` | The libraries `org.lwjgl.json` names; must be on the same release |
| `PureFaithful-server-<v>.zip` | The server package (`docs/multiplayer.md`), with its notices and sources |
| `PureFaithful-<v>-third-party-sources.zip` | The sources the licences ask for, beside the component's separate downloads |
| `SHA256SUMS.txt` | For the release notes |

Every zip entry has one fixed timestamp and a fixed order, so a rebuild from the same commit and mirror gives the same
bytes (checked: two `release` runs, identical `SHA256SUMS.txt`).

## The pack

`prism/release/` is its template; `gradle/release.gradle` fills it. What differs from the dev instances, and why:

- **Name and icon.** Prism names an imported instance after the zip's file name (`InstanceImportTask` takes
  `completeBaseName`), not `instance.cfg`'s `name`, so the file name is what players see first:
  `<release_file_name>-<version>.zip`. The icon, the β seal (`prism/release/icons/final/`, drawn by `tools/icon_candidates.py`; the user's pick), is
  installed by Prism from the instance root because `iconKey` names it. `instance.cfg`'s `notes` (quoted, since
  Prism reads it with QSettings, which would split a comma) shows in Prism's Notes page.
- **Inside, not fetched.** Every library is in the zip with Prism's `local` hint: one download, nothing fetched but
  Minecraft and its sounds from Mojang (and Java, if the player lets Prism get it), no dependency on our hosting.
  Prism doesn't hash-check local files, so the zip's SHA-256 in the release notes is the check.
- **Windows only.** Only the Windows natives; the dev instance's Linux ones are left out until a Linux release.
- **No instance JVM arguments.** The dev instance overrides the player's JVM arguments to pass
  `--enable-native-access=ALL-UNNAMED`; the pack's `net.minecraft` component adds it with `+jvmArgs` instead, so
  the player's own settings stay. Java 21 and 25 accept it; the component already limits Java to those.
- **Readable by players.** `README.txt` (what it is and isn't, where worlds, settings and the log are), and the
  licence material below.
- Minecraft's jar is always Mojang's, by URL and SHA-1, as in the dev instances. A .mrpack (Modrinth's pack format)
  can't express our own `net.minecraft` and `org.lwjgl` components, so the pack is a Prism zip only.

## On their own: mods and the LWJGL component

- The mods are the same jars as in the pack. Each carries the MIT licence (`LICENSE_<id>`), the icon, and contact
  links in `fabric.mod.json`.
- **The component, one step** (README.md has the player's wording): with Prism closed, put `org.lwjgl.json` into the
  instance's `patches/` folder, replacing what's there. Prism prefers a local patch file over its metadata for the
  same uid (`ComponentUpdateTask` `loadComponent`), downloads each library from its URL and checks its SHA-1.
  Prism's "Import Components" button doesn't serve: it refuses when `patches/org.lwjgl.json` exists, as it does in
  Babric's instance. Not tried in a real Prism yet: whether Prism rewrites `mmc-pack.json`'s old LWJGL version
  cleanly, and the downloads from GitHub's release URLs (redirects to its CDN).
- The component file adds no JVM arguments: a Babric instance may run Java 8, which refuses
  `--enable-native-access`. The component's classes are all Java 8 bytecode, and on Temurin 8u504 its core, SDL3 and
  OpenAL Soft load and start (a throwaway check, 2026-11-02); the game on it under Java 8 isn't tested, so the guide
  says Java 21.

## Versions

- **One number for everything**: `oldfaithful_version` in `gradle.properties`, carried by every mod, the component
  (`<v>+lwjgl.3.4.3`), the pack and the server package. Semantic versioning in spirit: patch for fixes, minor for
  anything a player would notice, major when worlds, configs or setup stop carrying over. Pre-releases get a
  suffix (`0.2.0-beta.1`).
- **The component must change version whenever its bytes do**, which one number guarantees: Prism keeps downloaded
  libraries in a shared cache by Maven path and never re-checks them, so a changed jar under an old version would
  never reach players.
- Our natives carry their own build revision (`3.4.18+oldfaithful.1`); a rebuild that changes their bytes bumps it
  (the verification metadata fails otherwise).
- Git tags `v<version>` on the public repository; GitHub release `v<version>`; Modrinth versions `<version>` with
  game version b1.7.3 and loader babric/fabric. `CHANGELOG.md` gets an entry per release, in players' terms; the
  release notes are that entry plus `SHA256SUMS.txt`.
- **In the game**: the title screen shows "Pure Faithful <version>" under beta's own "Minecraft Beta 1.7.3", in the
  same grey (the hardening mod's `Branding`; every Pure Faithful setup has that mod; Old Faithful will need its own
  name there). The client's log starts
  with the mods and their versions. `clientStartupTest` reads the title screen's pixels and saves the corner
  (`title-corner.png`).

## Licences: what we ship and what each asks

Our own code is MIT. Everything else, by artifact (the pack's `THIRD-PARTY-NOTICES.txt` is assembled from
`release/notices/`; licence texts in `release/licenses/` plus each jar's own):

| What | Licence | What it asks of us | How it's met |
|---|---|---|---|
| Fabric Loader 0.20.1 | Apache-2.0 | Licence copy; keep NOTICE files | Licence in `licenses/`; the loader has no NOTICE |
| inside it: tiny-remapper 0.15.0 | LGPL-3.0-or-later | Prominent notice; LGPL and GPL texts; the Library's source, and the application's in a form that allows relinking (§4d) | Notice; both texts; tiny-remapper's and the loader's sources in `sources/` of the pack and server zips |
| inside it: SAT4J 2.3.6 | EPL-1.0 (or LGPL-2.1+) | Licence copy; say where the source is | EPL text, the loader's `about-sat4j.html`, sources in `sources/`; MiniSat's MIT notice |
| inside it: mapping-io, class-tweaker, Gson's reader | Apache-2.0 | Licence copy | As the loader |
| inside it: MixinExtras 0.5.5 (+ Gson 2.11.0, Commons Lang 3.3.2 parts, ANTLR 4.13.1, java-semver 0.10.2) | MIT, Apache-2.0, BSD-3, MIT | Notices; Commons Lang's NOTICE | All texts; the NOTICE in `licenses/common/` |
| Mixin 0.8.7 (+ Guava 21, Gson 2.2.4) | MIT, Apache-2.0 | Notices | Its licence file; Apache text |
| ASM 9.10.1 | BSD-3-Clause | Copyright and conditions in the documentation | ASM's licence text (not in its jars) |
| Babric intermediary | CC0 | Nothing | Credited anyway |
| LWJGL 3.4.3 classes, LWJGL 2 classes | BSD-3-Clause | Notices | In the component jar's `META-INF/licenses/` and the pack's `licenses/` |
| LWJGL 2's GLU tessellator (from JOGL, from SGI's OpenGL Sample Implementation) | BSD (Sun) + SGI Free Software License B 1.1 | FreeB 1.1 §3.1: its Notice and a copy of the licence in the documentation | The JOGL notice file, with SGI's Notice, and FreeB 1.1's text, in the jar and `licenses/` |
| LWJGL 3 bindings' sources' notices (Khronos, SDL, libffi, liburing) | MIT-style, zlib | Notices | In the jar and `licenses/` |
| `lwjgl.dll`, `lwjgl_opengl.dll` (+ libffi) | BSD-3, MIT | Notices | In the natives jar and `licenses/` |
| `SDL3.dll` (+ HIDAPI, yuv2rgb) | zlib; HIDAPI's BSD option; BSD-3 | zlib asks nothing of binaries; the others notices | In the natives jar and `licenses/` |
| `OpenAL.dll`, OpenAL Soft 1.25.2 (+ pffft, {fmt}, BSD parts) | LGPL-2.0-or-later (its COPYING is the Library GPL 2) | §6: prominent notice and licence; the Library's complete source (with the scripts used to build it) accompanying it, or from the same place; replaceable by the user | Notice; COPYING; the source archive and `natives/build.py` (as `build-natives.py`) in the pack's `sources/`, and on the release page beside the separate natives jar; the DLL is a separate file the game loads at run time, unmodified |
| MSVC's static runtime in our DLLs | Microsoft's | Nothing for statically linked runtime code | |

Mojang's game, assets and server are never shipped. Every player-facing text carries "Not an official Minecraft
product. Not approved by or associated with Mojang or Microsoft."

Doubts, judged rather than asked of a lawyer: whether tiny-remapper's sources plus the loader's (with the loader's
public build at its tag) are everything LGPL-3 §4(d)(0) wants for a shaded jar (we think yes, and it's Fabric's own
distribution model); FreeB 1.1 is honoured as written, not leaning on SGI's 2008 FreeB 2.0.

## The public repository

This repository is a snapshot of the project's working repository, made for each release by a tool that checks
what goes public; its history starts at the first release.

## Publishing, when the user says go

1. `./gw build` and the harness's tests (`docs/building.md`), `./gw release`; then `python tools/publish.py --check`
   on the same commit, and `python tools/publish.py`.
2. Create `studio-quire/pure-faithful`, push `../pure-faithful-public` to it, tag `v<version>`.
3. A GitHub release `v<version>` with every file in `local/release/<version>/` (names unchanged: `org.lwjgl.json`'s
   URLs point at them), notes from `CHANGELOG.md` and `SHA256SUMS.txt`.
4. Check the standalone component in a Prism instance that downloads from the release (the URLs only work now).
5. Modrinth: the mod projects from `release/drafts/modrinth.md`, the same jars.
6. studioquire.org: the page from `release/drafts/site-page.html`, a card, `releases.json` for `/get/pure-faithful`
   (quire-site's CLAUDE.md says how).

## Not checked yet

- The pack imported into a real Prism: the import name from the file name, the icon, the notes, `+jvmArgs` reaching
  the game, Java 21 chosen, a first launch downloading the game and its sounds. The template is the dev instance's,
  which the user has imported, plus those four, each read from Prism 11.1.1's source, not tried.
- The standalone component in a real Babric instance: replacing `patches/org.lwjgl.json`, the downloads from the
  release's URLs (only possible once a release exists, or against a draft release), the display mod working there.
- The server package from the release zip on a machine other than this one.
