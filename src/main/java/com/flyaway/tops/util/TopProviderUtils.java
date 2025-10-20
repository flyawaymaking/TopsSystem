package com.flyaway.tops.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
            String name = onlinePlayer.getName();
            if (name != null) return name;
        }

        // Затем проверяем оффлайн игроков
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer != null) {
            String name = offlinePlayer.getName();
            if (name != null) return name;
        }

        return getNameFromUserCache(uuid);
    }

    public static String getNameFromUserCache(UUID uuid) {
        File cache = new File(Bukkit.getWorldContainer(), "usercache.json");
        if (!cache.exists()) return null;
        try {
            String json = Files.readString(cache.toPath(), StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : arr) {
                JsonObject entry = element.getAsJsonObject();
                if (UUID.fromString(entry.get("uuid").getAsString()).equals(uuid)) {
                    return entry.get("name").getAsString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
