package com.example.maltesewind;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class WindManager {

    /**
     * 8 сторон света (название указывает, КУДА дует ветер).
     * В Minecraft: Север = -Z, Юг = +Z, Восток = +X, Запад = -X.
     */
    public enum WindDirection {
        NORTH("на север", 0, -1),
        NORTHEAST("на северо-восток", 1, -1),
        EAST("на восток", 1, 0),
        SOUTHEAST("на юго-восток", 1, 1),
        SOUTH("на юг", 0, 1),
        SOUTHWEST("на юго-запад", -1, 1),
        WEST("на запад", -1, 0),
        NORTHWEST("на северо-запад", -1, -1);

        private final String displayName;
        private final Vector vector;

        WindDirection(String displayName, double x, double z) {
            this.displayName = displayName;
            this.vector = new Vector(x, 0, z).normalize();
        }

        public String getDisplayName() {
            return displayName;
        }

        public Vector getVector() {
            return vector.clone();
        }

        public static WindDirection getRandom() {
            WindDirection[] values = values();
            return values[ThreadLocalRandom.current().nextInt(values.length)];
        }
    }

    private final MalteseWind plugin;
    private boolean stormActive = false;
    private BukkitTask cycleTask;
    private BukkitTask physicsTask;

    // Текущее направление ветра (меняется при каждом старте шторма)
    private WindDirection currentDirection = WindDirection.NORTHEAST;

    // Временные интервалы в минутах
    private static final int MIN_CALM_MINUTES = 20;
    private static final int MAX_CALM_MINUTES = 40;
    private static final int MIN_STORM_MINUTES = 3;
    private static final int MAX_STORM_MINUTES = 5;

    // Сила ветра (применяется каждые 2 тика)
    private static final double PLAYER_WIND_STRENGTH = 0.16;
    private static final double ITEM_WIND_STRENGTH = 0.12;
    private static final double MAX_WIND_SPEED = 1.35;

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
        if (cycleTask != null && !cycleTask.isCancelled()) {
            cycleTask.cancel();
        }
        stopPhysicsTicker();
        stormActive = false;
    }

    /**
     * Планирует следующий шторм через случайные 20–40 минут.
     */
    private void scheduleNextStorm() {
        int delayMinutes = ThreadLocalRandom.current().nextInt(MIN_CALM_MINUTES, MAX_CALM_MINUTES + 1);
        long delayTicks = delayMinutes * 60L * 20L;

        cycleTask = Bukkit.getScheduler().runTaskLater(plugin, this::startStorm, delayTicks);
    }

    /**
     * Активирует шторм со случайным направлением, запускает физику и оповещает игроков.
     */
    public void startStorm() {
        stormActive = true;
        currentDirection = WindDirection.getRandom();

        int durationMinutes = ThreadLocalRandom.current().nextInt(MIN_STORM_MINUTES, MAX_STORM_MINUTES + 1);
        long durationTicks = durationMinutes * 60L * 20L;

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration((int) durationTicks);
            }
        }

        Component startMsg = Component.text("⚠️ Внимание! ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("Надвигается штормовой ветер 100 км/ч (дует ", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text(currentDirection.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(")! Ищите укрытие!", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, false));
        Bukkit.broadcast(startMsg);

        // Запускаем физику ветра
        startPhysicsTicker();

        // Таймер окончания шторма
        cycleTask = Bukkit.getScheduler().runTaskLater(plugin, this::stopStorm, durationTicks);
    }

    /**
     * Завершает шторм, останавливает тикер физики и запускает отсчет до следующего.
     */
    public void stopStorm() {
        stormActive = false;
        stopPhysicsTicker();

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

        scheduleNextStorm();
    }

    /**
     * Запускает тикер физики ветра (срабатывает каждые 2 тика).
     */
    private void startPhysicsTicker() {
        stopPhysicsTicker();
        physicsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickWindPhysics, 0L, 2L);
    }

    /**
     * Останавливает тикер физики ветра.
     */
    private void stopPhysicsTicker() {
        if (physicsTask != null && !physicsTask.isCancelled()) {
            physicsTask.cancel();
            physicsTask = null;
        }
    }

    /**
     * Основной метод расчета физики, вызываемый каждые 2 тика во время шторма.
     */
    private void tickWindPhysics() {
        if (!stormActive) return;

        Vector windVector = currentDirection.getVector();
        Vector playerPush = windVector.clone().multiply(PLAYER_WIND_STRENGTH);
        Vector itemPush = windVector.clone().multiply(ITEM_WIND_STRENGTH).setY(0.04);

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }

            // 1. Обработка игроков в Оверворлде
            for (Player player : world.getPlayers()) {
                GameMode mode = player.getGameMode();
                if (mode == GameMode.SPECTATOR || (mode == GameMode.CREATIVE && player.isFlying()) || player.isDead()) {
                    continue;
                }

                if (isExposedToWind(player.getLocation())) {
                    Vector currentVel = player.getVelocity();
                    Vector appliedPush = player.isSneaking() ? playerPush.clone().multiply(0.6) : playerPush;
                    Vector newVel = currentVel.add(appliedPush);

                    limitHorizontalSpeed(newVel, MAX_WIND_SPEED);

                    player.setVelocity(newVel);
                }
            }

            // 2. Обработка выброшенных предметов (ItemEntity)
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (!item.isValid()) continue;

                if (isExposedToWind(item.getLocation())) {
                    Vector currentVel = item.getVelocity();
                    Vector newVel = currentVel.add(itemPush);

                    limitHorizontalSpeed(newVel, MAX_WIND_SPEED);

                    item.setVelocity(newVel);
                }
            }
        }
    }

    /**
     * Ограничивает горизонтальную скорость вектора по осям X и Z.
     */
    private void limitHorizontalSpeed(Vector vector, double maxSpeed) {
        double horizontalSpeedSq = vector.getX() * vector.getX() + vector.getZ() * vector.getZ();
        if (horizontalSpeedSq > maxSpeed * maxSpeed) {
            double scale = maxSpeed / Math.sqrt(horizontalSpeedSq);
            vector.setX(vector.getX() * scale);
            vector.setZ(vector.getZ() * scale);
        }
    }

    /**
     * Проверяет, находится ли локация в Оверворлде и под открытым небом.
     */
    public boolean isExposedToWind(Location location) {
        World world = location.getWorld();
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }

        int highestBlockY = world.getHighestBlockYAt(location);
        return location.getBlockY() >= highestBlockY;
    }

    public boolean isStormActive() {
        return stormActive;
    }

    public WindDirection getCurrentDirection() {
        return currentDirection;
    }
}