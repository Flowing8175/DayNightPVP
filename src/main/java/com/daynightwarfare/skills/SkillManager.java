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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

public class SkillManager implements Listener {
    private final DayNightPlugin plugin;
    private final Map<String, Skill> skills = new HashMap<>();
    private final NamespacedKey skillIdKey;

    public SkillManager(DayNightPlugin plugin) {
        this.plugin = plugin;
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
                skill.execute(player);
                skill.setCooldown(player);
            }
        }
    }
}
