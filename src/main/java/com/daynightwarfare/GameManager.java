package com.daynightwarfare;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private static GameManager instance;

    private GameState state;
    private final Map<UUID, TeamType> playerTeams;
    private final Set<UUID> alivePlayers;

    public BukkitTask gracePeriodTask;
    public BukkitTask survivorLocatorTask;
    public long gracePeriodEndTime;

    private GameManager() {
        this.state = GameState.WAITING;
        this.playerTeams = new HashMap<>();
        this.alivePlayers = new HashSet<>();
    }

    public static synchronized GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    //<editor-fold desc="State and Team Management">
    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public Map<UUID, TeamType> getPlayerTeams() {
        return playerTeams;
    }

    public void setPlayerTeam(Player player, TeamType team) {
        playerTeams.put(player.getUniqueId(), team);
    }

    public TeamType getPlayerTeam(Player player) {
        return playerTeams.get(player.getUniqueId());
    }

    public Set<UUID> getAlivePlayers() {
        return alivePlayers;
    }

    public boolean isGameInProgress() {
        return state == GameState.IN_GAME || state == GameState.GRACE_PERIOD || state == GameState.COUNTDOWN;
    }

    public boolean isGracePeriodActive() {
        return state == GameState.GRACE_PERIOD;
    }
    //</editor-fold>

    public void resetGame() {
        if (gracePeriodTask != null) gracePeriodTask.cancel();
        if (survivorLocatorTask != null) survivorLocatorTask.cancel();

        gracePeriodTask = null;
        survivorLocatorTask = null;

        playerTeams.clear();
        alivePlayers.clear();

        setState(GameState.WAITING);
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<red>게임이 강제 종료되어 초기화되었습니다.</red>"));
    }

    public void assignTeams() {
        playerTeams.clear();
        alivePlayers.clear();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            TeamType team = (i % 2 == 0) ? TeamType.APOSTLE_OF_LIGHT : TeamType.APOSTLE_OF_MOON;
            setPlayerTeam(player, team);
            alivePlayers.add(player.getUniqueId());
            String teamColor = team == TeamType.APOSTLE_OF_LIGHT ? "<yellow>" : "<aqua>";
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>당신은 " + teamColor + team.getDisplayName() + "</color> 팀입니다.</gray>"));
        }
    }

    public void teleportPlayers() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double size = border.getSize();

        double cornerX = size / 2.0;
        double cornerZ = size / 2.0;

        Location lightTeamBase = center.clone().add(cornerX * 0.75, 0, cornerZ * 0.75);
        Location moonTeamBase = center.clone().add(-cornerX * 0.75, 0, -cornerZ * 0.75);

        Random random = new Random();

        for (UUID playerUUID : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null) continue;

            TeamType team = getPlayerTeam(player);
            Location base = (team == TeamType.APOSTLE_OF_LIGHT) ? lightTeamBase : moonTeamBase;

            int offsetX = random.nextInt(21) - 10;
            int offsetZ = random.nextInt(21) - 10;

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

    private Block getSafeHighestBlock(World world, int x, int z) {
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
                    endGracePeriod(); // This will cancel the task
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

        for (UUID playerUUID : getPlayerTeams().keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.0f);
            }
        }
        startSurvivorLocator();
    }

    public void startSurvivorLocator() {
        DayNightPlugin plugin = DayNightPlugin.getInstance();
        long interval = plugin.getConfig().getLong("survivor-locator.check-interval-minutes", 2) * 60 * 20;
        int triggerCount = plugin.getConfig().getInt("survivor-locator.trigger-player-count", 3);

        this.survivorLocatorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (getState() != GameState.IN_GAME) {
                    this.cancel();
                    return;
                }

                if (alivePlayers.size() <= triggerCount && alivePlayers.size() > 0) {
                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<bold><white>--------생존자 위치--------</white></bold>"));

                    List<Player> sortedPlayers = alivePlayers.stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(p -> getPlayerTeam(p).ordinal()))
                            .collect(Collectors.toList());

                    for (Player player : sortedPlayers) {
                        TeamType team = getPlayerTeam(player);
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
}
