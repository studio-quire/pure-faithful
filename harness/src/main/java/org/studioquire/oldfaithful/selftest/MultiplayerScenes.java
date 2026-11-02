package org.studioquire.oldfaithful.selftest;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.play.UpdateSignPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ServerWorld;

/**
 * Server half of `:harness:fixesMultiplayerTest` (only with {@code -Doldfaithful.selftest.multiplayer=true}): the
 * scene the hidden client ("OldFaithfulTest") joins into, and test commands players send as chat starting with
 * "!oftest ", so a raw client or the hidden client can ask for what only the server can do (die, a sign edited in
 * the same tick as another block change, another player turned, an entity removed). Never shipped.
 */
public final class MultiplayerScenes {
	public static final boolean ENABLED = Boolean.getBoolean("oldfaithful.selftest.multiplayer");
	public static final String CLIENT = "OldFaithfulTest";
	/** The scenes' floor: high above most terrain, but inside the world (blocks end at y 127; "Sky" players fly at 130). */
	public static final int FLOOR = 120;

	private MultiplayerScenes() {
	}

	/**
	 * The hidden client's scene, built around where it joins (high up, standing on it): a stone floor, three
	 * dirt blocks in a row 2 blocks west (to mine), and 3 blocks east a one-block fence pen with two pigs in it, which
	 * push each other all the time.
	 */
	public static void buildClientScene(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.world;
		int px = MathHelper.floor(player.x), pz = MathHelper.floor(player.z);
		for (int x = (px - 4) >> 4; x <= (px + 5) >> 4; x++) {
			for (int z = (pz - 3) >> 4; z <= (pz + 3) >> 4; z++) world.chunkCache.loadChunk(x, z);
		}
		for (int x = px - 4; x <= px + 5; x++) {
			for (int z = pz - 3; z <= pz + 3; z++) world.setBlock(x, FLOOR, z, Block.STONE.id);
		}
		for (int dz = -1; dz <= 1; dz++) world.setBlock(px - 2, FLOOR + 1, pz + dz, Block.DIRT.id);
		int penX = px + 3;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) world.setBlock(penX + dx, FLOOR + 1, pz + dz, Block.FENCE.id);
			}
		}
		for (int i = 0; i < 2; i++) {
			PigEntity pig = new PigEntity(world);
			pig.setPositionAndAngles(penX + 0.5 + i * 0.05, FLOOR + 1, pz + 0.5, 0, 0);
			world.spawnEntity(pig);
		}
		System.out.println("[oldfaithful-selftest] scene built for " + player.name + " at " + px + " " + (FLOOR + 1) + " " + pz);
	}

	/** Returns true if the chat message was a test command (and so isn't broadcast). */
	public static boolean command(MinecraftServer server, ServerPlayerEntity player, String message) {
		if (!message.startsWith("!oftest ")) return false;
		String[] a = message.substring("!oftest ".length()).trim().split(" ");
		ServerWorld world = (ServerWorld) player.world;
		int cx = MathHelper.floor(player.x) & ~15, cz = MathHelper.floor(player.z) & ~15;
		switch (a[0]) {
			case "die":
				player.damage(null, 1000);
				System.out.println("[oldfaithful-selftest] killed " + player.name + " (health " + player.health + ")");
				break;
			case "signplace":
				// A sign standing on stone in the sender's chunk; a dirt block's place, later, too.
				world.setBlock(cx + 4, FLOOR, cz + 4, Block.STONE.id);
				world.setBlock(cx + 10, FLOOR, cz + 10, Block.STONE.id);
				world.setBlock(cx + 4, FLOOR + 1, cz + 4, Block.SIGN.id, 0);
				System.out.println("[oldfaithful-selftest] sign placed at " + (cx + 4) + " " + (FLOOR + 1) + " " + (cz + 4));
				break;
			case "signedit":
				// In one tick, as a player's edit coinciding with any other change in the chunk: the dirt, then the edit
				// through the server's own handler (which is how an edit reaches other players).
				world.setBlock(cx + 10, FLOOR + 1, cz + 10, Block.DIRT.id);
				player.networkHandler.handleUpdateSign(new UpdateSignPacket(cx + 4, FLOOR + 1, cz + 4, new String[] {"Old", "Faithful", "sign", "test"}));
				System.out.println("[oldfaithful-selftest] sign edited with a block change beside it");
				break;
			case "turn": {
				ServerPlayerEntity other = server.playerManager.getPlayer(a[1]);
				other.yaw = Float.parseFloat(a[2]);
				other.pitch = 0;
				System.out.println("[oldfaithful-selftest] " + other.name + " turned to " + other.yaw);
				break;
			}
			case "unpig": {
				int id = Integer.parseInt(a[1]);
				for (Object o : world.entities) {
					Entity e = (Entity) o;
					if (e.id == id) {
						e.markDead();
						System.out.println("[oldfaithful-selftest] removed entity " + id);
					}
				}
				break;
			}
			default:
				System.out.println("[oldfaithful-selftest] unknown test command: " + message);
		}
		return true;
	}
}
