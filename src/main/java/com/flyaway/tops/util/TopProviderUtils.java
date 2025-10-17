package com.flyaway.tops.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TopProviderUtils {
    private static final LuckPerms luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);

    /**
     * Проверяет, исключен ли игрок из топов через право tops.exclude
     */
    public static boolean isPlayerExcluded(UUID uuid) {
        if (luckPerms == null) {
            return false;
        }

        try {
            User userData = luckPerms.getUserManager().getUser(uuid);
            if (userData == null) {
                // Загружаем оффлайн-пользователя, если его нет в памяти
                userData = luckPerms.getUserManager().loadUser(uuid).join();
            }

            if (userData != null) {
                return userData.getCachedData()
                        .getPermissionData()
                        .checkPermission("topssystem.exclude")
                        .asBoolean();
            }
        } catch (Exception e) {
            // Игнорируем ошибки при проверке прав
        }

        return false;
    }

    /**
     * Получает имя игрока по UUID (сначала проверяет онлайн, затем оффлайн)
     */
    public static String getPlayerName(UUID uuid) {
        // Сначала проверяем онлайн игроков
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }

        // Затем проверяем оффлайн игроков
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }

        return null;
    }
}
