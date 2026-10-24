package com.example.maltesewind;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class WindManager {

    public enum WindDirection {
        NORTH("на север ⬆", 0, -1),
        NORTHEAST("на северо-восток ⬈", 1, -1),
        EAST("на восток ➡", 1, 0),
        SOUTHEAST("на юго-восток ⬊", 1, 1),
        SOUTH("на юг ⬇", 0, 1),
        SOUTHWEST("на юго-запад ⬋", -1, 1),
        WEST("на запад ⬅", -1, 0),
        NORTHWEST("на северо-запад ⬉", -1, -1);

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

    private WindDirection currentDirection = WindDirection.NORTHEAST;

    // Поля для BossBar и отслеживания времени бури
    private BossBar stormBossBar;
    private long stormTotalTicks;
    private long stormRemainingTicks;
    private int tickCounter = 0;

    // Настройки из config.yml
    private int minCalmMinutes;
    private int maxCalmMinutes;
    private int minStormMinutes;
    private int maxStormMinutes;

    private double playerWindStrength;
    private double itemWindStrength;
    private double sneakMultiplier;
    private double maxWindSpeed;

    private boolean particlesEnabled;
    private int particlesAmount;
    private boolean enableHeavySmoke;

    private boolean soundsEnabled;
    private float soundVolume;
    private boolean bossBarEnabled;

    public WindManager(MalteseWind plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Загружает или перезагружает параметры из config.yml.
     */
    public void loadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.minCalmMinutes = Math.max(1, config.getInt("timers.calm-min-minutes", 20));
        this.maxCalmMinutes = Math.max(this.minCalmMinutes, config.getInt("timers.calm-max-minutes", 40));
        this.minStormMinutes = Math.max(1, config.getInt("timers.storm-min-minutes", 3));
        this.maxStormMinutes = Math.max(this.minStormMinutes, config.getInt("timers.storm-max-minutes", 5));

        this.playerWindStrength = config.getDouble("physics.player-wind-strength", 0.16);
        this.itemWindStrength = config.getDouble("physics.item-wind-strength", 0.12);
        this.sneakMultiplier = config.getDouble("physics.sneak-multiplier", 0.6);
        this.maxWindSpeed = config.getDouble("physics.max-wind-speed", 1.35);

        this.particlesEnabled = config.getBoolean("particles.enabled", true);
        this.particlesAmount = Math.max(0, config.getInt("particles.amount-per-tick", 6));
        this.enableHeavySmoke = config.getBoolean("particles.enable-heavy-smoke", true);

        this.soundsEnabled = config.getBoolean("sounds.enabled", true);
        this.soundVolume = (float) Math.max(0.0, Math.min(1.0, config.getDouble("sounds.volume", 0.65)));
        this.bossBarEnabled = config.getBoolean("bossbar.enabled", true);

        // Если во время активного шторма в конфиге выключили BossBar — сразу прячем его
        if (!this.bossBarEnabled) {
            hideBossBarFromAll();
        }
    }

    public void startCycle() {
        scheduleNextStorm();
    }

    public void shutdown() {
        cancelCycleTask();
        stopPhysicsTicker();
        hideBossBarFromAll();
        stormActive = false;
    }

    private void cancelCycleTask() {
        if (cycleTask != null && !cycleTask.isCancelled()) {
            cycleTask.cancel();
            cycleTask = null;
        }
    }

    private void scheduleNextStorm() {
        cancelCycleTask();
        int delayMinutes = ThreadLocalRandom.current().nextInt(minCalmMinutes, maxCalmMinutes + 1);
        long delayTicks = delayMinutes * 60L * 20L;

        cycleTask = Bukkit.getScheduler().runTaskLater(plugin, this::startStorm, delayTicks);
    }

    public void startStorm() {
        cancelCycleTask();
        stormActive = true;
        currentDirection = WindDirection.getRandom();
        tickCounter = 0;

        int durationMinutes = ThreadLocalRandom.current().nextInt(minStormMinutes, maxStormMinutes + 1);
        this.stormTotalTicks = durationMinutes * 60L * 20L;
        this.stormRemainingTicks = this.stormTotalTicks;

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration((int) stormTotalTicks);
            }
        }

        // Инициализация BossBar
        if (bossBarEnabled) {
            createAndShowBossBar();
        }

        Component startMsg = Component.text("⚠️ Внимание! ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("Надвигается штормовой ветер 100 км/ч (дует ", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text(currentDirection.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(")! Ищите укрытие!", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, false));
        Bukkit.broadcast(startMsg);

        startPhysicsTicker();

        cycleTask = Bukkit.getScheduler().runTaskLater(plugin, this::stopStorm, stormTotalTicks);
    }

    public void stopStorm() {
        cancelCycleTask();
        stormActive = false;
        stopPhysicsTicker();
        hideBossBarFromAll();

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

    private void startPhysicsTicker() {
        stopPhysicsTicker();
        physicsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickWindPhysics, 0L, 2L);
    }

    private void stopPhysicsTicker() {
        if (physicsTask != null && !physicsTask.isCancelled()) {
            physicsTask.cancel();
            physicsTask = null;
        }
    }

    /**
     * Основной цикл шторма (вызывается каждые 2 тика = 0.1 сек).
     */
    private void tickWindPhysics() {
        if (!stormActive) return;

        tickCounter++;
        stormRemainingTicks = Math.max(0, stormRemainingTicks - 2);

        // Обновляем BossBar раз в секунду (каждые 10 вызовов по 2 тика = 20 тиков)
        if (bossBarEnabled && tickCounter % 10 == 0) {
            updateBossBar();
        }

        // Проверяем, нужно ли проиграть звук ветра в этом тике (каждые 40 тиков = 2 секунды)
        boolean playSoundThisTick = soundsEnabled && (tickCounter % 20 == 1);

        Vector windVector = currentDirection.getVector();
        Vector playerPush = windVector.clone().multiply(playerWindStrength);
        Vector itemPush = windVector.clone().multiply(itemWindStrength).setY(0.04);

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }

            // 1. Обработка игроков в Оверворлде
            for (Player player : world.getPlayers()) {
                if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }

                if (isExposedToWind(player.getLocation())) {
                    // Визуальные частицы
                    if (particlesEnabled && particlesAmount > 0) {
                        spawnWindParticles(player, windVector);
                    }

                    // Звук завывания бури
                    if (playSoundThisTick) {
                        playWindHowlSound(player);
                    }

                    // Физический снос ветром
                    if (!(player.getGameMode() == GameMode.CREATIVE && player.isFlying())) {
                        Vector currentVel = player.getVelocity();
                        Vector appliedPush = player.isSneaking() ? playerPush.clone().multiply(sneakMultiplier) : playerPush;
                        Vector newVel = currentVel.add(appliedPush);

                        limitHorizontalSpeed(newVel, maxWindSpeed);
                        player.setVelocity(newVel);
                    }
                }
            }

            // 2. Обработка выброшенных предметов
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (!item.isValid()) continue;

                if (isExposedToWind(item.getLocation())) {
                    Vector currentVel = item.getVelocity();
                    Vector newVel = currentVel.add(itemPush);

                    limitHorizontalSpeed(newVel, maxWindSpeed);
                    item.setVelocity(newVel);
                }
            }
        }
    }

    /**
     * Проигрывает атмосферный звук завывания ветра только для конкретного игрока.
     */
    private void playWindHowlSound(Player player) {
        Location loc = player.getLocation();
        // Низкий гул ветра (звук полета на элитрах с низкой тональностью)
        player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, SoundCategory.WEATHER, soundVolume, 0.55f);
        // Свистящий порыв воздуха (звук Бриза)
        player.playSound(loc, Sound.ENTITY_BREEZE_IDLE_AIR, SoundCategory.WEATHER, soundVolume * 0.8f, 0.6f);
    }

    /**
     * Создает BossBar при старте шторма.
     */
    private void createAndShowBossBar() {
        hideBossBarFromAll();
        stormBossBar = BossBar.bossBar(
                buildBossBarTitle(),
                1.0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.NOTCHED_10
        );
        updateBossBarViewers();
    }

    /**
     * Обновляет текст, шкалу времени и список зрителей BossBar.
     */
    private void updateBossBar() {
        if (stormBossBar == null) {
            createAndShowBossBar();
            return;
        }

        float progress = stormTotalTicks > 0
                ? Math.max(0.0f, Math.min(1.0f, (float) stormRemainingTicks / stormTotalTicks))
                : 0.0f;

        stormBossBar.progress(progress);
        stormBossBar.name(buildBossBarTitle());
        updateBossBarViewers();
    }

    /**
     * Показывает BossBar игрокам в Оверворлде и скрывает у тех, кто ушел в Незер/Энд.
     */
    private void updateBossBarViewers() {
        if (stormBossBar == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
                player.showBossBar(stormBossBar);
            } else {
                player.hideBossBar(stormBossBar);
            }
        }
    }

    /**
     * Убирает BossBar у всех игроков на сервере.
     */
    private void hideBossBarFromAll() {
        if (stormBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(stormBossBar);
            }
            stormBossBar = null;
        }
    }

    /**
     * Формирует заголовок BossBar с направлением и обратным отсчетом.
     */
    private Component buildBossBarTitle() {
        long totalSeconds = stormRemainingTicks / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        String timeFormatted = String.format("%02d:%02d", minutes, seconds);

        return Component.text("🌪 Шторм 100 км/ч ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("| Дует ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(currentDirection.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" | Осталось: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(timeFormatted, NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    private void spawnWindParticles(Player player, Vector windDir) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location baseLoc = player.getLocation();

        double upwindOffsetX = -windDir.getX() * 6.0;
        double upwindOffsetZ = -windDir.getZ() * 6.0;

        for (int i = 0; i < particlesAmount; i++) {
            double rx = upwindOffsetX + random.nextDouble(-6.0, 6.0);
            double ry = random.nextDouble(0.2, 3.5);
            double rz = upwindOffsetZ + random.nextDouble(-6.0, 6.0);

            Location spawnLoc = baseLoc.clone().add(rx, ry, rz);

            player.spawnParticle(
                    Particle.CLOUD,
                    spawnLoc,
                    0,
                    windDir.getX(),
                    -0.05,
                    windDir.getZ(),
                    0.85
            );

            if (enableHeavySmoke && i % 3 == 0) {
                player.spawnParticle(
                        Particle.CAMPFIRE_COSY_SMOKE,
                        spawnLoc,
                        0,
                        windDir.getX(),
                        -0.02,
                        windDir.getZ(),
                        0.45
                );
            }
        }
    }

    private void limitHorizontalSpeed(Vector vector, double maxSpeed) {
        double horizontalSpeedSq = vector.getX() * vector.getX() + vector.getZ() * vector.getZ();
        if (horizontalSpeedSq > maxSpeed * maxSpeed) {
            double scale = maxSpeed / Math.sqrt(horizontalSpeedSq);
            vector.setX(vector.getX() * scale);
            vector.setZ(vector.getZ() * scale);
        }
    }

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