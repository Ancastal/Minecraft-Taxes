package org.ancastal.taxes.commands;

import net.milkbowl.vault.economy.Economy;
import org.ancastal.taxes.Taxes;
import org.ancastal.taxes.db.DatabaseManager;
import org.ancastal.taxes.listeners.PayTaxListener;
import org.ancastal.taxes.models.TaxData;
import org.ancastal.taxes.models.TaxManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.util.StringUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CollectTaxesCommand implements CommandExecutor, TabCompleter {

	private final Taxes plugin;
	private DatabaseManager databaseManager;
	private TaxManager taxManager;
	private int offlinePlayersLength;
	private volatile boolean isCollectingTaxes = false;
	private volatile int currentIndex = 0;


	public CollectTaxesCommand(Taxes plugin) {
		this.plugin = plugin;
		this.databaseManager = plugin.getDatabaseManager();
		this.taxManager = plugin.getTaxManager();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		Economy economy = Taxes.getEconomy();
		Taxes plugin = (Taxes) sender.getServer().getPluginManager().getPlugin("Taxes");
		Player player = (Player) sender;

		if (args.length == 0) {
			player.sendMessage(ChatColor.RED + "Correct usage: /tax <set-tax|collect|schedule|flat-amount|flat-percent|pay-percent");
			return true;
		}

		if (!player.hasPermission("taxes.command.use") && !player.hasPermission("businesscraft.developer")) {
			player.sendMessage(ChatColor.RED + "You do not have permissions to use this command. (taxes.command.use)");
			return true;
		}


		String subCommand = args[0].toLowerCase();
		switch (subCommand) {
			case "collect":
				if (isCollectingTaxes) {
					sendProgressBar(player, getCurrentIndex(), offlinePlayersLength);
				} else {
					try {
						databaseManager.truncateTable();
					} catch (SQLException e) {
						plugin.getLogger().severe("An error occurred while truncating the table: " + e.getMessage());
						e.printStackTrace();
					}
					collectTaxes(player, economy);
				}
				break;
			case "set-tax":
				setTaxType(player, args);
				break;
			case "flat-amount":
				setFlatAmount(player, args);
				break;
			case "flat-percent":
				setFlatPercent(player, args);
				break;
			case "pay-percent":
				setPayPercent(player, args);
				break;
			case "schedule":
				long currentTime = System.currentTimeMillis();
				long nextTaxTime = currentTime + TimeUnit.DAYS.toMillis(7); // Schedule for next week
				taxManager.saveNextTaxTime(nextTaxTime);
				taxManager.scheduleWeeklyTaxTask();
				plugin.getConfig().set("config.schedule", true);
				plugin.saveConfig();
				player.sendMessage(ChatColor.YELLOW + "[Taxes] Tax has been scheduled to be collected in 7 days.");
				break;
			case "type":
				player.sendMessage(ChatColor.YELLOW + "[Taxes] Active Tax: " + plugin.getConfig().getString("tax.tax-type"));
				break;
			case "toggle-paytax":
				if (plugin.registeredEvents.contains(PayTaxListener.class)) {
					HandlerList.unregisterAll(plugin);
					plugin.getConfig().set("config.enable-paytax", false);
					plugin.registeredEvents.clear();
					player.sendMessage(ChatColor.GREEN + "Pay-tax disabled.");
				} else {
					plugin.registerEventIfNotRegistered(PayTaxListener.class);
					plugin.getConfig().set("config.enable-paytax", true);
					plugin.registeredEvents.add(PayTaxListener.class);
					player.sendMessage(ChatColor.GREEN + "Pay-tax enabled.");
				}
				plugin.saveConfig();
				break;
			case "exclude":
				addToExcludeList(args[1]);
				player.sendMessage(ChatColor.YELLOW + "[Taxes] " + args[1] + " has been excluded.");
				break;
			case "rollback":
				Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
					try {
						rollBackTaxes(databaseManager.getPlayers());
					} catch (SQLException e) {
						plugin.getLogger().severe("An error occurred while adding balances to SQLite: " + e.getMessage());
						e.printStackTrace();
					}
				});
				player.sendMessage(ChatColor.GREEN + "Taxes have been rolled back for all players.");
				break;
			default:
				player.sendMessage(ChatColor.RED + "Correct usage: /tax <set-tax|collect|schedule|flat-amount|flat-percent|pay-percent");
				break;
		}
		return true;
	}

	private void rollBackTaxes(Map<UUID, Double> map) throws SQLException {
		try {
			Economy economy = Taxes.getEconomy();
			for (Map.Entry<UUID, Double> entry : map.entrySet()) {
				UUID uuid = entry.getKey();
				double settableBalance = entry.getValue();

				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
				double currentBalance = economy.getBalance(offlinePlayer);
				economy.withdrawPlayer(offlinePlayer, currentBalance);
				if (plugin.getMoneyPlugin().getMoneyDatabaseInterface().hasAccount(offlinePlayer)) {
					plugin.getMoneyPlugin().getMoneyDatabaseInterface().setBalance(offlinePlayer, 0D);
				}
				economy.depositPlayer(offlinePlayer, settableBalance);
			}
		} catch (Throwable t) {
			plugin.getLogger().severe("There was an error while rolling back taxes: " + t.getMessage());
			t.printStackTrace();
		}

	}

	private List<String> getExcludeList() {
		ConfigurationSection taxSection = plugin.getConfig().getConfigurationSection("tax");
		return taxSection.getStringList("exclude");
	}


	private void addToExcludeList(String playerName) {
		ConfigurationSection taxSection = plugin.getConfig().getConfigurationSection("tax");
		List<String> excludeList = taxSection.getStringList("exclude");

		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
		UUID playerUUID = offlinePlayer.getUniqueId();
		String uuidString = playerUUID.toString();

		if (!excludeList.contains(uuidString)) {
			excludeList.add(uuidString);
			taxSection.set("exclude", excludeList);
			plugin.saveConfig();
		}
	}

	private void setFlatPercent(Player sender, String[] args) {
		if (args.length == 2) {
			String newTaxType = args[1];
			plugin.getConfig().set("tax.flat-percent", newTaxType);
			plugin.saveConfig();
			sender.sendMessage(ChatColor.GREEN + "[Taxes] Flat Tax percent set to: " + newTaxType);
		} else {
			sender.sendMessage(ChatColor.RED + "Usage: /tax flat-percent <amount>");
		}
	}

	private void setFlatAmount(Player sender, String[] args) {
		if (args.length == 2) {
			String newTaxType = args[1];
			plugin.getConfig().set("tax.flat-amount", newTaxType);
			plugin.saveConfig();
			sender.sendMessage(ChatColor.GREEN + "[Taxes] Flat Tax amount set to: " + newTaxType);
		} else {
			sender.sendMessage(ChatColor.RED + "Usage: /tax flat-amount <amount>");
		}
	}

	private void setPayPercent(Player sender, String[] args) {
		if (args.length == 2) {
			String newTaxType = args[1];
			plugin.getConfig().set("tax.pay-amount", newTaxType);
			plugin.saveConfig();
			sender.sendMessage(ChatColor.GREEN + "[Taxes] Pay Tax percentage set to: " + newTaxType);
		} else {
			sender.sendMessage(ChatColor.RED + "Usage: /tax pay-percent <amount>");
		}
	}

	private void setTaxType(Player sender, String[] args) {
		if (args.length == 2) {
			if (sender.hasPermission("taxes.command.set") || sender.hasPermission("businesscraft.developer")) {
				String newTaxType = args[1];
				plugin.getConfig().set("tax.tax-type", newTaxType);
				plugin.saveConfig();
				sender.sendMessage(ChatColor.GREEN + "Tax type set to: " + newTaxType);
				if ("pay-tax".equalsIgnoreCase(newTaxType)) {
					plugin.registerEventIfNotRegistered(PayTaxListener.class);
				}
			} else {
				sender.sendMessage(ChatColor.RED + "You do not have permissions to use this command. (taxes.command.set)");

			}
		} else {
			sender.sendMessage(ChatColor.RED + "Usage: /tax set-tax <tax-type>");
		}
	}


	private void collectTaxes(Player sender, Economy economy) {
		if (hasPermissionToCollectTaxes(sender)) {
			TaxData taxData = taxManager.getTaxData();
			AtomicReference<Double> taxIncome = new AtomicReference<>(0D);
			int batchSize = 500; // Number of players
			currentIndex = 0;
			OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();
			offlinePlayersLength = offlinePlayers.length;
			sendTaxCollectionStartMessage(sender);
			isCollectingTaxes = true;
			scheduleTaxCollection(sender, taxData, taxIncome, batchSize, offlinePlayers);
		} else {
			sender.sendMessage(ChatColor.RED + "You do not have permission to use this command. (taxes.command.collect)");
		}
	}

	private boolean hasPermissionToCollectTaxes(Player sender) {
		return sender.hasPermission("taxes.command.collect") || sender.hasPermission("businesscraft.developer");
	}

	private void sendTaxCollectionStartMessage(Player sender) {
		sender.sendMessage(ChatColor.GREEN + "Tax collection started.");
		int offlinePlayersLength = Bukkit.getOfflinePlayers().length;
		int batchSize = 500;
		double estimatedTime = ((Math.ceil((double) offlinePlayersLength / batchSize) - 1) * 5);
		sender.sendMessage(ChatColor.YELLOW + "The process will take approx. " + estimatedTime + " seconds.");
	}

	private void scheduleTaxCollection(Player sender, TaxData taxData, AtomicReference<Double> taxIncome, int batchSize, OfflinePlayer[] offlinePlayers) {
		processBatch(sender, taxData, taxIncome, batchSize, offlinePlayers);
	}

	private void processBatch(Player sender, TaxData taxData, AtomicReference<Double> taxIncome, int batchSize, OfflinePlayer[] offlinePlayers) {
		AtomicReference<Connection> connection = new AtomicReference<>();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			int start = currentIndex;
			int end = Math.min(start + batchSize, offlinePlayers.length);
			establishDatabaseConnection(connection);
			processPlayersInRange(sender, taxData, taxIncome, start, end, offlinePlayers, connection.get());
			currentIndex = end;
			checkAndCancelTask(sender, taxData, taxIncome, batchSize, offlinePlayers, connection);
		});
	}

	private void establishDatabaseConnection(AtomicReference<Connection> connection) {
		try {
			connection.set(DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/taxes.db"));
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void processPlayersInRange(Player sender, TaxData taxData, AtomicReference<Double> taxIncome, int start, int end, OfflinePlayer[] offlinePlayers, Connection connection) {
		for (int i = start; i < end; i++) {
			OfflinePlayer offlinePlayer = offlinePlayers[i];
			UUID offlinePlayerUUID = offlinePlayer.getUniqueId();

			if (isValidPlayer(offlinePlayerUUID)) {
				double balance = taxManager.getPlayerBalance(offlinePlayer);
				double income = taxManager.applyWeeklyTax(offlinePlayer, balance, taxData);
				updateDatabase(connection, offlinePlayerUUID, offlinePlayer.getName(), balance);
				taxIncome.updateAndGet(v -> v + income);
			}
		}
	}

	private boolean isValidPlayer(UUID offlinePlayerUUID) {
		return !offlinePlayerUUID.toString().equals(plugin.getConfig().getString("config.admin-account")) && !getExcludeList().contains(offlinePlayerUUID.toString());
	}

	private void updateDatabase(Connection connection, UUID offlinePlayerUUID, String playerName, double balance) {
		try {
			databaseManager.addPlayerOpen(connection, offlinePlayerUUID, playerName, balance);
		} catch (SQLException e) {
			plugin.getLogger().severe("An error occurred while adding balances to SQLite: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void checkAndCancelTask(Player sender, TaxData taxData, AtomicReference<Double> taxIncome, int batchSize, OfflinePlayer[] offlinePlayers, AtomicReference<Connection> connection) {
		int end = currentIndex;
		int offlinePlayersLength = offlinePlayers.length;

		if (end >= offlinePlayersLength) {
			closeDatabaseConnection(connection.get());
			setIsCollectingTaxesFalse();
			sendTaxCollectionEndMessage(sender, taxIncome);
		} else {
			// Schedule the next batch
			processBatch(sender, taxData, taxIncome, batchSize, offlinePlayers);
		}
	}


	private void closeDatabaseConnection(Connection connection) {
		try {
			connection.close();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private void setIsCollectingTaxesFalse() {
		isCollectingTaxes = false;
	}

	private void sendTaxCollectionEndMessage(Player sender, AtomicReference<Double> taxIncome) {
		sender.sendMessage(ChatColor.GREEN + "[Taxes] Taxes have been collected.");
		plugin.getLogger().info("Taxes have been collected.");
		plugin.getLogger().info("Tax income: " + taxIncome);
	}


	private void sendProgressBar(Player player, int current, int total) {
		double percentage = (double) current / total;
		int progressBarLength = 30;
		int completedLength = (int) (percentage * progressBarLength);
		int remainingLength = progressBarLength - completedLength;

		String progressBar = ChatColor.GREEN + String.join("", Collections.nCopies(completedLength, "█"))
				+ ChatColor.RED + String.join("", Collections.nCopies(remainingLength, "█"));

		player.sendMessage(ChatColor.GRAY + "Tax collection progress: " + progressBar + " " + ChatColor.YELLOW + String.format("%.2f", percentage * 100) + "%");
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1) {
			final String[] SUB_COMMANDS = {"set-tax", "collect", "flat-amount", "flat-percent", "pay-percent", "schedule", "type", "toggle-paytax", "exclude", "rollback"};
			return StringUtil.copyPartialMatches(args[0], Arrays.asList(SUB_COMMANDS), new ArrayList<>());
		} else if (args.length == 2) {
			switch (args[0].toLowerCase()) {
				case "set-tax":
					final String[] TAX_TYPES = {"pay-tax", "flat-amount", "flat-percent", "progressive-tax"};
					return StringUtil.copyPartialMatches(args[1], Arrays.asList(TAX_TYPES), new ArrayList<>());
				case "flat-amount":
					return Collections.singletonList("<amount>");
				case "flat-percent":
				case "pay-percent":
					return Collections.singletonList("<decimal>");
				case "enable-paytax":
					return Collections.singletonList("<true|false>");
				case "exclude":
					return Collections.singletonList("<username>");
				default:
					break;
			}
		}
		return Collections.emptyList();
	}

}
