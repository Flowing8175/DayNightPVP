package com.daynightwarfare.commands.subcommands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.GameState;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class StartCommand implements SubCommand {
    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public StartCommand(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }

    @Override
    public String getName() {
        return "start";
    }

    @Override
    public String getPermission() {
        return "daynight.admin.start";
    }

    @Override
    public void execute(@NotNull CommandSender sender, String[] args) {
        if (gameManager.getState() != GameState.WAITING) {
            sender.sendMessage(miniMessage.deserialize("<red>게임이 이미 진행 중이거나 시작 중입니다.</red>"));
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
                    gameManager.assignTeamsAndTeleport();
                    gameManager.supplySkillItems();
                    long graceMinutes = plugin.getConfig().getLong("grace-period-minutes", 15);
                    gameManager.startGracePeriod(graceMinutes);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
