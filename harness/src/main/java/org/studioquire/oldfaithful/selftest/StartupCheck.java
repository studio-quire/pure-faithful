package org.studioquire.oldfaithful.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.SingleplayerInteractionManager;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDL_Event;
import org.studioquire.oldfaithful.lwjgl.Settings;
import org.studioquire.oldfaithful.lwjgl.Window;
import org.studioquire.oldfaithful.selftest.mixin.MultiplayerScreenAccessor;

/**
 * The client start-up test's in-game half (`:harness:clientStartupTest`, launched with
 * {@code -Doldfaithful.selftest.startup=true} on our LWJGL with a hidden window): from the game's own tick, it checks
 * the main menu came up as a player's would (title, hidden window, mouse setting, sound), pastes into the server
 * address with a synthetic Ctrl+V, then creates and loads a small world, lets it run a moment and stops the game the
 * way its close button does. Results are {@code [oldfaithful-selftest]} lines; the task reads them and the log.
 * <p>
 * The window's placement is checked against what the task launched with ({@code oldfaithful.selftest.expect*}: the
 * launcher's window setting, or a remembered placement). With {@code oldfaithful.selftest.menuOnly} the run stops
 * after the main menu; {@code oldfaithful.selftest.rearrange=<w>x<h>@<x>,<y>} first restores, resizes and moves the
 * hidden window as a player would, then maximises it, so the task can read back what the display mod kept.
 */
public final class StartupCheck {
	public static final boolean ENABLED = Boolean.getBoolean("oldfaithful.selftest.startup");
	private static final String WORLD = "oldfaithful-startup";
	private static final int WORLD_TICKS = 100;
	private static final boolean MENU_ONLY = Boolean.getBoolean("oldfaithful.selftest.menuOnly");
	private static final String REARRANGE = System.getProperty("oldfaithful.selftest.rearrange");
	private static final long STALL_MS = 150_000;

	private static final SelfTest results = new SelfTest();
	private static int phase;
	private static int wait;
	private static int worldTicks;
	private static long startedAt;
	private static String clipboard;
	/** Rows of beta's version-line grey in the title screen's corner, counted as separate lines; -1 until drawn. */
	private static volatile int titleLines = -1;

	private StartupCheck() {
	}

	public static void tick(Minecraft mc) {
		if (startedAt == 0) startedAt = System.currentTimeMillis();
		if (phase < 99 && System.currentTimeMillis() - startedAt > STALL_MS) {
			results.fail("start-up test stalled in phase " + phase + " (screen " + mc.currentScreen + ")", new AssertionError());
			finish(mc);
			return;
		}
		try {
			step(mc);
		} catch (Throwable t) {
			results.fail("start-up test, phase " + phase, t);
			finish(mc);
		}
	}

	/**
	 * The swap interval: what our layer asked for must be what the display mod's setting says, and the GL must report
	 * it in effect. A software GL with no display to sync to (Mesa's llvmpipe under Xvfb, on the Linux test images)
	 * accepts the request and keeps 0, or refuses it; there only the request is checked, and the output says so.
	 */
	private static void vsyncCheck() {
		int expected = Settings.vsync ? 1 : 0;
		int asked = Window.swapInterval();
		java.nio.IntBuffer interval = org.lwjgl.BufferUtils.createIntBuffer(1);
		boolean readable = SDLVideo.SDL_GL_GetSwapInterval(interval);
		String inEffect = readable ? String.valueOf(interval.get(0)) : "unreadable";
		String renderer = String.valueOf(GL11.glGetString(GL11.GL_RENDERER));
		boolean noSwapControl = Window.swapIntervalRefused() != null
				|| renderer.contains("llvmpipe") || renderer.contains("softpipe");
		if (readable && interval.get(0) == expected) {
			results.check("vsync: swap interval " + inEffect + ", as the display mod's setting asks (" + expected + ")", asked == expected);
		} else if (noSwapControl) {
			results.check("vsync: asked for swap interval " + asked + ", as the display mod's setting asks (" + expected + "); this GL ("
					+ renderer + ") has no display to sync to and keeps " + inEffect
					+ (Window.swapIntervalRefused() != null ? " (refused: " + Window.swapIntervalRefused() + ")" : "")
					+ ", so only the request can be checked here", asked == expected);
		} else {
			results.check("vsync: swap interval " + inEffect + ", as the display mod's setting asks (" + expected + ")", false);
		}
	}

	private static void step(Minecraft mc) {
		switch (phase) {
			case 0:
				if (!(mc.currentScreen instanceof TitleScreen) || titleLines < 0) return;
				results.pass("main menu reached");
				results.check("title screen: \"Minecraft Beta 1.7.3\" and Pure Faithful's line under it, in beta's grey (" + titleLines
						+ " lines; title-corner.png in the run folder)", titleLines == 2);
				results.check("window title is \"Minecraft\", as on LWJGL 2: \"" + Display.getTitle() + "\"", "Minecraft".equals(Display.getTitle()));
				results.check("window created and never shown, never active", Display.isCreated() && !Display.isVisible() && !Display.isActive());
				results.check("mouse: system pointer speed and acceleration (the display mod's default)", !Settings.rawMouse
						&& "1".equals(SDLHints.SDL_GetHint(SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE)));
				vsyncCheck();
				windowChecks(mc);
				System.out.println("[oldfaithful-selftest] GL: " + GL11.glGetString(GL11.GL_VERSION) + " / " + GL11.glGetString(GL11.GL_RENDERER));
				if (REARRANGE != null) {
					phase = 10;
				} else if (MENU_ONLY) {
					finish(mc);
				} else {
					mc.setScreen(new MultiplayerScreen(mc.currentScreen));
					phase = 1;
				}
				return;
			case 1:
				if (!(mc.currentScreen instanceof MultiplayerScreen)) return;
				// Read now, as the paste will: the test never writes the clipboard (it's the user's) or prints it.
				clipboard = Sys.getClipboard();
				pushKey(224, SDLKeycode.SDL_KMOD_LCTRL, true);
				pushKey(25, SDLKeycode.SDL_KMOD_LCTRL, true);
				pushKey(25, SDLKeycode.SDL_KMOD_LCTRL, false);
				pushKey(224, 0, false);
				phase = 2;
				wait = 5;
				return;
			case 2:
				if (--wait > 0) return;
				TextFieldWidget field = ((MultiplayerScreenAccessor) mc.currentScreen).oldfaithful$serverField();
				String c = clipboard == null ? "" : clipboard;
				String expected = c.substring(0, Math.min(32, c.length()));
				results.check("paste: Ctrl+V in the server address field pastes the clipboard through SDL ("
						+ (c.isEmpty() ? "the clipboard holds no text, so only the key path ran" : expected.length() + " characters, contents not shown") + ")",
						expected.equals(field.getText()));
				mc.setScreen(new TitleScreen());
				phase = 3;
				return;
			case 3:
				if (!(mc.currentScreen instanceof TitleScreen)) return;
				results.pass("back at the main menu");
				// As the Create World screen does.
				mc.interactionManager = new SingleplayerInteractionManager(mc);
				mc.startGame(WORLD, "Old Faithful start-up test", 7373L);
				mc.setScreen(null);
				phase = 4;
				return;
			case 4:
				if (mc.world == null || mc.player == null) return;
				if (++worldTicks < WORLD_TICKS) return;
				results.check("world created, loaded and running: player at y " + Math.round(mc.player.y) + ", world time " + mc.world.getTime()
						+ " after " + worldTicks + " game ticks", mc.world.getTime() >= WORLD_TICKS / 2 && mc.currentScreen == null);
				results.check("sound system initialised (paulscode on our OpenAL)", paulscode.sound.SoundSystem.initialized());
				finish(mc);
				return;
			case 10: {
				// As a player would: out of maximised, then resized and moved. The display mod sees it through the
				// window's events only (nothing here asks it), and the maximise after must not overwrite it.
				String[] sizeAt = REARRANGE.split("@");
				String[] wh = sizeAt[0].split("x"), xy = sizeAt[1].split(",");
				long w = Window.handle();
				SDLVideo.SDL_RestoreWindow(w);
				SDLVideo.SDL_SetWindowSize(w, Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
				SDLVideo.SDL_SetWindowPosition(w, Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
				phase = 11;
				wait = 5;
				return;
			}
			case 11:
				if (--wait > 0) return;
				SDLVideo.SDL_MaximizeWindow(Window.handle());
				results.check("rearranged: " + Display.getWidth() + "x" + Display.getHeight() + " at " + Display.getX() + "," + Display.getY()
						+ ", then maximised (" + REARRANGE + ")", REARRANGE.equals(Display.getWidth() + "x" + Display.getHeight() + "@" + Display.getX() + "," + Display.getY())
						&& maximized());
				phase = 12;
				wait = 5;
				return;
			case 12:
				if (--wait > 0) return;
				finish(mc);
				return;
			default:
		}
	}

	private static boolean maximized() {
		return (SDLVideo.SDL_GetWindowFlags(Window.handle()) & SDLVideo.SDL_WINDOW_MAXIMIZED) != 0;
	}

	/**
	 * Where the window opened, against what the task expects: the size (the window's and the game's), the position,
	 * maximised or not. A hidden window holds a maximise pending until it's shown, and its flags say so.
	 */
	private static void windowChecks(Minecraft mc) {
		String size = System.getProperty("oldfaithful.selftest.expectSize");
		if (size != null) {
			String window = Display.getWidth() + "x" + Display.getHeight(), game = mc.displayWidth + "x" + mc.displayHeight;
			results.check("window opened at " + size + ": " + window + ", the game's size " + game, size.equals(window) && size.equals(game));
		}
		String at = System.getProperty("oldfaithful.selftest.expectAt");
		if (at != null) {
			String got = Display.getX() + "," + Display.getY();
			results.check("window opened at position " + at + ": " + got, at.equals(got));
		}
		String max = System.getProperty("oldfaithful.selftest.expectMaximized");
		if (max != null) {
			results.check("window opened " + (Boolean.parseBoolean(max) ? "maximised" : "not maximised") + ": maximised " + maximized(),
					Boolean.parseBoolean(max) == maximized());
		}
	}

	/**
	 * Called after the title screen draws, before the frame is shown: counts the lines in the version text's grey
	 * (b1.7.3's 0x505050, which its darkened background never reaches) above the logo, and saves that corner.
	 */
	public static void titleDrawn(int screenWidth) {
		if (titleLines >= 0) return;
		int scale = Math.max(1, Display.getWidth() / Math.max(1, screenWidth));
		int w = Math.min(Display.getWidth(), 120 * scale);
		int h = Math.min(Display.getHeight(), 28 * scale);
		java.nio.ByteBuffer px = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
		GL11.glReadPixels(0, Display.getHeight() - h, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
		int lines = 0;
		boolean inLine = false;
		for (int y = 0; y < h; y++) {
			boolean grey = false;
			for (int x = 0; x < w; x++) {
				int i = ((h - 1 - y) * w + x) * 4;
				int r = px.get(i) & 0xFF, g = px.get(i + 1) & 0xFF, b = px.get(i + 2) & 0xFF;
				img.setRGB(x, y, r << 16 | g << 8 | b);
				if (r == 0x50 && g == 0x50 && b == 0x50) grey = true;
			}
			if (grey && !inLine) lines++;
			inLine = grey;
		}
		try {
			javax.imageio.ImageIO.write(img, "png", new java.io.File("title-corner.png"));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		titleLines = lines;
	}

	private static void finish(Minecraft mc) {
		phase = 99;
		results.done();
		// As the window's close button does: the game saves and stops itself.
		mc.scheduleStop();
	}

	private static void pushKey(int scancode, int mod, boolean down) {
		SDL_Event ev = SDL_Event.calloc();
		try {
			ev.key().type(down ? SDLEvents.SDL_EVENT_KEY_DOWN : SDLEvents.SDL_EVENT_KEY_UP).timestamp(SDLTimer.SDL_GetTicksNS())
					.windowID(Window.windowId()).scancode(scancode).mod((short) mod).down(down);
			SDLEvents.SDL_PushEvent(ev);
		} finally {
			ev.free();
		}
	}
}
