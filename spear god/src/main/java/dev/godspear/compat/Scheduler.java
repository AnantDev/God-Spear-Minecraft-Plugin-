package dev.godspear.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class Scheduler {
    private final Plugin plugin;
    private final boolean folia;
    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
        boolean found;
        try { Class.forName("io.papermc.paper.threadedregions.RegionizedServer"); found = true; } catch (ClassNotFoundException e) { found = false; }
        folia = found;
    }
    public boolean isFolia() { return folia; }
    public void entity(Entity entity, Runnable task) {
        if (!folia) { Bukkit.getScheduler().runTask(plugin, task); return; }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method run = scheduler.getClass().getMethod("run", Plugin.class, java.util.function.Consumer.class, Runnable.class);
            run.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run(), null);
        } catch (ReflectiveOperationException e) { plugin.getLogger().warning("Entity scheduler unavailable: " + e.getMessage()); }
    }
    public void entityLater(Entity entity, Runnable task, long ticks) {
        if (!folia) { Bukkit.getScheduler().runTaskLater(plugin, task, ticks); return; }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method run = scheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class);
            run.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run(), null, ticks);
        } catch (ReflectiveOperationException e) { plugin.getLogger().warning("Delayed entity scheduler unavailable: " + e.getMessage()); }
    }
}
