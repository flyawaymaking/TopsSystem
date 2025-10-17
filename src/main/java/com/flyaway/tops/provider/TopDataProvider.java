package com.flyaway.tops.provider;

import com.flyaway.tops.model.TopPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public interface TopDataProvider {
    String getCategoryId();
    List<TopPlayer> getTopPlayers(int limit);
    TopPlayer getPlayerData(Player player);
}
