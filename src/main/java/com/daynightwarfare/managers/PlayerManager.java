package com.daynightwarfare.managers;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.TeamType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class PlayerManager {
    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final TeamManager teamManager;
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerManager(DayNightPlugin plugin, GameManager gameManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.teamManager = teamManager;
    }

    public void addPlayer(Player player) {
        alivePlayers.add(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        alivePlayers.remove(player.getUniqueId());
    }

    public boolean isAlive(Player player) {
        return alivePlayers.contains(player.getUniqueId());
    }

    public Set<UUID> getAlivePlayers() {
        return alivePlayers;
    }

    public void clearPlayers() {
        alivePlayers.clear();
    }

    public boolean wasPlayerInGame(Player player) {
        return teamManager.getPlayerTeam(player) != null;
    }

    public void handleReconnect(Player player) {
        teamManager.updatePlayerDisplayName(player);
        if (!isAlive(player)) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(miniMessage.deserialize("<gray>관전자로 다시 참여했습니다.</gray>"));
        } else {
            if (player.getBedSpawnLocation() != null) {
                player.teleport(player.getBedSpawnLocation());
            }
            player.sendMessage(miniMessage.deserialize("<green>게임에 다시 참여했습니다!</green>"));
        }
    }

    public void handleLateJoin(Player player) {
        long lightTeamCount = teamManager.getPlayerTeams().values().stream().filter(t -> t == TeamType.APOSTLE_OF_LIGHT).count();
        long moonTeamCount = teamManager.getPlayerTeams().values().stream().filter(t -> t == TeamType.APOSTLE_OF_MOON).count();
        TeamType assignedTeam = (lightTeamCount <= moonTeamCount) ? TeamType.APOSTLE_OF_LIGHT : TeamType.APOSTLE_OF_MOON;

        teamManager.setPlayerTeam(player, assignedTeam);
        addPlayer(player);

        gameManager.supplySkillItems(player);

        Location base = (assignedTeam == TeamType.APOSTLE_OF_LIGHT) ? gameManager.getLightTeamBaseLocation() : gameManager.getMoonTeamBaseLocation();
        if (base != null) {
            Random random = new Random();
            int offsetX = random.nextInt(31) - 15;
            int offsetZ = random.nextInt(31) - 15;
            double finalX = base.getX() + offsetX;
            double finalZ = base.getZ() + offsetZ;
            Block safeBlock = gameManager.getSafeHighestBlock(base.getWorld(), (int) finalX, (int) finalZ);
            if (safeBlock != null) {
                Location tpLocation = safeBlock.getLocation().add(0.5, 1.5, 0.5);
                player.teleportAsync(tpLocation);
            } else {
                player.teleportAsync(base.getWorld().getSpawnLocation());
            }
        }

        player.sendMessage(miniMessage.deserialize("<green>진행중인 게임에 참여합니다!</green>"));
        player.sendMessage(miniMessage.deserialize(
                "<gray>당신은 <team> 팀입니다.</gray>",
                Placeholder.component("team", assignedTeam.getStyledDisplayName())
        ));
    }
}
