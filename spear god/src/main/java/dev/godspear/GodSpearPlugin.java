package dev.godspear;

import dev.godspear.command.GodSpearCommand;
import dev.godspear.compat.Scheduler;
import dev.godspear.listener.SpearListener;
import dev.godspear.service.SpearService;
import dev.godspear.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GodSpearPlugin extends JavaPlugin {
    private Database database; private SpearService spears; private Scheduler scheduler;
    private final Set<UUID> queued=ConcurrentHashMap.newKeySet();
    @Override public void onEnable(){
        saveDefaultConfig();for(String f:new String[]{"messages.yml","storage.yml","effects.yml","sounds.yml"})if(!new File(getDataFolder(),f).exists())saveResource(f,false);
        scheduler=new Scheduler(this);spears=new SpearService(this);database=new Database(this);
        try{database.open();}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Database unavailable; disabling safely",e);Bukkit.getPluginManager().disablePlugin(this);return;}
        String mc=Bukkit.getMinecraftVersion();String software=Bukkit.getName()+" / "+Bukkit.getVersion();getLogger().info("Detected "+software+", Minecraft "+mc+", Folia="+scheduler.isFolia());
        if(!getConfig().getStringList("tested-minecraft").contains(mc))getLogger().warning("Minecraft "+mc+" is untested. GodSpear will use compatibility fallbacks and fail safely.");
        SpearListener listener=new SpearListener(this);Bukkit.getPluginManager().registerEvents(listener,this);
        GodSpearCommand command=new GodSpearCommand(this);
        PluginCommand godSpearCommand=getCommand("godspear");
        if(godSpearCommand==null){
            getLogger().severe("Command 'godspear' is missing from plugin.yml; disabling safely.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        godSpearCommand.setExecutor(command);godSpearCommand.setTabCompleter(command);
        Bukkit.getOnlinePlayers().forEach(listener::load);
    }
    @Override public void onDisable(){if(database!=null)database.close();queued.clear();}
    public Database database(){return database;}public SpearService spears(){return spears;}public Scheduler scheduler(){return scheduler;}
    public void queue(UUID id){queued.add(id);}public void dequeue(UUID id){queued.remove(id);}public boolean queued(UUID id){return queued.contains(id);}
    public void retry(Player p){scheduler.entityLater(p,()->{if(p.isOnline()&&queued(p.getUniqueId()))spears.cached(p.getUniqueId()).ifPresent(r->{if(!r.destroyed())spears.giveOrQueue(p,r);});},getConfig().getLong("inventory-retry-ticks",100));}
}
