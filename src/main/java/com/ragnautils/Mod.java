package com.ragnautils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

public class Mod implements ModInitializer {
	public static final String MOD_VERSION = "1.0.0";

	public static final String MOD_ID = "ragnautils";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Map<UUID, Map<String, Long>> playerKitCooldowns = new HashMap<>();

	// in ms this is 1 hour
	private static final long KIT_COOLDOWN_MS = 60 * 60 * 1000;

	@Override
	public void onInitialize() {
		long startTime = System.currentTimeMillis();
		LOGGER.info("Initializing...");

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// Info
			dispatcher.register(
					CommandManager.literal("ragnautils").requires(source -> source.hasPermissionLevel(0))
							.executes(context -> {
								ServerCommandSource source = context.getSource();
								source.sendFeedback(
										() -> Text.literal("RagnaUtils v" + MOD_VERSION + " info:\n Only has /kit rn"),
										true);
								return 1;
							}));

			List<String> kits = Arrays.asList("food", "???");
			// Kit
			dispatcher.register(
					CommandManager.literal("kit")
							.requires(source -> source.hasPermissionLevel(0))
							.then(CommandManager.argument("kitname", StringArgumentType.word())
									.suggests((context, builder) -> {
										return suggestKits(builder, kits);
									})
									.executes(context -> {
										ServerCommandSource source = context.getSource();
										ServerPlayerEntity player = source.getPlayer();
										String kitName = StringArgumentType.getString(context, "kitname").toLowerCase();

										boolean success;
										if (!canUseKit(player, kitName, source)) {
											source.sendError(Text.literal(
													"You must wait before using the " + kitName + " kit again."));
											success = false;
										} else {
											success = giveKit(player, kitName);
											if (success) {
												source.sendFeedback(() -> Text.literal("Given " + kitName + " kit!"),
														false);
											} else {
												source.sendError(Text.literal("Kit '" + kitName + "' not found."));
											}
										}
										return success ? 1 : 0;
									})));
		});
		long elapsed = System.currentTimeMillis() - startTime;
		LOGGER.info("Loaded in {} ms", elapsed);
	}

	private CompletableFuture<Suggestions> suggestKits(SuggestionsBuilder builder, List<String> kits) {
		for (String kit : kits) {
			if (kit.startsWith(builder.getRemaining().toLowerCase())) {
				builder.suggest(kit);
			}
		}
		return builder.buildFuture();
	}

	// Give items based on kit name
	private boolean giveKit(ServerPlayerEntity player, String kitName) {
		switch (kitName) {
			case "food":
				player.getInventory().insertStack(new ItemStack(Items.COOKED_BEEF, 32));
				return true;
			case "???":
				player.getInventory().insertStack(new ItemStack(Items.ACACIA_HANGING_SIGN));
				player.getInventory().insertStack(new ItemStack(Items.DIRT, 256));
				return true;
			default:
				return false;
		}
	}

	private boolean canUseKit(ServerPlayerEntity player, String kitName, ServerCommandSource source) {
		UUID playerId = player.getUuid();
		long now = System.currentTimeMillis();

		Map<String, Long> cooldowns = playerKitCooldowns.get(playerId);
		if (cooldowns == null) {
			cooldowns = new HashMap<>();
			playerKitCooldowns.put(playerId, cooldowns);
		}

		Long lastUsed = cooldowns.get(kitName);
		if (lastUsed != null && (now - lastUsed) < KIT_COOLDOWN_MS) {
			long secondsLeft = (KIT_COOLDOWN_MS - (now - lastUsed)) / 1000;
			source.sendError(Text.literal(
					"You must wait " + secondsLeft + " seconds before using the " + kitName + " kit again."));
			return false;
		}

		cooldowns.put(kitName, now);
		return true;
	}

}
