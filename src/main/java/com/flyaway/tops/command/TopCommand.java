package com.flyaway.tops.command;

import com.flyaway.tops.TopsPlugin;
import com.flyaway.tops.config.ConfigManager;
import com.flyaway.tops.gui.TopGUI;
import com.flyaway.tops.manager.TopManager;
import com.flyaway.tops.rewards.WeeklyRewardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TopCommand implements CommandExecutor {

    private final TopsPlugin plugin;
    private final TopManager topManager;
    private final ConfigManager configManager;
    private final WeeklyRewardManager rewardManager;

    public TopCommand(TopsPlugin plugin, TopManager topManager, ConfigManager configManager, WeeklyRewardManager rewardManager) {
        this.plugin = plugin;
        this.topManager = topManager;
        this.configManager = configManager;
        this.rewardManager = rewardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Команда /tops - открыть GUI
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cЭта команда только для игроков!");
                return true;
            }

            TopGUI gui = new TopGUI(player, topManager, configManager);
            gui.open();
            return true;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reload":
                    // Команда /tops reload - перезагрузка конфига и кэша
                    if (!sender.hasPermission("topssystem.reload")) {
                        sender.sendMessage("§cУ вас нет прав для перезагрузки!");
                        return true;
                    }
                    plugin.reloadConfig();
                    configManager.loadConfig();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        topManager.forceUpdateCache();
                    });
                    sender.sendMessage("§aКонфигурация и кэш топов успешно перезагружены!");
                    return true;

                case "rewards":
                    // Команда /tops rewards - принудительная выдача наград
                    if (!sender.hasPermission("topssystem.rewards")) {
                        sender.sendMessage("§cУ вас нет прав для выдачи наград!");
                        return true;
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        rewardManager.forceDistributeRewards();
                    });
                    sender.sendMessage("§aЕженедельные награды выданы принудительно!");
                    return true;
            }
        }

        // Неизвестные аргументы
        sender.sendMessage("§cИспользование: /tops [reload|rewards]");
        return true;
    }
}
