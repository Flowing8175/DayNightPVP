package com.daynightwarfare.skills;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.list.AfterglowSkill;
import com.daynightwarfare.skills.list.MoonsChainSkill;
import com.daynightwarfare.skills.list.ShadowDashSkill;
import com.daynightwarfare.skills.list.ShadowWingsSkill;
import com.daynightwarfare.skills.list.SolarFlareSkill;
import com.daynightwarfare.skills.list.SunsSpearSkill;
import com.daynightwarfare.skills.list.StealthSkill;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillManager implements Listener {
    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final Map<String, Skill> skills = new HashMap<>();
    private final NamespacedKey skillIdKey;
    private final Map<UUID, Long> sneakingPlayers = new ConcurrentHashMap<>();
    private final StealthSkill stealthSkill;


    public SkillManager(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.skillIdKey = new NamespacedKey(plugin, "skill_id");
        this.stealthSkill = new StealthSkill();
        registerSkills();
        startSneakChecker();
    }

    private void registerSkills() {
        addSkill(new SolarFlareSkill());
        addSkill(new SunsSpearSkill());
        addSkill(new AfterglowSkill());
        addSkill(new ShadowDashSkill());
        addSkill(new MoonsChainSkill());
        addSkill(new ShadowWingsSkill());
        addSkill(stealthSkill);
    }

    private void addSkill(Skill skill) {
        skills.put(skill.getId(), skill);
        if (skill.getMaterial() != null) { // Only register events for skills with items
            plugin.getServer().getPluginManager().registerEvents(skill, plugin);
        }
    }

    private void startSneakChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (sneakingPlayers.isEmpty()) {
                    return;
                }

                long currentTime = System.currentTimeMillis();
                sneakingPlayers.forEach((uuid, startTime) -> {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player == null || !player.isOnline() || !player.isSneaking()) {
                        sneakingPlayers.remove(uuid);
                        return;
                    }

                    if (currentTime - startTime >= 3000) {
                        if (stealthSkill.canUse(player)) {
                            if (stealthSkill.execute(player)) {
                                stealthSkill.setCooldown(player);
                            }
                        }
                        sneakingPlayers.remove(uuid);
                    }
                });
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (gameManager.getTeamManager().getPlayerTeam(player) != TeamType.APOSTLE_OF_LIGHT) {
            return;
        }

        if (event.isSneaking()) {
            sneakingPlayers.put(player.getUniqueId(), System.currentTimeMillis());
        } else {
            sneakingPlayers.remove(player.getUniqueId());
        }
    }

    public Skill getSkill(String id) {
        return skills.get(id);
    }

    public Map<String, Skill> getSkills() {
        return skills;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        String skillId = item.getItemMeta().getPersistentDataContainer().get(skillIdKey, PersistentDataType.STRING);
        if (skillId == null) {
            return;
        }

        Skill skill = getSkill(skillId);
        if (skill != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (skill.canUse(player)) {
                if (skill.execute(player)) {
                    skill.setCooldown(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        if (item.getItemMeta().getPersistentDataContainer().has(skillIdKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    public void cleanUpAllSkills() {
        for (Skill skill : skills.values()) {
            skill.cleanUp();
        }
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        ItemStack item = event.getItem().getItemStack();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        String skillId = item.getItemMeta().getPersistentDataContainer().get(skillIdKey, PersistentDataType.STRING);
        if (skillId == null) {
            return;
        }

        Skill skill = getSkill(skillId);
        if (skill != null) {
            Player player = (Player) event.getEntity();
            TeamType playerTeam = gameManager.getTeamManager().getPlayerTeam(player);
            if (skill.getTeamType() != playerTeam) {
                event.setCancelled(true);
            }
        }
    }
}
