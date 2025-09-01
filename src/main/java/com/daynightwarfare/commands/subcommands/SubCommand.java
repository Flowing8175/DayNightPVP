package com.daynightwarfare.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SubCommand {
    String getName();
    String getPermission();
    void execute(@NotNull CommandSender sender, String[] args);
    List<String> onTabComplete(@NotNull CommandSender sender, String[] args);
}
