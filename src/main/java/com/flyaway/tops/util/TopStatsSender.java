package com.flyaway.tops.util;

import com.flyaway.tops.model.TopCategory;
import com.flyaway.tops.model.TopPlayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TopStatsSender {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String apiUrl;
    private final String apiKey;
    private final JavaPlugin plugin;

    public TopStatsSender(JavaPlugin plugin, String apiUrl, String apiKey) {
        this.plugin = plugin;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    public CompletableFuture<Boolean> sendTopStats(List<TopCategory> categories) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(apiUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);

                // Создаем простую структуру данных
                Map<String, Object> requestData = new HashMap<>();
                requestData.put("serverName", "Minecraft Server");
                requestData.put("timestamp", System.currentTimeMillis());

                List<Map<String, Object>> categoriesData = new ArrayList<>();
                for (TopCategory category : categories) {
                    Map<String, Object> categoryData = new HashMap<>();
                    categoryData.put("id", category.getId());
                    categoryData.put("displayName", category.getDisplayName());
                    categoryData.put("description", category.getDescription());
                    categoryData.put("icon", category.getIcon());

                    List<Map<String, Object>> playersData = new ArrayList<>();
                    for (TopPlayer player : category.getTopPlayers()) {
                        Map<String, Object> playerData = new HashMap<>();
                        playerData.put("uuid", player.getUuid().toString());
                        playerData.put("name", player.getPlayerName());
                        playerData.put("displayValue", player.getDisplayValue());
                        playerData.put("rawValue", player.getRawValue());
                        playersData.add(playerData);
                    }

                    categoryData.put("topPlayers", playersData);
                    categoriesData.add(categoryData);
                }

                requestData.put("categories", categoriesData);

                String json = GSON.toJson(requestData);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    plugin.getLogger().info("✓ Статистика топов отправлена на сервер");
                    return true;
                } else {
                    // Получим тело ошибки для лучшего понимания
                    String errorResponse = getErrorResponse(connection);
                    plugin.getLogger().warning("✗ Ошибка отправки статистики: HTTP " + responseCode + " - " + errorResponse);
                    return false;
                }

            } catch (IOException e) {
                plugin.getLogger().warning("✗ Ошибка при отправке статистики: " + e.getMessage());
                return false;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String getErrorResponse(HttpURLConnection connection) {
        try {
            return new String(connection.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Не удалось получить детали ошибки";
        }
    }
}
