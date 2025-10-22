package com.flyaway.tops.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.util.*;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    private String guiTitle;
    private int guiSize;
    private Material fillerItem;

    // Новые поля для наград
    private String rewardCurrency;
    private String rewardTime;
    private Map<Integer, Integer> positionRewards;
    private List<Integer> rewardPositions;

    private boolean statsEnabled;
    private String statsApiUrl;
    private String statsApiKey;
    private long cacheDuration;
    private List<String> categoryPreLore;

    private final Map<String, CategoryConfig> categories = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();

        // Загрузка настроек GUI
        this.guiTitle = config.getString("gui.title", "🏆 Топы игроков");
        this.guiSize = config.getInt("gui.size", 54);
        this.fillerItem = Material.matchMaterial(config.getString("gui.filler_item", "GRAY_STAINED_GLASS_PANE"));
        this.categoryPreLore = config.getStringList("gui.category_pre_lore");

        // Загрузка настроек наград
        this.rewardCurrency = config.getString("rewards.currency", "coins");
        this.rewardTime = config.getString("rewards.distribution_time", "SUNDAY 18:00");

        // Загрузка настроек статистики
        this.statsEnabled = config.getBoolean("stats.enabled", false);
        this.statsApiUrl = config.getString("stats.api_url", "http://192.168.0.120:8000/tops/update/");
        this.statsApiKey = config.getString("stats.api_key", "Sel9C5POak1sL.08zWQ64q3_B");

        // Загрузка настройки кэша (в минутах)
        this.cacheDuration = config.getLong("cache.duration_minutes", 15) * 60 * 20L; // Конвертируем в тики

        // Загрузка наград за позиции
        this.positionRewards = new HashMap<>();
        this.rewardPositions = new ArrayList<>();

        if (config.contains("rewards.position_rewards")) {
            for (String positionStr : config.getConfigurationSection("rewards.position_rewards").getKeys(false)) {
                try {
                    int position = Integer.parseInt(positionStr);
                    int reward = config.getInt("rewards.position_rewards." + positionStr);
                    positionRewards.put(position, reward);
                    rewardPositions.add(position);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Некорректная позиция в конфиге: " + positionStr);
                }
            }
        }

        // Сортируем позиции по возрастанию
        Collections.sort(rewardPositions);

        // Если в конфиге нет наград, используем значения по умолчанию
        if (positionRewards.isEmpty()) {
            positionRewards.put(1, 250);
            positionRewards.put(2, 150);
            positionRewards.put(3, 100);
            rewardPositions.addAll(Arrays.asList(1, 2, 3));
        }

        categories.clear();

        // Загрузка категорий
        if (config.contains("categories")) {
            for (String categoryId : config.getConfigurationSection("categories").getKeys(false)) {
                String path = "categories." + categoryId;

                // Загрузка типа эффекта зелья для категории
                String categoryPotionTypeName = config.getString(path + ".potion_type", "LUCK");
                PotionEffectType potionType = getPotionEffectTypeFromString(categoryPotionTypeName);

                CategoryConfig categoryConfig = new CategoryConfig(
                    categoryId,
                    config.getString(path + ".display_name"),
                    config.getInt(path + ".slot"),
                    Material.matchMaterial(config.getString(path + ".item", "PAPER")),
                    config.getString(path + ".texture", ""),
                    potionType,
                    config.getStringList(path + ".lore_template")
                );

                categories.put(categoryId, categoryConfig);
            }
        }
    }

    private PotionEffectType getPotionEffectTypeFromString(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return PotionEffectType.LUCK; // По умолчанию - удача
        }

        try {
            // Прямое получение по имени
            return PotionEffectType.getByName(typeName.toUpperCase());
        } catch (Exception e) {
            return PotionEffectType.LUCK; // По умолчанию - удача
        }
    }

    // Геттеры
    public String getGuiTitle() { return guiTitle; }
    public int getGuiSize() { return guiSize; }
    public Material getFillerItem() { return fillerItem; }
    public Map<String, CategoryConfig> getCategories() { return categories; }
    public CategoryConfig getCategory(String categoryId) { return categories.get(categoryId); }

    // Новые геттеры для наград
    public String getRewardCurrency() { return rewardCurrency; }
    public String getRewardTime() { return rewardTime; }
    public Map<Integer, Integer> getPositionRewards() { return positionRewards; }
    public List<Integer> getRewardPositions() { return rewardPositions; }
    public List<String> getCategoryPreLore() { return categoryPreLore; }
    public int getRewardForPosition(int position) {
        return positionRewards.getOrDefault(position, 0);
    }
    public boolean isStatsEnabled() { return statsEnabled; }
    public String getStatsApiUrl() { return statsApiUrl; }
    public String getStatsApiKey() { return statsApiKey; }
    public long getCacheDuration() { return cacheDuration; }

    public String getCurrencySymbol() {
        try {
            Currency currency = CoinsEngineAPI.getCurrency(rewardCurrency);
            return currency != null ? currency.getSymbol() : rewardCurrency;
        } catch (Exception e) {
            return rewardCurrency; // fallback на название валюты
        }
    }

    public static class CategoryConfig {
        private final String id;
        private final String displayName;
        private final int slot;
        private final Material item;
        private final String texture;
        private final PotionEffectType potionType;
        private final List<String> loreTemplate;

        public CategoryConfig(String id, String displayName, int slot, Material item, String texture,
                            PotionEffectType potionType, List<String> loreTemplate) {
            this.id = id;
            this.displayName = displayName;
            this.slot = slot;
            this.item = item;
            this.texture = texture;
            this.potionType = potionType;
            this.loreTemplate = loreTemplate;
        }

        // Геттеры
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getSlot() { return slot; }
        public Material getItem() { return item; }
        public String getTexture() { return texture; }
        public List<String> getLoreTemplate() { return loreTemplate; }
        public PotionEffectType getPotionType() { return potionType; }

        public boolean isHead() {
            return item == Material.PLAYER_HEAD;
        }
    }
}
