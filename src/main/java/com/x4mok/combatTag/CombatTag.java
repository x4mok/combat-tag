package com.x4mok.combatTag;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public final class CombatTag extends JavaPlugin implements Listener {
	Map<String, Map<String, Object>> inCombat = new HashMap<>();
	Map<String, Integer> log_times = new HashMap<>();
	Map<String, Map<String, Object>> bans = new HashMap<>();
	Map<String, Long> unbanTime = new HashMap<>();
	Plugin plugin = this;

	@Override
	public void onEnable() {
		getLogger().info("Combat Tag is loading...");

		saveDefaultConfig();
		createBansConfig();

		getServer().getPluginManager().registerEvents(this, this);

		Bukkit.getScheduler().runTaskTimer(this, this::tickCombatTimers, 0L, 20L);

		getLogger().info("Combat Tag successfully loaded!");
		Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "" + ChatColor.BOLD + " __ ___");
		Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "/    | " + ChatColor.GREEN + "Loaded Succesfully!");
		Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "\\__  |" + ChatColor.AQUA + "Version v" + plugin.getDescription().getVersion());
		String version = plugin.getDescription().getVersion();
		if (version.toUpperCase().endsWith("BETA")) {
			getLogger().warning("This is a beta version of the plugin!");
			getLogger().warning("Please report all bugs to @x4mok on discord!");
		} else if (version.toUpperCase().endsWith("ALPHA")) {
			getLogger().warning("This is an alpha version of the plugin!");
			getLogger().warning("Please report all bugs to @x4mok on discord!");
		} else if (version.toUpperCase().endsWith("DEV")) {
			getLogger().warning("This is an dev version of the plugin!");
			getLogger().warning("Please report all bugs to @x4mok on discord!");
		} else {
			getLogger().info("This should be a stable build of the plugin,");
			getLogger().info("if you encounter any bugs please report them to @x4mok on discord.");
		}
	}

	@Override
	public void onDisable() {
		getLogger().info("Combat Tag is disabled.");
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		String playerID = player.getUniqueId().toString();
		Map<String,Object> plrBan = bans.getOrDefault(playerID, null);

		if (plrBan != null) {
			if (!(Boolean) plrBan.get("active") && System.currentTimeMillis() < unbanTime.getOrDefault(playerID, System.currentTimeMillis() + 300)) {
				plrBan.put("active", true);
				bans.put(playerID, plrBan);
				long time = System.currentTimeMillis();
				long timeAtUnban = time + ((int)plrBan.get("time") * 1000L);
				unbanTime.put(playerID, timeAtUnban);
				player.kick(Component.text("You recently combat logged! Try again in " + plrBan.get("time") + " seconds!"));
			}
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if (checkCombatStatus(player)) {
			String playerID = player.getUniqueId().toString();
			Map<String, Object> playerThing = inCombat.getOrDefault(playerID, null);
			if (playerThing != null) {
				// Get the original log amount
				Integer log_amount = log_times.getOrDefault(playerID, 0);
				Integer new_log_amount = log_amount + 1;
				log_times.put(playerID, new_log_amount);

				String enemy = (String) playerThing.getOrDefault("enemy", null);
				UUID enemyID = UUID.fromString(enemy);
				Player enemyPlayer = Bukkit.getPlayer(enemyID);

				// give player kill credit
				// although check config first for what happens there
				FileConfiguration config = getConfig();
				String combatLogOption = config.getString("combat-log-punish", "DEATH");
				List<String> escalationList = config.getStringList("escalation-punish");
				boolean doEscalation = config.getBoolean("escalation");

				if (log_amount != 0 && doEscalation && !escalationList.isEmpty()) {
					String punish = combatLogOption;

					// Determine the punishment based on escalation list and config
					if (log_amount <= escalationList.size()) {
						punish = escalationList.get(log_amount - 1);
					} else if (config.getBoolean("do-last-escalation-punish")) {
						punish = escalationList.get(escalationList.size() - 1);
					}

					// Execute the punishment
					if (!punish.equalsIgnoreCase("NONE")) {
						player.setKiller(enemyPlayer); // kill player if they log lmfao
						if (punish.equalsIgnoreCase("TEMPBAN")) {
							// Create a map to hold the player's information
							Map<String, Object> playerInfo = new HashMap<>();

							// Add "active" and "time" to the player's map
							playerInfo.put("active", false);
							playerInfo.put("time", config.getInt("tempban-time"));

							// Put the player's information map into the 'bans' map under the playerID
							bans.put(playerID, playerInfo);
						}
					}
				} else {
					if (!combatLogOption.equalsIgnoreCase("NONE")) {
						player.setKiller(enemyPlayer); // kill player if they log lmfao
						if (combatLogOption.equalsIgnoreCase("TEMPBAN")) {
							// do tempban logic here
							// sort out config/ file storage for tempban in startup or enable first tho
						}
					}
				}
			}
			// combat code on disconnect
			// create a persisting thing in bans.yml
			// so that players get a 5-min ban after their disc
			// so if they try to rejoin they cooked until its up,
			// or they die whichever
			// or even nun happens
		}
	}

	@EventHandler
	public void onEntityDamage(EntityDamageByEntityEvent event) {
		// Check if the entity taking damage is a player
		if (event.getEntity() instanceof Player) {
			Player damagedPlayer = (Player) event.getEntity();

			// Check if the damager is also a player
			if (event.getDamager() instanceof Player) {
				Player damagerPlayer = (Player) event.getDamager();

				// Handle the combat logic
				handleCombat(damagedPlayer, damagerPlayer);
			}
		}
	}

	public void handleCombat(Player attackedplr, Player attackerplr) {
		// Retrieve combat time from config, default to 15 if not set or invalid
		int combatTime = getCombatTime();
		if (combatTime == -13) {
			combatTime = 15;
		}

		// Update combat status for both players
		updateCombatStatus(attackedplr, combatTime, attackerplr);
		updateCombatStatus(attackerplr, combatTime, attackedplr);
	}

	public static void saveNestedMapToConfig(File configFile, Map<String, Map<String, Object>> data) {
		// Load the existing configuration
		FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		// Set the 'saved' key with the nested map
		config.set("saved", data);

		// Save the updated configuration to the file
		try {
			config.save(configFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void updateCombatStatus(Player player, int combatTime, Player player2) {
		String id = player.getUniqueId().toString();
		String id2 = player2.getUniqueId().toString();
		Map<String, Object> playerData = new HashMap<>();
		playerData.put("time", combatTime);
		playerData.put("combat", true); // Assuming the player is in combat
		playerData.put("enemy", id2);

		inCombat.put(id, playerData);
	}

	public void sendActionBar(Player player, String message) {
		// Create a TextComponent for the message
		TextComponent text = new TextComponent(message);

		// Send the message to the player’s action bar
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, text);
	}

	public int getCombatTime() {
		return getConfig().getInt("combat-tag-time", -13);
	}

	public boolean checkCombatStatus(Player player) {
		return inCombat.containsKey(player.getUniqueId().toString());
	}

	public void createBansConfig() {
		File bansFile = new File(getDataFolder(), "bans.yml");
		if (!bansFile.exists()) {
			bansFile.getParentFile().mkdirs();
			saveResource("bans.yml", false); // Copies `bans.yml` from the plugin's resources folder if it exists.
		}
		YamlConfiguration bansConfig = YamlConfiguration.loadConfiguration(bansFile);
	}

	public void tickCombatTimers() {
		Iterator<Map.Entry<String, Map<String, Object>>> iterator = inCombat.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<String, Map<String, Object>> entry = iterator.next();
			String playerId = entry.getKey();
			Map<String, Object> playerData = entry.getValue();

			// Get the current combat time
			int currentTime = (int) playerData.getOrDefault("time", 0);

			if (currentTime > 0) {
				// Decrement the combat time
				playerData.put("time", currentTime - 1);

				// Update the player's combat status
				inCombat.put(playerId, playerData);

				// Optionally, notify the player if needed
				if (getConfig().getBoolean("actionbar-combat", true)) {
					Player player = Bukkit.getPlayer(UUID.fromString(playerId));
					if (player != null) {
						Map<String, Object> playerThing = inCombat.getOrDefault(playerId, null);
						if (playerThing != null) {
							String player2Thing = (String) playerThing.getOrDefault("enemy", null);
							if (player2Thing != null) {
								Integer time = (Integer) playerThing.getOrDefault("time", null);
								if (time != null) {
									sendActionBar(player, "In Combat to " + Bukkit.getPlayer(player2Thing).getName() + ": " + time + " seconds remaining.");
								}

							}
						}

					}
				}
			} else {
				// Remove the player from combat if time is up
				iterator.remove();
			}
		}

	}
}