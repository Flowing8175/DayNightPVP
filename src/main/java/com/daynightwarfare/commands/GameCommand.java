package com.daynightwarfare.commands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.GameState;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import com.daynightwarfare.TeamType;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GameCommand implements CommandExecutor, TabCompleter {

    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final MiniMessage miniMessage;

    public GameCommand(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(miniMessage.deserialize("<red>Usage: /game <start|stop|grace|team></red>"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                handleStart(sender);
                break;
            case "stop":
                handleStop(sender);
                break;
            case "grace":
                handleGrace(sender, args);
                break;
            case "team":
                handleTeam(sender, args);
                break;
            default:
                sender.sendMessage(miniMessage.deserialize("<red>Unknown subcommand. Usage: /game <start|stop|grace|team></red>"));
                break;
        }

        return true;
    }

    private void handleStart(CommandSender sender) {
        if (!sender.hasPermission("daynight.admin.start")) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to start the game.</red>"));
            return;
        }
        if (gameManager.getState() != GameState.WAITING) {
            sender.sendMessage(miniMessage.deserialize("<red>The game is already in progress or starting.</red>"));
            return;
        }
        gameManager.setState(GameState.COUNTDOWN);
        new BukkitRunnable() {
            int i = 5;
            @Override
            public void run() {
                if (i > 0) {
                    Bukkit.broadcast(miniMessage.deserialize("<yellow>게임 시작까지 <red>" + i + "</red>초...</yellow>"));
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    }
                    i--;
                } else {
                    this.cancel();
                    Bukkit.broadcast(miniMessage.deserialize("<yellow>게임이 시작되었습니다!</yellow>"));
                    gameManager.assignTeams();
                    gameManager.supplySkillItems();
                    gameManager.teleportPlayers();
                    long graceMinutes = plugin.getConfig().getLong("grace-period-minutes", 15);
                    gameManager.startGracePeriod(graceMinutes);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("daynight.admin.stop")) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to stop the game.</red>"));
            return;
        }
        if (!gameManager.isGameInProgress()) {
            sender.sendMessage(miniMessage.deserialize("<red>There is no game in progress to stop.</red>"));
            return;
        }
        gameManager.resetGame();
        sender.sendMessage(miniMessage.deserialize("<green>Game has been forcibly stopped.</green>"));
    }

    private void handleGrace(CommandSender sender, String[] args) {
        if (!sender.hasPermission("daynight.admin.grace")) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to modify the grace period.</red>"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>Usage: /game grace <add|subtract|end> [minutes]</red>"));
            return;
        }
        String graceAction = args[1].toLowerCase();
        int minutes = 0;
        if (args.length > 2) {
            try {
                minutes = Integer.parseInt(args[2]);
                if (minutes <= 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>Minutes must be a positive number.</red>"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(miniMessage.deserialize("<red>Invalid number of minutes.</red>"));
                return;
            }
        }
        switch (graceAction) {
            case "add":
                if (minutes == 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>Usage: /game grace add <minutes></red>"));
                    return;
                }
                if (!gameManager.isGracePeriodActive()) {
                    gameManager.startGracePeriod(minutes);
                    sender.sendMessage(miniMessage.deserialize("<green>Grace period started for " + minutes + " minutes.</green>"));
                } else {
                    gameManager.addGracePeriodTime(minutes);
                    sender.sendMessage(miniMessage.deserialize("<green>Added " + minutes + " minutes to the grace period.</green>"));
                }
                break;
            case "subtract":
                 if (minutes == 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>Usage: /game grace subtract <minutes></red>"));
                    return;
                }
                if (!gameManager.isGracePeriodActive()) {
                    sender.sendMessage(miniMessage.deserialize("<red>Cannot subtract time, grace period is not active.</red>"));
                    return;
                }
                gameManager.subtractGracePeriodTime(minutes);
                sender.sendMessage(miniMessage.deserialize("<green>Subtracted " + minutes + " minutes from the grace period.</green>"));
                break;
            case "end":
                if (!gameManager.isGracePeriodActive()) {
                    sender.sendMessage(miniMessage.deserialize("<red>Grace period is not active.</red>"));
                    return;
                }
                gameManager.endGracePeriod();
                sender.sendMessage(miniMessage.deserialize("<green>Grace period has been ended.</green>"));
                break;
            default:
                sender.sendMessage(miniMessage.deserialize("<red>Unknown action. Usage: /game grace <add|subtract|end></red>"));
                break;
        }
    }

    private void handleTeam(CommandSender sender, String[] args) {
        if (!sender.hasPermission("daynight.admin.team")) { // Add this permission to plugin.yml
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to manage teams.</red>"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(miniMessage.deserialize("<red>Usage: /game team <pin|unpin> <player> [team]</red>"));
            return;
        }

        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(miniMessage.deserialize("<red>Player not found.</red>"));
            return;
        }

        if (action.equals("pin")) {
            if (args.length < 4) {
                sender.sendMessage(miniMessage.deserialize("<red>Usage: /game team pin <player> <Light|Moon></red>"));
                return;
            }
            String teamName = args[3].toLowerCase();
            TeamType team;
            if (teamName.startsWith("l")) {
                team = TeamType.APOSTLE_OF_LIGHT;
            } else if (teamName.startsWith("m") || teamName.startsWith("s")) { // Moon or Shadow
                team = TeamType.APOSTLE_OF_MOON;
            } else {
                sender.sendMessage(miniMessage.deserialize("<red>Invalid team. Use 'Light' or 'Moon'.</red>"));
                return;
            }
            gameManager.setPlayerPin(target.getUniqueId(), team);
            sender.sendMessage(miniMessage.deserialize("<green>Pinned " + target.getName() + " to the " + team.getDisplayName() + " team.</green>"));
        } else if (action.equals("unpin")) {
            gameManager.setPlayerPin(target.getUniqueId(), null);
            sender.sendMessage(miniMessage.deserialize("<green>Unpinned " + target.getName() + ".</green>"));
        } else {
            sender.sendMessage(miniMessage.deserialize("<red>Usage: /game team <pin|unpin> <player> [team]</red>"));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            if (sender.hasPermission("daynight.admin.start")) completions.add("start");
            if (sender.hasPermission("daynight.admin.stop")) completions.add("stop");
            if (sender.hasPermission("daynight.admin.grace")) completions.add("grace");
            if (sender.hasPermission("daynight.admin.team")) completions.add("team");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("grace") && sender.hasPermission("daynight.admin.grace")) {
                completions.addAll(Arrays.asList("add", "subtract", "end"));
            } else if (args[0].equalsIgnoreCase("team") && sender.hasPermission("daynight.admin.team")) {
                completions.addAll(Arrays.asList("pin", "unpin"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("team")) {
             return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(currentArg))
                        .collect(Collectors.toList());
        } else if (args.length == 4 && args[1].equalsIgnoreCase("pin")) {
            completions.addAll(Arrays.asList("Light", "Moon"));
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg))
                .collect(Collectors.toList());
    }
}
