package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Old Faithful's multiplayer fixes (docs/fixes.md) on the real server, in every mix of sides: the server package with
 * and without oldfaithful-fixes, each joined once by the real client with the fix mod and once without it (hidden, on
 * a private desktop, as the start-up test runs it; its in-game half is the self-test's MultiplayerCheck), and the
 * server-side fixes checked with raw-packet clients. Where neither side that matters has a fix, the check expects
 * beta's bug, so each check is known to see what it checks.
 * <p>
 * Server side, with raw clients (RawClient): #5 a refused break answered with the block; #16a a teleported player seen
 * by the one they joined; #16b the chunks after that teleport nearest first; #48 a sign edited in the same tick as
 * another change reaching another player; #49 a player who died told on respawn that what they saw is gone.
 */
public abstract class FixesMultiplayerTestTask extends ClientStartupTask {
	/** local/server-package/OldFaithful-server, as :serverPackage writes it. */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** The self-test mod, for the server (its scenes and test commands). */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getHarnessMod();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getFixesMod();

	/** The client's mods without oldfaithful-fixes ({@code mods} has them with it). */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getClientModsWithoutFixes();

	/** Only the raw-client checks, no client launches (quicker, for working on the server side). */
	@org.gradle.api.tasks.Input
	public abstract org.gradle.api.provider.Property<Boolean> getRawOnly();

	public FixesMultiplayerTestTask() {
		getRawOnly().convention(false);
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		Path run = freshRunDir();
		List<String> failures = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		for (boolean fixedServer : new boolean[] {true, false}) {
			for (boolean fixedClient : getRawOnly().get() ? new boolean[] {true} : new boolean[] {true, false}) {
				String label = (fixedServer ? "fixed-server" : "plain-server") + "-" + (fixedClient ? "fixed-client" : "plain-client");
				lines.add("=== " + label);
				getLogger().lifecycle("--- {}", label);
				// Raw-client checks once per server kind, after its first client (so they don't share the scene).
				lines.addAll(serverRun(run.resolve(label), fixedServer, fixedClient, fixedClient, failures));
			}
		}
		report("Multiplayer fixes test", lines, failures);
	}

	private List<String> serverRun(Path dir, boolean fixedServer, boolean fixedClient, boolean rawChecks, List<String> failures) throws Exception {
		Path server = dir.resolve("server");
		copyTree(getServerPackage().get().getAsFile().toPath(), server);
		Files.copy(getServerJar().get().getAsFile().toPath(), server.resolve("minecraft_server.jar"));
		Files.copy(getHarnessMod().get().getAsFile().toPath(), server.resolve("mods").resolve(getHarnessMod().get().getAsFile().getName()));
		if (fixedServer) Files.copy(getFixesMod().get().getAsFile().toPath(), server.resolve("mods").resolve(getFixesMod().get().getAsFile().getName()));
		int port = freePort();
		Files.writeString(server.resolve("server.properties"), String.join("\n",
				"level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port,
				"view-distance=3", "max-players=20", "spawn-monsters=false",
				// Animals on: with them off, the server removes every animal, the scene's pigs too.
				"spawn-animals=true", "pvp=true", "allow-flight=true", ""));
		// The hidden client mines beside the spawn: past spawn protection as an operator.
		Files.writeString(server.resolve("ops.txt"), "OldFaithfulTest\n");

		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", server.resolve("start.bat").toString()) : List.of("sh", server.resolve("start.sh").toString());
		Map<String, String> env = Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx768m -Doldfaithful.selftest.multiplayer=true");
		String label = dir.getFileName().toString();
		List<String> lines = new ArrayList<>();
		try (GameProcess process = new GameProcess(command, server, server.resolve("harness-output.log"), env)) {
			if (process.await(l -> l.contains("Done ("), 300) == null) {
				failures.add(label + ": the server never finished starting");
			} else {
				try {
					if (!getRawOnly().get()) clientRun(dir, port, fixedServer, fixedClient, lines, failures);
					if (rawChecks) rawChecks(process, port, fixedServer, failures);
				} catch (Exception e) {
					failures.add(label + ": stopped: " + e);
					getLogger().lifecycle("Stopped", e);
				}
				process.send("stop");
			}
			Integer exit = process.waitFor(120);
			if (exit == null) failures.add(label + ": the server didn't stop within 2 minutes");
			List<String> out = process.lines();
			checkSelfTest(out, failures);
			for (String p : problems(out)) failures.add(label + ", server: " + p);
			lines.add("--- server");
			lines.addAll(out);
		}
		return lines;
	}

	/** The real client, hidden, joins as OldFaithfulTest while a raw client, SkyTurner, stands by to be turned. */
	private void clientRun(Path dir, int port, boolean fixedServer, boolean fixedClient, List<String> lines, List<String> failures) throws Exception {
		Path game = dir.resolve("client");
		Files.createDirectories(game.resolve("mods"));
		Iterable<File> mods = fixedClient ? getMods().getFiles() : getClientModsWithoutFixes().getFiles();
		for (File mod : mods) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		try (RawClient turner = new RawClient(port, "SkyTurner")) {
			String r = turner.login(60);
			if (r != null) failures.add("SkyTurner couldn't log in: " + r);
			List<String> client = launch(game, false, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port,
					"-Doldfaithful.selftest.mp.fixedClient=" + fixedClient, "-Doldfaithful.selftest.mp.fixedServer=" + fixedServer), failures);
			lines.add("--- client");
			lines.addAll(client);
		}
	}

	private void rawChecks(GameProcess server, int port, boolean fixed, List<String> failures) throws Exception {
		String side = fixed ? "fixed server" : "server without the fixes (control)";
		check(failures, side, "#5: a finish of mining 10 blocks away answered with the block", fixed, refusedBreak(port));
		boolean[] tp = teleport(server, port);
		check(failures, side, "#16a: a teleported player seen by the player they joined", fixed, tp[0]);
		check(failures, side, "#16b: after the teleport, the first chunk sent is the one the player stands in", fixed, tp[1]);
		check(failures, side, "#48: a sign edited in the same tick as another block change reaches another player", fixed, signEdit(port));
		check(failures, side, "#49: a respawned player told that a player who left while they were dead is gone", fixed, respawn(port));
	}

	/** {@code seen} must be {@code expected}: the fix's behaviour on a fixed server, beta's on the control. */
	private void check(List<String> failures, String side, String what, boolean expected, boolean seen) {
		String outcome = seen == expected ? "as expected" : "UNEXPECTED";
		getLogger().lifecycle("{} ({}): {} ({})", what, side, seen ? "yes" : "no", outcome);
		if (seen != expected) failures.add(what + " (" + side + "): " + (seen ? "yes" : "no"));
	}

	private boolean refusedBreak(int port) throws Exception {
		try (RawClient c = new RawClient(port, "SkyMiner")) {
			login(c);
			double[] p = c.position();
			int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[3]), y = (int) Math.floor(p[1]) - 10;
			c.playerAction(2, x, y, z, 1);
			return c.await(r -> r.id() == 53 && r.a() == x && r.b() == y && r.c() == z, 3) != null;
		}
	}

	/** #16a and #16b: SkyB flies 130 blocks east, out of SkyA's sight; then the console teleports SkyA to SkyB. */
	private boolean[] teleport(GameProcess server, int port) throws Exception {
		try (RawClient a = new RawClient(port, "SkyA"); RawClient b = new RawClient(port, "SkyB")) {
			login(a);
			login(b);
			a.stand(80, 50); // its first chunks, so none from before is still waiting at the teleport
			int bId = a.await(r -> r.id() == 20 && "SkyB".equals(r.text()), 5) != null ? a.playerEntity("SkyB") : -1;
			double[] p = b.position();
			for (int i = 1; i <= 130; i++) {
				b.move(p[0] + i, p[1], p[2], p[3], 0, 0, false);
				Thread.sleep(50);
			}
			if (a.await(r -> r.id() == 29 && r.a() == bId, 5) == null) throw new IOException("SkyA still sees SkyB 130 blocks away");
			int cx = (int) Math.floor(p[0] + 130) >> 4, cz = (int) Math.floor(p[3]) >> 4;
			long t0 = System.nanoTime();
			server.send("tp SkyA SkyB");
			boolean seen = b.await(r -> r.id() == 20 && "SkyA".equals(r.text()) && r.at() > t0, 3) != null;
			a.stand(60, 50);
			List<String> order = new ArrayList<>();
			int firstDistance = -1;
			for (RawClient.Received r : a.received()) {
				if (r.id() != 51 || r.d() != 1 || r.at() < t0) continue;
				int d = Math.max(Math.abs((r.a() >> 4) - cx), Math.abs((r.b() >> 4) - cz));
				if (firstDistance < 0) firstDistance = d;
				if (order.size() < 6) order.add((r.a() >> 4) + "," + (r.b() >> 4) + " (" + d + " away)");
			}
			getLogger().lifecycle("Chunks after the teleport to {},{}: {}", cx, cz, order);
			return new boolean[] {seen, firstDistance == 0};
		}
	}

	private boolean signEdit(int port) throws Exception {
		try (RawClient signer = new RawClient(port, "SkySigner"); RawClient watcher = new RawClient(port, "SkyWatcher")) {
			login(signer);
			login(watcher);
			signer.chat("!oftest signplace");
			Thread.sleep(1000);
			signer.chat("!oftest signedit");
			boolean seen = watcher.await(r -> r.id() == 130 && r.text().contains("Faithful"), 3) != null;
			List<String> signs = new ArrayList<>();
			for (RawClient.Received r : watcher.received()) if (r.id() == 130) signs.add(r.a() + " " + r.b() + " " + r.c() + " \"" + r.text() + "\"");
			getLogger().lifecycle("Sign texts SkyWatcher received: {}", signs);
			return seen;
		}
	}

	private boolean respawn(int port) throws Exception {
		try (RawClient dier = new RawClient(port, "SkyDier"); RawClient leaver = new RawClient(port, "SkyLeaver")) {
			login(dier);
			login(leaver);
			if (dier.await(r -> r.id() == 20 && "SkyLeaver".equals(r.text()), 5) == null) throw new IOException("SkyDier doesn't see SkyLeaver");
			int leaverId = dier.playerEntity("SkyLeaver");
			Thread.sleep(4000); // past the join invulnerability
			dier.chat("!oftest die");
			// A real client goes on sending its position while dead, and the server ticks the body on each: after the
			// death animation, it's removed.
			dier.stand(50, 50);
			leaver.close();
			dier.stand(30, 50);
			dier.respawn();
			return dier.await(r -> r.id() == 29 && r.a() == leaverId, 3) != null;
		}
	}

	private static void login(RawClient c) throws Exception {
		String r = c.login(60);
		if (r != null) throw new IOException(c.name + " couldn't log in: " + r);
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path p : walk.toList()) {
				Path target = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private static int freePort() throws IOException {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		}
	}
}
