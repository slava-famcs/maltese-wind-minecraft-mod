package com.example.maltesewind;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MalteseWind extends JavaPlugin {

    private WindManager windManager;

    @Override
    public void onEnable() {
        var console = Bukkit.getConsoleSender();

        // Инициализация и запуск менеджера ветра
        this.windManager = new WindManager(this);
        this.windManager.startCycle();

        console.sendMessage(Component.text("========================================", NamedTextColor.DARK_AQUA));
        console.sendMessage(Component.text(" 🌪 MalteseWind ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text("v" + getPluginMeta().getVersion(), NamedTextColor.GRAY, TextDecoration.ITALIC)));
        console.sendMessage(Component.text(" Штормовой ветер поднимается! Плагин успешно загружен.", NamedTextColor.GREEN));
        console.sendMessage(Component.text("========================================", NamedTextColor.DARK_AQUA));
    }

    @Override
    public void onDisable() {
        var console = Bukkit.getConsoleSender();

        // Остановка активных таймеров шторма
        if (this.windManager != null) {
            this.windManager.shutdown();
        }

        console.sendMessage(Component.text("========================================", NamedTextColor.DARK_RED));
        console.sendMessage(Component.text(" 🌪 MalteseWind ", NamedTextColor.RED, TextDecoration.BOLD));
        console.sendMessage(Component.text(" Буря утихла. Плагин штормового ветра отключен.", NamedTextColor.YELLOW));
        console.sendMessage(Component.text("========================================", NamedTextColor.DARK_RED));
    }

    public WindManager getWindManager() {
        return windManager;
    }
}