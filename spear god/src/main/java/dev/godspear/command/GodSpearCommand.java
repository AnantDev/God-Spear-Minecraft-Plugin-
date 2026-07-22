package dev.godspear.command;

import dev.godspear.GodSpearPlugin;
import dev.godspear.model.SpearRecord;
import dev.godspear.model.SpearStage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GodSpearCommand implements CommandExecutor,TabCompleter {
    private final GodSpearPlugin plugin;private final Map<UUID,Long> confirms=new ConcurrentHashMap<>();
    public GodSpearCommand(GodSpearPlugin plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("help")){help(sender);return true;}
        if(sub.equals("info")){Player p=args.length>1&&sender.hasPermission("godspear.admin")?Bukkit.getPlayer(args[1]):sender instanceof Player x?x:null;if(p==null){sender.sendMessage("§cPlayer must be online.");return true;}info(sender,p);return true;}
        if(sub.equals("destroy")){if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}destroy(p,args);return true;}
        if(!sender.hasPermission("godspear."+sub)&&!sender.hasPermission("godspear.admin")){sender.sendMessage("§cNo permission.");return true;}
        if(sub.equals("reload")){plugin.reloadConfig();sender.sendMessage("§aGodSpear configuration reloaded.");return true;}
        if(sub.equals("debug")){sender.sendMessage("§7GodSpear "+plugin.getPluginMeta().getVersion()+" | MC "+Bukkit.getMinecraftVersion()+" | "+Bukkit.getName()+" | Folia="+plugin.scheduler().isFolia()+" | cached="+Bukkit.getOnlinePlayers().stream().filter(p->plugin.spears().cached(p.getUniqueId()).isPresent()).count());return true;}
        if(args.length<2){sender.sendMessage("§cUsage: /godspear "+sub+" <player>");return true;}Player target=Bukkit.getPlayer(args[1]);if(target==null){sender.sendMessage("§cPlayer must be online.");return true;}
        switch(sub){
            case "give"->give(sender,target);
            case "recover"->recover(sender,target);
            case "remove"->remove(sender,target);
            case "reset"->modify(sender,target,r->new SpearRecord(r.spearId(),r.ownerId(),target.getName(),SpearStage.WOOD,0,r.createdAt(),plugin.getPluginMeta().getVersion(),false));
            case "setstage"->{if(args.length<3){sender.sendMessage("§cSpecify a stage.");break;}try{SpearStage s=SpearStage.parse(args[2]);modify(sender,target,r->r.withProgress(s,r.kills()));}catch(IllegalArgumentException e){sender.sendMessage("§cInvalid stage.");}}
            case "setkills"->{if(args.length<3){sender.sendMessage("§cSpecify kills.");break;}try{int k=Math.max(0,Integer.parseInt(args[2]));modify(sender,target,r->r.withProgress(r.stage(),k));}catch(NumberFormatException e){sender.sendMessage("§cInvalid amount.");}}
            case "validate"->{validate(target);sender.sendMessage("§aValidation complete for "+target.getName()+".");}
            default->help(sender);
        }return true;
    }
    private void help(CommandSender s){s.sendMessage("§6§lGod Spear §7commands\n§e/godspear info [player]\n§e/godspear destroy [confirm]\n§e/godspear give|recover|reset|remove <player>\n§e/godspear setstage <player> <stage>\n§e/godspear setkills <player> <amount>\n§e/godspear reload|validate <player>|debug");}
    private void info(CommandSender s,Player p){plugin.spears().cached(p.getUniqueId()).ifPresentOrElse(r->s.sendMessage("§6God Spear §8— §f"+p.getName()+"\n§7UUID: §f"+r.spearId()+"\n§7Stage: §f"+r.stage()+"\n§7Kills: §f"+r.kills()+"\n§7Created: §f"+r.createdAt()+"\n§7Destroyed: §f"+r.destroyed()),()->s.sendMessage("§cNo spear record loaded."));}
    private void give(CommandSender s,Player p){if(plugin.spears().cached(p.getUniqueId()).filter(r->!r.destroyed()).isPresent()){s.sendMessage("§cThat player already owns a spear.");return;}SpearRecord r=plugin.spears().create(p);plugin.spears().cache(r);plugin.database().save(r);plugin.spears().giveOrQueue(p,r);s.sendMessage("§aCreated a unique spear for "+p.getName()+".");}
    private void recover(CommandSender s,Player p){SpearRecord r=plugin.spears().cached(p.getUniqueId()).orElse(null);if(r==null||r.destroyed()){s.sendMessage("§cNo active record to recover.");return;}for(int slot:plugin.spears().allSpearSlots(p))p.getInventory().setItem(slot,null);plugin.spears().giveOrQueue(p,r);s.sendMessage("§aRecovered the registered spear.");}
    private void remove(CommandSender s,Player p){SpearRecord r=plugin.spears().cached(p.getUniqueId()).orElse(null);if(r==null){s.sendMessage("§cNo spear.");return;}for(int slot:plugin.spears().allSpearSlots(p))p.getInventory().setItem(slot,null);plugin.spears().cache(r.markDestroyed());plugin.database().destroy(r);s.sendMessage("§aSpear removed and ownership tombstoned.");}
    private void modify(CommandSender s,Player p,java.util.function.UnaryOperator<SpearRecord> f){SpearRecord r=plugin.spears().cached(p.getUniqueId()).orElse(null);if(r==null||r.destroyed()){s.sendMessage("§cNo active spear.");return;}SpearRecord n=f.apply(r);plugin.spears().cache(n);plugin.spears().updateInventory(p,n);plugin.database().save(n);s.sendMessage("§aUpdated "+p.getName()+"'s spear.");}
    private void validate(Player p){SpearRecord r=plugin.spears().cached(p.getUniqueId()).orElse(null);if(r==null)return;boolean kept=false;for(int slot:plugin.spears().allSpearSlots(p)){ItemStack i=p.getInventory().getItem(slot);if(!kept&&plugin.spears().itemId(i).filter(r.spearId()::equals).isPresent()){kept=true;p.getInventory().setItem(slot,plugin.spears().build(r,i));}else p.getInventory().setItem(slot,null);}if(!kept)plugin.spears().giveOrQueue(p,r);}
    private void destroy(Player p,String[] args){SpearRecord r=plugin.spears().cached(p.getUniqueId()).orElse(null);if(r==null||r.destroyed()){p.sendMessage("§cYou do not own a spear.");return;}if(args.length<2||!args[1].equalsIgnoreCase("confirm")){confirms.put(p.getUniqueId(),System.currentTimeMillis()+30000);p.sendMessage("§cRun §f/godspear destroy confirm §cwithin 30 seconds. This cannot be undone.");return;}Long until=confirms.remove(p.getUniqueId());if(until==null||until<System.currentTimeMillis()){p.sendMessage("§cConfirmation expired.");return;}int slot=plugin.spears().find(p,r.spearId());if(slot<0){p.sendMessage("§cYour registered spear must be in your inventory.");return;}ItemStack visual=p.getInventory().getItem(slot).clone();p.getInventory().setItem(slot,null);p.getWorld().spawnParticle(Particle.PORTAL,p.getLocation().add(0,1,0),80,0.4,1,0.4,0.1);plugin.spears().cache(r.markDestroyed());plugin.database().destroy(r);p.sendMessage("§4Your Special Spear fell into the void and was permanently destroyed.");plugin.getLogger().warning(p.getName()+" intentionally destroyed spear "+r.spearId()+" (visual="+visual.getType()+")");}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1)return filter(List.of("help","info","destroy","give","recover","reset","remove","setstage","setkills","reload","validate","debug"),args[0]);if(args.length==2&&Set.of("give","recover","reset","remove","setstage","setkills","validate","info").contains(args[0].toLowerCase()))return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),args[1]);if(args.length==3&&args[0].equalsIgnoreCase("setstage"))return filter(List.of("wood","stone","iron","diamond","netherite","god"),args[2]);return List.of();}
    private List<String> filter(List<String> in,String prefix){return in.stream().filter(x->x.regionMatches(true,0,prefix,0,prefix.length())).toList();}
}
