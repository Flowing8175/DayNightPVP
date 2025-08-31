package com.daynightwarfare;

import com.daynightwarfare.commands.GameCommand;
import com.daynightwarfare.listeners.GameListener;
import com.daynightwarfare.listeners.PlayerJoinListener;
import com.daynightwarfare.listeners.SkillListener;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public final class DayNightPlugin extends JavaPlugin {

    private static DayNightPlugin instance;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.gameManager = GameManager.getInstance();

        getCommand("game").setExecutor(new GameCommand(this));
        getCommand("game").setTabCompleter(new GameCommand(this));

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        startPassiveEffectTask();

        getLogger().info("DayNightWarfare has been enabled!");
    }

    private void startPassiveEffectTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameManager.isGameInProgress() || gameManager.isGracePeriodActive()) {
                    return;
                }

                World world = getServer().getWorlds().get(0);
                boolean isDay = world.isDayTime();

                for (Player player : getServer().getOnlinePlayers()) {
                    if (!gameManager.getAlivePlayers().contains(player.getUniqueId())) {
                        continue;
                    }

                    TeamType team = gameManager.getPlayerTeam(player);
                    if (team == null) continue;

                    if (team == TeamType.APOSTLE_OF_LIGHT) {
                        if (isDay) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 6 * 20, 0, true, false, true));
                        } else {
                            player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
                        }
                    }
                    else if (team == TeamType.APOSTLE_OF_MOON) {
                        if (!isDay) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 6 * 20, 0, true, false, true));
                        } else {
                            player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 60L); // Runs every 3 seconds
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameInProgress()) {
            gameManager.resetGame();
        }
        getLogger().info("DayNightWarfare has been disabled!");
    }

    public static DayNightPlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
