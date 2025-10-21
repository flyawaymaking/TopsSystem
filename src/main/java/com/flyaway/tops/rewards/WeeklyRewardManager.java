package com.flyaway.tops.rewards;

import com.flyaway.tops.TopsPlugin;
import com.flyaway.tops.config.ConfigManager;
import com.flyaway.tops.manager.TopManager;
import com.flyaway.tops.model.TopCategory;
import com.flyaway.tops.model.TopPlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

public class WeeklyRewardManager {
    private final TopsPlugin plugin;
    private final TopManager topManager;
    private final ConfigManager configManager;
    private BukkitTask weeklyTask;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public WeeklyRewardManager(TopsPlugin plugin, TopManager topManager) {
        this.plugin = plugin;
        this.topManager = topManager;
        this.configManager = plugin.getConfigManager();
        checkMissedRewardDistribution();
        startWeeklyRewardTask();
    }

    public void disable() {
        if (weeklyTask != null) {
            weeklyTask.cancel();
            weeklyTask = null;
        }
    }

    private void startWeeklyRewardTask() {
        this.weeklyTask = new BukkitRunnable() {
            @Override
            public void run() {
                distributeWeeklyRewards();
            }
        }.runTaskTimerAsynchronously(plugin, calculateInitialDelay(), TimeUnit.DAYS.toSeconds(7) * 20L);
    }

    private void saveLastRewardTime() {
        plugin.getConfig().set("last-reward-time", LocalDateTime.now().format(FORMATTER));
        plugin.saveConfig();
        plugin.getLogger().info("💾 Дата последней выдачи обновлена в config.yml");
    }

    private void checkMissedRewardDistribution() {
        String lastRewardStr = plugin.getConfig().getString("last-reward-time", null);
        if (lastRewardStr == null || lastRewardStr.isEmpty()) {
            plugin.getLogger().info("⏳ Награды ещё не выдавались — пропускаем проверку.");
            return;
        }

        try {
            LocalDateTime lastRewardTime = LocalDateTime.parse(lastRewardStr, FORMATTER);
            Duration since = Duration.between(lastRewardTime, LocalDateTime.now());

            if (since.toDays() >= 7) {
                plugin.getLogger().warning("⚠️ Прошло более недели с последней выдачи (" + lastRewardStr + "). Запускаем выдачу через 30 секунд!");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        distributeWeeklyRewards();
                    }
                }.runTaskLaterAsynchronously(plugin, 30 * 20L);

            } else {
                plugin.getLogger().info("✅ Последняя выдача наград была " + lastRewardStr + " (" + since.toDays() + " дн. назад)");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка чтения даты последней выдачи: " + e.getMessage());
        }
    }

    private long calculateInitialDelay() {
        String rewardTime = configManager.getRewardTime();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReward = parseRewardTime(rewardTime, now);

        // Если время уже прошло сегодня, планируем на следующую неделю
        if (nextReward.isBefore(now)) {
            nextReward = nextReward.plusWeeks(1);
        }

        long delay = java.time.Duration.between(now, nextReward).getSeconds();
        plugin.getLogger().info("Следующая выдача наград через " + delay + " секунд (" + rewardTime + ")");
        return delay * 20L; // Конвертируем в тики
    }

    private LocalDateTime parseRewardTime(String rewardTime, LocalDateTime baseTime) {
        try {
            // Формат: "DAY_OF_WEEK HH:mm" например "SUNDAY 18:00"
            String[] parts = rewardTime.split(" ");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Неверный формат времени: " + rewardTime);
            }

            DayOfWeek dayOfWeek = DayOfWeek.valueOf(parts[0].toUpperCase());
            LocalTime time = LocalTime.parse(parts[1], DateTimeFormatter.ofPattern("HH:mm"));

            return baseTime.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                          .withHour(time.getHour())
                          .withMinute(time.getMinute())
                          .withSecond(0)
                          .withNano(0);

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка парсинга времени наград: " + rewardTime + ". Использую воскресенье 18:00 по умолчанию.");
            return baseTime.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                          .withHour(18)
                          .withMinute(0)
                          .withSecond(0)
                          .withNano(0);
        }
    }

    public void distributeWeeklyRewards() {
        plugin.getLogger().info("Начало распределения еженедельных наград...");

        topManager.forceUpdateCache(); // Принудительно обновляем кэш топов перед распределением наград
        processRewardDistribution(); // Выдаём награды
    }

    private void processRewardDistribution() {
        List<RewardResult> allRewards = new ArrayList<>();

        // Получаем все категории (теперь с обновленными данными)
        for (TopCategory category : topManager.getAllCategories()) {
            List<RewardResult> categoryRewards = distributeRewardsForCategory(category);
            allRewards.addAll(categoryRewards);
        }

        topManager.afterRewardDistribution();

        // Отправляем сообщения в чат
        broadcastRewardMessages(allRewards);

        plugin.getLogger().info("Еженедельные награды распределены для " + allRewards.size() + " игроков");

        // Логируем детали распределения
        logRewardDistribution(allRewards);

        // Записываем время выдачи наград
        saveLastRewardTime();
    }

    private List<RewardResult> distributeRewardsForCategory(TopCategory category) {
        List<RewardResult> rewards = new ArrayList<>();
        List<TopPlayer> topPlayers = category.getTopPlayers();

        // Логируем информацию о категории
        plugin.getLogger().info("Обработка категории: " + category.getDisplayName() +
                               " (игроков в топе: " + topPlayers.size() + ")");

        // Используем позиции из конфига
        for (int position : configManager.getRewardPositions()) {
            int index = position - 1; // Конвертируем в индекс (1 -> 0, 2 -> 1, и т.д.)

            if (index >= 0 && index < topPlayers.size()) {
                TopPlayer topPlayer = topPlayers.get(index);
                int coins = configManager.getRewardForPosition(position);

                if (coins > 0) {
                    boolean success = giveCoinsToPlayer(topPlayer.getUuid(), coins, category, position);
                    if (success) {
                        rewards.add(new RewardResult(topPlayer, category, position, coins));
                    }
                }
            }
        }

        if (rewards.isEmpty()) {
            plugin.getLogger().info("В категории " + category.getDisplayName() + " нет игроков для награждения");
        }

        return rewards;
    }

    private boolean giveCoinsToPlayer(UUID playerUuid, int coins, TopCategory category, int position) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            String playerName = player.getName() != null ? player.getName() : "Unknown";

            // Пытаемся выдать коины через CoinsEngine с валютой из конфига
            try {
                Currency currency = CoinsEngineAPI.getCurrency(configManager.getRewardCurrency());
                if (currency != null) {
                    CoinsEngineAPI.addBalance(playerUuid, currency, coins);
                    plugin.getLogger().info("Выдано " + coins + " " + configManager.getRewardCurrency() +
                                           " игроку " + playerName +
                                           " за " + position + " место в категории " + category.getDisplayName());
                } else {
                    plugin.getLogger().warning("Валюта " + configManager.getRewardCurrency() + " не найдена в CoinsEngine!");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Не удалось выдать " + coins + " " + configManager.getRewardCurrency() +
                                         " игроку " + playerName + " через CoinsEngine: " + e.getMessage());
            }

            // Отправляем личное сообщение если игрок онлайн
            Player onlinePlayer = Bukkit.getPlayer(playerUuid);
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                String positionText = getPositionText(position);
                String message = "§d§lНаграда за топ! §fВы получили награду за " + positionText + "§f место в категории " + category.getDisplayName();
                onlinePlayer.sendMessage(message);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при выдаче награды игроку " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    private String getPositionText(int position) {
        return switch (position) {
            case 1 -> "§6первое";
            case 2 -> "§7второе";
            case 3 -> "§cтретье";
            default -> position + "-е";
        };
    }

    private void broadcastRewardMessages(List<RewardResult> rewards) {
        if (rewards.isEmpty()) {
            Bukkit.broadcastMessage("§d§l=== Еженедельные награды за топы ===");
            Bukkit.broadcastMessage("§cВ этом неделю нет игроков для награждения!");
            Bukkit.broadcastMessage("§eСледующие награды: " + configManager.getRewardTime());
            return;
        }

        // Создаем сообщение с наградами из конфига
        Bukkit.broadcastMessage("§d§l=== Еженедельные награды за топы ===");
        Bukkit.broadcastMessage("§fВыданы награды топ игрокам в каждой категории!");

        String currencySymbol = configManager.getCurrencySymbol();

        // Показываем все награды из конфига
        for (int position : configManager.getRewardPositions()) {
            int reward = configManager.getRewardForPosition(position);
            if (reward > 0) {
                String positionColor = getPositionColor(position);
                Bukkit.broadcastMessage(positionColor + "За " + position + " место§e - " + reward + currencySymbol);
            }
        }

        Bukkit.broadcastMessage("§fСледующие награды: " + configManager.getRewardTime());
    }

    private String getPositionColor(int position) {
        return switch (position) {
            case 1 -> "§6";
            case 2 -> "§7";
            case 3 -> "§c";
            default -> "§e";
        };
    }

    private void logRewardDistribution(List<RewardResult> rewards) {
        if (rewards.isEmpty()) {
            plugin.getLogger().info("Награды не были распределены - нет подходящих игроков");
            return;
        }

        // Группируем по игрокам для логирования
        Map<String, List<RewardResult>> rewardsByPlayer = new HashMap<>();
        for (RewardResult reward : rewards) {
            String playerName = reward.getPlayerName();
            rewardsByPlayer.computeIfAbsent(playerName, k -> new ArrayList<>()).add(reward);
        }

        String currencySymbol = configManager.getCurrencySymbol();
        plugin.getLogger().info("=== ДЕТАЛИ РАСПРЕДЕЛЕНИЯ НАГРАД ===");
        plugin.getLogger().info("Всего награждено игроков: " + rewardsByPlayer.size());
        plugin.getLogger().info("Всего выдано наград: " + rewards.size());
        plugin.getLogger().info("Используемая валюта: " + configManager.getRewardCurrency() + " (" + currencySymbol + ")");

        for (Map.Entry<String, List<RewardResult>> entry : rewardsByPlayer.entrySet()) {
            String playerName = entry.getKey();
            List<RewardResult> playerRewards = entry.getValue();
            int totalCoins = playerRewards.stream().mapToInt(RewardResult::getCoins).sum();

            String categories = playerRewards.stream()
                .map(reward -> reward.getCategory().getDisplayName() + " (#" + reward.getPosition() + ")")
                .collect(Collectors.joining(", "));

            plugin.getLogger().info("Игрок " + playerName + ": " + totalCoins + currencySymbol + " (" + categories + ")");
        }
        plugin.getLogger().info("=================================");
    }

    // Метод для принудительной выдачи наград (для тестирования)
    public void forceDistributeRewards() {
        plugin.getLogger().info("Принудительная выдача еженедельных наград...");
        plugin.getLogger().info("Принудительное обновление кэша...");
        topManager.forceUpdateCache();

        new BukkitRunnable() {
            @Override
            public void run() {
                processRewardDistribution();
            }
        }.runTaskLaterAsynchronously(plugin, 5 * 20L);
    }

    // Вспомогательный класс для хранения результатов наград
    private static class RewardResult {
        private final TopPlayer player;
        private final TopCategory category;
        private final int position;
        private final int coins;

        public RewardResult(TopPlayer player, TopCategory category, int position, int coins) {
            this.player = player;
            this.category = category;
            this.position = position;
            this.coins = coins;
        }

        public String getPlayerName() {
            return player.getPlayerName();
        }

        public TopCategory getCategory() {
            return category;
        }

        public int getPosition() {
            return position;
        }

        public int getCoins() {
            return coins;
        }
    }
}
