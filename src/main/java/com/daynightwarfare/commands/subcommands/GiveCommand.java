package com.daynightwarfare.commands.subcommands;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.skills.Skill;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GiveCommand implements SubCommand {
    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GiveCommand(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public String getPermission() {
        return "daynight.admin.give";
    }

    @Override
    public void execute(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize("<red>This command can only be run by a player.</red>"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>사용법: /game give <skill_id></red>"));
            return;
        }

        Player player = (Player) sender;
        String skillId = args[1].toLowerCase();

        ItemStack skillItem = gameManager.createSkillItem(skillId);

        if (skillItem == null) {
            sender.sendMessage(miniMessage.deserialize("<red>알 수 없는 스킬 ID입니다.</red>"));
            return;
        }

        player.getInventory().addItem(skillItem);
        sender.sendMessage(miniMessage.deserialize("<green>" + skillId + " 아이템을 받았습니다.</green>"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, String[] args) {
        if (args.length == 2) {
            return plugin.getSkillManager().getSkills().values().stream()
                    .map(Skill::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
