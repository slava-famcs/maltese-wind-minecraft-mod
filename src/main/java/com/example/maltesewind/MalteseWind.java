package com.example.maltesewind;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MalteseWind extends JavaPlugin {

    @Override
    public void onEnable() {
        var console = Bukkit.getConsoleSender();

        console.sendMessage(Component.text("========================================", NamedTextColor.DARK_AQUA));
        console.sendMessage(Component.text(" 🌪 MalteseWind ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text("v" + getPluginMeta().getVersion(), NamedTextColor.GRAY, TextDecoration.ITALIC)));
        console.sendMessage(Component.text(" A storm is brewing! The plugin has loaded successfully.", NamedTextColor.GREEN));
        console.sendMessage(Component.text("========================================", NamedTextColor.DARK_AQUA));
    }

    @Override
    public void onDisable() {
        var console = Bukkit.getConsoleSender();

        console.sendMessage(Component.text("========================================", NamedTextColor.DARK_RED));
        console.sendMessage(Component.text(" 🌪 MalteseWind ", NamedTextColor.RED, TextDecoration.BOLD));
        console.sendMessage(Component.text(" The storm has subsided. The storm wind plugin has been disabled.", NamedTextColor.YELLOW));
        console.sendMessage(Component.text("========================================", NamedTextColor.DARK_RED));
    }
}