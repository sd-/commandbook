/*
 * CommandBook
 * Copyright (C) 2011 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.commandbook;

import com.sk89q.commandbook.components.AbstractComponent;
import com.sk89q.commandbook.config.ConfigurationBase;
import com.sk89q.commandbook.config.Setting;
import com.sk89q.commandbook.util.PlayerUtil;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.worldedit.blocks.ItemType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.sk89q.commandbook.util.ItemUtil.*;
import static com.sk89q.commandbook.CommandBookUtil.giveItem;

/**
 * @author zml2008
 */
public class ItemsComponent extends AbstractComponent {
    protected LocalConfiguration config;

    @Override
    public void initialize() {
        config = configure(new LocalConfiguration());
        registerCommands(Commands.class);
    }

    private static class LocalConfiguration extends ConfigurationBase {
        @Setting("item-permissions-only") public boolean useItemPermissionsOnly;
        @Setting("allowed-items") public Set<Integer> allowedItems = Collections.emptySet();
        @Setting("disllowed-items") public Set<Integer> disallowedItems = Collections.emptySet();
        @Setting("default-item-stack-size") public int defaultItemStackSize = 1;
        @Setting("time-names") public Map<String, Integer> itemNames;
    }

    // -- Helper methods

    /**
     * Checks to see if a user can use an item.
     *
     * @param sender
     * @param id
     * @throws CommandException
     */
    public void checkAllowedItem(CommandSender sender, int id)
            throws CommandException {

        if (Material.getMaterial(id) == null || id == 0) {
            throw new CommandException("Non-existent item specified.");
        }

        // Check if the user has an override
        if (CommandBook.inst().hasPermission(sender, "commandbook.override.any-item")) {
            return;
        }

        boolean hasPermissions = CommandBook.inst().hasPermission(sender, "commandbook.items." + id);

        // Also check the permissions system
        if (hasPermissions) {
            return;
        }

        if (config.useItemPermissionsOnly) {
            if (!hasPermissions) {
                throw new CommandException("That item is not allowed.");
            }
        }

        if (config.allowedItems.size() > 0) {
            if (!config.allowedItems.contains(id)) {
                throw new CommandException("That item is not allowed.");
            }
        }

        if (config.disallowedItems.contains((id))) {
            throw new CommandException("That item is disallowed.");
        }
    }

    /**
     * Returns a matched item.
     *
     * @param name
     * @return item
     */
    public ItemStack getItem(String name) {

        int id = 0;
        int dmg = 0;
        String dataName = null;

        if (name.contains(":")) {
            String[] parts = name.split(":");
            dataName = parts[1];
            name = parts[0];
        }

        try {
            id = Integer.parseInt(name);
        } catch (NumberFormatException e) {
            // First check the configurable list of aliases
            Integer idTemp = config.itemNames.get(name.toLowerCase());

            if (idTemp != null) {
                id = (int) idTemp;
            } else {
                // Then check WorldEdit
                ItemType type = ItemType.lookup(name);

                if (type == null) {
                    return null;
                }

                id = type.getID();
            }
        }

        // If the user specified an item data or damage value, let's try
        // to parse it!
        if (dataName != null) {
            try {
                dmg = matchItemData(id, dataName);
            } catch (CommandException e) {
                return null;
            }
        }
        return new ItemStack(id, 1, (short)dmg);
    }

    /**
     * Matches an item and gets the appropriate item stack.
     *
     * @param source
     * @param name
     * @return iterator for players
     * @throws CommandException
     */
    public ItemStack matchItem(CommandSender source, String name)
            throws CommandException {

        int id = 0;
        int dmg = 0;
        String dataName = null;

        if (name.contains(":")) {
            String[] parts = name.split(":");
            dataName = parts[1];
            name = parts[0];
        }

        try {
            id = Integer.parseInt(name);
        } catch (NumberFormatException e) {
            // First check the configurable list of aliases
            Integer idTemp = config.itemNames.get(name.toLowerCase());

            if (idTemp != null) {
                id = (int) idTemp;
            } else {
                // Then check WorldEdit
                ItemType type = ItemType.lookup(name);

                if (type == null) {
                    throw new CommandException("No item type known by '" + name + "'");
                }

                id = type.getID();
            }
        }

        // If the user specified an item data or damage value, let's try
        // to parse it!
        if (dataName != null) {
            dmg = matchItemData(id, dataName);
        }
        return new ItemStack(id, 1, (short)dmg);
    }

    private class Commands {
        @Command(aliases = {"item"},
                usage = "[target] <item[:data]> [amount]", desc = "Give an item",
                flags = "do", min = 1, max = 3)
        @CommandPermissions({"commandbook.give"})
        public void item(CommandContext args, CommandSender sender) throws CommandException {
            ItemStack item = null;
            int amt = config.defaultItemStackSize;
            Iterable<Player> targets = null;

            // How this command handles parameters depends on how many there
            // are, so the following code splits the incoming input
            // into three different possibilities

            // One argument: Just the item type and amount 1
            if (args.argsLength() == 1) {
                item = matchItem(sender, args.getString(0));
                targets = PlayerUtil.matchPlayers(PlayerUtil.checkPlayer(sender));
                // Two arguments: Item type and amount
            } else if (args.argsLength() == 2) {
                item = matchItem(sender, args.getString(0));
                amt = args.getInteger(1);
                targets = PlayerUtil.matchPlayers(PlayerUtil.checkPlayer(sender));
                // Three arguments: Player, item type, and item amount
            } else if (args.argsLength() == 3) {
                item = matchItem(sender, args.getString(1));
                amt = args.getInteger(2);
                targets = PlayerUtil.matchPlayers(sender, args.getString(0));

                // Make sure that this player has permission to give items to other
                /// players!
                CommandBook.inst().checkPermission(sender, "commandbook.give.other");
            }

            if (item == null) {
                throw new CommandException("Something went wrong parsing the item info!");
            }
            giveItem(sender, item, amt, targets, ItemsComponent.this, args.hasFlag('d'), args.hasFlag('o'));
        }

        @Command(aliases = {"give"},
                usage = "[-d] <target> <item[:data]> [amount]", desc = "Give an item",
                flags = "do", min = 2, max = 3)
        @CommandPermissions({"commandbook.give.other"})
        public void give(CommandContext args, CommandSender sender) throws CommandException {
            ItemStack item = null;
            int amt = config.defaultItemStackSize;
            Iterable<Player> targets = null;

            // How this command handles parameters depends on how many there
            // are, so the following code splits the incoming input
            // into three different possibilities

            // Two arguments: Player, item type
            if (args.argsLength() == 2) {
                targets = PlayerUtil.matchPlayers(sender, args.getString(0));
                item = matchItem(sender, args.getString(1));
                // Three arguments: Player, item type, and item amount
            } else if (args.argsLength() == 3) {
                targets = PlayerUtil.matchPlayers(sender, args.getString(0));
                item = matchItem(sender, args.getString(1));
                amt = args.getInteger(2);
            }

            if (item == null) {
                throw new CommandException("Something went wrong parsing the item info!");
            }
            giveItem(sender, item, amt, targets, ItemsComponent.this, args.hasFlag('d'), args.hasFlag('o'));
        }

        @Command(aliases = {"clear"},
                usage = "[target]", desc = "Clear your inventory",
                flags = "as", min = 0, max = 1)
        @CommandPermissions({"commandbook.clear"})
        public void clear(CommandContext args, CommandSender sender) throws CommandException {

            Iterable<Player> targets = null;
            boolean clearAll = args.hasFlag('a');
            boolean clearSingle = args.hasFlag('s');
            boolean included = false;

            if (args.argsLength() == 0) {
                targets = PlayerUtil.matchPlayers(PlayerUtil.checkPlayer(sender));
                // A different player
            } else {
                targets = PlayerUtil.matchPlayers(sender, args.getString(0));

                // Make sure that this player can clear other players!
                CommandBook.inst().checkPermission(sender, "commandbook.clear.other");
            }

            for (Player player : targets) {
                Inventory inventory = player.getInventory();

                if (clearSingle) {
                    player.setItemInHand(null);
                } else {
                    for (int i = (clearAll ? 0 : 9); i < 36; i++) {
                        inventory.setItem(i, null);
                    }

                    if (clearAll) {
                        // Armor slots
                        for (int i = 36; i <= 39; i++) {
                            inventory.setItem(i, null);
                        }
                    }
                }

                // Tell the user about the given item
                if (player.equals(sender)) {
                    if (clearAll) {
                        player.sendMessage(ChatColor.YELLOW
                                + "Your inventory has been cleared.");
                    } else {
                        player.sendMessage(ChatColor.YELLOW
                                + "Your inventory has been cleared. Use -a to clear ALL.");
                    }

                    // Keep track of this
                    included = true;
                } else {
                    player.sendMessage(ChatColor.YELLOW
                            + "Your inventory has been cleared by "
                            + PlayerUtil.toName(sender));

                }
            }

            // The player didn't receive any items, then we need to send the
            // user a message so s/he know that something is indeed working
            if (!included) {
                sender.sendMessage(ChatColor.YELLOW
                        + "Inventories cleared.");
            }
        }
    }
}