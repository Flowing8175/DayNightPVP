package com.daynightwarfare;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private static GameManager instance;

    private GameState state;
    private final Map<UUID, TeamType> playerTeams;
    private final Map<UUID, TeamType> pinnedPlayers;
    private final Set<UUID> alivePlayers;

    public BukkitTask gracePeriodTask;
    public BukkitTask survivorLocatorTask;
    public long gracePeriodEndTime;

    private GameManager() {
        this.state = GameState.WAITING;
        this.playerTeams = new HashMap<>();
        this.pinnedPlayers = new HashMap<>();
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

    public void setPlayerPin(UUID uuid, TeamType team) {
        if (team == null) {
            pinnedPlayers.remove(uuid);
        } else {
            pinnedPlayers.put(uuid, team);
        }
    }

    public void assignTeams() {
        playerTeams.clear();
        alivePlayers.clear();

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Player> unassignedPlayers = new ArrayList<>();

        int lightTeamCount = 0;
        int moonTeamCount = 0;

        // First, assign pinned players who are online
        for (Player player : onlinePlayers) {
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

        // Shuffle and assign the rest to balance the teams
        Collections.shuffle(unassignedPlayers);
        for (Player player : unassignedPlayers) {
            TeamType team = (lightTeamCount <= moonTeamCount) ? TeamType.APOSTLE_OF_LIGHT : TeamType.APOSTLE_OF_MOON;
            setPlayerTeam(player, team);
            if (team == TeamType.APOSTLE_OF_LIGHT) lightTeamCount++;
            else moonTeamCount++;
        }

        // Finalize assignments and send messages
        for(Player player : onlinePlayers) {
            alivePlayers.add(player.getUniqueId());
            TeamType team = getPlayerTeam(player);
            String teamColor = team == TeamType.APOSTLE_OF_LIGHT ? "yellow" : "aqua";
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>당신은 <" + teamColor + ">" + team.getDisplayName() + "</" + teamColor + "> 팀입니다.</gray>"));
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

    public void supplySkillItems() {
        String[] sunSkills = {"solar-flare", "suns-spear", "afterglow", "mirror-dash"};
        String[] moonSkills = {"moons-chain", "shadow-wings", "moon-smash"};

        for (UUID playerUUID : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null) continue;

            player.getInventory().clear();
            TeamType team = getPlayerTeam(player);
            String[] skillsToGive = (team == TeamType.APOSTLE_OF_LIGHT) ? sunSkills : moonSkills;

            for (String skillId : skillsToGive) {
                ItemStack skillItem = createSkillItem(skillId);
                if (skillItem != null) {
                    player.getInventory().addItem(skillItem);
                }
            }
        }
    }

    private ItemStack createSkillItem(String skillId) {
        Material material;
        String name;
        List<String> lore = new ArrayList<>();
        DayNightPlugin plugin = DayNightPlugin.getInstance();

        switch (skillId) {
            case "solar-flare":
                material = Material.GLOWSTONE_DUST; name = "<gold>Solar Flare</gold>"; lore.add("<gray>Right-click to unleash a blinding light.</gray>"); break;
            case "suns-spear":
                material = Material.GOLDEN_SWORD; name = "<gold>Sun's Spear</gold>"; lore.add("<gray>Right-click to throw a spear of light.</gray>"); break;
            case "afterglow":
                material = Material.TORCH; name = "<gold>Afterglow</gold>"; lore.add("<gray>Right-click to burn nearby enemies.</gray>"); break;
            case "mirror-dash":
                material = Material.GLASS_PANE; name = "<gold>Mirror Dash</gold>"; lore.add("<gray>Right-click to dash behind an enemy.</gray>"); break;
            case "moons-chain":
                material = Material.FLOWER_BANNER_PATTERN; name = "<aqua>Moon's Chain</aqua>"; lore.add("<gray>Right-click to chain nearby enemies.</gray>"); break;
            case "moon-smash":
                material = Material.NETHER_STAR; name = "<aqua>Moon Smash</aqua>"; lore.add("<gray>Sneak while gliding to smash the ground.</gray>"); break;
            default:
                return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> loreComponents = lore.stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false))
                .collect(Collectors.toList());
        meta.lore(loreComponents);
        NamespacedKey key = new NamespacedKey(plugin, "skill_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, skillId);
        item.setItemMeta(meta);
        return item;
    }
}
