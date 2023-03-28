package org.ancastal.taxes.listeners;

import net.ess3.api.events.UserBalanceUpdateEvent;
import net.milkbowl.vault.economy.Economy;
import org.ancastal.taxes.Taxes;
import org.ancastal.taxes.models.TaxManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.UUID;

public class PayTaxListener implements Listener {
	Economy economy = Taxes.getEconomy();
	private TaxManager taxManager;
	private final JavaPlugin plugin;
	

	public PayTaxListener(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onUserBalanceUpdate(UserBalanceUpdateEvent event) {
		BigDecimal taxRate = new BigDecimal(plugin.getConfig().getString("tax.pay-amount"));
		if (event.getCause() == UserBalanceUpdateEvent.Cause.COMMAND_PAY) {
			Economy economy = Taxes.getEconomy();
			UUID playerUUID = event.getPlayer().getUniqueId();
			BigDecimal oldBalance = event.getOldBalance();
			BigDecimal newBalance = event.getNewBalance();

			// if the player is receiving money
			if (newBalance.compareTo(oldBalance) > 0) {
				BigDecimal receivedAmount = newBalance.subtract(oldBalance);
				BigDecimal taxAmount = receivedAmount.multiply(taxRate);
				BigDecimal newAmount = receivedAmount.subtract(taxAmount);

				event.setNewBalance(oldBalance.add(newAmount));

				// Store the original transaction amount for the sender
				try { // Give tax to gov bal
					OfflinePlayer governmentAccount = Bukkit.getOfflinePlayer(UUID.fromString(plugin.getConfig().getString("config.admin-account")));
					economy.depositPlayer(governmentAccount, taxAmount.doubleValue());
				} catch (Throwable t) {
					t.printStackTrace();
				}
				// Send tax message
				event.getPlayer().sendMessage(ChatColor.YELLOW + "A tax of " + Math.round(taxAmount.doubleValue()) + " was deducted.");
			}
		}
	}


}

