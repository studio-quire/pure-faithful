# Changelog

Every release has one version number, shared by the mods, the LWJGL component, the pack and the server package
(docs/release.md > Versions). Newest first. Entries say what a player or server owner notices; the commits have the
rest.

## 0.1.4 (2026-11-08)

Released with Old Faithful 0.2.2, which carries it.

- Fixed a rare crash when a server makes a new world: about once in 900 new worlds, it stopped at "Preparing level".
- Saves: the messages in chat about trouble in a world's files (a chunk that couldn't be read, or one too big for
  beta's format) say what happened, where, and where the original is kept, without the file-format detail, which stays
  in the log. In Old Faithful or Still Faithful they start with that edition's
  name instead of Pure Faithful's.

## 0.1.3 (2026-11-02)

Bug fixes.

- Fixed a rare server crash involving pistons near the edge of the loaded area.
- Saves: a spawner set to something that isn't a mob, which only an edited world can hold, crashed the game (or
  stopped the server) whenever a player came near. It's set back to a pig spawner when its chunk loads, and reported
  with the other repairs.

## 0.1.2 (2026-11-02)

Released with the first Old Faithful, which carries it. Nothing changes for Pure Faithful players.

- The title line can name an edition built on Pure Faithful instead: Old Faithful says "Old Faithful" and its own
  version there.
- The pack's README and this one point to Old Faithful.

## 0.1.1 (2026-11-02)

Bug fixes.

- Saves: a chest, furnace, dispenser, jukebox, sign, note block or spawner whose contents can't be read now loads
  empty (reported with the rest of the damage). 0.1.0 left it with nothing behind it, and breaking such a chest,
  furnace, dispenser or jukebox crashed the game or stopped the server; worlds that 0.1.0 saved that way are mended
  when the chunk next loads.
- Server: with `allow-nether=false`, stopping or `save-all` no longer puts the world's time and weather back to where
  the session began (time 0 on a world made that way), nor replaces a new world's spawn point with the Nether's. A
  beta 1.7.3 bug; servers with the Nether on save as before.
- A double slab under another block no longer builds a face nobody can see: a little less work, the same picture.
- Display settings: `window.remember` keeps fullscreen too, and a new `window.startMaximized` opens the window
  maximised when neither Prism nor a remembered window says otherwise. Both are off here, so nothing changes unless
  you turn them on.

## 0.1.0 (2026-11-02)

The first release of Pure Faithful, for Windows.

- Runs on Java 21 on our own LWJGL (the LWJGL 2 API on LWJGL 3 and SDL3, natives built from source): its own
  resizable window, sharp on high-resolution screens, borderless fullscreen with F11, paste with Ctrl+V, mouse look
  that follows Windows' pointer settings as the original did (raw input is a setting), sound that follows a change of
  output device.
- The window opens at the size, or maximised, as set in Prism's instance settings (Window size, Start Minecraft
  maximized), as the original game did in Prism; Fabric's launch had lost those settings. (Remembering the window
  between launches is a setting, `window.remember`, off here; Old Faithful will turn it on.)
- Vsync on by default, with even frame pacing and chunk building that keeps up under it.
- Faster with the same picture: batched clouds, faster chunk lookups, light cached while a chunk is rebuilt, an
  unused per-frame calculation skipped.
- Safe online: sounds from Mojang's asset index over HTTPS, checked by hash; skins and logins over HTTPS; the access
  token never sent in the clear or printed. The client and the server are hardened against malicious servers and
  clients.
- Saves: chunks, level.dat, player and map files written so a crash or power cut leaves the old or the new version,
  never a damaged one; unreadable chunks kept in `corrupt/` and reported instead of regenerated over.
- Screenshots (F2) work after the window is enlarged.
- "Pure Faithful 0.1.0" on the title screen, under beta's version line; the client's log in
  `.minecraft/logs/purefaithful/`.
