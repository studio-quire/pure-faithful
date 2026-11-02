# b1.7.3's own bugs: the catalogue (phase 5, the Fixed edition)

Claude Opus 5.5, 2026-11-02. Second pass, for the user to read before any fixing is planned. Nothing here is fixed
yet. Purist leaves all of it alone except where an entry says it may be the user's call to take into Purist.

Sources:
- The decompiled game (`local/decomp/b1.7.3-named`, Babric's names), read for causes.
- For the list: the bugfix sections of UniTweaks, RetroTweaks, MojangFix(-StationAPI) and LegacyFix
  (`local/audit/`), read for what they fix, never for how.
- The Minecraft Wiki's pages for Beta 1.7, 1.7.3 and 1.8 (what 1.8 fixed or changed is the best list of what 1.7.3
  got wrong).
- Mojira barely covers this version: the tracker opened after 1.0, and beta-era reports lived on forums that are
  mostly gone. So the wiki stands in for it, and the multiplayer and piston entries are thinner than they'd be with
  a real tracker. The wiki's per-topic pages (mobs, redstone, doors, rails, farming) were only reached through the
  version pages' change lists.

Key:
- **Status**: **T** = cause traced in the decompiled code here (class/method given); **L** = listed by a fix mod,
  the wiki or wide knowledge, cause not traced (the entry says so).
- **Kind**: **bug** (clearly wrong, fix it), **quirk** (part of beta's feel: a toggle, default as beta), **ask** (the
  user's call).
- **Impact**: how much players notice: high (most players, or loses their work), mid (many, or a regular annoyance),
  low (few, or cosmetic and rare).

## Ranked summary

| # | What | Kind | Impact | Status |
|---|---|---|---|---|
| 1 | A chunk that fails to load is silently regenerated, erasing what was built there | bug (Purist) | high | fixed |
| 2 | Chunk saves overwrite in place, and an oversized chunk is silently never saved | bug (Purist) | high, rare | fixed |
| 3 | Lighting errors: dark or bright patches left behind at chunk edges and after big changes | bug | high | T |
| 4 | Farm animals despawn: pens empty while you're away (all but tamed wolves) | ask | high | T |
| 5 | Multiplayer ghost blocks: a break the server refuses for distance is never corrected | bug | high (MP) | T |
| 6 | Dusk and dawn: every step of daylight rebuilds the whole view (stutter, stepped darkening) | ask (speed, looks) | mid-high | T |
| 7 | Grey sky while an inventory or any container is open | bug | mid | T |
| 8 | Tools' last use: the block drops nothing (multiplayer: everything; single player: shears, shovels on snow) | bug | mid | T |
| 9 | Wooden slabs need a pickaxe to drop | bug | mid | T |
| 10 | Wrong tools for many blocks (axes slow on crafting tables, fences, doors; pickaxes on furnaces, rails...) | bug | mid | L |
| 11 | Furnace eats the lava bucket | bug | mid | T |
| 12 | Boats break on bumping into things at speed | ask | mid | T |
| 13 | Stairs drop their material (wood stairs: a plank; cobblestone stairs: one cobblestone) | ask | mid | T |
| 14 | Autosave every 2 seconds on the game thread: a hitch every 40 ticks | ask (speed) | mid | T |
| 15 | Multiplayer entity jitter (client interpolation) | bug | mid (MP) | L |
| 16 | Multiplayer: invisible players, holes of unloaded chunks | bug | mid (MP) | L |
| 17 | Advanced OpenGL: chunks flicker out of view | bug | mid | L |
| 18 | Screenshots fail after the window is enlarged | bug (Purist?) | mid now | T |
| 19 | Water flowing down destroys torches, rails and redstone without dropping them | bug | low-mid | T |
| 20 | Lava lingers without its source; water sources never form above water | ask | low-mid | T |
| 21 | Far Lands | quirk | low-mid | L |
| 22 | Jitter and stretched textures far from the origin (float precision) | bug | low-mid | L |
| 23 | Piston crashes with some blocks | bug | low-mid | L |
| 24 | Player-placed leaves decay | ask | low-mid | L |
| 25 | Slimes don't split when killed by a big hit | bug | low | T |
| 26 | Mobs that wake you can reach you through a wall | bug | low-mid | T |
| 27 | Falling through the boat when getting out | bug | low-mid | L |
| 28 | Sleeping view turned the wrong way | bug | low | L |
| 29 | Saddled pigs don't drop the saddle | bug | low | T |
| 30 | Fences: dark bottom face; hitbox too big (the "smart farm" trick) | bug, ask | low | L |
| 31 | Minecarts: stopped by items and arrows; a slip in cart-on-cart collisions | bug, ask | low-mid | T |
| 32 | Video settings sliders can't be dragged | bug | low-mid | L |
| 33 | Crash on a slab item with a bad data value | bug | low | L |
| 34 | Small visual bugs (list in the entry) | bug | low | L |
| 35 | Eating at full health wastes the food | ask | low | L |
| 36 | Multiplayer mining delay inconsistent | ask (MP) | low-mid | L |
| 37 | Skeletons circle the player, hard to hit | quirk | low | L |
| 38 | Footsteps saved up going down stairs, then played in a burst at the bottom | bug | mid | T |
| 39 | The item on the cursor draws behind the player model and the screen's labels | bug | low-mid | T |
| 40 | Option sliders can't reach every percentage | bug | low | T |

## Already handled by Purist work

- AMD z-fighting ("bit depth"; Beta 1.8 went to a 24-bit depth buffer): our LWJGL layer asks for at least 24 bits
  (`Window.create`).
- Controller start-up freeze (jinput): our `Controllers` reports none.
- Fullscreen, alt-tab, high DPI, resizing, paste, mouse feel: `oldfaithful-display` and our LWJGL layer.
- Skins, capes, logins, sound downloads: `oldfaithful-hardening`.
- For phase 6 (multiplayer) rather than here: the client's artificial delay in packet processing and the missing
  TCP_NODELAY (listed by UniTweaks).

## Beta design, not bugs (no entry)

Changed later on purpose and part of what beta is: zombies drop feathers, arrows vanish on hitting a mob, no hunger,
taiga with snow, wheat seeds from grass. If a Fixed edition wants "what it should have been", these belong to the
later transformative packs, not to bug fixing.

## Possibly the user's call for Purist too

Purist fixes what broke because hardware and systems moved on. Three entries sit on that line:
- **1 and 2 (save safety).** Not caused by modern machines, but they lose players' worlds, and a hardening pack that
  leaves known data loss in place may not read as "hardened".
- **18 (screenshots).** The bug is beta's, but our resizable window makes it easy to hit.

## Entries

### 1. Failed chunk load regenerates the chunk (T)
Fixed in Purist, 2026-11-02: `oldfaithful-saves` (`docs/saves.md`).
`ChunkCache.loadChunkFromStorage` catches any exception from `RegionChunkStorage.loadChunk` and returns null;
`ChunkCache.loadChunk` then asks the generator for a fresh chunk, which is saved over the old one at the next save.
Same for a chunk record missing `Level` or `Blocks` (`RegionChunkStorage.loadChunk` returns null). A truncated
record, a corrupt NBT tag or a block entity whose `readNbt` throws (`AlphaChunkStorage.loadChunkFromNbt` ->
`BlockEntity.createFromNbt`) is enough. Fix: on a failed load, copy the raw record aside (a `corrupt/` folder), and
load what can be read (drop only the bad block entity or entity, keep the blocks); regenerate only when nothing is
readable, and say so in the log. Risk: low; changes nothing in a healthy world.

### 2. Non-atomic chunk saves (T)
Fixed in Purist, 2026-11-02, with level.dat, player and map files: `oldfaithful-saves` (`docs/saves.md`).
`RegionFile.writeChunkData`: a chunk that still fits its sectors is rewritten in place, so a crash or power cut
mid-write leaves a corrupt record (then entry 1 regenerates it). A chunk needing 256 sectors or more (1 MB compressed)
returns without saving and without a word. Nothing is synced to disk. Fix: write the new record to free sectors, then
update the header (old data stays valid until the header points elsewhere); log oversized chunks and store them
anyway in a side file; sync on world save. Risk: low-moderate (region format must stay readable by vanilla; the
format allows this). Interaction: entry 14's cadence.

### 3. Lighting errors (T)
`World.queueLightUpdate` silently drops an update whose centre column isn't loaded (`isPosLoaded(midX, 64, midZ)`) or
whose chunk is empty, and any update queued 50 levels deep in recursion (`lightingQueueCount == 50`); the queue is
cleared outright past 1,000,000 entries ("aborting lighting updates"). `doLightingUpdates` processes at most 500 a
call. What players see: black or over-bright patches under overhangs, in caves and along chunk borders that never
heal, especially after generation and explosions. Beta 1.8 replaced the lighting engine. Fix: never drop work (defer
updates for unloaded areas until the chunk loads; replace the depth cap and overflow clear with a proper queue), and
relight a chunk's borders when its neighbour loads. Risk: moderate: correct light differs from beta's where beta was
wrong, which is the point, but affects mob spawning and grass/ice behaviour slightly. Interaction: phase 4 (the
meshing light cache; a faster lighting engine was set aside in `docs/speed.md` as changing light values, which is
exactly this entry).

### 4. Farm animals despawn (T, ask)
`LivingEntity.tryDespawn`: any mob more than 32 blocks from the nearest player, once 600 ticks have passed since it was
last hurt, has a 1 in 800 chance a tick of vanishing, and beyond 128 blocks vanishes at once (while its chunk is
loaded); `canDespawn()` is true for every mob except a
tamed wolf (`WolfEntity`). So penned cows, pigs (saddled ones too), sheep and chickens disappear whenever the player
wanders off. Beta 1.8 stopped passive mobs despawning. Players rebuilt farms around this for years; whether it's a bug
or beta's design is the user's call (a fix: passive mobs never despawn, or only naturally spawned ones away from
players' builds; Beta 1.8's way pairs with its changed animal spawning).

### 5. Ghost blocks in multiplayer (T)
`ServerPlayNetworkHandler.handlePlayerAction`: a start or finish of mining more than 6 blocks (squared distance 36)
from the player's position returns without a `BlockUpdateS2CPacket`, while the client has already removed the block
(`MultiplayerInteractionManager`). The client then shows air where the server has a block (walk into it, fall
through, get pushed back). Fix: send the block update back whenever the server refuses. Risk: none. Fits phase 6 too.

### 6. Dusk and dawn rebuild the whole view (T, ask)
`World.updateSkyBrightness` -> `notifyAmbientDarknessChanged` -> `WorldRenderer.notifyAmbientDarknessChanged` marks
every sky-lit render chunk dirty whenever the ambient darkness steps (about eleven steps at each dusk and dawn), since
sky brightness is baked into the chunk meshes. Each step re-meshes the whole view: a wave of chunk rebuilds (a hitch,
and with a far view the darkening sweeps across over seconds), and the light changes in visible steps. Beta 1.8 made
the cycle smooth with no chunk updates (a lightmap). Fix: apply sky brightness at draw time (a lightmap or a
per-vertex sky term), so no rebuild is needed. It changes how dusk looks (smooth instead of stepped), so not a pure
speed win: the user's call, Fixed edition, possibly a toggle. Interaction: phase 4 (chunk building throughput; the
vsync chunk budget).

### 7. Grey sky while a container is open (T)
`HandledScreen.render` ends with `glEnable(GL_LIGHTING)` (and depth test). The next frame `GameRenderer.renderFrame`
draws the sky (`WorldRenderer.renderSky`, at Far and Normal) before anything calls `Lighting.turnOff`, so the sky is
lit by the fixed-function lights with the normal `applyFog` sets (pointing down): only ambient reaches it, and it
reads grey. Every frame while the screen is open, and the first frame after it closes. Fix: disable lighting before
the sky (or not leave it on after the screen). Risk: none. Interaction: `renderFrame` is where phase 4's chunk budget
lives (separate lines).

### 8. Last durability point: no drop (T)
`ServerPlayerInteractionManager.tryBreakBlock` clears the broken tool from the hand before checking
`player.canHarvest`, so in multiplayer the block mined with a tool's last use (stone, ore, obsidian) drops nothing.
`SingleplayerInteractionManager.breakBlock` checks `canHarvest` first, so single player only loses drops decided
inside `afterBreak` from the hand (leaves with shears, snow with a shovel). Fix: decide drops before removing the
tool. Risk: none.

### 9. Wooden slabs need a pickaxe (T)
`SlabBlock` is `Material.STONE` for every variant, and stone material needs a pickaxe to drop, so wooden slabs mined
by hand or axe vanish. Fix: wooden variant harvestable without a tool, axe effective. Risk: low (one block, metadata
dependent).

### 10. Tool effectiveness gaps (L)
UniTweaks' list: axes aren't effective on crafting tables, wooden slabs and stairs, fences, wooden doors, ladders,
signs, pumpkins and jack-o'-lanterns, wooden pressure plates, jukeboxes, note blocks, trapdoors; pickaxes on furnaces,
cobblestone stairs, bricks, redstone ore, iron doors, rails, dispensers, stone pressure plates, spawners, buttons;
shovels on soul sand. Cause: each tool's effective-block list (`ToolItem` subclasses), not traced item by item. Fix:
complete the lists. Risk: low; changes mining speed only. Some (rails, buttons) may be deliberate: check each.

### 11. Furnace eats the lava bucket (T)
`FurnaceBlockEntity.tick` decrements the fuel stack and nulls it at zero; a lava bucket leaves no empty bucket.
Fix: leave an empty bucket in the fuel slot. Risk: none.

### 12. Boats break on collisions (T, ask)
`BoatEntity.tick`: any horizontal collision above 0.15 speed kills the boat and drops three planks and two sticks.
Players hated it, but it was how beta's boats worked, and later versions changed it on purpose. Options: keep, toggle
(default beta), or fix. The user's call.

### 13. Stairs drop their material (T, ask)
`StairsBlock.getDroppedItemId/Count` delegate to the base block: wooden stairs drop a plank, cobblestone stairs one
cobblestone, from a recipe of six blocks for four stairs. Later versions drop the stairs. Bug or beta design: the
user's call.

### 14. Autosave every 2 seconds (T, ask, speed)
`World.tick` calls `saveWithLoadingDisplay(false, null)` when the world time is a multiple of `saveInterval` (40
ticks): level.dat plus up to 24 dirty chunks (`ChunkCache.save`), compressed and written on the game thread. A
periodic hitch while building or exploring. Moving saves off the thread or spacing them out changes timing and the
crash-loss window, so it isn't a pure speed win (phase 4 didn't take it). With entry 2 fixed, a longer interval is
safer. The user's call.

### 15. Multiplayer entity jitter (L)
Client-side interpolation of other entities' positions jitters (UniTweaks, RetroTweaks). Not traced; start at
`ClientWorld` and the entity `updatePositionAndAngles` path. Fits phase 6.

### 16. Multiplayer: invisible players, chunk holes (L)
Widely reported on beta servers: a player who teleports, respawns or changes dimension can stay invisible to others
until they relog, and the client can show holes of unloaded chunks (walk into them and fall). Not traced; start at
the server's entity tracker and player chunk map (`server/` package) and the client's chunk packet handling. Fits
phase 6.

### 17. Advanced OpenGL flicker (L)
With Advanced OpenGL on, chunks blink out at the edges of view when turning: occlusion query results lag the camera.
Not traced. Interaction: phase 4's culling and chunk work; any fix there must keep Purist's pixels where the option
is off.

### 18. Screenshots fail after enlarging the window (T)
`Screenshot.take` reallocates its pixel buffer only when `capacity < width * height`, but uses `width * height * 3`
bytes. After the window grows (say 1281x720 to a 1920x1080 maximise), `glReadPixels` overruns the buffer and the
screenshot fails. Vanilla's window could be resized too, but our resizable, HiDPI-scaled window makes it common.
Fix: compare against the size actually used. Risk: none. **Fixed in both editions** (user, 2026-11-02): `oldfaithful-display`'s
`ScreenshotMixin` drops a buffer too small for the read.

### 19. Water flowing down destroys without drops (T)
`FlowingLiquidBlock.onScheduledTick`: sideways spread goes through `spreadTo`, which calls `dropStacks` on what it
replaces; downward flow calls `world.setBlock(x, y - 1, z, ...)` directly, so torches, rails, redstone, flowers and
crops below a falling stream vanish. Fix: drop (or fizz, for lava) on the downward path as on the sideways one.
Risk: none (players used water to clear things either way).

### 20. Lingering lava; water sources over water (T, ask)
Same method. Lava: with 3 chances in 4 it refuses to thin out (`random.nextInt(4) != 0`), and when it refuses it
neither changes nor schedules another tick, so flowing lava cut off from its source can stay forever until something
next to it changes. Water: the check meant to make a new source above still water reads the current block's data
(`world.getBlockMeta(x, y, z) == 0`, never true for flowing water there) instead of the block below, so sources only
form over solid ground. Both are slips in the code, but both shape builds players made (lava lighting, water
setups), so: the user's call; toggles likely.

### 21. Far Lands (L, quirk)
Terrain noise overflows past about 12.5 million blocks out. Part of beta's lore: a toggle, default as beta. Beta 1.8
moved them beyond 32 million.

### 22. Far-out jitter and stretching (L)
Rendering and movement lose float precision far from the origin: camera and entities shake, and ladders, rails,
redstone and the sides of falling liquids stretch or snap well before the Far Lands (Beta 1.8 fixed these). Fixable
by rendering relative to the camera. Not traced. Bug, separate from 21. Interaction: phase 4's rendering.

### 23. Piston crashes (L)
Beta 1.7.3 fixed piston duplication and one sign crash; Beta 1.8 "fixed several crash bugs relating to pistons"
without naming them. Not traced; a crash hunt would push and pull every block kind with a piston on the test
server. Quasi-connectivity and block-update detection are quirks technical players rely on: leave them.

### 24. Player-placed leaves decay (L, ask)
Leaves placed by a player decay when no log is near (Beta 1.8 stopped that). Not traced (`LeavesBlock` decay
check). Beta's design or a bug: the user's call.

### 25. Slimes don't split on overkill (T)
`SlimeEntity.markDead` splits only when `health == 0`; a hit that takes health below zero kills a big slime outright.
Fix: `health <= 0`. Risk: none.

### 26. Waking mobs reach you through a wall (T)
`NaturalSpawner.spawnMonstersAndWakePlayers` accepts a monster when the path the pathfinder found ends within 1.5
blocks of the bed on each axis. The path is the closest the mob can get, not a path to the bed, so a mob outside a
one-block wall passes and wakes the player. The pathfinder itself treats closed doors as solid. Fix: require the path
to reach the bed's block. Risk: low.

### 27 and 28 (L)
27: getting out of a boat can drop the player through it. 28: the view while sleeping is turned the wrong way. Not
traced.

### 29. Saddled pigs drop no saddle (T)
`PigEntity` only drops pork; the saddle is lost. Later versions drop it. Fix: drop it. Risk: none.

### 30. Fences (L, ask)
The bottom face of a fence on a block renders dark, and the fence's hitbox ignores its connections. The oversized
hitbox reaches over farmland, which players used to stop crops being trampled ("smart farms", Beta 1.8 removed it):
the lighting is a bug, the hitbox the user's call. Not traced.

### 31. Minecarts (T, ask)
`MinecartEntity.onCollision` pushes against any entity it touches, so dropped items and arrows slow or stop carts.
In the cart-on-cart branch the check meant to read the other cart's motion mixes `velocityZ` with `prevX` (a
position) where `velocityX` belongs. Partly traced: the item push is plain; what the slip does to two-cart behaviour,
including the booster trick players rely on, isn't worked out. Items: bug. The slip: the user's call once it's
understood.

### 32 to 34 (L)
- 32: the video settings sliders can't be dragged.
- 33: a slab item with a bad data value crashes (a name lookup out of range).
- 34, small visual bugs: bow held like an item, breaking animation invisible from below, hotbar flashes white (Fast,
  no clouds), grass block item, torch bottom, torches always drawn at full brightness, dropped item sizes (stairs,
  cactus, farmland, pressure plates, fences, cake, snow, buttons, trapdoors), items under container text, leggings
  and first-person hand while riding, death screen score format, caught fish flies behind you, clouds pass through
  blocks, entities unlit in the void.

Not traced.

### 35 to 37 (L)
- 35: food eaten at full health is wasted. Beta has no hunger, so this is design: ask.
- 36: the multiplayer block-mining delay differs from single player; some servers read the fix as a hack: ask,
  phase 6.
- 37: skeletons strafe around the player (Beta 1.8 changed it): their AI, a quirk.

### 38. Footsteps burst at the bottom of stairs (T)
Noticed by the user while playing (2026-11-02). `Entity.move` adds walked distance to `horizontalSpeed` every tick,
but plays a step (and advances `nextStepSoundDistance` by one) only when the block under the feet isn't air.
Going down stairs the player is briefly airborne, so distance piles up silently; back on solid ground one step
plays every tick until the counter catches up: a rattle of steps at the bottom. Fix: when no step can play,
keep `nextStepSoundDistance` level with the distance walked, so the backlog never builds. Risk: none.

### 39. The cursor's item draws behind the player and labels (T)
Noticed by the user (2026-11-02). `HandledScreen.render` draws the stack held on the cursor at z +32 with depth
on, then `drawForeground` (labels such as "Crafting") with depth off; `InventoryScreen.drawBackground` drew the
player model earlier at z 50 with depth on. So the held item passes behind the model and under the labels. Fix:
draw the cursor stack after `drawForeground`, above everything, as later versions do. Risk: mods drawing
foreground overlays expect vanilla's order; check the popular container mods.

### 40. Sliders can't reach every percentage (T)
Noticed by the user (2026-11-02). `SliderWidget` takes its value from the mouse's GUI x across a 142-unit track, so
there are 143 positions at any window size. Volume (0-100%, 0.7% a step) can reach every whole percent but only by
hitting the right GUI pixel, fiddly at large GUI scales; sensitivity is labelled 0-200% (`GameOptions.getString`),
1.4% a step, so about four in ten values can't be chosen. Fix: arrow keys (or the wheel) nudge a focused slider by
one labelled step, and dragging snaps to whole labelled percentages. Risk: none.

## Still thin

Redstone and pistons beyond crashes, doors, rails and farming have only what the version pages and fix mods mention.
A crash hunt with pistons, and a read of `RedstoneWireBlock`, `DoorBlock`, `RailBlock` and the crop blocks, would
fill them. The multiplayer entries (15, 16, 36) want the multiplayer agent's tracing in phase 6.
