package com.example.maltesewind;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

public class WindManager {

    private final MalteseWind plugin;
    private boolean stormActive = false;
    private BukkitTask currentTask;

    // Временные интервалы в минутах
    private static final int MIN_CALM_MINUTES = 20;
    private static final int MAX_CALM_MINUTES = 40;
    private static final int MIN_STORM_MINUTES = 3;
    private static final int MAX_STORM_MINUTES = 5;

    public WindManager(MalteseWind plugin) {
        this.plugin = plugin;
    }

    /**
     * Запускает цикл смены погоды.
     */
    public void startCycle() {
        scheduleNextStorm();
    }

    /**
     * Останавливает все активные задачи при выключении плагина.
     */
    public void shutdown() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel();
        }
        stormActive = false;
    }

    /**
     * Планирует следующий шторм через случайные 20–40 минут.
     */
    private void scheduleNextStorm() {
        int delayMinutes = ThreadLocalRandom.current().nextInt(MIN_CALM_MINUTES, MAX_CALM_MINUTES + 1);
        long delayTicks = delayMinutes * 60L * 20L; // 1 секунда = 20 тиков

        currentTask = Bukkit.getScheduler().runTaskLater(plugin, this::startStorm, delayTicks);
    }

    /**
     * Активирует шторм на 3–5 минут и оповещает игроков.
     */
    public void startStorm() {
        stormActive = true;

        int durationMinutes = ThreadLocalRandom.current().nextInt(MIN_STORM_MINUTES, MAX_STORM_MINUTES + 1);
        long durationTicks = durationMinutes * 60L * 20L;

        // Включаем штормовую погоду во всех мирах типа NORMAL (Оверворлд)
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration((int) durationTicks);
            }
        }

        // Глобальное оповещение в чат
        Component startMsg = Component.text("⚠️ Внимание! ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("Надвигается штормовой ветер 100 км/ч! Ищите укрытие под крышей!", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, false));
        Bukkit.broadcast(startMsg);

        // Таймер окончания шторма
        currentTask = Bukkit.getScheduler().runTaskLater(plugin, this::stopStorm, durationTicks);
    }

    /**
     * Завершает шторм и запускает отсчет до следующего.
     */
    public void stopStorm() {
        stormActive = false;

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setStorm(false);
                world.setThundering(false);
            }
        }

        Component stopMsg = Component.text("🌤 Буря утихла! ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("Штормовой ветер стих, выходить из укрытий безопасно.", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, false));
        Bukkit.broadcast(stopMsg);

        // Планируем следующий цикл
        scheduleNextStorm();
    }

    /**
     * Проверяет, находится ли локация в Оверворлде и под открытым небом.
     *
     * @param location Локация для проверки (например, позиция игрока).
     * @return true, если над точкой нет блоков и это Оверворлд.
     */
    public boolean isExposedToWind(Location location) {
        World world = location.getWorld();
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }

        // Получаем самый высокий непрозрачный/твердый блок по координатам X и Z
        int highestBlockY = world.getHighestBlockYAt(location);

        // Если координата Y локации выше или на уровне самого высокого блока — над ней открытое небо
        return location.getBlockY() >= highestBlockY;
    }

    public boolean isStormActive() {
        return stormActive;
    }
}