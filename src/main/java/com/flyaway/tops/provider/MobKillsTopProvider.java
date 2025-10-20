package com.flyaway.tops.provider;

import com.flyaway.tops.model.TopPlayer;
import com.flyaway.tops.util.TopProviderUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.flyaway.trackplayer.TrackPlayer;

import java.util.*;
import java.util.stream.Collectors;


public class MobKillsTopProvider implements TopDataProvider {
    private final String categoryId;

    public MobKillsTopProvider(String categoryId) {
        this.categoryId = categoryId;
    }

    @Override
    public String getCategoryId() {
        return categoryId;
    }

    @Override
    public List<TopPlayer> getTopPlayers(int limit) {
        List<TopPlayer> topPlayers = new ArrayList<>();
        
        // Получаем экземпляр плагина TrackPlayer
        TrackPlayer trackPlugin = getTrackPlugin();
        if (trackPlugin == null) {
            return topPlayers;
        }

        // Получаем данные об убитых мобах
        Map<UUID, Integer> mobKillsData = trackPlugin.getPlayerMobKills();
        if (mobKillsData == null || mobKillsData.isEmpty()) {
            return topPlayers;
        }

        // Обрабатываем данные игроков
        for (Map.Entry<UUID, Integer> entry : mobKillsData.entrySet()) {
            UUID uuid = entry.getKey();
            int mobKills = entry.getValue();

            // Пропускаем игроков с нулевыми убийствами или исключенных из топа
            if (TopProviderUtils.isPlayerExcluded(uuid)) {
                continue;
            }

            // Получаем имя игрока
            String playerName = TopProviderUtils.getPlayerName(uuid);

            String displayValue = formatKills(mobKills);
            topPlayers.add(new TopPlayer(uuid, playerName, displayValue, mobKills));
        }

        // Сортируем по убыванию количества убийств и ограничиваем лимитом
        return topPlayers.stream()
                .sorted(Comparator.comparingDouble(TopPlayer::getRawValue).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public TopPlayer getPlayerData(Player player) {
        TrackPlayer trackPlugin = getTrackPlugin();
        if (trackPlugin == null) {
            return null;
        }

        int mobKills = trackPlugin.getMobKills(player.getUniqueId());
        String displayValue = formatKills(mobKills);

        return new TopPlayer(
            player.getUniqueId(),
            player.getName(),
            displayValue,
            mobKills
        );
    }

    private TrackPlayer getTrackPlugin() {
        TrackPlayer trackPlugin = (TrackPlayer) Bukkit.getPluginManager().getPlugin("TrackPlayer");
        if (trackPlugin == null) {
            Bukkit.getLogger().warning("Плагин TrackPlayer не найден!");
            return null;
        }
        return trackPlugin;
    }

    public void resetMobKills() {
        TrackPlayer trackPlugin = getTrackPlugin();
        if (trackPlugin == null) {
            Bukkit.getLogger().warning("Не удалось сбросить убийства мобов!");
            return;
        }
        try {
            trackPlugin.resetAllMobKills();
        } catch (Exception e) {
            Bukkit.getLogger().warning("Ошибка при сбросе убийств мобов: " + e.getMessage());
        }
    }

    private String formatKills(int kills) {
        if (kills >= 1000000) {
            return String.format("%.1fM", kills / 1000000.0);
        } else if (kills >= 1000) {
            return String.format("%.1fK", kills / 1000.0);
        } else {
            return String.valueOf(kills);
        }
    }
}
