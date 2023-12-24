# Taxes Plugin for Bukkit Servers

## Overview

This plugin introduces a comprehensive tax system for Bukkit-based game servers, allowing server administrators and players with the right permissions to manage and collect taxes in-game. From setting tax rates to scheduling tax collections, this plugin adds an engaging economic layer to your server's gameplay.

## Features

`Tax Management`: Set flat or percentage-based taxes.
`Tax Collection`: Collect taxes from players at specified intervals.
`Tax Scheduling`: Schedule tax collection on a weekly basis.
`Exclusion System`: Exclude specific players from taxes.
`User Permissions`: Restrict access to tax commands based on user permissions.
## Commands

`/tax set-tax <type>`: Set the type of tax.
`/tax collect`: Collect taxes from all players.
`/tax schedule`: Schedule the next tax collection.
`/tax flat-amount <amount>`: Set a flat tax amount.
`/tax flat-percent <percentage>`: Set a flat tax percentage.
`/tax pay-percent <percentage>`: Set a percentage for pay tax.
`/tax type`: Display the active tax type.
`/tax toggle-paytax`: Enable or disable pay-tax.
`/tax exclude <player>`: Exclude a player from tax.
`/tax rollback`: Roll back taxes for all players.

## Installation
Download the plugin JAR file.
Place it in your server's plugins directory.
Restart the server or load the plugin dynamically.

## Configuration
Edit the config.yml file to customize the tax settings according to your server needs.

## Permissions
`taxes.command.use`: Allows using all tax commands.
`taxes.command.set`: Allows setting tax types.
`taxes.command.collect`: Allows collecting taxes.
`businesscraft.developer`: Developer-specific permissions.

## Dependencies
- Vault
- Economy Plugin (compatible with Vault)

## License
This app is distributed with MIT License. See LICENSE for more information.
