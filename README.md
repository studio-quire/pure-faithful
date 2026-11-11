# Pure Faithful

**beta 1.7.3 as it was, safe and fast**

Minecraft beta 1.7.3 exactly as it was, made easy to play on today's computers: safe online, faster, and unable to
lose your world. Beta's own bugs and quirks stay. Open source, from [Studio Quire](https://studioquire.org/).

Pure Faithful is mods, not a modified game: your launcher downloads Minecraft itself from Mojang, with your own
account. Nothing of Mojang's is in this repository or in our downloads.

**[Old Faithful](https://github.com/studio-quire/old-faithful)** is Pure Faithful plus fixes for beta's own clear
mistakes, each on a switch, with what players built their game around kept as beta had it.
**[Still Faithful](https://github.com/studio-quire/still-faithful)** is Old Faithful plus the polish later versions
brought while the game stayed beta (smooth dusk, stairs that drop themselves, animals that stay, sturdier boats).
Pure Faithful is for Windows now; macOS and Linux follow. If you'd like to help test on them, open an issue.

## What it does

- **Runs on modern machines.** Java 21; its own window again, resizable, opening at Prism's window size (or
  maximised), sharp on high-resolution screens, with borderless fullscreen (F11) and working paste; sound that follows
  your speakers or headphones when you switch.
  Underneath: our own implementation of the LWJGL 2 API that beta calls, on LWJGL 3 and SDL3.
- **Safe online.** Logins and skins go to Mojang over HTTPS, sounds come over HTTPS and are checked against their
  known contents, and your account's access token is never sent in the clear or written to logs. The game and the server are hardened against malicious servers and
  clients.
- **Faster, with the same picture.** Only changes that give exactly the same result with less work: about a fifth
  less time per frame standing still, and the far view filling in steadily.
- **Your world is safe.** Saves are written whole or not at all, so a crash or power cut can't damage a world; a
  chunk that can't be read is kept and reported, never silently replaced with new terrain. Worlds stay in beta's
  own format and open in the original game.
- **Beta's own sounds.** Launchers today give four of beta's sounds as later recordings: the bow, doors opening and
  closing, and explosions (version 1.2's set of four in place of beta's single one). The game plays beta's own: the
  explosion from Mojang's files, and the bow and doors, which Mojang no longer offers, fetched once from the archive
  of beta's files kept by Modification Station, a community of beta modders. Until they arrive (or if they never
  do), the later recordings play.
- Vsync on (the original drew as fast as it could, which tears on today's displays), and screenshots that work after
  you enlarge the window.

The title screen shows "Pure Faithful" and its version under beta's own version line.

## Download and play

You need a Minecraft account that owns the game, and [Prism Launcher](https://prismlauncher.org/).

1. Download **PureFaithful-&lt;version&gt;.zip** from [the latest release](https://github.com/studio-quire/pure-faithful/releases/latest).
2. In Prism Launcher: **Add Instance > Import**, choose the zip (or paste the download link), **OK**.
3. Launch it. Prism downloads beta 1.7.3 and its sounds from Mojang, and, if you let it, a Java 21 runtime.

Everything else is inside the zip, and nothing comes from us. Besides Mojang (your login, skins and sounds) and the
servers you join, the game reaches one other place: on its first start it fetches beta's own bow and door sounds
(about 28 KB) from Modification Station's archive of beta's files. To keep it
from doing so, before you first launch it set `resources.download=false` in
`.minecraft/config/purefaithful-hardening.properties`, creating the file if it isn't there.

## For existing instances

Already playing b1.7.3 on Fabric (Babric) in Prism? Our mods work on their own.

- **purefaithful-hardening**, **purefaithful-saves** and **purefaithful-speed** (client and server): drop the jars
  from the release into the instance's `.minecraft/mods`. The hardening mod fetches beta's own bow and door sounds
  once, as above; `resources.download=false` in `.minecraft/config/purefaithful-hardening.properties` stops it.
- **purefaithful-display** (the window, high-resolution screens, fullscreen, paste) needs our LWJGL in place of
  LWJGL 2. One step: close Prism, then put **`org.lwjgl.json`** from the release into the instance's `patches` folder
  (in Prism: right-click the instance, **Folder**), replacing the file there if there is one. Prism downloads the
  libraries it names from the release and checks each one's hash. Use Java 21 or newer. Keep a copy of the file you
  replace: putting it back undoes the change.

Mods that reach into LWJGL 2's own internals won't work on our LWJGL; we checked 20 popular b1.7.3 mods and found
one (fullscreen-tweaks, whose fix we include).

## Servers

`PureFaithful-server-<version>.zip` is beta 1.7.3's own server with the hardening, saves and speed mods: bring
Mojang's server jar (the scripts check it's genuine) and run `start.bat` or `start.sh`. Its README says more.

## Problems

Please [open an issue](https://github.com/studio-quire/pure-faithful/issues) and attach
`.minecraft/logs/purefaithful/latest.log` from the instance folder. It never contains your access token.

## Building

`./gw release` builds everything a release contains into `local/release/<version>/`. Our native libraries (LWJGL 3,
SDL3, OpenAL Soft) are built from pinned sources first. [docs/building.md](docs/building.md) and
[docs/release.md](docs/release.md) say how; [docs/natives.md](docs/natives.md) covers the natives. Inside, the
project is still called by its working name, `oldfaithful` (folders, packages, tasks).

## Credits and licence

Made by Claude Opus 5.5, an AI model by Anthropic, at Studio Quire. Idea, direction and play-testing: Shoalstone.

Pure Faithful's code is under the [MIT licence](LICENSE). It builds on Fabric Loader, Mixin, ASM, Babric's mappings,
LWJGL, SDL and OpenAL Soft, each under its own licence; the downloads carry their notices and licence texts, and the
source code their licences ask for.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
