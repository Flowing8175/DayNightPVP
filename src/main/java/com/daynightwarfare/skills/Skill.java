package com.daynightwarfare.skills;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.TeamType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class Skill implements Listener {

    protected final DayNightPlugin plugin;
    protected final GameManager gameManager;
    protected final MiniMessage miniMessage;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private final String id;
    private final String name;
    private final List<String> lore;
    private final TeamType teamType;
    private final Material material;
    private final long cooldown; // In seconds

    public Skill(String id, String name, List<String> lore, TeamType teamType, Material material, long cooldown) {
        this.plugin = DayNightPlugin.getInstance();
        this.gameManager = GameManager.getInstance();
        this.miniMessage = MiniMessage.miniMessage();
        this.id = id;
        this.name = name;
        this.lore = lore;
        this.teamType = teamType;
        this.material = material;
        this.cooldown = cooldown;
    }

    public String getId() {
        return id;
    }

    public abstract void execute(Player player);

    public boolean canUse(Player player) {
        if (!gameManager.isGameInProgress() || gameManager.isGracePeriodActive()) {
            player.sendMessage(miniMessage.deserialize("<red>지금은 스킬을 사용할 수 없습니다.</red>"));
            return false;
        }
        if (gameManager.getTeamManager().getPlayerTeam(player) != teamType) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>이 스킬은 <team> 팀만 사용할 수 있습니다.</red>",
                    Placeholder.component("team", teamType.getStyledDisplayName())
            ));
            return false;
        }
        return isCooldownFinished(player);
    }

    public boolean isCooldownFinished(Player player) {
        if (cooldowns.containsKey(player.getUniqueId())) {
            long expires = cooldowns.get(player.getUniqueId());
            if (System.currentTimeMillis() < expires) {
                long timeLeft = (expires - System.currentTimeMillis()) / 1000;
                player.sendMessage(miniMessage.deserialize("<red>해당 스킬은 " + timeLeft + "초 후에 사용할 수 있습니다.</red>"));
                return false;
            }
        }
        return true;
    }

    public void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldown * 1000L));
    }

    public ItemStack createSkillItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));

        List<Component> loreComponents = lore.stream()
                .map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false))
                .collect(Collectors.toList());
        meta.lore(loreComponents);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        NamespacedKey key = new NamespacedKey(plugin, "skill_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }
}
