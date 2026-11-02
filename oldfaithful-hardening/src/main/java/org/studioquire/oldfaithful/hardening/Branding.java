package org.studioquire.oldfaithful.hardening;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * How a player can tell they're on Pure Faithful: one line under beta's own version on the title screen, in the same
 * grey ({@code client.TitleScreenMixin}). This mod carries it because every Pure Faithful setup has it.
 * The Old Faithful edition (Pure Faithful plus its fix mods) will want its own name here.
 */
public final class Branding {
	public static final String TEXT = "Pure Faithful " + version();
	/** b1.7.3's colour for its own "Minecraft Beta 1.7.3" line. */
	public static final int COLOUR = 0x505050;

	private Branding() {
	}

	private static String version() {
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			if (mod.getMetadata().getId().equals("purefaithful-hardening")) return mod.getMetadata().getVersion().getFriendlyString();
		}
		return "";
	}
}
