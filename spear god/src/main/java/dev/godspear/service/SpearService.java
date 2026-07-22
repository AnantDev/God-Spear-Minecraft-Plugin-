package dev.godspear.service;

import dev.godspear.GodSpearPlugin;
import dev.godspear.model.SpearRecord;
import dev.godspear.model.SpearStage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SpearService {
    private final GodSpearPlugin plugin;
    private final Map<UUID, SpearRecord> owners = new ConcurrentHashMap<>();
    private final NamespacedKey marker, spearId, ownerId, ownerName, kills, stage, created, version;
    public SpearService(GodSpearPlugin plugin) {
        this.plugin=plugin; marker=key("marker");spearId=key("spear_uuid");ownerId=key("owner_uuid");ownerName=key("owner_name");kills=key("kills");stage=key("stage");created=key("created_at");version=key("plugin_version");
    }
    private NamespacedKey key(String k){return new NamespacedKey(plugin,k);}
    public Optional<SpearRecord> cached(UUID owner){return Optional.ofNullable(owners.get(owner));}
    public void cache(SpearRecord record){owners.put(record.ownerId(),record);}
    public void uncache(UUID owner){owners.remove(owner);}
    public boolean isSpear(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(marker,PersistentDataType.BYTE);}
    public Optional<UUID> itemOwner(ItemStack item){return uuid(item,ownerId);}
    public Optional<UUID> itemId(ItemStack item){return uuid(item,spearId);}
    private Optional<UUID> uuid(ItemStack item, NamespacedKey key){
        if(!isSpear(item))return Optional.empty(); String v=item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);
        try{return Optional.of(UUID.fromString(v));}catch(Exception e){return Optional.empty();}
    }
    public boolean isExactOwnedSpear(Player p,ItemStack item){
        SpearRecord r=owners.get(p.getUniqueId());
        return r!=null&&!r.destroyed()&&itemOwner(item).filter(p.getUniqueId()::equals).isPresent()&&itemId(item).filter(r.spearId()::equals).isPresent()&&validatePdc(item,r);
    }
    private boolean validatePdc(ItemStack item,SpearRecord r){
        PersistentDataContainer p=item.getItemMeta().getPersistentDataContainer();
        return Objects.equals(p.get(stage,PersistentDataType.STRING),r.stage().name())&&Objects.equals(p.get(kills,PersistentDataType.INTEGER),r.kills());
    }
    public SpearRecord create(Player p){return new SpearRecord(UUID.randomUUID(),p.getUniqueId(),p.getName(),SpearStage.WOOD,0,Instant.now(),plugin.getPluginMeta().getVersion(),false);}
    public ItemStack build(SpearRecord r,ItemStack old){
        ItemStack out=new ItemStack(r.stage().material()); ItemMeta meta=out.getItemMeta();
        if(old!=null&&old.hasItemMeta()){
            ItemMeta om=old.getItemMeta(); om.getEnchants().forEach((e,l)->meta.addEnchant(e,l,true));
        }
        meta.displayName(Component.text(r.stage()==SpearStage.GOD?"God Spear":pretty(r.stage())+" Special Spear",r.stage()==SpearStage.GOD?NamedTextColor.GOLD:NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Soulbound to "+r.ownerName(),NamedTextColor.GRAY),Component.text("Kills: "+r.kills(),NamedTextColor.YELLOW),Component.text("UUID: "+r.spearId(),NamedTextColor.DARK_GRAY)));
        meta.setUnbreakable(true); meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        PersistentDataContainer p=meta.getPersistentDataContainer();p.set(marker,PersistentDataType.BYTE,(byte)1);p.set(spearId,PersistentDataType.STRING,r.spearId().toString());p.set(ownerId,PersistentDataType.STRING,r.ownerId().toString());p.set(ownerName,PersistentDataType.STRING,r.ownerName());p.set(kills,PersistentDataType.INTEGER,r.kills());p.set(stage,PersistentDataType.STRING,r.stage().name());p.set(created,PersistentDataType.LONG,r.createdAt().toEpochMilli());p.set(version,PersistentDataType.STRING,r.pluginVersion());
        if(r.stage()==SpearStage.GOD){add(meta,"minecraft:sharpness",plugin.getConfig().getInt("god.sharpness-level",7));add(meta,"minecraft:lunge",plugin.getConfig().getInt("god.lunge-level",5));}
        out.setItemMeta(meta);return out;
    }
    private void add(ItemMeta m,String key,int level){Enchantment e=Registry.ENCHANTMENT.get(NamespacedKey.fromString(key));if(e!=null)m.addEnchant(e,level,true);else plugin.getLogger().warning("Enchantment unavailable: "+key);}
    private String pretty(SpearStage s){String n=s.name().toLowerCase(Locale.ROOT);return Character.toUpperCase(n.charAt(0))+n.substring(1);}
    public int find(Player p,UUID id){ItemStack[] c=p.getInventory().getContents();for(int i=0;i<c.length;i++)if(itemId(c[i]).filter(id::equals).isPresent())return i;return -1;}
    public List<Integer> allSpearSlots(Player p){List<Integer> result=new ArrayList<>();ItemStack[] c=p.getInventory().getContents();for(int i=0;i<c.length;i++)if(isSpear(c[i]))result.add(i);return result;}
    public boolean giveOrQueue(Player p,SpearRecord r){
        if(find(p,r.spearId())>=0)return true; if(p.getInventory().firstEmpty()<0){plugin.queue(p.getUniqueId());return false;}
        p.getInventory().addItem(build(r,null));plugin.dequeue(p.getUniqueId());return true;
    }
    public ItemStack updateInventory(Player p,SpearRecord r){int slot=find(p,r.spearId());if(slot<0)return null;ItemStack old=p.getInventory().getItem(slot);ItemStack updated=build(r,old);p.getInventory().setItem(slot,updated);return updated;}
}
