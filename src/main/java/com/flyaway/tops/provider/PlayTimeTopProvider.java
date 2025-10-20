package com.flyaway.tops.provider;

import com.flyaway.tops.model.TopPlayer;
import com.flyaway.tops.util.TopProviderUtils;
import com.flyaway.timereward.TimeReward;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PlayTimeTopProvider implements TopDataProvider {
    private final String categoryId;
    private final String timeType;

    public PlayTimeTopProvider(String categoryId) {
        this.categoryId = categoryId;
        this.timeType = extractTimeTypeFromCategoryId(categoryId);
    }

    @Override
    public String getCategoryId() {
        return categoryId;
    }

    @Override
    public List<TopPlayer> getTopPlayers(int limit) {
        List<TopPlayer> topPlayers = new ArrayList<>();

        // Получаем данные времени
        Map<UUID, Long> timeData = getTimeData();
        if (timeData == null || timeData.isEmpty()) {
            return topPlayers;
        }

        // Обрабатываем данные игроков
        for (Map.Entry<UUID, Long> entry : timeData.entrySet()) {
            UUID uuid = entry.getKey();
            long playtime = entry.getValue();

            // Пропускаем игроков с нулевым временем или исключенных из топа
            if (TopProviderUtils.isPlayerExcluded(uuid)) {
                continue;
            }

            // Получаем имя игрока
            String playerName = TopProviderUtils.getPlayerName(uuid);

            String displayValue = formatTime(playtime);
            topPlayers.add(new TopPlayer(uuid, playerName, displayValue, playtime));
        }

        // Сортируем по убыванию времени и ограничиваем лимитом
        return topPlayers.stream()
                .sorted(Comparator.comparingDouble(TopPlayer::getRawValue).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public TopPlayer getPlayerData(Player player) {
        long playtime = getPlayerPlayTime(player.getUniqueId());
        String displayValue = formatTime(playtime);

        return new TopPlayer(
            player.getUniqueId(),
            player.getName(),
            displayValue,
            playtime
        );
    }

    private Map<UUID, Long> getTimeData() {
        TimeReward timeReward = getTimeReward();
        if (timeReward == null) {
            return Collections.emptyMap();
        }

        if ("total".equals(timeType)) {
            return timeReward.getAllPlayersTotalTime();
        } else if ("weekly".equals(timeType)) {
            return timeReward.getAllPlayersPeriodTime();
        } else {
            Bukkit.getLogger().warning("Неизвестный тип времени: " + timeType);
            return Collections.emptyMap();
        }
    }

    private long getPlayerPlayTime(UUID uuid) {
        TimeReward timeReward = getTimeReward();
        if (timeReward == null) {
            return 0;
        }

        if ("total".equals(timeType)) {
            return timeReward.getPlayerTotalTime(uuid);
        } else if ("weekly".equals(timeType)) {
            return timeReward.getPlayerPeriodTime(uuid);
        } else {
            return 0;
        }
    }

    private TimeReward getTimeReward() {
        TimeReward timeReward = (TimeReward) Bukkit.getPluginManager().getPlugin("TimeReward");
        if (timeReward == null) {
            Bukkit.getLogger().warning("Плагин TimeReward не найден!");
            return null;
        }
        return timeReward;
    }

    public void resetPeriodTime() {
        if ("weekly".equals(timeType)) {
            TimeReward timeReward = getTimeReward();
            if (timeReward == null) {
                Bukkit.getLogger().warning("Не удалось сбросить время!");
                return;
            }
            try {
                timeReward.resetAllPlayersPeriodTime();
            } catch (Exception e) {
                Bukkit.getLogger().warning("Ошибка при сбросе периодического времени: " + e.getMessage());
            }
        }
    }

    private String extractTimeTypeFromCategoryId(String categoryId) {
        return categoryId.replace("playtime_", "");
    }

    private String formatTime(long time) {
        long days = time / 86400;
        long hours = (time % 86400) / 3600;
        long minutes = (time % 3600) / 60;

        if (days > 0) {
            return String.format("%dд %dч %dм", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dч %dм", hours, minutes);
        } else {
            return String.format("%dм", minutes);
        }
    }
}
