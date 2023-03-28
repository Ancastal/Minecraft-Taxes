package org.ancastal.taxes;

import net.craftersland.money.Money;
import net.milkbowl.vault.economy.Economy;
import org.ancastal.taxes.commands.CollectTaxesCommand;
import org.ancastal.taxes.db.DatabaseManager;
import org.ancastal.taxes.listeners.PayTaxListener;
import org.ancastal.taxes.models.TaxData;
import org.ancastal.taxes.models.TaxManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class Taxes extends JavaPlugin {
	private TaxData taxData;
	private TaxManager taxManager;
	private FileConfiguration config;
	private DatabaseManager databaseManager;
	private Money moneyPlugin;

	private static Economy economy;
	public Set<Class<? extends Listener>> registeredEvents = new HashSet<>();


	private boolean setupEconomy() {
		if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
			return false;
		}

		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		economy = rsp.getProvider();
		return economy != null;
	}

	@Override
	public void onEnable() {


		saveDefaultConfig();
		taxManager = new TaxManager(this, this);
		taxData = taxManager.getTaxData();
		config = getConfig();


		if (!setupEconomy()) {
			Bukkit.getConsoleSender().sendMessage("Disabled due to no Vault dependency found!");
			Bukkit.getPluginManager().disablePlugin(this);
		}


		PluginManager pluginManager = Bukkit.getPluginManager();
		Plugin moneyPluginInstance = pluginManager.getPlugin("MySqlEconomyBank");

		if (moneyPluginInstance instanceof Money) {
			moneyPlugin = (Money) moneyPluginInstance;
		} else {
			Bukkit.getLogger().severe("Disabled due to no MySqlEconomyBank dependency found.");
			Bukkit.getPluginManager().disablePlugin(this);
		}

		try {
			databaseManager = new DatabaseManager(getDataFolder() + "/taxes.db");
			databaseManager.initializeDatabase();
			databaseManager.close();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		
		getCommand("tax").setExecutor(new CollectTaxesCommand(this));

		// If pay-tax is set in the config, register the pay-tax listener
		if ("pay-tax".equalsIgnoreCase(getConfig().getString("tax.tax-type")) || getConfig().getBoolean("config.enable-paytax")) {
			registerEventIfNotRegistered(PayTaxListener.class);
		}

		if (getConfig().getBoolean("config.schedule")) {
			taxManager.scheduleWeeklyTaxTask();
		}

	}


	public TaxData getTaxData() {
		return taxData;
	}

	public DatabaseManager getDatabaseManager() {
		return databaseManager;
	}

	public TaxManager getTaxManager() {
		return taxManager;
	}

	public static Economy getEconomy() {
		return economy;
	}

	public void registerEventIfNotRegistered(Class<? extends Listener> listenerClass) {
		if (!registeredEvents.contains(listenerClass)) {
			try {
				Constructor<? extends Listener> constructor = listenerClass.getDeclaredConstructor(JavaPlugin.class);
				Listener listener = constructor.newInstance(this);
				PluginManager pluginManager = getServer().getPluginManager();
				pluginManager.registerEvents(listener, this);
				registeredEvents.add(listenerClass);
			} catch (ReflectiveOperationException e) {
				getLogger().severe("Failed to register event: " + listenerClass.getSimpleName());
				e.printStackTrace();
			}
		}
	}

	public Money getMoneyPlugin() {
		return moneyPlugin;
	}
}
