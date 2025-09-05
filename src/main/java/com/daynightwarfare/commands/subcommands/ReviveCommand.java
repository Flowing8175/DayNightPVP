package com.daynightwarfare.commands.subcommands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.managers.PlayerManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.attribute.Attribute;

import java.util.Collections;
import java.util.List;

public class ReviveCommand implements SubCommand {
    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ReviveCommand(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }

    @Override
    public String getName() {
        return "revive";
    }

    @Override
    public String getPermission() {
        return "daynight.admin.revive";
    }

    @Override
    public void execute(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>사용법: /game revive <player></red>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(miniMessage.deserialize("<red>플레이어를 찾을 수 없습니다.</red>"));
            return;
        }

        PlayerManager playerManager = gameManager.getPlayerManager();
        if (playerManager.isAlive(target)) {
            sender.sendMessage(miniMessage.deserialize("<red>이미 살아있는 플레이어입니다.</red>"));
            return;
        }

        playerManager.addPlayer(target);
        target.setGameMode(GameMode.SURVIVAL);
        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        target.setFireTicks(0);

        sender.sendMessage(miniMessage.deserialize("<green>" + target.getName() + "님을 부활시켰습니다.</green>"));
        target.sendMessage(miniMessage.deserialize("<green>관리자에 의해 부활되었습니다.</green>"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
