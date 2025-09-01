package com.daynightwarfare.commands.subcommands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GraceCommand implements SubCommand {
    private final GameManager gameManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GraceCommand(DayNightPlugin plugin) {
        this.gameManager = plugin.getGameManager();
    }

    @Override
    public String getName() {
        return "grace";
    }

    @Override
    public String getPermission() {
        return "daynight.admin.grace";
    }

    @Override
    public void execute(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>사용법: /game grace <add|subtract|end> [분]</red>"));
            return;
        }
        String graceAction = args[1].toLowerCase();
        int minutes = 0;
        if (args.length > 2) {
            try {
                minutes = Integer.parseInt(args[2]);
                if (minutes <= 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>분은 양수여야 합니다.</red>"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(miniMessage.deserialize("<red>올바르지 않은 숫자입니다.</red>"));
                return;
            }
        }
        switch (graceAction) {
            case "add":
                if (minutes == 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>사용법: /game grace add <분></red>"));
                    return;
                }
                if (!gameManager.isGracePeriodActive()) {
                    gameManager.startGracePeriod(minutes);
                    sender.sendMessage(miniMessage.deserialize("<green>무적 시간이 " + minutes + "분으로 시작되었습니다.</green>"));
                } else {
                    gameManager.addGracePeriodTime(minutes);
                    sender.sendMessage(miniMessage.deserialize("<green>무적 시간이 " + minutes + "분 추가되었습니다.</green>"));
                }
                break;
            case "subtract":
                if (minutes == 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>사용법: /game grace subtract <분></red>"));
                    return;
                }
                if (!gameManager.isGracePeriodActive()) {
                    sender.sendMessage(miniMessage.deserialize("<red>무적 시간이 활성화되어 있지 않아 시간을 뺄 수 없습니다.</red>"));
                    return;
                }
                gameManager.subtractGracePeriodTime(minutes);
                sender.sendMessage(miniMessage.deserialize("<green>무적 시간이 " + minutes + "분 감소되었습니다.</green>"));
                break;
            case "end":
                if (!gameManager.isGracePeriodActive()) {
                    sender.sendMessage(miniMessage.deserialize("<red>무적 시간이 활성화되어 있지 않습니다.</red>"));
                    return;
                }
                gameManager.endGracePeriod();
                sender.sendMessage(miniMessage.deserialize("<green>무적 시간이 종료되었습니다.</green>"));
                break;
            default:
                sender.sendMessage(miniMessage.deserialize("<red>알 수 없는 행동입니다. 사용법: /game grace <add|subtract|end></red>"));
                break;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("add", "subtract", "end").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
