package com.daynightwarfare;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

        // TODO: Add logic to clear potion effects and reset other player states.

        setState(GameState.WAITING);
        Bukkit.broadcast(Component.text("게임이 강제 종료되어 초기화되었습니다.", NamedTextColor.RED));
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
            player.sendMessage(Component.text("당신은 ", NamedTextColor.GRAY).append(team.getStyledDisplayName()).append(Component.text(" 팀입니다.")));
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
                Component actionBarMessage = Component.text("무적 시간 : ", NamedTextColor.AQUA)
                        .append(Component.text(formattedTime, NamedTextColor.YELLOW));

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar(actionBarMessage);
                }

                if (shouldAnnounce(remainingSeconds)) {
                    Component broadcastMessage = Component.text("무적 시간 ", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .append(Component.text("종료까지 ", NamedTextColor.YELLOW))
                            .append(Component.text(formattedTime, NamedTextColor.AQUA))
                            .append(Component.text(" 남았습니다!", NamedTextColor.YELLOW));
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
        return String.format("%d분 %02d초", minutes, seconds);
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
                Component.text("무적 시간이 종료되었습니다!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("FIGHT!", NamedTextColor.YELLOW)
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
                    List<Component> messages = new ArrayList<>();
                    messages.add(Component.text("----------생존자 위치----------", NamedTextColor.WHITE, TextDecoration.BOLD));

                    List<Player> sortedPlayers = alivePlayers.stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(p -> getPlayerTeam(p).ordinal()))
                            .collect(Collectors.toList());

                    for (Player player : sortedPlayers) {
                        TeamType team = getPlayerTeam(player);
                        Location loc = player.getLocation();
                        Component message = Component.text()
                                .append(team.getStyledDisplayName())
                                .append(Component.text("팀 ", team.getDisplayStyle()))
                                .append(Component.text(player.getName(), team.getDisplayStyle()))
                                .append(Component.text("님의 위치: ", NamedTextColor.WHITE))
                                .append(Component.text(String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), NamedTextColor.WHITE))
                                .build();
                        messages.add(message);
                    }

                    messages.add(Component.text("----------------------------", NamedTextColor.WHITE, TextDecoration.BOLD));
                    messages.forEach(Bukkit::broadcast);
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
