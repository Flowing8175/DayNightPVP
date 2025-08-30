package com.daynightwarfare.commands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GameCommand implements CommandExecutor, TabCompleter {

    private final DayNightPlugin plugin;
    private final GameManager gameManager;

    public GameCommand(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /game <start|stop|grace|give>", NamedTextColor.RED));
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
            case "give":
                handleGive(sender, args);
                break;
            default:
                sender.sendMessage(Component.text("Unknown subcommand. Usage: /game <start|stop|grace|give>", NamedTextColor.RED));
                break;
        }

        return true;
    }

    private void handleStart(CommandSender sender) {
        if (!sender.hasPermission("daynight.admin.start")) {
            sender.sendMessage(Component.text("You do not have permission to start the game.", NamedTextColor.RED));
            return;
        }
        if (gameManager.getState() != GameState.WAITING) {
            sender.sendMessage(Component.text("The game is already in progress or starting.", NamedTextColor.RED));
            return;
        }
        gameManager.setState(GameState.COUNTDOWN);
        new BukkitRunnable() {
            int i = 5;
            @Override
            public void run() {
                if (i > 0) {
                    Component message = Component.text("게임 시작까지 ", NamedTextColor.YELLOW)
                            .append(Component.text(i, NamedTextColor.RED))
                            .append(Component.text("초...", NamedTextColor.YELLOW));
                    Bukkit.broadcast(message);
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    }
                    i--;
                } else {
                    this.cancel();
                    Bukkit.broadcast(Component.text("게임이 시작되었습니다!", NamedTextColor.YELLOW));
                    gameManager.assignTeams();
                    gameManager.teleportPlayers();
                    long graceMinutes = plugin.getConfig().getLong("grace-period-minutes", 15);
                    gameManager.startGracePeriod(graceMinutes);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("daynight.admin.stop")) {
            sender.sendMessage(Component.text("You do not have permission to stop the game.", NamedTextColor.RED));
            return;
        }
        if (!gameManager.isGameInProgress()) {
            sender.sendMessage(Component.text("There is no game in progress to stop.", NamedTextColor.RED));
            return;
        }
        gameManager.resetGame();
        sender.sendMessage(Component.text("Game has been forcibly stopped.", NamedTextColor.GREEN));
    }

    private void handleGrace(CommandSender sender, String[] args) {
        if (!sender.hasPermission("daynight.admin.grace")) {
            sender.sendMessage(Component.text("You do not have permission to modify the grace period.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /game grace <add|subtract|end> [minutes]", NamedTextColor.RED));
            return;
        }
        String graceAction = args[1].toLowerCase();
        int minutes = 0;
        if (args.length > 2) {
            try {
                minutes = Integer.parseInt(args[2]);
                if (minutes <= 0) {
                    sender.sendMessage(Component.text("Minutes must be a positive number.", NamedTextColor.RED));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid number of minutes.", NamedTextColor.RED));
                return;
            }
        }
        switch (graceAction) {
            case "add":
                if (minutes == 0) {
                    sender.sendMessage(Component.text("Usage: /game grace add <minutes>", NamedTextColor.RED));
                    return;
                }
                if (!gameManager.isGracePeriodActive()) {
                    gameManager.startGracePeriod(minutes);
                    sender.sendMessage(Component.text("Grace period started for " + minutes + " minutes.", NamedTextColor.GREEN));
                } else {
                    gameManager.addGracePeriodTime(minutes);
                    sender.sendMessage(Component.text("Added " + minutes + " minutes to the grace period.", NamedTextColor.GREEN));
                }
                break;
            case "subtract":
                 if (minutes == 0) {
                    sender.sendMessage(Component.text("Usage: /game grace subtract <minutes>", NamedTextColor.RED));
                    return;
                }
                if (!gameManager.isGracePeriodActive()) {
                    sender.sendMessage(Component.text("Cannot subtract time, grace period is not active.", NamedTextColor.RED));
                    return;
                }
                gameManager.subtractGracePeriodTime(minutes);
                sender.sendMessage(Component.text("Subtracted " + minutes + " minutes from the grace period.", NamedTextColor.GREEN));
                break;
            case "end":
                if (!gameManager.isGracePeriodActive()) {
                    sender.sendMessage(Component.text("Grace period is not active.", NamedTextColor.RED));
                    return;
                }
                gameManager.endGracePeriod();
                sender.sendMessage(Component.text("Grace period has been ended.", NamedTextColor.GREEN));
                break;
            default:
                sender.sendMessage(Component.text("Unknown action. Usage: /game grace <add|subtract|end>", NamedTextColor.RED));
                break;
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("daynight.admin.give")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /game give <player> <skill_id>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        String skillId = args[2].toLowerCase();
        ItemStack skillItem = createSkillItem(skillId);

        if (skillItem == null) {
            sender.sendMessage(Component.text("Unknown skill ID. Valid IDs: solar-flare, suns-spear, afterglow, mirror-dash, moons-chain, shadow-wings, moon-smash", NamedTextColor.RED));
            return;
        }

        target.getInventory().addItem(skillItem);
        sender.sendMessage(Component.text("Gave " + skillId + " item to " + target.getName(), NamedTextColor.GREEN));
    }

    private ItemStack createSkillItem(String skillId) {
        Material material;
        String name;
        List<String> lore = new ArrayList<>();

        switch (skillId) {
            case "solar-flare":
                material = Material.GLOWSTONE_DUST; name = "§6Solar Flare"; lore.add("§7Right-click to unleash a blinding light."); break;
            case "suns-spear":
                material = Material.GOLDEN_SWORD; name = "§6Sun's Spear"; lore.add("§7Right-click to throw a spear of light."); break;
            case "afterglow":
                material = Material.TORCH; name = "§6Afterglow"; lore.add("§7Right-click to burn nearby enemies."); break;
            case "mirror-dash":
                material = Material.GLASS_PANE; name = "§6Mirror Dash"; lore.add("§7Right-click to dash behind an enemy."); break;
            case "moons-chain":
                material = Material.FLOWER_BANNER_PATTERN; name = "§bMoon's Chain"; lore.add("§7Right-click to chain nearby enemies."); break;
            case "shadow-wings":
                material = Material.FEATHER; name = "§bShadow Wings"; lore.add("§7Sneak for 2s to gain temporary flight."); break;
            case "moon-smash":
                material = Material.NETHER_STAR; name = "§bMoon Smash"; lore.add("§7Sneak while gliding to smash the ground."); break;
            default:
                return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        List<Component> loreComponents = lore.stream().map(line -> Component.text(line).decoration(TextDecoration.ITALIC, false)).collect(Collectors.toList());
        meta.lore(loreComponents);
        NamespacedKey key = new NamespacedKey(plugin, "skill_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, skillId);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("daynight.admin.start")) completions.add("start");
            if (sender.hasPermission("daynight.admin.stop")) completions.add("stop");
            if (sender.hasPermission("daynight.admin.grace")) completions.add("grace");
            if (sender.hasPermission("daynight.admin.give")) completions.add("give");
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("grace") && sender.hasPermission("daynight.admin.grace")) {
                completions.addAll(Arrays.asList("add", "subtract", "end"));
            } else if (args[0].equalsIgnoreCase("give") && sender.hasPermission("daynight.admin.give")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (sender.hasPermission("daynight.admin.give")) {
                completions.addAll(Arrays.asList("solar-flare", "suns-spear", "afterglow", "mirror-dash", "moons-chain", "shadow-wings", "moon-smash"));
            }
        }
        return completions.stream()
                .filter(s -> s.startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
