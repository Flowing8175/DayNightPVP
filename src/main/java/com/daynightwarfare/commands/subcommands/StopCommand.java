package com.daynightwarfare.commands.subcommands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class StopCommand implements SubCommand {
    private final GameManager gameManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public StopCommand(DayNightPlugin plugin) {
        this.gameManager = plugin.getGameManager();
    }

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getPermission() {
        return "daynight.admin.stop";
    }

    @Override
    public void execute(@NotNull CommandSender sender, String[] args) {
        if (!gameManager.isGameInProgress()) {
            sender.sendMessage(miniMessage.deserialize("<red>중지할 게임이 없습니다.</red>"));
            return;
        }
        gameManager.resetGame(true);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
