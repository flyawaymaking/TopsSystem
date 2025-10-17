package com.flyaway.tops;

import com.flyaway.tops.config.ConfigManager;
import com.flyaway.tops.command.TopCommand;
import com.flyaway.tops.listener.GUIListener;
import com.flyaway.tops.manager.TopManager;
import com.flyaway.tops.provider.*;
import com.flyaway.tops.rewards.WeeklyRewardManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class TopsPlugin extends JavaPlugin {

    private static TopsPlugin instance;
    private TopManager topManager;
    private ConfigManager configManager;
    private WeeklyRewardManager rewardManager;

    @Override
    public void onEnable() {
        instance = this;

        // Загрузка конфигурации
        this.configManager = new ConfigManager(this);
        configManager.loadConfig();

        // Автоматическая регистрация провайдеров из конфига
        List<TopDataProvider> providers = registerProvidersFromConfig();

        this.topManager = new TopManager(this, providers, configManager);

        // Инициализация системы наград
        this.rewardManager = new WeeklyRewardManager(this, topManager);

        // Регистрация команд
        getCommand("tops").setExecutor(new TopCommand(this, topManager, configManager, rewardManager));

        // Регистрация слушателей
        getServer().getPluginManager().registerEvents(new GUIListener(), this);

        getLogger().info("Tops plugin enabled! Registered " + providers.size() + " categories");
    }

    private List<TopDataProvider> registerProvidersFromConfig() {
        List<TopDataProvider> providers = new ArrayList<>();

        for (String categoryId : configManager.getCategories().keySet()) {
            TopDataProvider provider = createProviderForCategory(categoryId);
            if (provider != null) {
                providers.add(provider);
                getLogger().info("Registered provider for category: " + categoryId);
            }
        }

        return providers;
    }

    private TopDataProvider createProviderForCategory(String categoryId) {
        // Определяем тип провайдера по categoryId
        if (categoryId.startsWith("aura_skills_")) {
            return new AuraSkillsTopProvider(categoryId);
        } else if (categoryId.startsWith("coins_engine_")) {
            return new CoinsEngineTopProvider(categoryId);
        } else if (categoryId.startsWith("playtime_")) {
            return new PlayTimeTopProvider(categoryId);
        } else if (categoryId.equals("mob_kills")) {
            return new MobKillsTopProvider(categoryId);
        }

        getLogger().warning("Unknown category type: " + categoryId);
        return null;
    }

    @Override
    public void onDisable() {
        if (rewardManager != null) {
            rewardManager.disable();
        }
        if (topManager != null) {
            topManager.disable();
        }
        getLogger().info("Tops plugin disabled!");
    }

    public static TopsPlugin getInstance() {
        return instance;
    }

    public TopManager getTopManager() {
        return topManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public WeeklyRewardManager getRewardManager() {
        return rewardManager;
    }
}
