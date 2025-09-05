package com.daynightwarfare;

import com.daynightwarfare.listeners.GameListener;
import com.daynightwarfare.managers.PlayerManager;
import com.daynightwarfare.managers.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Set;


public class GameManager {

    private static GameManager instance;

    private final DayNightPlugin plugin;
    private final TeamManager teamManager;
    private final PlayerManager playerManager;

    private GameState state;
    private BukkitTask gracePeriodTask;
    private BukkitTask survivorLocatorTask;
    private long gracePeriodEndTime;
    private Location lightTeamBaseLocation;
    private Location moonTeamBaseLocation;

    private GameManager() {
        this.plugin = DayNightPlugin.getInstance();
        this.teamManager = new TeamManager();
        this.playerManager = new PlayerManager(plugin, this, teamManager);
        this.state = GameState.WAITING;
    }

    public static synchronized GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    //<editor-fold desc="State Management">
    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public boolean isGameInProgress() {
        return state == GameState.IN_GAME || state == GameState.GRACE_PERIOD || state == GameState.COUNTDOWN;
    }

    public boolean isGracePeriodActive() {
        return state == GameState.GRACE_PERIOD;
    }
    //</editor-fold>

    public void resetGame(boolean forced) {
        if (gracePeriodTask != null) gracePeriodTask.cancel();
        if (survivorLocatorTask != null) survivorLocatorTask.cancel();

        gracePeriodTask = null;
        survivorLocatorTask = null;

        if (plugin.getSkillManager() != null) {
            plugin.getSkillManager().cleanUpAllSkills();
        }

        if (GameListener.getInstance() != null) {
            GameListener.getInstance().reset();
        }

        teamManager.clearTeams();
        playerManager.clearPlayers();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            teamManager.resetPlayerDisplayName(player);
        }

        setState(GameState.WAITING);
        if (forced) {
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<red>게임이 강제 종료되어 초기화되었습니다.</red>"));
        }
    }

    public void assignTeamsAndTeleport() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        teamManager.assignTeams(onlinePlayers);
        for (Player player : onlinePlayers) {
            playerManager.addPlayer(player);
        }
        teleportPlayers();
    }

    public void teleportPlayers() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double size = border.getSize();

        double cornerX = size / 2.0;
        double cornerZ = size / 2.0;

        this.lightTeamBaseLocation = center.clone().add(cornerX * 0.75, 0, cornerZ * 0.75);
        this.moonTeamBaseLocation = center.clone().add(-cornerX * 0.75, 0, -cornerZ * 0.75);

        Random random = new Random();

        for (UUID playerUUID : teamManager.getPlayerTeams().keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null) continue;

            TeamType team = teamManager.getPlayerTeam(player);
            Location base = (team == TeamType.APOSTLE_OF_LIGHT) ? this.lightTeamBaseLocation : this.moonTeamBaseLocation;

            int offsetX = random.nextInt(31) - 15;
            int offsetZ = random.nextInt(31) - 15;

            double finalX = base.getX() + offsetX;
            double finalZ = base.getZ() + offsetZ;

            Block safeBlock = getSafeHighestBlock(world, (int) finalX, (int) finalZ);
            if (safeBlock != null) {
                Location tpLocation = safeBlock.getLocation().add(0.5, 1.5, 0.5);
                player.teleportAsync(tpLocation);
                player.setBedSpawnLocation(tpLocation, true);
            } else {
                player.teleportAsync(world.getSpawnLocation());
                Bukkit.getLogger().warning("Could not find a safe teleport spot for " + player.getName());
            }
        }
    }

    public Block getSafeHighestBlock(World world, int x, int z) {
        for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();

            if (type == Material.WATER) return block;
            if (type.isSolid() && !type.name().endsWith("_LEAVES") && !type.name().endsWith("_LOG")) {
                Block oneAbove = world.getBlockAt(x, y + 1, z);
                Block twoAbove = world.getBlockAt(x, y + 2, z);
                if (!oneAbove.getType().isOccluding() && !twoAbove.getType().isOccluding()) {
                    return block;
                }
            }
        }
        return null;
    }


    public void startGracePeriod(long minutes) {
        setState(GameState.GRACE_PERIOD);
        this.gracePeriodEndTime = System.currentTimeMillis() + (minutes * 60 * 1000);
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<aqua>무적 시간이 " + minutes + "분으로 설정되었습니다.</aqua>"));

        this.gracePeriodTask = new BukkitRunnable() {
            @Override
            public void run() {
                long remainingMillis = gracePeriodEndTime - System.currentTimeMillis();
                if (remainingMillis <= 0) {
                    endGracePeriod();
                    return;
                }

                long remainingSeconds = remainingMillis / 1000;
                String formattedTime = formatTime(remainingSeconds);
                Component actionBarMessage = MiniMessage.miniMessage().deserialize("<aqua>무적 시간 : <yellow>" + formattedTime + "</yellow></aqua>");

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar(actionBarMessage);
                }

                if (shouldAnnounce(remainingSeconds)) {
                    String announceTime = (remainingSeconds >= 60) ? (remainingSeconds/60) + "분" : remainingSeconds + "초";
                    Component broadcastMessage = MiniMessage.miniMessage().deserialize("<bold><aqua>무적 시간 <yellow>종료까지 <aqua>" + announceTime + "<yellow> 남았습니다!</bold>");
                    Bukkit.broadcast(broadcastMessage);
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 2.0f);
                    }
                }
            }
        }.runTaskTimer(DayNightPlugin.getInstance(), 0L, 20L);
    }

    private String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private boolean shouldAnnounce(long seconds) {
        return seconds == 600 || seconds == 300 || seconds == 60 || seconds == 30 || seconds == 10 || (seconds <= 3 && seconds > 0);
    }

    public void endGracePeriod() {
        if (!isGracePeriodActive()) return;

        if (gracePeriodTask != null) {
            gracePeriodTask.cancel();
            gracePeriodTask = null;
        }
        setState(GameState.IN_GAME);

        Title title = Title.title(
                MiniMessage.miniMessage().deserialize("<bold><red>무적 시간이 종료되었습니다!</red></bold>"),
                MiniMessage.miniMessage().deserialize("<yellow>FIGHT!</yellow>")
        );

        for (UUID playerUUID : teamManager.getPlayerTeams().keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.0f);
            }
        }
        startSurvivorLocator();
    }

    public void startSurvivorLocator() {
        long interval = plugin.getConfig().getLong("survivor-locator.check-interval-minutes", 2) * 60 * 20;
        int triggerCount = plugin.getConfig().getInt("survivor-locator.trigger-player-count", 3);

        this.survivorLocatorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (getState() != GameState.IN_GAME) {
                    this.cancel();
                    return;
                }

                if (playerManager.getAlivePlayers().size() <= triggerCount && playerManager.getAlivePlayers().size() > 0) {
                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<bold><white>--------생존자 위치--------</white></bold>"));

                    List<Player> sortedPlayers = playerManager.getAlivePlayers().stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(p -> teamManager.getPlayerTeam(p).ordinal()))
                            .collect(Collectors.toList());

                    for (Player player : sortedPlayers) {
                        TeamType team = teamManager.getPlayerTeam(player);
                        Location loc = player.getLocation();
                        String teamColor = team == TeamType.APOSTLE_OF_LIGHT ? "<yellow>" : "<aqua>";
                        String message = teamColor + "<bold>" + team.getDisplayName() + "팀 " + player.getName() + "</bold>님의 위치: <white>"
                                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</white>";
                        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(message));
                    }

                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<bold><white>----------------------------</white></bold>"));
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }


    public void addGracePeriodTime(long minutes) {
        if (isGracePeriodActive()) {
            this.gracePeriodEndTime += minutes * 60 * 1000;
        }
    }

    public void subtractGracePeriodTime(long minutes) {
        if (isGracePeriodActive()) {
            this.gracePeriodEndTime -= minutes * 60 * 1000;
            if (this.gracePeriodEndTime <= System.currentTimeMillis()) {
                endGracePeriod();
            }
        }
    }

    public void supplySkillItems() {
        for (UUID playerUUID : teamManager.getPlayerTeams().keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                supplySkillItems(player);
            }
        }
    }

    public void supplySkillItems(Player player) {
        String[] sunSkills = {"solar-flare", "suns-spear", "afterglow", "mirror-dash"};
        String[] moonSkills = {"moons-chain", "shadow-wings", "shadow-dash"};

        player.getInventory().clear();
        TeamType team = teamManager.getPlayerTeam(player);
        if (team == null) return;

        String[] skillsToGive = (team == TeamType.APOSTLE_OF_LIGHT) ? sunSkills : moonSkills;

        for (String skillId : skillsToGive) {
            ItemStack skillItem = createSkillItem(skillId);
            if (skillItem != null) {
                player.getInventory().addItem(skillItem);
            }
        }
    }

    public ItemStack createSkillItem(String skillId) {
        if (plugin.getSkillManager() != null) {
            com.daynightwarfare.skills.Skill skill = plugin.getSkillManager().getSkill(skillId);
            if (skill != null) {
                return skill.createSkillItem();
            }
        }
        return null;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public Location getLightTeamBaseLocation() {
        return lightTeamBaseLocation;
    }

    public Location getMoonTeamBaseLocation() {
        return moonTeamBaseLocation;
    }

    public void checkWinCondition() {
        if (state != GameState.IN_GAME) {
            return;
        }

        Set<UUID> alivePlayers = playerManager.getAlivePlayers();
        if (alivePlayers.isEmpty()) {
            Title title = Title.title(
                    MiniMessage.miniMessage().deserialize("<gold>무승부</gold>"),
                    MiniMessage.miniMessage().deserialize("<gray>생존자가 없습니다!</gray>")
            );
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            resetGame(false);
            return;
        }

        TeamType winningTeam = null;
        boolean mixedTeams = false;
        for (UUID playerUUID : alivePlayers) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null) continue;

            TeamType team = teamManager.getPlayerTeam(player);
            if (winningTeam == null) {
                winningTeam = team;
            } else if (winningTeam != team) {
                mixedTeams = true;
                break;
            }
        }

        if (!mixedTeams && winningTeam != null) {
            Title title = Title.title(
                    winningTeam.getStyledDisplayName().append(Component.text(" 팀 승리!")),
                    MiniMessage.miniMessage().deserialize("<gray>게임이 종료되었습니다.</gray>")
            );
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            resetGame(false);
        }
    }
}
