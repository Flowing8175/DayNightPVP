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
    public boolean execute(Player player) {
        boolean isNight = !player.getWorld().isDayTime();
        double velocityMultiplier = isNight ? 1.5 : 3.0;

        if (isNight) {
            player.sendMessage("빛이 충분히 모이지 않아 순수한 창을 만들기 어렵습니다..");
        }

        Vector velocity = player.getEyeLocation().getDirection().multiply(velocityMultiplier);
        Trident spear = player.launchProjectile(Trident.class, velocity);
        spear.setGlowing(true);
        spear.getPersistentDataContainer().set(sunsSpearKey, PersistentDataType.STRING, player.getUniqueId().toString());

        if (isNight) {
            spear.getPersistentDataContainer().set(new NamespacedKey(plugin, "suns_spear_night"), PersistentDataType.BYTE, (byte) 1);
        }

        return true;
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident spear)) return;
        if (!spear.getPersistentDataContainer().has(sunsSpearKey, PersistentDataType.STRING)) return;

        if (event.getHitEntity() instanceof LivingEntity target) {
            double damage = 6.0;
            NamespacedKey nightKey = new NamespacedKey(plugin, "suns_spear_night");
            if (spear.getPersistentDataContainer().has(nightKey, PersistentDataType.BYTE)) {
                damage = 3.0;
            }
            target.damage(damage);
            target.setFireTicks(40);
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            target.setVelocity(spear.getVelocity().multiply(0.2).setY(0.2));
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
