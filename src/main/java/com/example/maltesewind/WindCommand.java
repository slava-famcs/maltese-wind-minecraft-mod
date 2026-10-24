package com.example.maltesewind;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class WindCommand implements CommandExecutor, TabCompleter {

    private final WindManager windManager;
    private static final String ADMIN_PERM = "maltesewind.admin";

    public WindCommand(WindManager windManager) {
        this.windManager = windManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Component.text("❌ У вас нет прав для управления штормом (", NamedTextColor.RED)
                    .append(Component.text(ADMIN_PERM, NamedTextColor.DARK_RED))
                    .append(Component.text(").", NamedTextColor.RED)));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start" -> {
                if (windManager.isStormActive()) {
                    sender.sendMessage(Component.text("⚠️ Шторм уже активен!", NamedTextColor.YELLOW));
                } else {
                    windManager.startStorm();
                    sender.sendMessage(Component.text("✅ Вы принудительно запустили штормовой ветер.", NamedTextColor.GREEN));
                }
            }
            case "stop" -> {
                if (!windManager.isStormActive()) {
                    sender.sendMessage(Component.text("⚠️ Сейчас на сервере штиль, останавливать нечего.", NamedTextColor.YELLOW));
                } else {
                    windManager.stopStorm();
                    sender.sendMessage(Component.text("✅ Вы принудительно остановили штормовой ветер.", NamedTextColor.GREEN));
                }
            }
            case "status" -> {
                if (windManager.isStormActive()) {
                    sender.sendMessage(Component.text("🌪 Статус погоды: ", NamedTextColor.GRAY)
                            .append(Component.text("ШТОРМ АКТИВЕН", NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text(" (ветер дует " + windManager.getCurrentDirection().getDisplayName() + ")", NamedTextColor.AQUA)));
                } else {
                    sender.sendMessage(Component.text("🌤 Статус погоды: ", NamedTextColor.GRAY)
                            .append(Component.text("ШТИЛЬ", NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.text(" (ожидание следующего шторма по таймеру)", NamedTextColor.YELLOW)));
                }
            }
            case "reload" -> {
                windManager.loadConfig();
                sender.sendMessage(Component.text("🔄 Конфигурация ", NamedTextColor.GREEN)
                        .append(Component.text("MalteseWind", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" успешно перезагружена!", NamedTextColor.GREEN)));
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Использование: /wind <start|stop|status|reload>", NamedTextColor.GOLD));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Stream.of("start", "stop", "status", "reload")
                    .filter(sub -> sub.startsWith(prefix))
                    .toList();
        }

        return Collections.emptyList();
    }
}