package com.daynightwarfare.commands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.commands.subcommands.GraceCommand;
import com.daynightwarfare.commands.subcommands.ReviveCommand;
import com.daynightwarfare.commands.subcommands.StartCommand;
import com.daynightwarfare.commands.subcommands.StopCommand;
import com.daynightwarfare.commands.subcommands.SubCommand;
import com.daynightwarfare.commands.subcommands.TeamCommand;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GameCommand(DayNightPlugin plugin) {
        registerSubCommand(new StartCommand(plugin));
        registerSubCommand(new StopCommand(plugin));
        registerSubCommand(new GraceCommand(plugin));
        registerSubCommand(new TeamCommand(plugin));
        registerSubCommand(new ReviveCommand(plugin));
    }

    private void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sendUsage(sender);
            return true;
        }

        if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to use this command.</red>"));
            return true;
        }

        subCommand.execute(sender, args);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        String usage = subCommands.keySet().stream().collect(Collectors.joining("|"));
        sender.sendMessage(miniMessage.deserialize("<red>사용법: /game <" + usage + "></red>"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return subCommands.values().stream()
                    .filter(sub -> sub.getPermission() == null || sender.hasPermission(sub.getPermission()))
                    .map(SubCommand::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length > 1) {
            SubCommand subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                if (subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission())) {
                    List<String> completions = subCommand.onTabComplete(sender, args);
                    if (completions != null) {
                        return completions;
                    }
                }
            }
        }

        return new ArrayList<>();
    }
}
