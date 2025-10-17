package com.flyaway.tops.manager;

import com.flyaway.tops.TopsPlugin;
import com.flyaway.tops.config.ConfigManager;
import com.flyaway.tops.model.TopCategory;
import com.flyaway.tops.model.TopPlayer;
import com.flyaway.tops.provider.TopDataProvider;
import com.flyaway.tops.provider.PlayTimeTopProvider;
import com.flyaway.tops.provider.MobKillsTopProvider;
import com.flyaway.tops.util.TopStatsSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TopManager {

    private final TopsPlugin plugin;
    private final List<TopDataProvider> providers;
    private final ConfigManager configManager;
    private final TopStatsSender statsSender;

    // Кэш для топов по категориям
    private final Map<String, List<TopPlayer>> topCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private final long cacheDuration;
    private BukkitTask cacheTask;

    public TopManager(TopsPlugin plugin, List<TopDataProvider> providers, ConfigManager configManager) {
        this.plugin = plugin;
        this.providers = providers;
        this.configManager = configManager;
        this.cacheDuration = configManager.getCacheDuration();

        String apiUrl = configManager.getStatsApiUrl();
        String apiKey = configManager.getStatsApiKey();
        this.statsSender = new TopStatsSender(plugin, apiUrl, apiKey);

        startCacheUpdateTask();
    }

    public void disable() {
        if (cacheTask != null) {
            cacheTask.cancel();
            cacheTask = null;
        }
    }

    private void startCacheUpdateTask() {
        this.cacheTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllCache();
                sendTopStatsToServer();
            }
        }.runTaskTimerAsynchronously(plugin, 30*20L, cacheDuration);
    }

    private void updateAllCache() {
        for (TopDataProvider provider : providers) {
            updateCacheForProvider(provider);
        }
    }

    private void updateCacheForProvider(TopDataProvider provider) {
        String categoryId = provider.getCategoryId();
        try {
            List<TopPlayer> topPlayers = provider.getTopPlayers(10);
            topCache.put(categoryId, topPlayers);
            cacheTimestamps.put(categoryId, System.currentTimeMillis());
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при обновлении кэша для категории " + categoryId + ": " + e.getMessage());
        }
    }

    private void sendTopStatsToServer() {
        List<TopCategory> categories = getAllCategories();
        statsSender.sendTopStats(categories).thenAccept(success -> {
            if (!success) {
                plugin.getLogger().warning("Не удалось отправить статистику топов на сервер");
            }
        });
    }

    /**
     * Метод вызывается после распределения наград для сброса периодического времени
     * в PlayTimeTopProvider провайдерах
     */
    public void afterRewardDistribution() {
        for (TopDataProvider provider : providers) {
            if (provider instanceof PlayTimeTopProvider) {
                try {
                    PlayTimeTopProvider playTimeProvider = (PlayTimeTopProvider) provider;
                    playTimeProvider.resetPeriodTime();
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка при сбросе времени для провайдера " +
                            provider.getCategoryId() + ": " + e.getMessage());
                }
            } else if (provider instanceof MobKillsTopProvider) {
                try {
                    MobKillsTopProvider mobKillsProvider = (MobKillsTopProvider) provider;
                    mobKillsProvider.resetMobKills();
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка при сбросе убийств мобов для провайдера " +
                            provider.getCategoryId() + ": " + e.getMessage());
                }
            }
        }

        // Принудительно обновляем кэш после сброса времени
        plugin.getLogger().info("Принудительное обновление кэша после выдачи наград...");
        updateAllCache();
    }

    public List<TopCategory> getAllCategories() {
        List<TopCategory> categories = new ArrayList<>();

        for (TopDataProvider provider : providers) {
            ConfigManager.CategoryConfig config = configManager.getCategory(provider.getCategoryId());
            if (config != null) {
                List<TopPlayer> topPlayers = getCachedTopPlayers(provider.getCategoryId());
                TopCategory category = new TopCategory(
                    provider.getCategoryId(),
                    config.getDisplayName(),
                    "",
                    "",
                    topPlayers
                );
                categories.add(category);
            }
        }

        return categories;
    }

    public TopCategory getCategory(String categoryId) {
        for (TopDataProvider provider : providers) {
            if (provider.getCategoryId().equals(categoryId)) {
                ConfigManager.CategoryConfig config = configManager.getCategory(categoryId);
                if (config != null) {
                    List<TopPlayer> topPlayers = getCachedTopPlayers(categoryId);
                    return new TopCategory(
                        provider.getCategoryId(),
                        config.getDisplayName(),
                        "",
                        "",
                        topPlayers
                    );
                }
            }
        }
        return null;
    }

    private List<TopPlayer> getCachedTopPlayers(String categoryId) {
        return topCache.getOrDefault(categoryId, new ArrayList<>());
    }

    public TopPlayer getPlayerData(Player player, String categoryId) {
        for (TopDataProvider provider : providers) {
            if (provider.getCategoryId().equals(categoryId)) {
                return provider.getPlayerData(player);
            }
        }
        return null;
    }

    // Метод для принудительного обновления кэша (можно использовать для команд админа)
    public void forceUpdateCache() {
        plugin.getLogger().info("Принудительное обновление кэша топов...");
        updateAllCache();
    }

    // Метод для получения времени последнего обновления кэша
    public long getCacheAge(String categoryId) {
        Long timestamp = cacheTimestamps.get(categoryId);
        if (timestamp == null) return -1;
        return System.currentTimeMillis() - timestamp;
    }

    // Метод для получения информации о кэше (для админов)
    public Map<String, String> getCacheInfo() {
        Map<String, String> info = new HashMap<>();
        for (String categoryId : cacheTimestamps.keySet()) {
            long age = getCacheAge(categoryId);
            long ageMinutes = age / (60 * 1000);
            int playerCount = topCache.getOrDefault(categoryId, new ArrayList<>()).size();
            info.put(categoryId, playerCount + " игроков, " + ageMinutes + " мин. назад");
        }
        return info;
    }
}
