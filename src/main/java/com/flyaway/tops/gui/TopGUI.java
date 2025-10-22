package com.flyaway.tops.gui;

import com.flyaway.tops.config.ConfigManager;
import com.flyaway.tops.manager.TopManager;
import com.flyaway.tops.model.TopCategory;
import com.flyaway.tops.model.TopPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TopGUI {
    private final Player player;
    private final TopManager topManager;
    private final ConfigManager configManager;
    private Inventory inventory;

    public TopGUI(Player player, TopManager topManager, ConfigManager configManager) {
        this.player = player;
        this.topManager = topManager;
        this.configManager = configManager;

        // Создаем инвентарь с нашим холдером
        TopInventoryHolder holder = new TopInventoryHolder();
        this.inventory = Bukkit.createInventory(
            holder,
            configManager.getGuiSize(),
            configManager.getGuiTitle()
        );
        // Устанавливаем инвентарь в холдер
        holder.setInventory(inventory);
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }

    private void setupGUI() {
        // Заполняем категории
        for (TopCategory category : topManager.getAllCategories()) {
            ConfigManager.CategoryConfig categoryConfig = configManager.getCategory(category.getId());
            if (categoryConfig != null) {
                ItemStack categoryItem = createCategoryItem(category, categoryConfig);
                inventory.setItem(categoryConfig.getSlot(), categoryItem);
            }
        }

        // Заполняем пустые слоты
        ItemStack filler = createFiller();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private ItemStack createCategoryItem(TopCategory category, ConfigManager.CategoryConfig config) {
        List<String> lore = buildLore(category, config);

        // Если это голова и есть текстура - создаем голову с текстурой
        if (config.isHead() && config.getTexture() != null && !config.getTexture().isEmpty()) {
            return createHeadWithTexture(config.getTexture(), config.getDisplayName(), lore);
        }

        // Если это зелье - создаем зелье с указанным типом
        if (isPotion(config.getItem())) {
            return createPotionItem(config.getItem(), config.getDisplayName(), lore, config.getPotionType());
        }

        // Обычный предмет
        return createItem(config.getItem(), config.getDisplayName(), lore);
    }

    private List<String> buildLore(TopCategory category, ConfigManager.CategoryConfig config) {
        List<String> lore = new ArrayList<>();
        List<TopPlayer> topPlayers = category.getTopPlayers();

        for (String line : config.getLoreTemplate()) {
            if (line.contains("%top_players%")) {
                // Добавляем информацию о наградах перед списком игроков
                String currencySymbol = configManager.getCurrencySymbol();
                lore.addAll(configManager.getCategoryPreLore());

                // Заменяем на топ игроков
                for (int i = 0; i < Math.min(10, topPlayers.size()); i++) {
                    TopPlayer topPlayer = topPlayers.get(i);
                    int position = i + 1;
                    String positionColor = getPositionColor(position);
                    String positionIcon = getPositionIcon(position);

                    String playerLine = positionColor + positionIcon + " " + topPlayer.getPlayerName() +
                            " §7- §e" + topPlayer.getDisplayValue();

                    // Добавляем информацию о наградах для позиций из конфига
                    if (configManager.getRewardPositions().contains(position)) {
                        int reward = configManager.getRewardForPosition(position);
                        if (reward > 0) {
                            playerLine += " §7[§6" + reward + currencySymbol + "§7]";
                        }
                    }

                    lore.add(playerLine);
                }
            } else if (line.contains("%player_level%")) {
                // Заменяем на данные игрока
                TopPlayer playerData = topManager.getPlayerData(player, category.getId());
                if (playerData != null) {
                    lore.add(line.replace("%player_level%", playerData.getDisplayValue()));
                } else {
                    lore.add(line.replace("%player_level%", "§cНет данных"));
                }
            } else {
                lore.add(line);
            }
        }

        return lore;
    }

    private ItemStack createFiller() {
        return createItem(configManager.getFillerItem(), " ", null);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHeadWithTexture(String texture, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null && texture != null && !texture.isEmpty()) {
            // Создаем профиль с текстурой (используем Paper API)
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "CustomHead");
            ProfileProperty property = new ProfileProperty("textures", texture);
            profile.setProperty(property);
            meta.setPlayerProfile(profile);

            meta.setDisplayName(displayName);
            if (lore != null) {
                meta.setLore(lore);
            }
            head.setItemMeta(meta);
        }

        return head;
    }

    private ItemStack createPotionItem(Material material, String name, List<String> lore, PotionEffectType potionType) {
        ItemStack potion = new ItemStack(material);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }

            // Добавляем эффект зелья
            if (potionType != null) {
                // Добавляем эффект зелья (1 секунда, уровень 1)
                PotionEffect effect = new PotionEffect(potionType, 20, 0); // 20 тиков = 1 секунда
                meta.addCustomEffect(effect, true);
            }

            // Скрываем описание эффектов зелья
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

            potion.setItemMeta(meta);
        }

        return potion;
    }

    // Проверка является ли материал зельем
    private boolean isPotion(Material material) {
        return material == Material.POTION ||
               material == Material.SPLASH_POTION ||
               material == Material.LINGERING_POTION ||
               material == Material.TIPPED_ARROW;
    }

    private String getPositionColor(int position) {
        return switch (position) {
            case 1 -> "§6";
            case 2 -> "§7";
            case 3 -> "§c";
            default -> "§f";
        };
    }

    private String getPositionIcon(int position) {
        return switch (position) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> position + ".";
        };
    }
}
