package org.ancastal.taxes.models;

import com.earth2me.essentials.Essentials;
import net.milkbowl.vault.economy.Economy;
import org.ancastal.taxes.Taxes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TaxManager {


	private final JavaPlugin plugin;
	private final TaxData taxData;
	private final Taxes taxes;

	public TaxManager(JavaPlugin plugin, Taxes taxes) {
		this.plugin = plugin;
		this.taxes = taxes;
		this.taxData = loadTaxData();
		scheduleWeeklyTaxTask();
	}

	public TaxData getTaxData() {
		return taxData;
	}

	private TaxData loadTaxData() {
		ConfigurationSection taxSection = plugin.getConfig().getConfigurationSection("tax");
		String taxType = taxSection.getString("tax-type");
		double flatPercent = Double.parseDouble(taxSection.getString("flat-percent"));
		double flatAmount = Double.parseDouble(taxSection.getString("flat-amount"));
		List<TaxBracket> taxBrackets = loadTaxBrackets(taxSection);
		double payAmount = taxSection.getDouble("pay-amount");

		return new TaxData(taxType, flatPercent, flatAmount, taxBrackets, payAmount);
	}

	private List<TaxBracket> loadTaxBrackets(ConfigurationSection taxSection) {
		List<Map<?, ?>> bracketSections = taxSection.getMapList("brackets");
		List<TaxBracket> taxBrackets = new ArrayList<>();

		for (Map<?, ?> bracketSection : bracketSections) {
			if (bracketSection.containsKey("min") && bracketSection.containsKey("max") && bracketSection.containsKey("rate")) {
				int min = (int) bracketSection.get("min");
				int max = (int) bracketSection.get("max");
				double rate = (double) bracketSection.get("rate");
				taxBrackets.add(new TaxBracket(min, max, rate));
			} else {
				plugin.getLogger().warning("Invalid tax bracket in config.yml. Please check the structure and keys.");
				plugin.getLogger().warning(bracketSection.entrySet().toString());
			}
		}

		return taxBrackets;
	}

	public double applyWeeklyTax(OfflinePlayer player, double balance, TaxData taxData) {
		double taxAmount = 0.0;
		taxData = loadTaxData();
		String taxType = taxData.getTaxType();
		if ("flat-percent".equalsIgnoreCase(taxType)) {
			taxAmount = balance * taxData.getFlatPercent();
		} else if ("flat-amount".equalsIgnoreCase(taxType)) {
			taxAmount = taxData.getFlatAmount();
		} else if ("progressive-tax".equalsIgnoreCase(taxType)) {
			ConfigurationSection taxSection = plugin.getConfig().getConfigurationSection("tax");
			List<TaxBracket> taxBrackets = loadTaxBrackets(taxSection);
			TaxBracket bracket = findTaxBracket(balance, taxBrackets);
			if (bracket != null) {
				taxAmount = balance * bracket.getRate();
			}
		} else if ("pay-tax".equalsIgnoreCase(taxType)) {
			Bukkit.getLogger().info("You cannot collect taxes by command with a pay-tax.");
			return 0;
		} else {
			plugin.getLogger().warning("Invalid tax-type in config.yml. Please choose between flat-percent, flat-amount, and progressive-tax.");
			plugin.getLogger().warning(taxType);
			return taxAmount;
		}
		UUID uuid = player.getUniqueId();

		setPlayerBalance(player, taxAmount);
		plugin.saveConfig();
		return taxAmount;
	}

	private TaxBracket findTaxBracket(double balance, List<TaxBracket> taxBrackets) {
		for (TaxBracket bracket : taxBrackets) {
			if (bracket.isWithinRange(balance)) {
				return bracket;
			}
		}
		return null;
	}

	public double getPlayerBalance(OfflinePlayer player) {
		Economy economy = Taxes.getEconomy();
		if (taxes.getMoneyPlugin().getMoneyDatabaseInterface().hasAccount(player.getUniqueId())) {
			return economy.getBalance(player) + taxes.getMoneyPlugin().getMoneyDatabaseInterface().getBalance(player);
		} else {
			return economy.getBalance(player);
		}
	}


	private void setPlayerBalance(OfflinePlayer player, double tax) {
		Economy economy = Taxes.getEconomy();
		String currency = plugin.getConfig().getConfigurationSection("config").getString("currency");
		double personalBalance = economy.getBalance(player);
		double bankBalance = 0;

		if (taxes.getMoneyPlugin().getMoneyDatabaseInterface().hasAccount(player.getUniqueId())) {
			bankBalance = taxes.getMoneyPlugin().getMoneyDatabaseInterface().getBalance(player.getUniqueId());
		}

		double totalBalance = personalBalance + bankBalance;

		if (tax > 0) {
			double personalTax = Math.min(tax, personalBalance);
			double remainingTax = tax - personalTax;

			// Withdraw tax from player's personal balance
			economy.withdrawPlayer(player, personalTax);

			// Withdraw remaining tax from player's bank balance
			if (remainingTax > 0 && bankBalance > 0) {
				double newBankBalance = Math.max(bankBalance - remainingTax, 0);
				taxes.getMoneyPlugin().getMoneyDatabaseInterface().setBalance(player.getUniqueId(), newBankBalance);
			}

			if (player.isOnline()) {
				Player onlinePlayer = (Player) player;
				onlinePlayer.sendMessage(ChatColor.YELLOW + "You have been taxed " + Math.round(tax) + " " + currency + ".");
				onlinePlayer.sendMessage(ChatColor.YELLOW + "Your bank was taxed " + Math.round(remainingTax));
			}


			// Give tax to Government or Admin account
			UUID economyUUID = UUID.fromString(plugin.getConfig().getConfigurationSection("config").getString("admin-account"));
			OfflinePlayer economyAccount = Bukkit.getOfflinePlayer(economyUUID);
			economy.depositPlayer(economyAccount, tax);

			if (economyAccount == null) {
				//plugin.getLogger().severe("### Economy admin account was not found ###");
				return;
			}

			Essentials essentials = null;
			Plugin essentialsPlugin = Bukkit.getServer().getPluginManager().getPlugin("Essentials");
			if (essentialsPlugin instanceof Essentials) {
				essentials = (Essentials) essentialsPlugin;
				if (essentials.getUser(economyUUID) == null) {
					//plugin.getLogger().severe("### Economy admin account exists but not in this server ###");
					return;
				}
				essentials.getUser(player.getUniqueId()).sendMail(essentials.getUser(economyUUID), ChatColor.RED + "You have been taxed " + Math.round(tax) + " " + currency + ".");
			}


		}
	}


	public void scheduleWeeklyTaxTask() {
		Economy economy = Taxes.getEconomy();
		long currentTime = System.currentTimeMillis();
		long nextTaxTime = loadNextTaxTime();

		if (nextTaxTime == -1 || currentTime >= nextTaxTime) {
			nextTaxTime = currentTime + TimeUnit.DAYS.toMillis(7); // Schedule for next week
			saveNextTaxTime(nextTaxTime);
		}

		long delay = nextTaxTime - currentTime;
		long delayTicks = TimeUnit.MILLISECONDS.toSeconds(delay) * 20;

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			// Run the tax collection task
			for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
				applyWeeklyTax(offlinePlayer, economy.getBalance(offlinePlayer), getTaxData());
			}

			// Schedule the next tax collection
			scheduleWeeklyTaxTask();
		}, delayTicks);
	}


	public void saveNextTaxTime(long nextTaxTime) {
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.exists()) {
			dataFolder.mkdir();
		}

		File nextTaxTimeFile = new File(dataFolder, "next-tax-time.yml");

		YamlConfiguration yamlConfiguration = new YamlConfiguration();
		yamlConfiguration.set("next-tax-time", nextTaxTime);
		try {
			yamlConfiguration.save(nextTaxTimeFile);
		} catch (IOException e) {
			plugin.getLogger().severe("Could not save next tax time.");
			e.printStackTrace();
		}
	}

	public long loadNextTaxTime() {
		File dataFolder = plugin.getDataFolder();
		File nextTaxTimeFile = new File(dataFolder, "next-tax-time.yml");

		if (nextTaxTimeFile.exists()) {
			YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(nextTaxTimeFile);
			return yamlConfiguration.getLong("next-tax-time");
		}

		return -1;
	}

	// All the other tax-related methods, like applyWeeklyTax(), loadTaxBrackets(), etc.

	// ...
}
