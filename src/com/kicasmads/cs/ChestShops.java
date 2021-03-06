package com.kicasmads.cs;

import com.kicasmads.cs.data.DataHandler;
import com.kicasmads.cs.data.Shop;
import com.kicasmads.cs.data.ShopType;
import com.kicasmads.cs.event.CSEventHandler;
import com.kicasmads.cs.gui.GuiGlobalView;
import com.kicasmads.cs.gui.GuiHandler;
import com.kicasmads.cs.gui.GuiShopsView;

import com.mojang.authlib.GameProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class ChestShops extends JavaPlugin {
    private final DataHandler dataHandler;
    private final GuiHandler guiHandler;
    private Material currencyItem;
    private ItemStack currencyStack;

    public static final String SHOP_HEADER = "[shop]";
    private static ChestShops instance;

    public ChestShops() {
        this.dataHandler = new DataHandler();
        this.guiHandler = new GuiHandler();
        instance = this;
    }

    public static ChestShops getInstance() {
        return instance;
    }

    public static Material getCurrencyItem() {
        return instance.currencyItem;
    }

    public static ItemStack getCurrencyStack() {
        return instance.currencyStack.clone();
    }

    @Override
    public void onEnable() {
        initConfig();

        Bukkit.getScheduler().runTaskLater(this, () -> dataHandler.load("shops.nbt"), 1);

        Bukkit.getPluginManager().registerEvents(guiHandler, this);
        Bukkit.getPluginManager().registerEvents(new CSEventHandler(), this);

        PluginCommand shopsCommand = getCommand("shops");
        shopsCommand.setExecutor((sender, command, alias, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
                return true;
            }
            Player player = (Player) sender;

            boolean selfView = args.length == 1 && args[0].equals("me") && !ChestShops.getDataHandler().getShops(player).isEmpty();
            if (selfView) {
                (new GuiShopsView(dataHandler.getShops(player.getUniqueId()), "My Shops", false, false)).openGui(player);
            } else {
                if(args.length >= 1 && !args[0].equals("everyone")){
                    GameProfile shopOwner = dataHandler.getAllShops()
                            .stream()
                            .filter(shop -> shop.isNotEmpty() && shop.getCachedOwner().getName().equalsIgnoreCase(args[0]))
                            .map(Shop::getCachedOwner)
                            .findFirst()
                            .orElse(null);

                    if(shopOwner != null) {
                        String shopName = shopOwner.getName() + "'s Shops";
                        (new GuiShopsView(dataHandler.getShops(shopOwner.getId()), shopName, true, false)).openGui(player);
                        return true;
                    }

                    player.sendMessage(ChatColor.RED + "The player \"" + args[0] + "\" does not have any shops.");
                }
                (new GuiGlobalView()).openGui(player);
            }

            return true;
        });
        shopsCommand.setTabCompleter(
                (sender, command, alias, args) -> {
                    if(args.length == 1) {
                        List<String> tabComplete = new ArrayList<>();
                        if("me".startsWith(args[0].toLowerCase())) {tabComplete.add("me");}
                        if("everyone".startsWith(args[0].toLowerCase())) {tabComplete.add("everyone");}
                        dataHandler.getAllShops().forEach(shop -> {
                            String name = shop.getCachedOwner().getName();
                            if(name == null){
                                Location chest = shop.getChestLocation();
                                error("Shop at [" + chest.getWorld().getName() + "] " + chest.getBlockX() + ", " + chest.getBlockY() + ", " + chest.getBlockZ() +"] - cached owner name missing");
                                return;
                            }
                            if (name.toLowerCase().startsWith(args[0].toLowerCase())) {
                                tabComplete.add(name);
                            }
                        });
                        return tabComplete;
                    }
                    return Collections.emptyList();

                }
        );

        PluginCommand searchshopsCommand = getCommand("searchshops");
        searchshopsCommand.setExecutor((sender, command, alias, args) -> {
            if (args.length == 0)
                return false;

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
                return true;
            }
            Player player = (Player) sender;

            boolean searchBuy = args.length > 1 && "buy".equalsIgnoreCase(args[1]);
            args[0] = args[0].toLowerCase();
            List<Shop> shops = dataHandler.getAllShops().stream()
                    .filter(shop -> searchBuy == (shop.getType() == ShopType.BUY) &&
                            Utils.searchableName(searchBuy ? shop.getBuyItem() : shop.getSellItem()).contains(args[0]))
                    .collect(Collectors.toList());
            if (shops.isEmpty()) {
                player.sendMessage(ChatColor.RED + "There are no shops that sell this item.");
                return true;
            }

            (new GuiShopsView(shops, "Shops", true, true)).openGui(player);
            return true;
        });
        searchshopsCommand.setTabCompleter((sender, command, alias, args) -> {
            switch (args.length) {
                case 1: {
                    List<String> suggestions = Arrays.stream(Material.values())
                            .map(Utils::formattedName)
                            .collect(Collectors.toCollection(ArrayList::new));
                    suggestions.addAll(Utils.ENCHANTMENT_NAMES);
                    return suggestions.stream()
                            .filter(name -> name.startsWith(args[0]))
                            .collect(Collectors.toList());
                }

                case 2:
                    return Arrays.asList("buy", "sell");

                default:
                    return Collections.emptyList();
            }
        });
    }

    @Override
    public void onDisable() {
        dataHandler.save("shops.nbt");
    }

    private void initConfig() {
        FileConfiguration config = getConfig();

        config.addDefault("general.currency-item", Utils.formattedName(Material.DIAMOND));

        config.options().copyDefaults(true);
        saveConfig();

        String currencyItemString = config.getString("general.currency-item");
        currencyItem = Utils.valueOfFormattedName(currencyItemString, Material.class);
        if (currencyItem == null) {
            error("Invalid currency item: " + currencyItemString);
            currencyItem = Material.DIAMOND;
        }
        currencyStack = new ItemStack(currencyItem);
    }

    public static DataHandler getDataHandler() {
        return instance.dataHandler;
    }

    public static GuiHandler getGuiHandler() {
        return instance.guiHandler;
    }

    /**
     * Logs an object to the console.
     *
     * @param x the object to log.
     */
    public static void log(Object x) {
        Bukkit.getLogger().info("[ChestShops] " + x);
    }

    /**
     * Logs an error to the console.
     *
     * @param x the error to log.
     */
    public static void error(Object x) {
        Bukkit.getLogger().severe("[ChestShops] " + x);
    }
}
