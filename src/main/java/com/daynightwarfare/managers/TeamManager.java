package com.daynightwarfare.managers;

import com.daynightwarfare.TeamType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeamManager {
    private final Map<UUID, TeamType> playerTeams = new HashMap<>();
    private final Map<UUID, TeamType> pinnedPlayers = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public TeamType getPlayerTeam(Player player) {
        return playerTeams.get(player.getUniqueId());
    }

    public void setPlayerTeam(Player player, TeamType team) {
        playerTeams.put(player.getUniqueId(), team);
        updatePlayerDisplayName(player);
    }

    public void setPlayerPin(UUID uuid, TeamType team) {
        if (team == null) {
            pinnedPlayers.remove(uuid);
        } else {
            pinnedPlayers.put(uuid, team);
        }
    }

    public void assignTeams(List<Player> players) {
        playerTeams.clear();
        List<Player> unassignedPlayers = new ArrayList<>();
        int lightTeamCount = 0;
        int moonTeamCount = 0;

        for (Player player : players) {
            UUID uuid = player.getUniqueId();
            if (pinnedPlayers.containsKey(uuid)) {
                TeamType team = pinnedPlayers.get(uuid);
                setPlayerTeam(player, team);
                if (team == TeamType.APOSTLE_OF_LIGHT) lightTeamCount++;
                else moonTeamCount++;
            } else {
                unassignedPlayers.add(player);
            }
        }

        Collections.shuffle(unassignedPlayers);
        for (Player player : unassignedPlayers) {
            TeamType team = (lightTeamCount <= moonTeamCount) ? TeamType.APOSTLE_OF_LIGHT : TeamType.APOSTLE_OF_MOON;
            setPlayerTeam(player, team);
            if (team == TeamType.APOSTLE_OF_LIGHT) lightTeamCount++;
            else moonTeamCount++;
        }

        for (Player player : players) {
            TeamType team = getPlayerTeam(player);
            player.sendMessage(miniMessage.deserialize("<gray>당신은 " + team.getStyledDisplayName() + " 팀입니다.</gray>"));
        }
    }

    public void updatePlayerDisplayName(Player player) {
        TeamType team = getPlayerTeam(player);
        if (team == null) {
            resetPlayerDisplayName(player);
            return;
        }
        Component prefix = team.getStyledDisplayName().append(Component.text(" "));
        Component newName = prefix.append(Component.text(player.getName()));

        player.displayName(newName);
        player.playerListName(newName);
        player.customName(newName);
        player.setCustomNameVisible(true);
    }

    public void resetPlayerDisplayName(Player player) {
        player.displayName(Component.text(player.getName()));
        player.playerListName(Component.text(player.getName()));
        player.customName(null);
        player.setCustomNameVisible(false);
    }

    public void clearTeams() {
        playerTeams.clear();
    }

    public Map<UUID, TeamType> getPlayerTeams() {
        return playerTeams;
    }
}
