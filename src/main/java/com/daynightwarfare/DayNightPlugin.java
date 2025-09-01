package com.daynightwarfare;

import com.daynightwarfare.commands.GameCommand;
import com.daynightwarfare.listeners.GameListener;
import com.daynightwarfare.listeners.PlayerJoinListener;
import com.daynightwarfare.listeners.PlayerQuitListener;
import com.daynightwarfare.managers.TimeManager;
import com.daynightwarfare.skills.SkillManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public final class DayNightPlugin extends JavaPlugin {

    private static DayNightPlugin instance;
    private GameManager gameManager;
    private SkillManager skillManager;
    private TimeManager timeManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.gameManager = GameManager.getInstance();
        this.skillManager = new SkillManager(this);
        this.timeManager = new TimeManager(this);


        getCommand("game").setExecutor(new GameCommand(this));
        getCommand("game").setTabCompleter(new GameCommand(this));

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(skillManager, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);

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
                applyPassiveEffects();
            }
        }.runTaskTimer(this, 0L, 60L); // Runs every 3 seconds
    }

    private void applyPassiveEffects() {
        World world = getServer().getWorlds().get(0);
        boolean isDay = world.isDayTime();

        for (Player player : getServer().getOnlinePlayers()) {
            if (!gameManager.getPlayerManager().isAlive(player)) {
                continue;
            }

            TeamType team = gameManager.getTeamManager().getPlayerTeam(player);
            if (team == null) continue;

            // Strength buff logic
            boolean shouldHaveStrength = (team == TeamType.APOSTLE_OF_LIGHT && isDay) || (team == TeamType.APOSTLE_OF_MOON && !isDay);
            if (shouldHaveStrength) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 7 * 20, 0, true, false, true));
            } else {
                player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
            }

            // Night vision logic
            boolean shouldHaveNightVision = (team == TeamType.APOSTLE_OF_MOON && !isDay);
            if (shouldHaveNightVision) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 20, 0, true, false, true));
            } else {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameInProgress()) {
            gameManager.resetGame();
        }
        if (timeManager != null) {
            timeManager.restoreDefaults();
        }
        getLogger().info("DayNightWarfare has been disabled!");
    }

    public static DayNightPlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }
}
