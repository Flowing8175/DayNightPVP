package com.daynightwarfare.commands.subcommands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.TeamType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TeamCommand implements SubCommand {
    private final GameManager gameManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public TeamCommand(DayNightPlugin plugin) {
        this.gameManager = plugin.getGameManager();
    }

    @Override
    public String getName() {
        return "team";
    }

    @Override
    public String getPermission() {
        return "daynight.admin.team";
    }

    @Override
    public void execute(@NotNull CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(miniMessage.deserialize("<red>사용법: /game team <pin|unpin> <플레이어> [팀]</red>"));
            return;
        }

        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(miniMessage.deserialize("<red>플레이어를 찾을 수 없습니다.</red>"));
            return;
        }

        if (action.equals("pin")) {
            if (args.length < 4) {
                sender.sendMessage(miniMessage.deserialize("<red>사용법: /game team pin <플레이어> <Light|Moon></red>"));
                return;
            }
            String teamName = args[3].toLowerCase();
            TeamType team;
            if (teamName.startsWith("l")) {
                team = TeamType.APOSTLE_OF_LIGHT;
            } else if (teamName.startsWith("m") || teamName.startsWith("s")) {
                team = TeamType.APOSTLE_OF_MOON;
            } else {
                sender.sendMessage(miniMessage.deserialize("<red>올바르지 않은 팀입니다. 'Light' 또는 'Moon'을 사용하세요.</red>"));
                return;
            }
            gameManager.getTeamManager().setPlayerPin(target.getUniqueId(), team);
            sender.sendMessage(miniMessage.deserialize("<green>" + target.getName() + "님을 " + team.getStyledDisplayName() + " 팀에 고정했습니다.</green>"));
        } else if (action.equals("unpin")) {
            gameManager.getTeamManager().setPlayerPin(target.getUniqueId(), null);
            sender.sendMessage(miniMessage.deserialize("<green>" + target.getName() + "님의 고정을 해제했습니다.</green>"));
        } else {
            sender.sendMessage(miniMessage.deserialize("<red>사용법: /game team <pin|unpin> <플레이어> [팀]</red>"));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("pin", "unpin").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 4 && args[1].equalsIgnoreCase("pin")) {
            return Arrays.asList("Light", "Moon").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
}
