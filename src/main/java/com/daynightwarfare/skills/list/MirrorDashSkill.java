package com.daynightwarfare.skills.list;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Arrays;

public class MirrorDashSkill extends Skill {
    public MirrorDashSkill() {
        super(
                "mirror-dash",
                "<gold>거울 잔상</gold>",
                Arrays.asList("<gray>우클릭하여 적의 뒤로 돌진합니다.</gray>"),
                TeamType.APOSTLE_OF_LIGHT,
                Material.GLASS_PANE,
                25L
        );
    }

    @Override
    public boolean execute(Player player) {
        final Location originalLocation = player.getLocation().clone();

        Vector playerDirection = player.getEyeLocation().getDirection();
        playerDirection.setY(0).normalize();

        Player bestTarget = null;
        double closestDot = -1.0;
        double coneThreshold = Math.cos(Math.toRadians(22.5));

        for (Player targetPlayer : player.getWorld().getPlayers()) {
            if (targetPlayer.equals(player) || gameManager.getTeamManager().getPlayerTeam(targetPlayer) == TeamType.APOSTLE_OF_LIGHT) {
                continue;
            }
            if (targetPlayer.getLocation().distanceSquared(player.getLocation()) > 30 * 30) {
                continue;
            }

            Vector targetDirection = targetPlayer.getLocation().toVector().subtract(player.getLocation().toVector());
            targetDirection.setY(0).normalize();

            double dot = playerDirection.dot(targetDirection);

            if (dot > coneThreshold && dot > closestDot) {
                closestDot = dot;
                bestTarget = targetPlayer;
            }
        }

        if (bestTarget != null) {
            Vector targetLookDir = bestTarget.getLocation().getDirection().normalize();
            Location behind = bestTarget.getLocation().subtract(targetLookDir.multiply(3));
            behind.setDirection(bestTarget.getLocation().toVector().subtract(behind.toVector()));

            if (behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                final Player finalBestTarget = bestTarget;
                final Location finalBehind = behind;

                new BukkitRunnable() {
                    int count = 0;
                    final Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO);
                    final Component subtitle1 = miniMessage.deserialize("<yellow>[ ♥ ]</yellow>");
                    final Component subtitle2 = miniMessage.deserialize("<yellow>[</yellow><red> ♥ </red><yellow>]</yellow>");

                    @Override
                    public void run() {
                        if (count >= 4) {
                            this.cancel();
                            player.teleport(finalBehind);
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 8 * 20, 1));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 8 * 20, 0));

                            new BukkitRunnable() {
                                int ticks = 0;
                                final int duration = 6 * 20;
                                final Particle.DustOptions dustOptions = new Particle.DustOptions(Color.YELLOW, 1);

                                @Override
                                public void run() {
                                    if (ticks >= duration) {
                                        this.cancel();
                                        if (player.isOnline() && !player.isDead()) {
                                            player.teleport(originalLocation);
                                        }
                                        return;
                                    }

                                    if (!player.isOnline() || player.isDead()) {
                                        this.cancel();
                                        return;
                                    }

                                    Location currentPos = player.getLocation();
                                    Vector direction = originalLocation.toVector().subtract(currentPos.toVector());
                                    double distance = direction.length();
                                    direction.normalize();

                                    for (double d = 0; d < distance; d += 0.5) {
                                        Location particleLoc = currentPos.clone().add(direction.clone().multiply(d));
                                        player.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, dustOptions);
                                    }

                                    ticks++;
                                }
                            }.runTaskTimer(plugin, 0L, 1L);
                            return;
                        }

                        finalBestTarget.playSound(finalBestTarget.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);

                        Component subtitle = (count % 2 == 0) ? subtitle1 : subtitle2;
                        finalBestTarget.showTitle(Title.title(Component.empty(), subtitle, times));

                        count++;
                    }
                }.runTaskTimer(plugin, 0L, 10L);

                return true;
            } else {
                player.sendMessage(miniMessage.deserialize("<red>대상을 추적할 수 없습니다.</red>"));
                return false;
            }
        } else {
            player.sendMessage(miniMessage.deserialize("<red>시야에 대상이 없습니다.</red>"));
            return false;
        }
    }
}
