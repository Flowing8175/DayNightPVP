package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;

public class StealthSkill extends Skill {

    public StealthSkill() {
        super(
                "stealth",
                "<green>Stealth</green>",
                Collections.singletonList("<gray>Hold sneak for 3 seconds to gain a burst of speed and glowing.</gray>"),
                TeamType.APOSTLE_OF_LIGHT,
                null, // No item associated with this skill
                18L // Cooldown in seconds
        );
    }

    @Override
    public boolean execute(Player player) {
        // Apply Speed V for 3.5 seconds (70 ticks)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 70, 4, true, false, true));
        // Apply Glowing for 3.5 seconds (70 ticks)
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 70, 0, true, false, true));
        return true;
    }
}
