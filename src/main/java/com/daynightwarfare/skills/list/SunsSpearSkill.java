package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
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
            player.sendMessage(miniMessage.deserialize("<gray>빛이 충분히 모이지 않아 온전한 창을 만들 수 없습니다.</gray>"));
        }

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.0f);
        Vector velocity = player.getEyeLocation().getDirection().multiply(velocityMultiplier);
        Trident spear = player.launchProjectile(Trident.class, velocity);
        spear.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        spear.setGlowing(true);
        spear.getPersistentDataContainer().set(sunsSpearKey, PersistentDataType.STRING, player.getUniqueId().toString());

        if (isNight) {
            spear.getPersistentDataContainer().set(new NamespacedKey(plugin, "suns_spear_night"), PersistentDataType.BYTE, (byte) 1);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!spear.isValid() || spear.isOnGround()) {
                    this.cancel();
                    return;
                }
                spear.getWorld().spawnParticle(Particle.FLAME, spear.getLocation(), 5, 0.1, 0.1, 0.1, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident spear)) return;
        if (!spear.getPersistentDataContainer().has(sunsSpearKey, PersistentDataType.STRING)) return;

        boolean removeSpear = true;

        if (event.getHitEntity() instanceof LivingEntity target) {
            double damage = 4.0;
            int fireTicks = 60;
            NamespacedKey nightKey = new NamespacedKey(plugin, "suns_spear_night");
            if (spear.getPersistentDataContainer().has(nightKey, PersistentDataType.BYTE)) {
                damage = 2.0;
                fireTicks = 10;
            }
            target.damage(damage);
            target.setFireTicks(fireTicks);
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            target.setVelocity(spear.getVelocity().multiply(0.2).setY(0.2));
        } else if (event.getHitBlock() != null) {
            Block hitBlock = event.getHitBlock();
            Block lightBlock = hitBlock.getRelative(event.getHitBlockFace());
            Material hitBlockType = lightBlock.getType();
            if (hitBlockType == Material.SNOW ||
                hitBlockType == Material.SHORT_GRASS ||
                hitBlockType == Material.TALL_GRASS ||
                hitBlockType == Material.FERN ||
                hitBlockType == Material.LARGE_FERN ||
                hitBlockType == Material.DEAD_BUSH ||
                hitBlockType == Material.VINE ||
                hitBlockType == Material.LILY_PAD ||
                hitBlockType == Material.SUGAR_CANE ||
                hitBlockType == Material.WATER ||
                hitBlockType == Material.LAVA ||
                hitBlockType == Material.AIR) {
                lightBlock.setType(Material.LIGHT);
                removeSpear = false;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (lightBlock.getType() == Material.LIGHT) {
                            lightBlock.setType(Material.AIR);
                        }
                        if (spear.isValid()) {
                            spear.remove();
                        }
                    }
                }.runTaskLater(plugin, 60L);
            }
        }

        if (removeSpear && spear.isValid()) {
            spear.remove();
        }
    }
}
