package org.studioquire.oldfaithful.saves;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.chunk.storage.RegionFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The real b1.7.3 dedicated server with oldfaithful-saves: damaged chunks are kept and reported, never regenerated
 * silently; the server killed outright during saves, again and again, always leaves a world whose every chunk and
 * level.dat read cleanly. The world is checked with vanilla's own reader in this JVM (no mixins here).
 * Run with {@code ./gw :oldfaithful-saves:serverSaveTest}.
 */
@Tag("server")
class ServerSaveTest {
	private static final int KILLS = 8;

	/**
	 * A folder of this run's own: runs started by different sessions at the same time (or a server left behind by a
	 * test JVM that was killed) once shared one world, and a server saving into it changed region files between the
	 * test's checks and its edits.
	 */
	private final Path base = Path.of(System.getProperty("oldfaithful.run"));
	private final Path run = base.resolve("run-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis());
	/** Servers still running, killed if this JVM exits before they're stopped. */
	private static final java.util.Set<Process> live = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			synchronized (live) {
				for (Process p : live) p.destroyForcibly();
			}
		}));
	}
	private final Path world = run.resolve("world");
	private final List<String> log = new ArrayList<>();

	@Test
	void damagedChunksAndKilledSaves() throws Exception {
		fresh();
		// 1. A world. Started twice: on a new world b1.7.3's server gives the nether its own spawn search and saves its
		// level.dat last, so the spawn level.dat ends up with (vanilla's own behaviour) can lie away from the area the
		// first start generated. The second start loads that spawn and generates the area around it.
		for (int start = 0; start < 2; start++) {
			try (Server s = new Server()) {
				s.await(l -> l.contains("Done ("), 300);
				s.saveAndStop();
			}
		}
		assertClean(world);

		// 2. Damage four chunks next to spawn, in four different ways.
		NbtCompound data = levelData(world);
		int sx = data.getInt("SpawnX") >> 4, sz = data.getInt("SpawnZ") >> 4;
		int[] garbage = {sx + 1, sz}, version = {sx - 1, sz}, chest = {sx, sz + 1}, sky = {sx, sz - 1};
		byte[] garbageRecord = damagePayload(garbage);
		byte[] versionSectors = damageVersion(version);
		editChunk(chest, level -> {
			NbtCompound bad = new NbtCompound();
			bad.putString("id", "Chest");
			bad.putInt("x", chest[0] * 16);
			bad.putInt("y", 1);
			bad.putInt("z", chest[1] * 16);
			bad.putInt("Items", 5); // a chest's Items must be a list: reading it throws
			level.getList("TileEntities").add(bad);
		});
		editChunk(sky, level -> level.putByteArray("SkyLight", new byte[100]));

		// 3. The server loads them (spawn chunks load at start-up).
		List<String> lines;
		try (Server s = new Server()) {
			s.await(l -> l.contains("Done ("), 300);
			s.saveAndStop();
			lines = s.lines();
		}
		assertTrue(has(lines, "The chunk at blocks " + garbage[0] * 16 + ", " + garbage[1] * 16 + " in "), "damaged data reported");
		assertTrue(has(lines, "The chunk at blocks " + version[0] * 16 + ", " + version[1] * 16 + " in "), "bad compression type reported");
		assertTrue(has(lines, "The chunk at blocks " + chest[0] * 16 + ", " + chest[1] * 16 + " had damaged parts (a block entity (Chest)"),
				"the unreadable chest left out, the chunk kept");
		assertTrue(has(lines, "The chunk at blocks " + sky[0] * 16 + ", " + sky[1] * 16 + " had damaged parts (sky light"), "sky light rebuilt");
		assertCopy(world, garbage, garbageRecord);
		assertCopy(world, version, versionSectors);
		// Partly damaged chunks load without the damaged part, the record as found is kept, and the repaired chunk is
		// saved over it (so step 5 must find nothing to report).
		assertCopyExists(world, chest);
		assertCopyExists(world, sky);
		assertClean(world);

		// 3b. A region file left a partial sector long (a write cut short while growing it, or another tool): vanilla's
		// opener would zero its chunk index and every chunk in it would be generated anew. Every one must survive.
		File spawnRegion = regionOf(new int[] {sx, sz});
		List<Integer> before = entriesIn(spawnRegion);
		try (RandomAccessFile raf = new RandomAccessFile(spawnRegion, "rw")) {
			raf.seek(raf.length());
			raf.write(new byte[300]);
		}
		try (Server s = new Server()) {
			s.await(l -> l.contains("Done ("), 300);
			s.saveAndStop();
			lines = s.lines();
		}
		assertTrue(has(lines, "padded to"), "the unaligned file wasn't noticed");
		assertTrue(!has(lines, "couldn't be read"), "chunks lost from the unaligned file");
		assertTrue(entriesIn(spawnRegion).containsAll(before), "entries of the unaligned file went missing");
		assertEquals(0, spawnRegion.length() % 4096);
		assertClean(world);

		// 4. Kill the server outright during saves, again and again.
		Random random = new Random(42);
		int saves = 0;
		for (int k = 0; k < KILLS; k++) {
			Server s = new Server();
			s.await(l -> l.contains("Done ("), 300);
			long until = System.currentTimeMillis() + 500 + random.nextInt(3000);
			while (System.currentTimeMillis() < until) {
				s.send("save-all");
				saves++;
				Thread.sleep(random.nextInt(250));
			}
			s.kill();
			assertClean(world);
			// Nothing that was there before is gone.
			List<Integer> now = entriesIn(spawnRegion);
			assertTrue(now.containsAll(before), "entries missing after kill " + k);
			before = now;
		}
		System.out.println("[ServerSaveTest] " + KILLS + " kills during " + saves + " save-alls: every chunk and level.dat read cleanly after each");

		// 5. And it opens normally afterwards.
		try (Server s = new Server()) {
			s.await(l -> l.contains("Done ("), 300);
			s.saveAndStop();
			lines = s.lines();
		}
		for (String l : lines) assertTrue(!l.contains("couldn't be read") && !l.contains("had damaged parts"), "after the kills: " + l);
		assertClean(world);
		for (String l : log) {
			String low = l.toLowerCase();
			assertTrue(!(low.contains("mixin") && (low.contains("error") || low.contains("failed"))), "Mixin problem: " + l);
		}
		// Passed: this run's world goes (a failed run's stays for a look, and is cleared after two hours).
		delete(run);
	}

	// --- the world ---------------------------------------------------------------------------------------------------

	/** Every chunk in every region reads with vanilla's reader and parses; level.dat (or the fallback) parses. */
	private static void assertClean(Path world) throws IOException {
		List<String> problems = new ArrayList<>();
		int chunks = 0;
		try (Stream<Path> files = Files.walk(world)) {
			for (Path p : files.filter(f -> f.toString().endsWith(".mcr")).toList()) {
				// Never opened with vanilla's constructor if unaligned: it would zero the index we're checking.
				if (Files.size(p) % 4096 != 0) {
					problems.add(p.getFileName() + " left " + Files.size(p) + " bytes, not whole sectors");
					continue;
				}
				RegionFile region = new RegionFile(p.toFile());
				try {
					for (int i = 0; i < 1024; i++) {
						if (!region.hasChunkData(i & 31, i >> 5)) continue;
						chunks++;
						try {
							DataInputStream in = region.getChunkInputStream(i & 31, i >> 5);
							if (in == null) throw new IOException("vanilla can't read it");
							NbtCompound level = NbtIo.read(in).getCompound("Level");
							if (level.getByteArray("Blocks").length != 32768) throw new IOException("no blocks");
						} catch (Exception e) {
							problems.add(p.getFileName() + " entry " + i + ": " + e);
						}
					}
				} finally {
					region.close();
				}
			}
		}
		assertTrue(chunks > 100, "only " + chunks + " chunks");
		assertNotNull(levelData(world), "level.dat unreadable");
		assertEquals(List.of(), problems);
	}

	private static NbtCompound levelData(Path world) throws IOException {
		File dat = world.resolve("level.dat").toFile();
		if (!dat.exists()) dat = world.resolve("level.dat_new").toFile(); // a kill between the renames
		try (InputStream in = new FileInputStream(dat)) {
			return NbtIo.readCompressed(in).getCompound("Data");
		}
	}

	private File regionOf(int[] c) {
		return world.resolve("region").resolve("r." + (c[0] >> 5) + "." + (c[1] >> 5) + ".mcr").toFile();
	}

	private static int index(int[] c) {
		return (c[0] & 31) + (c[1] & 31) * 32;
	}

	/** The indices with an entry in a region's header, read directly (no opener that could change the file). */
	private static List<Integer> entriesIn(File region) throws IOException {
		List<Integer> out = new ArrayList<>();
		try (RandomAccessFile raf = new RandomAccessFile(region, "r")) {
			for (int i = 0; i < 1024; i++) if (raf.readInt() != 0) out.add(i);
		}
		return out;
	}

	private int entry(RandomAccessFile raf, int[] c) throws IOException {
		raf.seek(index(c) * 4L);
		int e = raf.readInt();
		assertTrue(e != 0, "chunk " + c[0] + ", " + c[1] + " not generated");
		return e;
	}

	/** Damages the compressed bytes; returns the record as it now stands (what the copy must hold). */
	private byte[] damagePayload(int[] c) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(regionOf(c), "rw")) {
			int e = entry(raf, c);
			long at = (long) (e >>> 8) * 4096;
			assertTrue(at + 5 <= raf.length(), "entry " + Integer.toHexString(e) + " points past the end (" + raf.length() + " bytes)");
			raf.seek(at);
			int length = raf.readInt();
			assertTrue(length > 1 && at + 4 + length <= raf.length() && 4 + length <= (e & 0xFF) * 4096,
					"record at sector " + (e >>> 8) + " claims " + length + " bytes; file " + raf.length() + " bytes, " + (e & 0xFF) + " sectors");
			raf.seek(at + 5 + 10);
			byte[] junk = new byte[60];
			java.util.Arrays.fill(junk, (byte) 0x55);
			raf.write(junk);
			byte[] record = new byte[4 + length];
			raf.seek(at);
			raf.readFully(record);
			return record;
		}
	}

	/** An unknown compression type; returns the record's sectors (what the copy must hold). */
	private byte[] damageVersion(int[] c) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(regionOf(c), "rw")) {
			int e = entry(raf, c);
			long at = (long) (e >>> 8) * 4096;
			raf.seek(at + 4);
			raf.writeByte(7);
			byte[] sectors = new byte[(e & 0xFF) * 4096];
			raf.seek(at);
			raf.readFully(sectors);
			return sectors;
		}
	}

	private void editChunk(int[] c, java.util.function.Consumer<NbtCompound> edit) throws IOException {
		NbtCompound root;
		RegionFile region = new RegionFile(regionOf(c));
		try {
			root = NbtIo.read(region.getChunkInputStream(c[0] & 31, c[1] & 31));
			edit.accept(root.getCompound("Level"));
			DataOutputStream out = region.getChunkOutputStream(c[0] & 31, c[1] & 31);
			NbtIo.write(root, out);
			out.close();
		} finally {
			region.close();
		}
	}

	private static void assertCopy(Path world, int[] c, byte[] expected) throws IOException {
		Path corrupt = world.resolve("corrupt");
		String prefix = "chunk." + c[0] + "." + c[1] + ".";
		try (Stream<Path> files = Files.list(corrupt)) {
			Path copy = files.filter(p -> p.getFileName().toString().startsWith(prefix) && p.toString().endsWith(".record"))
					.findFirst().orElseThrow(() -> new AssertionError("no copy of chunk " + c[0] + ", " + c[1]));
			assertArrayEquals(expected, Files.readAllBytes(copy), "the copy isn't the record as found");
			assertTrue(Files.exists(Path.of(copy + ".txt")));
		}
	}

	private static void assertCopyExists(Path world, int[] c) throws IOException {
		String prefix = "chunk." + c[0] + "." + c[1] + ".";
		try (Stream<Path> files = Files.list(world.resolve("corrupt"))) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith(prefix) && p.toString().endsWith(".record")),
					"no copy of chunk " + c[0] + ", " + c[1]);
		}
	}

	private static boolean has(List<String> lines, String text) {
		for (String l : lines) if (l.contains(text)) return true;
		return false;
	}

	// --- the server --------------------------------------------------------------------------------------------------

	private void fresh() throws IOException {
		if (!base.toString().replace('\\', '/').contains("/local/run/")) throw new AssertionError("unexpected run dir " + base);
		Files.createDirectories(base);
		// Old runs' folders (failed ones are kept for a look); never one that might still be running.
		long cutoff = System.currentTimeMillis() - 2 * 3600_000L;
		try (Stream<Path> old = Files.list(base)) {
			for (Path p : old.toList()) {
				if (!Files.isDirectory(p) || !p.getFileName().toString().startsWith("run-")) {
					// The single shared folder of earlier versions of this test.
					if (Files.isDirectory(p) && Files.getLastModifiedTime(p).toMillis() < cutoff) delete(p);
					else if (!Files.isDirectory(p)) Files.delete(p);
					continue;
				}
				if (Files.getLastModifiedTime(p).toMillis() < cutoff) delete(p);
			}
		}
		Files.createDirectories(run.resolve("mods"));
		Path mod = Path.of(System.getProperty("oldfaithful.saves.mod"));
		Files.copy(mod, run.resolve("mods").resolve(mod.getFileName()));
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		Files.writeString(run.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false",
				"server-ip=127.0.0.1", "server-port=" + port, "view-distance=4", "allow-nether=false", "spawn-animals=true", ""));
	}

	private static void delete(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		}
	}

	private final class Server implements AutoCloseable {
		private final Process process;
		private final List<String> lines = new ArrayList<>();
		private final OutputStream stdin;

		Server() throws IOException {
			String java = ProcessHandle.current().info().command().orElse("java");
			process = new ProcessBuilder(java, "-Xmx768m", "-cp", System.getProperty("oldfaithful.server.classpath"),
					"net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui").directory(run.toFile()).redirectErrorStream(true).start();
			live.add(process);
			process.onExit().thenRun(() -> live.remove(process));
			stdin = process.getOutputStream();
			Thread reader = new Thread(() -> {
				try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = in.readLine()) != null) {
						synchronized (lines) {
							lines.add(line);
							lines.notifyAll();
						}
						synchronized (log) {
							log.add(line);
						}
						if (line.contains("[purefaithful-saves]")) System.out.println("  server: " + line);
					}
				} catch (IOException ignored) {
					// The process went away.
				}
			});
			reader.setDaemon(true);
			reader.start();
		}

		void await(Predicate<String> p, int seconds) throws InterruptedException {
			long deadline = System.currentTimeMillis() + seconds * 1000L;
			synchronized (lines) {
				int seen = 0;
				while (true) {
					for (; seen < lines.size(); seen++) if (p.test(lines.get(seen))) return;
					long left = deadline - System.currentTimeMillis();
					if (left <= 0 || !process.isAlive() && seen >= lines.size()) throw new AssertionError("timed out; output:\n" + String.join("\n", lines));
					lines.wait(Math.min(left, 500));
				}
			}
		}

		void send(String command) throws IOException {
			stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
			stdin.flush();
		}

		void saveAndStop() throws Exception {
			send("save-all");
			await(l -> l.contains("Save complete."), 120);
			send("stop");
			assertTrue(process.waitFor(120, TimeUnit.SECONDS), "server didn't stop");
		}

		void kill() throws InterruptedException {
			process.destroyForcibly();
			process.waitFor(30, TimeUnit.SECONDS);
		}

		List<String> lines() {
			synchronized (lines) {
				return new ArrayList<>(lines);
			}
		}

		@Override
		public void close() throws InterruptedException {
			if (process.isAlive()) kill();
		}
	}
}
