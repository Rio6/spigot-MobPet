package net.rio;

import org.bukkit.configuration.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.World;

import net.minecraft.server.v1_11_R1.EntityInsentient;
import net.minecraft.server.v1_11_R1.EntityCreature;
import net.minecraft.server.v1_11_R1.EntityLiving;
import net.minecraft.server.v1_11_R1.EntityHuman;
import net.minecraft.server.v1_11_R1.IRangedEntity;
import net.minecraft.server.v1_11_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_11_R1.PathfinderGoalLookAtPlayer;
import net.minecraft.server.v1_11_R1.PathfinderGoalMeleeAttack;
import net.minecraft.server.v1_11_R1.PathfinderGoalArrowAttack;
import org.bukkit.craftbukkit.v1_11_R1.entity.CraftEntity;

import com.google.common.collect.Sets;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Pet implements Listener {

    public final static String configPath = "pets";

    private JavaPlugin plugin;

    public Pet(JavaPlugin plugin) {
        this.plugin = plugin;

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for(String k : getPetList()) {

                Entity pet = getEntityById(k);
                Entity owner = plugin.getServer().getPlayer(UUID.fromString(((String) plugin.getConfig().get(configPath + "." + k))));

                if(pet != null && owner != null) {

                    EntityInsentient NMSPet = (EntityInsentient) ((CraftEntity) pet).getHandle();

                    Location ol = owner.getLocation();
                    Location pl = pet.getLocation();
                    
                    if(ol.getWorld() != pl.getWorld()) {
                        pet.teleport(owner);
                        return;
                    }

                    double walk = 8;
                    double tele = 16;
                    double distance = ol.distance(pl);

                    if(distance > tele) {
                        pet.teleport(owner);
                    } else if(distance > walk) {
                        NMSPet.getNavigation().a(ol.getX(), ol.getY(), ol.getZ(), 1.2D);
                    }
                }
            }
        }, 0, 1);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent eve) {
        Entity victim = eve.getEntity();
        Entity damager = eve.getDamager();

        if(damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if(shooter instanceof Entity) {
                damager = (Entity) shooter;
            }
        }

        if(victim instanceof Player && damager instanceof Player) {
            attackEntity((Player) victim, damager);
            attackEntity((Player) damager, victim);
        } else if(victim instanceof LivingEntity && damager instanceof Player) {
            attackEntity((Player) damager, victim);
        } else if(victim instanceof Player && damager instanceof LivingEntity) {
            if(victim.getUniqueId().toString().equals((String) plugin.getConfig().get(configPath + "." + damager.getUniqueId().toString()))) {
                eve.setCancelled(true);
                return;
            } else {
                attackEntity((Player) victim, damager);
            }
        }
    }   

    @EventHandler
    public void onEntityDeath(EntityDeathEvent eve) {
        Entity target = eve.getEntity();
        for(String k : getPetList()) {
            Entity e = getEntityById(k);
            if(e != null) {
                EntityInsentient pet = (EntityInsentient) ((CraftEntity) e).getHandle();
                if(pet.getGoalTarget() == (EntityLiving) ((CraftEntity) target).getHandle()) {
                    pet.setGoalTarget(null);
                }
            }
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent eve) {
        Entity ent = eve.getEntity();
        if(ent instanceof Creature && getPetList().contains(ent.getUniqueId().toString())) {
            overrideBehavior((LivingEntity) ent);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent eve) {
        Player owner = eve.getPlayer();
        Entity pet = eve.getRightClicked();

        if(pet instanceof Player) return;

        if(owner.hasPermission("pet.ownpet")) {
            if((owner.getInventory().getItemInMainHand().getType() == Material.MELON ||
                        owner.getInventory().getItemInOffHand().getType() == Material.MELON) &&
                    pet instanceof LivingEntity &&
                    !getPetList().contains(pet.getUniqueId().toString())) {
                if(pet instanceof Creature)
                    overrideBehavior((LivingEntity) pet);
                plugin.getConfig().set(configPath + "." + pet.getUniqueId().toString(), owner.getUniqueId().toString());
                for(int i = 0; i < 5; i++)
                    pet.getWorld().spawnParticle(Particle.HEART, pet.getLocation().add(Math.random() - .5, Math.random(), Math.random() -.5), 1);
                    }
        } else {
            owner.sendMessage("You don't have permission to own a pet");
        }
    }

    public Entity getEntityById(String id) {
        for(World w : plugin.getServer().getWorlds()) {
            List<Entity> ents = w.getEntities();
            for(Entity e : ents) {
                if(e.getUniqueId().toString().equals(id)) {
                    return e;
                }
            }
        }
        return null;
    }

    /*
     * Remove dead or non-exist entities from config
     */
    public void cleanPets() {
        for(String k : getPetList()) {
            Entity e = getEntityById(k);
            if(e == null || e.isDead()) {
                plugin.getLogger().info("Removing " + k);
                plugin.getConfig().set(configPath + "." + k, null);
            }
        }
    }

    /*
     * Returns a list of entities in config file
     */
    public List<String> getPetList() {
        List<String> list = new ArrayList<>();
        MemorySection memSec = (MemorySection) plugin.getConfig().get(configPath);
        if(memSec != null) {
            for(String k : memSec.getKeys(false)) {
                list.add(k);
            }
        }
        return list;
    }

    /*
     * Returns the owner id of the pet in config
     */
    public String getOwnerId(String petId) {
        return (String) plugin.getConfig().get(configPath + "." + petId);
    }

    private void overrideBehavior(LivingEntity ent) {
        EntityCreature c = (EntityCreature) ((EntityInsentient)((CraftEntity) ent).getHandle());

        //This gets the EntityCreature, we need it to change the values

        try {
            Field bField = PathfinderGoalSelector.class.getDeclaredField("b");
            bField.setAccessible(true);
            Field cField = PathfinderGoalSelector.class.getDeclaredField("c");
            cField.setAccessible(true);
            bField.set(c.goalSelector, Sets.newLinkedHashSet());
            bField.set(c.targetSelector, Sets.newLinkedHashSet());
            cField.set(c.goalSelector, Sets.newLinkedHashSet());
            cField.set(c.targetSelector, Sets.newLinkedHashSet());
            //this code clears fields B, C. so right now the mob wont walk
        } catch (Exception e) {e.printStackTrace();}

        c.goalSelector.a(8, new PathfinderGoalLookAtPlayer(c, EntityHuman.class, 0.0F));

        if(c instanceof IRangedEntity)
            c.goalSelector.a(2, new PathfinderGoalArrowAttack((IRangedEntity) c, 1.0D, 20, 16F));
        else
            c.goalSelector.a(2, new PathfinderGoalMeleeAttack(c, 1.0D, false));

        c.setGoalTarget(null, EntityTargetEvent.TargetReason.CUSTOM, false);

    }

    /*
     * Sends the pets of the player to attack an entity
     */
    private void attackEntity(Player player, Entity target) {
        if(player == null || target == null) return;
        for(String k : getPetList()) {
            Entity e = getEntityById(k);
            if( e != null && player.getUniqueId().toString().equals((String) plugin.getConfig().get(configPath + "." + k)) &&
                    !target.getUniqueId().toString().equals(k)) {
                ((EntityInsentient) ((CraftEntity) e).getHandle()).setGoalTarget((EntityLiving) ((CraftEntity) target).getHandle(),
                    EntityTargetEvent.TargetReason.CUSTOM, false);
            }
        }
    }
}
