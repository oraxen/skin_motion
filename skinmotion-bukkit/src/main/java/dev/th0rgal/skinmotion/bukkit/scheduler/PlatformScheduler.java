package dev.th0rgal.skinmotion.bukkit.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Schedules work on the right thread for the running server.
 * Folia uses the global region, entity and async schedulers; every other server uses the Bukkit scheduler.
 */
public final class PlatformScheduler {

    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private final Plugin plugin;

    public PlatformScheduler(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Run a task that touches global server state (main thread, or the global region on Folia).
     */
    public void runGlobal(@NotNull Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a task on the thread owning {@code entity}. The task is dropped if the entity is removed first.
     */
    public void runForEntity(@NotNull Entity entity, @NotNull Runnable task) {
        runForEntityLater(entity, task, 0L);
    }

    /**
     * Run a delayed task on the thread owning {@code entity}. The task is dropped if the entity is removed first.
     */
    public void runForEntityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        if (FOLIA) {
            if (delayTicks <= 0) {
                entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
            } else {
                entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
            }
        } else if (delayTicks <= 0) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public void runAsync(@NotNull Runnable task) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Run a repeating task on the main thread (global region on Folia).
     */
    @NotNull
    public Cancellable runGlobalTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, scheduledTask -> task.run(), Math.max(1L, delayTicks), periodTicks);
            return scheduled::cancel;
        }
        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return scheduled::cancel;
    }

    /**
     * Whether the current thread may safely access {@code entity}.
     */
    public boolean isOwnedByCurrentThread(@NotNull Entity entity) {
        return FOLIA ? Bukkit.isOwnedByCurrentRegion(entity) : Bukkit.isPrimaryThread();
    }

    public void cancelAll() {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @FunctionalInterface
    public interface Cancellable {
        void cancel();
    }
}
