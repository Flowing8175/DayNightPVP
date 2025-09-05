package com.daynightwarfare.listeners;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.attribute.Attribute;

public class GameListener implements Listener {

    private static GameListener instance;
    private final GameManager gameManager;
    private final Set<UUID> resurrectedPlayers = new HashSet<>();

    public GameListener(DayNightPlugin plugin) {
        instance = this;
        this.gameManager = plugin.getGameManager();
    }

    public static GameListener getInstance() {
        return instance;
    }

    public void reset() {
        resurrectedPlayers.clear();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (gameManager.isGracePeriodActive()) {
            if (event.getEntity() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();
            if (gameManager.getPlayerManager().isAlive(damager) &&
                    gameManager.getPlayerManager().isAlive(victim) &&
                    gameManager.getTeamManager().getPlayerTeam(damager) == gameManager.getTeamManager().getPlayerTeam(victim)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (gameManager.getTeamManager().getPlayerTeam(player) != null) {
            if (!resurrectedPlayers.contains(player.getUniqueId())) {
                resurrectedPlayers.add(player.getUniqueId());
                event.setCancelled(true);
                player.setHealth(player.getMaxHealth());
                player.sendMessage("부활했습니다!");

                com.daynightwarfare.TeamType team = gameManager.getTeamManager().getPlayerTeam(player);
                if (team != null) {
                    Location base = (team == com.daynightwarfare.TeamType.APOSTLE_OF_LIGHT) ? gameManager.getLightTeamBaseLocation() : gameManager.getMoonTeamBaseLocation();
                    if (base != null) {
                        player.teleport(base);
                    }
                }
                return;
            }

            event.setDroppedExp(0);

            gameManager.getPlayerManager().removePlayer(player);

            // Set to spectator mode after a short delay
            org.bukkit.Bukkit.getScheduler().runTaskLater(DayNightPlugin.getInstance(), () -> {
                player.setGameMode(GameMode.SPECTATOR);
                gameManager.checkWinCondition();
            }, 20L);

            event.deathMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<gray>" + player.getName() + "님이 사망했습니다.</gray>"));
        }
    }
}
