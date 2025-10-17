package com.flyaway.tops.provider;

import com.flyaway.tops.model.TopPlayer;
import com.flyaway.tops.util.TopProviderUtils;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.registry.GlobalRegistry;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AuraSkillsTopProvider implements TopDataProvider {

    private final AuraSkillsApi aura;
    private final String categoryId;
    private final Skill skill;

    public AuraSkillsTopProvider(String categoryId) {
        this.categoryId = categoryId;
        this.aura = AuraSkillsApi.get();

        String key = categoryId;
        if (key.startsWith("aura_skills_")) {
            key = key.substring("aura_skills_".length()); // извлекаем имя скила
        }

        GlobalRegistry registry = aura.getGlobalRegistry();

        // Прямо создаём NamespacedId
        NamespacedId id = NamespacedId.of("auraskills", key);
        Skill found = registry.getSkill(id);

        this.skill = found;
    }

    @Override
    public String getCategoryId() {
        return categoryId;
    }

    @Override
    public List<TopPlayer> getTopPlayers(int limit) {
        List<TopPlayer> list = new ArrayList<>();
        Map<UUID, Double> playerXpMap = new HashMap<>(); // храним опыт игроков

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            UUID uuid = offline.getUniqueId();

            if (TopProviderUtils.isPlayerExcluded(uuid)) continue;

            // Загружаем данные из AuraSkills
            SkillsUser skillsUser = aura.getUser(uuid);
            if (skillsUser == null || !skillsUser.isLoaded()) {
                try {
                    skillsUser = aura.getUserManager().loadUser(uuid).join();
                } catch (Exception e) {
                    continue;
                }
            }

            if (skillsUser == null || !skillsUser.isLoaded()) continue;

            double level = skillsUser.getSkillLevel(skill);
            double xp = skillsUser.getSkillXp(skill); // получаем опыт

            // Сохраняем опыт для сортировки
            playerXpMap.put(uuid, xp);

            String disp = formatDisplay(level);
            list.add(new TopPlayer(uuid, offline.getName(), disp, level));
        }

        // Сортируем сначала по уровню (убывание), потом по опыту (убывание)
        return list.stream()
                .sorted((p1, p2) -> {
                    // Сначала сравниваем по уровню
                    int levelCompare = Double.compare(p2.getRawValue(), p1.getRawValue());
                    if (levelCompare != 0) {
                        return levelCompare;
                    }
                    // При равных уровнях сравниваем по опыту
                    double xp1 = playerXpMap.get(p1.getUuid());
                    double xp2 = playerXpMap.get(p2.getUuid());
                    return Double.compare(xp2, xp1);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public TopPlayer getPlayerData(Player player) {
        SkillsUser user = aura.getUser(player.getUniqueId());
        if (user == null) return null;

        double level = user.getSkillLevel(skill);

        String disp = formatDisplay(level);
        return new TopPlayer(player.getUniqueId(), player.getName(), disp, level);
    }

    private String formatDisplay(double level) {
        return String.format(Locale.ROOT, "%.0f ур.", level);
    }
}
