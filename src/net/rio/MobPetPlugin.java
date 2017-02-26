package net.rio;

import org.bukkit.command.*;
import org.bukkit.configuration.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

public class MobPetPlugin extends JavaPlugin {

    private Pet petControll;

    @Override
    public void onEnable() {
        petControll = new Pet(this);
        getServer().getPluginManager().registerEvents(petControll, this);
    }

    @Override
    public void onDisable() {
        petControll.cleanPets();
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("petlist")) {
            Player p;
            petControll.cleanPets();
            if(args.length > 0) {
                p = getServer().getPlayer(args[0]);
                if(p == null) {
                    sender.sendMessage("Player " + args[0] + " not found");
                    return false;
                }
            } else if(sender instanceof Player) {
                p = (Player) sender;
            } else {
                sender.sendMessage("Please specify a player");
                return false;
            }
            int i = 0;
            for(String k : petControll.getPetList()) {
                Entity pet = petControll.getEntityById(k);
                if(pet != null && p.getUniqueId().toString().equals(petControll.getOwnerId(k))) {
                    sender.sendMessage(pet.getName() + ": " + pet.toString());
                    i++;
                }
            }
            if(i == 0) sender.sendMessage("No pet found");
            return true;
        }
        return false;
    }

}
