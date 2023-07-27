package org.milkteamc.hotspring;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;

public class HotSpring extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private Location hotSpring1;
    private Location hotSpring2;
    private int intervalSeconds;
    private int coinAmount;
    private Economy economy;

    private final HashMap<Player, Long> playerCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        loadConfig();
        setupEconomy();
        startTimer();
        getLogger().info("溫泉插件已經啟用");
        getLogger().info("作者：Maoyue 為奶茶伺服器製作");
        getLogger().info("支援群組：https://discord.gg/uQ4UXANnP2");
    }

    private void loadConfig() {
        config = getConfig();
        hotSpring1 = getLocationFromConfig("HotSpring1");
        hotSpring2 = getLocationFromConfig("HotSpring2");
        intervalSeconds = config.getInt("IntervalSeconds", 5);
        coinAmount = config.getInt("CoinAmount", 1);
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("請先安裝 Vault 才能使用本插件");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("請先安裝任意經濟插件才能使用本指令");
            return;
        }

        economy = rsp.getProvider();
        getLogger().info("已成功連接到經濟插件：" + rsp.getProvider().getName());
    }

    private Location getLocationFromConfig(String path) {
        World world = Bukkit.getWorld(config.getString(path + ".World"));
        double x = config.getDouble(path + ".X");
        double y = config.getDouble(path + ".Y");
        double z = config.getDouble(path + ".Z");
        float yaw = (float) config.getDouble(path + ".Yaw");
        float pitch = (float) config.getDouble(path + ".Pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void startTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isInHotSpring(player)) {
                        if (checkCooldown(player)) {
                            int seconds = intervalSeconds;
                            String subtitle = ChatColor.AQUA + "在温泉中，每 " + seconds + " 秒可以獲得" + coinAmount + "個金幣！";
                            sendSubtitle(player, subtitle);
                            giveCoins(player, coinAmount);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0, intervalSeconds * 20);
    }

    private boolean checkCooldown(Player player) {
        long currentTime = System.currentTimeMillis();
        if (playerCooldowns.containsKey(player)) {
            long lastRewardTime = playerCooldowns.get(player);
            if (currentTime - lastRewardTime < (intervalSeconds * 1000)) {
                return false; // Player is still on cooldown
            }
        }
        playerCooldowns.put(player, currentTime);
        return true; // Player is not on cooldown, grant reward
    }

    private boolean isInHotSpring(Player player) {
        Location playerLocation = player.getLocation();
        return (playerLocation.getWorld().equals(hotSpring1.getWorld()) &&
                isInsideRegion(playerLocation, hotSpring1, hotSpring2) &&
                playerLocation.getBlock().isLiquid());
    }

    private boolean isInsideRegion(Location location, Location pos1, Location pos2) {
        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ);
    }

    private void sendSubtitle(Player player, String subtitle) {
        player.sendTitle("", subtitle, 0, 60, 20);
    }

    private void giveCoins(Player player, double amount) {
        if (economy != null) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> economy.depositPlayer(player, amount));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("hs1")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                hotSpring1 = player.getLocation();
                saveLocationToConfig("HotSpring1", hotSpring1);
                sender.sendMessage(ChatColor.GREEN + "第一點設定完成");
            } else {
                sender.sendMessage("只有玩家可使用此指令");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("hs2")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                hotSpring2 = player.getLocation();
                saveLocationToConfig("HotSpring2", hotSpring2);
                sender.sendMessage(ChatColor.GREEN + "第二點設定完成");
            } else {
                sender.sendMessage("只有玩家可使用此指令");
            }
            return true;
        }
        return false;
    }

    private void saveLocationToConfig(String path, Location location) {
        config.set(path + ".World", location.getWorld().getName());
        config.set(path + ".X", location.getX());
        config.set(path + ".Y", location.getY());
        config.set(path + ".Z", location.getZ());
        config.set(path + ".Yaw", location.getYaw());
        config.set(path + ".Pitch", location.getPitch());
        saveConfig();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isInHotSpring(player)) {
            if (checkCooldown(player)) {
                int seconds = intervalSeconds;
                String message = ChatColor.AQUA + "在温泉中，每 " + seconds + " 秒可以獲得" + coinAmount + "個金幣！";
                sendSubtitle(player, message);
                giveCoins(player, coinAmount);
            }
        }
    }
}
