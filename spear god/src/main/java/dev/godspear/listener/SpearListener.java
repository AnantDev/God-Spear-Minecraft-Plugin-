package dev.godspear.listener;

import dev.godspear.GodSpearPlugin;
import dev.godspear.model.SpearRecord;
import dev.godspear.model.SpearStage;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SpearListener implements Listener {
    private final GodSpearPlugin plugin;
    private final Map<UUID,ItemStack> deathItems=new ConcurrentHashMap<>();
    private final Map<UUID,SpearHit> spearHits=new ConcurrentHashMap<>();
    private record SpearHit(UUID killerId,UUID spearId,long time){}
    public SpearListener(GodSpearPlugin plugin){this.plugin=plugin;}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){load(e.getPlayer());}
    public void load(Player p){
        plugin.database().findOwner(p.getUniqueId()).whenComplete((found,error)->plugin.scheduler().entity(p,()->{
            if(!p.isOnline())return;if(error!=null){plugin.getLogger().severe("Could not load "+p.getName()+": "+error.getMessage());return;}
            SpearRecord r=found.orElse(null);
            if(r==null){plugin.database().isDestroyedOwner(p.getUniqueId()).thenAccept(destroyed->plugin.scheduler().entity(p,()->{if(!destroyed&&p.isOnline()&&plugin.getConfig().getBoolean("auto-give-on-first-join",true)){SpearRecord made=plugin.spears().create(p);plugin.spears().cache(made);plugin.database().save(made);if(!plugin.spears().giveOrQueue(p,made))plugin.retry(p);}}));return;}
            if(r==null||r.destroyed())return;plugin.spears().cache(r);validate(p,true);if(!plugin.spears().giveOrQueue(p,r))plugin.retry(p);
        }));
    }
    @EventHandler public void quit(PlayerQuitEvent e){plugin.spears().uncache(e.getPlayer().getUniqueId());plugin.dequeue(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(PlayerDropItemEvent e){if(plugin.spears().isSpear(e.getItemDrop().getItemStack()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p&&plugin.spears().isSpear(e.getItem().getItemStack())&&!plugin.spears().isExactOwnedSpear(p,e.getItem().getItemStack()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){if(!plugin.getConfig().getBoolean("allow-offhand",true)&&(plugin.spears().isSpear(e.getMainHandItem())||plugin.spears().isSpear(e.getOffHandItem())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void launch(ProjectileLaunchEvent e){if(e.getEntity().getShooter() instanceof Player p&&plugin.spears().isExactOwnedSpear(p,p.getInventory().getItemInMainHand()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!plugin.spears().isSpear(e.getOldCursor()))return;
        int top=e.getView().getTopInventory().getSize();if(e.getRawSlots().stream().anyMatch(s->s<top)||( !plugin.getConfig().getBoolean("allow-offhand",true)&&e.getRawSlots().contains(top+40)))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;int top=e.getView().getTopInventory().getSize();
        ItemStack current=e.getCurrentItem(),cursor=e.getCursor();boolean cur=plugin.spears().isSpear(current),held=plugin.spears().isSpear(cursor);
        if(cur&&!plugin.spears().itemOwner(current).filter(p.getUniqueId()::equals).isPresent()){e.setCancelled(true);return;}
        if((held&&e.getRawSlot()<top)||(cur&&e.getRawSlot()<top)||(cur&&e.isShiftClick())||(e.getClick()==ClickType.NUMBER_KEY&&plugin.spears().isSpear(p.getInventory().getItem(e.getHotbarButton())))||e.getClick()==ClickType.DOUBLE_CLICK&&(cur||held))e.setCancelled(true);
        if(!plugin.getConfig().getBoolean("allow-offhand",true)&&e.getSlotType()==InventoryType.SlotType.QUICKBAR&&e.getSlot()==40&&(cur||held))e.setCancelled(true);
        if(plugin.queued(p.getUniqueId())&&p.getInventory().firstEmpty()>=0)plugin.retry(p);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void creative(InventoryCreativeEvent e){if(plugin.spears().isSpear(e.getCursor())||plugin.spears().isSpear(e.getCurrentItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void death(PlayerDeathEvent e){
        Player p=e.getEntity();SpearRecord r=plugin.spears().cached(p.getUniqueId()).orElse(null);if(r==null)return;
        for(Iterator<ItemStack> it=e.getDrops().iterator();it.hasNext();){ItemStack i=it.next();if(plugin.spears().itemId(i).filter(r.spearId()::equals).isPresent()){deathItems.put(p.getUniqueId(),i.clone());it.remove();}}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void creditDeath(PlayerDeathEvent e){
        Player victim=e.getEntity(),killer=victim.getKiller();SpearHit hit=spearHits.remove(victim.getUniqueId());
        if(killer==null||hit==null||!killer.getUniqueId().equals(hit.killerId())||System.currentTimeMillis()-hit.time()>10000)return;
        SpearRecord owned=plugin.spears().cached(killer.getUniqueId()).orElse(null);
        if(owned==null||owned.destroyed()||!owned.spearId().equals(hit.spearId()))return;
        if(plugin.getConfig().getBoolean("anti-farming.block-npcs",true)&&victim.hasMetadata("NPC"))return;
        if(plugin.getConfig().getBoolean("anti-farming.block-fake-players",true)&&(!victim.isConnected()||victim.getAddress()==null))return;
        long minTicks=plugin.getConfig().getLong("anti-farming.minimum-victim-playtime-minutes",30)*1200L;if(victim.getStatistic(Statistic.PLAY_ONE_MINUTE)<minTicks)return;
        String ki=ip(killer),vi=ip(victim);if(plugin.getConfig().getBoolean("anti-farming.check-ip",false)&&ki!=null&&ki.equals(vi))return;
        long now=System.currentTimeMillis(),cooldown=plugin.getConfig().getLong("anti-farming.same-victim-cooldown-seconds",3600)*1000L;
        long day=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();int max=plugin.getConfig().getInt("anti-farming.daily-victim-limit",3);
        CompletableFuture<Long> recent=plugin.database().victimCount(killer.getUniqueId(),victim.getUniqueId(),now-cooldown);
        CompletableFuture<Long> daily=plugin.database().victimCount(killer.getUniqueId(),victim.getUniqueId(),day);
        recent.thenCombine(daily,(recentCount,dailyCount)->recentCount==0&&dailyCount<max)
                .thenAccept(valid->{if(valid)plugin.scheduler().entity(killer,()->progress(killer,victim,hit.spearId(),ki,vi));})
                .exceptionally(error->{plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not process spear kill for "+killer.getName(),error);return null;});
    }
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e){Player p=e.getPlayer();plugin.scheduler().entityLater(p,()->{SpearRecord r=plugin.spears().cached(p.getUniqueId()).orElse(null);if(r==null)return;ItemStack exact=deathItems.remove(p.getUniqueId());if(exact!=null&&p.getInventory().firstEmpty()>=0)p.getInventory().addItem(exact);else if(plugin.spears().find(p,r.spearId())<0){plugin.queue(p.getUniqueId());plugin.retry(p);}},1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spearHit(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player killer)||!(e.getEntity() instanceof Player victim)||killer.equals(victim))return;
        ItemStack weapon=killer.getInventory().getItemInMainHand();if(!plugin.spears().isExactOwnedSpear(killer,weapon))return;
        plugin.spears().itemId(weapon).ifPresent(id->spearHits.put(victim.getUniqueId(),new SpearHit(killer.getUniqueId(),id,System.currentTimeMillis())));
    }
    private void progress(Player k,Player v,UUID creditedSpear,String ki,String vi){
        SpearRecord old=plugin.spears().cached(k.getUniqueId()).orElse(null);if(old==null||old.destroyed()||!old.spearId().equals(creditedSpear))return;
        int kills=old.kills()+1;SpearStage next=stageFor(kills);SpearRecord updated=old.withProgress(next,kills);plugin.spears().cache(updated);plugin.spears().updateInventory(k,updated);
        plugin.database().save(updated).thenCompose(ignored->plugin.database().recordKill(k.getUniqueId(),v.getUniqueId(),ki,vi)).exceptionally(error->{plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save spear progression for "+k.getName(),error);return null;});
        if(next!=old.stage())k.sendMessage("§6God Spear §8» §aYour spear evolved to §f"+next+"§a! Kills: "+kills);else k.sendMessage("§6God Spear §8» §aKill credited. §f"+kills+" §akills.");
    }
    private SpearStage stageFor(int kills){
        if(kills>=plugin.getConfig().getInt("progression.god",5))return SpearStage.GOD;
        if(kills>=plugin.getConfig().getInt("progression.netherite",4))return SpearStage.NETHERITE;
        if(kills>=plugin.getConfig().getInt("progression.diamond",3))return SpearStage.DIAMOND;
        if(kills>=plugin.getConfig().getInt("progression.iron",2))return SpearStage.IRON;
        if(kills>=plugin.getConfig().getInt("progression.stone",1))return SpearStage.STONE;
        return SpearStage.WOOD;
    }
    private String ip(Player p){return p.getAddress()==null?null:p.getAddress().getAddress().getHostAddress();}
    public void validate(Player p,boolean repair){
        SpearRecord r=plugin.spears().cached(p.getUniqueId()).orElse(null);if(r==null)return;boolean kept=false;
        for(int slot:plugin.spears().allSpearSlots(p)){ItemStack item=p.getInventory().getItem(slot);boolean exact=plugin.spears().itemId(item).filter(r.spearId()::equals).isPresent()&&!kept;if(exact){kept=true;if(repair&&!plugin.spears().isExactOwnedSpear(p,item))p.getInventory().setItem(slot,plugin.spears().build(r,item));}else p.getInventory().setItem(slot,null);}
        if(!kept&&repair)plugin.spears().giveOrQueue(p,r);
    }
}
