package oldfaithful.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

/**
 * Starts the real client on our LWJGL component, as the SDL dev instance does (Fabric's launch, our mods, Java 21+,
 * offline), in a scratch game folder, and lets the self-test mod drive it: main menu, a paste into the server
 * address, a new small world loaded and run, then the game's own clean stop. Fails on any error in the output, a
 * missing check, no sound start-up, or a crash; always stops within the timeout.
 * <p>
 * Nobody sees it or is disturbed by it, so it can run on a PC that's in use: the game runs on a private Windows
 * desktop (tools/private_desktop.py), where no window can show, take focus or reach the cursor whatever the game
 * does; its own window is created hidden besides; sound goes to a file (OpenAL Soft's wave writer).
 * <p>
 * The launch passes a Prism window size ({@code org.prismlauncher.window.dimensions}, as Prism does), and the
 * window must open at it. Then, on Windows, two short launches in a game folder of their own, with the display
 * mod's {@code window.remember} on: the first with Prism asking for maximised and a kept placement from when Prism
 * asked otherwise (Prism's change wins: maximised), which the self-test then resizes, moves and maximises; the second
 * must open exactly there. The kept placement is read back after each.
 * <p>
 * With {@code platform} set to {@code linux-<image>}, the same run happens on Linux x64 instead: in a Docker
 * container with an X server nobody sees (tools/in_linux.py, harness/linux/Dockerfile.<image>), from any host.
 */
public abstract class ClientStartupTask extends GameTestTask {
	/** Our LWJGL component's natives, extracted as a launcher does. */
	@Internal
	public abstract DirectoryProperty getNatives();

	/** A game folder whose resources/ holds the sounds (clientAudit's cache), copied in so nothing downloads. */
	@Internal
	public abstract DirectoryProperty getSoundsGameDir();

	@Internal
	public abstract RegularFileProperty getPrivateDesktop();

	/** tools/in_linux.py, for the Linux platforms. */
	@Internal
	public abstract RegularFileProperty getInLinux();

	/** {@code windows} (a private desktop here) or {@code linux-<image>} (Docker, tools/in_linux.py). */
	@Input
	public abstract Property<String> getPlatform();

	@Input
	public abstract Property<String> getPython();

	@Input
	public abstract Property<Integer> getTimeoutSeconds();

	/**
	 * The two short launches that check the window's placement beyond the launcher's size (maximised, and kept
	 * between launches with {@code window.remember}). On by default on Windows only: positions under the Linux test
	 * images' X server, which has no window manager, say little.
	 */
	@Input
	public abstract Property<Boolean> getWindowRuns();

	public ClientStartupTask() {
		getPython().convention("python");
		getTimeoutSeconds().convention(180);
		getPlatform().convention("windows");
		getWindowRuns().convention(getPlatform().map(p -> p.equals("windows")));
	}

	private static final String PRISM_WINDOW = "-Dorg.prismlauncher.window.dimensions=";
	private static final String KEPT_PLACEMENT = "config/purefaithful-display-window.properties";

	@TaskAction
	public void run() throws Exception {
		String platform = getPlatform().get();
		boolean linux = platform.startsWith("linux-");
		if (!linux && !System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
			throw new org.gradle.api.GradleException("clientStartupTest runs the game on a private Windows desktop; elsewhere use a linux-<image> platform");
		}
		Path run = freshRunDir();
		prepare(run, linux);

		List<String> failures = new ArrayList<>();
		List<String> lines = launch(run, linux, List.of(PRISM_WINDOW + "1000x600",
				"-Doldfaithful.selftest.expectSize=1000x600", "-Doldfaithful.selftest.expectMaximized=false"), failures);
		if (lines.stream().noneMatch(l -> l.contains("OpenAL initialized"))) failures.add("the sound system didn't start (no \"OpenAL initialized\")");
		if (!Files.isRegularFile(run.resolve("saves/oldfaithful-startup/level.dat"))) failures.add("the test world wasn't saved");
		if (Files.exists(run.resolve(KEPT_PLACEMENT))) failures.add("the window's placement was kept with window.remember off (the default)");
		Path log = run.resolve("logs/purefaithful/latest.log");
		if (!Files.isRegularFile(log)) failures.add("no log file at " + log);
		else if (!Files.readString(log, StandardCharsets.UTF_8).contains("[oldfaithful-selftest] PASS main menu reached")) {
			failures.add(log + " lacks the start-up test's output");
		}

		if (getWindowRuns().get() && failures.isEmpty()) {
			Path game = run.resolve("window-memory");
			Files.createDirectories(game.resolve("config"));
			Files.createDirectories(game.resolve("mods"));
			try (Stream<Path> mods = Files.list(run.resolve("mods"))) {
				for (Path mod : mods.toList()) Files.copy(mod, game.resolve("mods").resolve(mod.getFileName()));
			}
			prepare(game, linux);
			Files.writeString(game.resolve("config/purefaithful-display.properties"), "window.remember=true\n");
			Files.writeString(game.resolve(KEPT_PLACEMENT), "x=60\ny=80\nwidth=900\nheight=500\nmaximized=false\nlauncher=1000x600\n");
			List<String> menuOnly = List.of(PRISM_WINDOW + "maximized", "-Doldfaithful.selftest.menuOnly=true");

			List<String> first = new ArrayList<>(menuOnly);
			first.addAll(List.of("-Doldfaithful.selftest.expectMaximized=true", "-Doldfaithful.selftest.rearrange=950x550@100,120"));
			lines.add("--- window run 1: Prism asks for maximised; kept from when it asked for 1000x600");
			lines.addAll(launch(game, linux, first, failures));
			checkKept(game, "after window run 1", failures);

			List<String> second = new ArrayList<>(menuOnly);
			second.addAll(List.of("-Doldfaithful.selftest.expectSize=950x550", "-Doldfaithful.selftest.expectAt=100,120",
					"-Doldfaithful.selftest.expectMaximized=true"));
			lines.add("--- window run 2: Prism unchanged; opens where it was");
			lines.addAll(launch(game, linux, second, failures));
			checkKept(game, "after window run 2", failures);
		}
		report(linux ? "Client start-up test (" + platform + ")" : "Client start-up test", lines, failures);
	}

	/** What both window runs leave kept: where the first one put the window, and Prism's request then. */
	private static void checkKept(Path game, String when, List<String> failures) throws IOException {
		Path file = game.resolve(KEPT_PLACEMENT);
		if (!Files.isRegularFile(file)) {
			failures.add(when + ": no kept placement at " + file);
			return;
		}
		java.util.Properties kept = new java.util.Properties();
		try (java.io.Reader r = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
			kept.load(r);
		}
		Map<String, String> want = Map.of("x", "100", "y", "120", "width", "950", "height", "550", "maximized", "true", "launcher", "maximized");
		for (Map.Entry<String, String> e : want.entrySet()) {
			if (!e.getValue().equals(kept.getProperty(e.getKey()))) {
				failures.add(when + ": kept " + e.getKey() + "=" + kept.getProperty(e.getKey()) + ", expected " + e.getValue());
			}
		}
	}

	/** A game folder ready to run: the sounds (so nothing downloads), a quick view distance, sound into a file. */
	private void prepare(Path game, boolean linux) throws IOException {
		copySounds(game);
		// Tiny render distance: a quick world load.
		Files.writeString(game.resolve("options.txt"), "viewDistance:3\n");
		Path wave = game.resolve("sound.wav");
		Files.writeString(game.resolve("alsoft.ini"), String.join("\n", "[general]", "channels = stereo", "sample-type = int16", "frequency = 44100",
				"[wave]", "file = " + (linux ? inContainer(wave) : wave.toAbsolutePath().toString().replace('\\', '/')), ""));
	}

	/** Runs the game unseen in {@code game} with the self-test, and checks it ran and stopped cleanly. */
	private List<String> launch(Path game, boolean linux, List<String> extraJvmArgs, List<String> failures) throws Exception {
		String natives = getNatives().get().getAsFile().getAbsolutePath();
		List<String> jvm = new ArrayList<>(List.of("-Xmx1g", "-Doldfaithful.lwjgl.hidden=true", "-Doldfaithful.selftest.startup=true",
				"-Djava.library.path=" + natives, "-Dorg.lwjgl.librarypath=" + natives, "--enable-native-access=ALL-UNNAMED"));
		jvm.addAll(extraJvmArgs);
		List<String> javaCommand = javaCommand("net.fabricmc.loader.impl.launch.knot.KnotClient", jvm,
				List.of("--username", "OldFaithfulTest", "--gameDir", game.toAbsolutePath().toString()));
		int timeout = getTimeoutSeconds().get();
		Map<String, String> soundToFile = Map.of("ALSOFT_DRIVERS", "wave", "ALSOFT_CONF", game.resolve("alsoft.ini").toAbsolutePath().toString());
		List<String> command;
		if (linux) {
			// The environment goes into the container; the launch (paths, classpath, java) is translated by in_linux.py.
			command = new ArrayList<>(List.of(getPython().get(), getInLinux().get().getAsFile().getAbsolutePath(),
					"--image", getPlatform().get().substring("linux-".length()), "--timeout", String.valueOf(timeout),
					"--workdir", game.toAbsolutePath().toString()));
			soundToFile.forEach((k, v) -> command.addAll(List.of("--env", k + "=" + v)));
			command.add("--");
		} else {
			command = new ArrayList<>(List.of(getPython().get(), getPrivateDesktop().get().getAsFile().getAbsolutePath(),
					"--timeout", String.valueOf(timeout), "--"));
		}
		command.addAll(javaCommand);

		List<String> lines;
		try (GameProcess client = new GameProcess(command, game, game.resolve("harness-output.log"), linux ? Map.of() : soundToFile)) {
			Integer exit = client.waitFor(timeout + 30);
			if (exit == null) failures.add("the game didn't stop within " + (timeout + 30) + " s");
			else if (exit == 124) failures.add("the game didn't stop within " + timeout + " s and was stopped");
			else if (exit != 0) failures.add("the game exited with " + exit);
			lines = new ArrayList<>(client.lines());
		}
		checkSelfTest(lines, failures);
		for (String p : problems(lines)) failures.add("log: " + p);
		for (String e : errors(lines)) failures.add("error in the output: " + e);
		if (lines.stream().noneMatch(l -> l.equals("Stopping!"))) failures.add("the game didn't stop cleanly (no \"Stopping!\")");
		return lines;
	}

	/** A path under the project as tools/in_linux.py mounts it (the project at /of). */
	private String inContainer(Path path) {
		Path root = getProject().getRootDir().toPath().toAbsolutePath();
		return "/of/" + root.relativize(path.toAbsolutePath()).toString().replace('\\', '/');
	}

	/** Anything in the output that reads as an error: exceptions, LWJGL's and the game's own error reports. */
	private static List<String> errors(List<String> lines) {
		List<String> errors = new ArrayList<>();
		for (String line : lines) {
			String l = line.toLowerCase(Locale.ROOT);
			if (l.contains("exception") || l.contains("[error]") || l.contains("severe") || l.contains("fatal")
					|| line.contains("GL ERROR") || line.startsWith("Caused by") || line.startsWith("\tat ")) {
				errors.add(line);
			}
		}
		return errors;
	}

	private void copySounds(Path run) throws IOException {
		Path from = getSoundsGameDir().get().getAsFile().toPath().resolve("resources");
		if (!Files.isDirectory(from)) {
			getLogger().lifecycle("No sounds in {} yet (clientAudit fills it); the game will download them (about 44 MB).", from);
			return;
		}
		Path to = run.resolve("resources");
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path p : walk.toList()) {
				Path target = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else Files.copy(p, target, StandardCopyOption.COPY_ATTRIBUTES);
			}
		}
	}
}
