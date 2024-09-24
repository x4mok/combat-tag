package com.x4mok.combatTag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public final class CombatTag extends JavaPlugin implements Listener {
	Plugin plugin = this;
	// Create the combat handler
	CombatHandler combatHandler = new CombatHandler();

	@Override
	public void onEnable() {
		getLogger().info("Combat Tag is loading...");

		// Save the plugin config
		saveDefaultConfig();
		// Register event handler so it can be used
		getServer().getPluginManager().registerEvents(this, this);
		// Combat timer ticker
		combatHandler.tickCombatTimers(this);

		// Send the "mark" of the plugin
		getLogger().info("Combat Tag successfully loaded!");
		Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "" + ChatColor.BOLD + " __ ___");
		Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "/    | " + ChatColor.GREEN + "Loaded Succesfully!");
		Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "\\__  |" + ChatColor.AQUA + "Version v" + plugin.getDescription().getVersion());
		String version = plugin.getDescription().getVersion();
		// Warnings in case of unstable build
		sendPluginMessage(version);
	}

	@Override
	public void onDisable() {
		getLogger().info("Combat Tag is disabled.");
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer(); // The player who is quitting

		if (combatHandler.getInCombat(player)) {
			boolean allowLog = (boolean) getFromConfig("allow-log");
			String combatLogPunish = (String) getFromConfig("combat-log-punish");

			// Null or error checks
			if (!allowLog && combatLogPunish != null && !combatLogPunish.equals("-13") && !combatLogPunish.equalsIgnoreCase("NONE")) {
				Player enemy = combatHandler.getEnemy(player);  // Get the enemy
				if (enemy != null) {
					player.setKiller(enemy);  // Set the enemy as the killer
				}
				// Kill the player explicitly to punish combat logging
				player.setHealth(0.0);  // This will kill the player, might not work though
				getLogger().info("Player might have died");
				simulatePlayerDeath(player);
			}
		}
	}

	// Player Death simulator
	public void simulatePlayerDeath(Player player) {
		// Get player's inventory contents and filter out null values
		List<ItemStack> drops = Arrays.stream(player.getInventory().getContents())
				.filter(Objects::nonNull)  // Remove null items
				.collect(Collectors.toList());

		// Create a custom PlayerDeathEvent with filtered drops
		PlayerDeathEvent deathEvent = new PlayerDeathEvent(player, drops, 0, player.getName() + " combat logged!");

		// Call the event so other plugins or the server can handle it
		Bukkit.getServer().getPluginManager().callEvent(deathEvent);

		// Broadcast the death message manually if needed
		// Bukkit.broadcastMessage(player.getName() + " was slain after quitting!");
	}

	// Player Death handler
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();  // The player who died
		Player killer = player.getKiller();  // The player who killed (could be null)

		// Fetch the custom death message setting
		boolean customDeathMessages = (boolean) getFromConfig("custom-kill-messages");

		// Only proceed with custom death messages if enabled in the config
		if (customDeathMessages) {
			String deathMessageKey = (killer != null) ? "kill-messages.pvp" : "kill-messages.pve";

			// Retrieve the death message from the config
			String deathMessage = (String) getFromConfig(deathMessageKey);

			// Null check and error code (-13) check
			if (deathMessage != null && !deathMessage.equals("-13")) {
				// Replace placeholders for player and killer names
				deathMessage = deathMessage.replace("%player%", player.getName());

				if (killer != null) {
					deathMessage = deathMessage.replace("%killer%", killer.getName());
				}

				deathMessage = ChatColor.translateAlternateColorCodes('&', deathMessage);

				// Set the custom death message
				event.setDeathMessage(deathMessage);
			}
		}

		// Remove the player from combat
		combatHandler.removeFromCombat(player);
	}

	// Combat Initiator
	@EventHandler
	public void onEntityDamage(EntityDamageByEntityEvent event) {
		// Check if the entity taking damage is a player
		if (event.getEntity() instanceof Player victim) {

			// Check if the damager is also a player
			if (event.getDamager() instanceof Player attacker) {

				// Handle the combat logic
				// need to create a combat logic handler
				// Put player in combat <--
				int combatTime = (int) getFromConfig("combat-tag-time");
				// Put the player who got attacked first into the thing
				combatHandler.putInCombat(victim, attacker, true, combatTime);
				// Put the player who attacked first into the thing
				combatHandler.putInCombat(attacker, victim, false, combatTime);
			}
		}
	}

	// Combat handler full class
	static class CombatHandler {
		private final Map<UUID, Map<String, Object>> playerCombat = new HashMap<>();
		// PlayerID:
		//   Enemy: EnemyID
		//   Victim: false # or true
		//   Time: 15 # In seconds, how long the player has left of combat
		// remove the player when their time reaches 0 (time <= 0)

		// Puts the player mentioned in combat (NOTE: DOES NOT PUT THE ENEMY IN COMBAT)
		public void putInCombat(Player player, Player enemy, boolean victim, int time) {
			Map<String, Object> tempMap = new HashMap<>();
			tempMap.put("Enemy", enemy);
			tempMap.put("Victim", victim);
			tempMap.put("Time", time);
			playerCombat.put(player.getUniqueId(), tempMap);
		}

		// Returns true if the player is in the Map with a time that would suggest a player being in combat (>0)
		public boolean getInCombat(Player player) {
			Map<String, Object> plrNested = playerCombat.get(player.getUniqueId());
			if (plrNested != null) {
				return (int) plrNested.get("Time") > 0;
			}
			return false;
		}

		public Player getEnemy(Player player) {
			Map<String, Object> plrNested = playerCombat.get(player.getUniqueId());
			if (plrNested != null) {
				return (Player) plrNested.getOrDefault("Enemy", null);
			}
			return null;
		}

		public void removeFromCombat(Player player) {
			playerCombat.remove(player.getUniqueId());
		}

		public void tickCombatTimers(Plugin plugin) {
			// Schedule a repeating task that runs every second (20 ticks)
			Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
				@Override
				public void run() {
					// This code will run every second
					for (Map.Entry<UUID, Map<String, Object>> entry : playerCombat.entrySet()) {
						for (Map.Entry<String, Object> entry2 : entry.getValue().entrySet()) {
							if (entry2.getKey().equalsIgnoreCase("Time")) {
								if ((int) entry2.getValue() > 0) {
									entry.getValue().put("Time", (int) entry2.getValue() - 1);
									playerCombat.put(entry.getKey(), entry.getValue());
								} else {
									removeFromCombat(Objects.requireNonNull(Bukkit.getPlayer(entry.getKey())));
								}
							}
						}
					}
				}
			}, 0L, 20L); // 0L = no initial delay, 20L = 20 ticks (1 second) interval
		}
	}











	// Config grabber
	public Object getFromConfig(String key) {
		FileConfiguration config = getConfig();
		Object toReturn = config.get(key);
		if (toReturn != null) {
			return toReturn;
		} else {
			getLogger().info("That was not a valid line in the configuration!");
		}
		getLogger().warning("Error in getFromConfig function line:" + getLineNumber());
		return -13;
	}











	// Line grabber for code/ error messages
	public int getLineNumber() {
		return Thread.currentThread().getStackTrace()[2].getLineNumber();
	}

	// Can be "hidden" at the bottom
	public void sendPluginMessage(String version) { // Sends the plugin
		String reportTo = "@x4mok on discord";
		if (version.toUpperCase().endsWith("BETA")) {
			getLogger().warning("This is a beta version of the plugin!");
			getLogger().warning("Please report all bugs to " + reportTo + "!");
		} else if (version.toUpperCase().endsWith("ALPHA")) {
			getLogger().warning("This is an alpha version of the plugin!");
			getLogger().warning("Please report all bugs to " + reportTo + "!");
		} else if (version.toUpperCase().endsWith("DEV")) {
			getLogger().warning("This is a dev version of the plugin!");
			getLogger().warning("Please report all bugs to " + reportTo + "!");
		} else {
			getLogger().info("This should be a stable build of the plugin,");
			getLogger().info("if you encounter any bugs please report them to " + reportTo + ".");
		}
	}
}