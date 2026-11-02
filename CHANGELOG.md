# Changelog

Every release has one version number, shared by the mods, the LWJGL component, the pack and the server package
(docs/release.md > Versions). Newest first. Entries say what a player or server owner notices; the commits have the
rest.

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
