package com.daynightwarfare.skills;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.skills.list.AfterglowSkill;
import com.daynightwarfare.skills.list.MirrorDashSkill;
import com.daynightwarfare.skills.list.MoonsChainSkill;
import com.daynightwarfare.skills.list.ShadowWingsSkill;
import com.daynightwarfare.skills.list.SolarFlareSkill;
import com.daynightwarfare.skills.list.SunsSpearSkill;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.TeamType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

public class SkillManager implements Listener {
    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final Map<String, Skill> skills = new HashMap<>();
    private final NamespacedKey skillIdKey;

    public SkillManager(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.skillIdKey = new NamespacedKey(plugin, "skill_id");
        registerSkills();
    }

    private void registerSkills() {
        addSkill(new SolarFlareSkill());
        addSkill(new SunsSpearSkill());
        addSkill(new AfterglowSkill());
        addSkill(new MirrorDashSkill());
        addSkill(new MoonsChainSkill());
        addSkill(new ShadowWingsSkill());
    }

    private void addSkill(Skill skill) {
        skills.put(skill.getId(), skill);
        plugin.getServer().getPluginManager().registerEvents(skill, plugin);
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
