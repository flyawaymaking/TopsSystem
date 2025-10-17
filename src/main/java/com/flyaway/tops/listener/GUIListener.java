package com.flyaway.tops.listener;

import com.flyaway.tops.gui.TopInventoryHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Проверяем по классу холдера
        if (event.getInventory().getHolder() instanceof TopInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Можно добавить логику очистки при необходимости
    }
}
