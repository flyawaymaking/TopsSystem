package com.flyaway.tops.provider;

import com.flyaway.tops.model.TopPlayer;
import com.flyaway.tops.util.TopProviderUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.util.*;
import java.util.stream.Collectors;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class CoinsEngineTopProvider implements TopDataProvider {
    private final String categoryId;
    private final String currency;

    public CoinsEngineTopProvider(String categoryId) {
        this.categoryId = categoryId;
        this.currency = extractCurrencyFromCategoryId(categoryId);
    }

    @Override
    public String getCategoryId() {
        return categoryId;
    }

    @Override
    public List<TopPlayer> getTopPlayers(int limit) {
        List<TopPlayer> list = new ArrayList<>();

        Currency currencyObj = CoinsEngineAPI.getCurrency(currency);

        if (currencyObj == null) {
            Bukkit.getLogger().warning("Валюта не найдена: " + currency);
            return list;
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            UUID uuid = offline.getUniqueId();

            if (TopProviderUtils.isPlayerExcluded(uuid)) continue;

            // Получаем баланс из CoinsEngine
            double balance = CoinsEngineAPI.getBalance(uuid, currencyObj);

            String displayValue = formatDisplay(balance, currencyObj);
            String playerName = TopProviderUtils.getPlayerName(uuid);
            list.add(new TopPlayer(uuid, playerName, displayValue, balance));
        }

        return list.stream()
                .sorted(Comparator.comparingDouble(TopPlayer::getRawValue).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public TopPlayer getPlayerData(Player player) {
        Currency currencyObj = CoinsEngineAPI.getCurrency(currency);

        if (currencyObj == null) {
            return null;
        }

        double balance = CoinsEngineAPI.getBalance(player.getUniqueId(), currencyObj);
        String displayValue = formatDisplay(balance, currencyObj);

        return new TopPlayer(
            player.getUniqueId(),
            player.getName(),
            displayValue,
            balance
        );
    }

    private String extractCurrencyFromCategoryId(String categoryId) {
        // Извлекаем валюту из categoryId (coins_engine_gold -> gold)
        return categoryId.replace("coins_engine_", "");
    }

    private String formatDisplay(double raw, Currency currency) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');  // Пробел как разделитель тысяч
        symbols.setDecimalSeparator('.');   // Точка как разделитель дробной части

        // Для coins убираем дробную часть, для остальных валют оставляем 2 знака
        String pattern = "coins".equals(currency.getId()) ? "#,##0" : "#,##0.00";

        DecimalFormat formatter = new DecimalFormat(pattern, symbols);
        return formatter.format(raw) + currency.getSymbol();
    }
}
