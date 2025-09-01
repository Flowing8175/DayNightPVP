package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;

public class SunsSpearSkill extends Skill {

    private final NamespacedKey sunsSpearKey;

    public SunsSpearSkill() {
        super(
                "suns-spear",
                "<gold>태양의 창</gold>",
                Arrays.asList("<gray>우클릭하여 빛의 창을 던집니다.</gray>"),
                TeamType.APOSTLE_OF_LIGHT,
                Material.GOLDEN_SWORD,
                20L
        );
        this.sunsSpearKey = new NamespacedKey(plugin, "suns_spear_id");
    }

    @Override
    public void execute(Player player) {
        Vector velocity = player.getEyeLocation().getDirection().multiply(3);
        Trident spear = player.launchProjectile(Trident.class, velocity);
        spear.setGlowing(true);
        spear.getPersistentDataContainer().set(sunsSpearKey, PersistentDataType.STRING, player.getUniqueId().toString());
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident spear)) return;
        if (!spear.getPersistentDataContainer().has(sunsSpearKey, PersistentDataType.STRING)) return;

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(6.0);
            target.setFireTicks(40);
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
        } else if (event.getHitBlock() != null) {
            Block hitBlock = event.getHitBlock();
            Block lightBlock = hitBlock.getRelative(event.getHitBlockFace());
            if (lightBlock.getType() == Material.AIR) {
                lightBlock.setType(Material.LIGHT);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (lightBlock.getType() == Material.LIGHT) {
                            lightBlock.setType(Material.AIR);
                        }
                    }
                }.runTaskLater(plugin, 60L);
            }
        }
        if (spear.isValid()) {
            spear.remove();
        }
    }
}
