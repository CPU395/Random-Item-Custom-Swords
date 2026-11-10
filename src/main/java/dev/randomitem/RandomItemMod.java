package dev.randomitem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Prediction;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Configurable server-side random item timer for Minecraft 26.3. */
public final class RandomItemMod implements ModInitializer {
    private static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final int MAX_INTERVAL_SECONDS = 120;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RANDOM = new Random();
    private static final BossEvent.BossBarColor[] BAR_COLORS = BossEvent.BossBarColor.values();
    private static final String[] TEXT_COLORS = {"white", "yellow", "gold", "red", "light_purple", "aqua", "green", "blue", "gray"};
    private static final Set<String> NON_WEAPON_SIMPLY_SWORDS_ITEMS = Set.of(
            "runic_forge", "runic_tablet", "runefused_gem", "netherfused_gem",
            "empowered_remnant", "contained_remnant", "tampered_remnant");
    private static final ServerBossEvent TIMER_BAR = new ServerBossEvent(
            UUID.randomUUID(), Component.literal("Random item in 30 seconds"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private static Config config;
    private static int elapsedTicks;
    private static MinecraftServer serverInstance;

    @Override
    public void onInitialize() {
        loadConfig();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> serverInstance = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> serverInstance = null);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            TIMER_BAR.addPlayer(handler.getPlayer());
            TIMER_BAR.setVisible(!config.stopped);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TIMER_BAR.removePlayer(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (config.stopped) {
                TIMER_BAR.setVisible(false);
                return;
            }
            if (server.getPlayerList().getPlayers().isEmpty()) {
                elapsedTicks = 0;
                TIMER_BAR.setVisible(false);
                return;
            }
            TIMER_BAR.setVisible(true);
            int intervalTicks = config.intervalSeconds * 20;
            elapsedTicks++;
            TIMER_BAR.setProgress((float) elapsedTicks / intervalTicks);
            if (elapsedTicks % 20 == 0) updateTimerLabel(Math.max(0, (intervalTicks - elapsedTicks) / 20));
            if (elapsedTicks >= intervalTicks) {
                elapsedTicks = 0;
                TIMER_BAR.setProgress(0.0F);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) giveRandomItem(player);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("randomitem")
                        .then(literal("interval").then(argument("seconds", IntegerArgumentType.integer(1, MAX_INTERVAL_SECONDS))
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> {
                                    config.intervalSeconds = IntegerArgumentType.getInteger(context, "seconds");
                                    elapsedTicks = 0;
                                    saveConfig();
                                    updateTimerLabel(config.intervalSeconds);
                                    TIMER_BAR.setProgress(0.0F);
                                    context.getSource().sendSuccess(() -> Component.literal("Random item interval set to " + config.intervalSeconds + " seconds."), true);
                                    return 1;
                                })))
                        .then(literal("simplychance").then(argument("percent", IntegerArgumentType.integer(10, 100))
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .suggests((context, builder) -> {
                                    for (int value = 10; value <= 100; value += 10) builder.suggest(value);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    int percent = IntegerArgumentType.getInteger(context, "percent");
                                    if (percent % 10 != 0) {
                                        context.getSource().sendFailure(Component.literal("Use 10, 20, 30, and so on up to 100."));
                                        return 0;
                                    }
                                    config.simplySwordsChancePercent = percent;
                                    saveConfig();
                                    context.getSource().sendSuccess(() -> Component.literal("Simply Swords weapon chance set to " + percent + "% per player roll."), true);
                                    return 1;
                                })))
                        .then(literal("stop").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).executes(context -> {
                            config.stopped = true;
                            elapsedTicks = 0;
                            TIMER_BAR.setVisible(false);
                            saveConfig();
                            context.getSource().sendSuccess(() -> Component.literal("Random item timer stopped."), true);
                            return 1;
                        }))
                        .then(literal("start").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).executes(context -> {
                            config.stopped = false;
                            elapsedTicks = 0;
                            TIMER_BAR.setProgress(0.0F);
                            TIMER_BAR.setVisible(true);
                            updateTimerLabel(config.intervalSeconds);
                            saveConfig();
                            context.getSource().sendSuccess(() -> Component.literal("Random item timer started."), true);
                            return 1;
                        }))
                        .then(literal("text").then(argument("label", StringArgumentType.greedyString())
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> {
                                    config.timerText = StringArgumentType.getString(context, "label");
                                    saveConfig();
                                    updateTimerLabel(Math.max(0, (config.intervalSeconds * 20 - elapsedTicks) / 20));
                                    context.getSource().sendSuccess(() -> Component.literal("Timer label saved. Use %time% for seconds remaining."), true);
                                    return 1;
                                })))
                        .then(literal("color").then(argument("color", StringArgumentType.word())
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .suggests((context, builder) -> {
                                    for (BossEvent.BossBarColor color : BAR_COLORS) builder.suggest(color.name().toLowerCase(Locale.ROOT));
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String value = StringArgumentType.getString(context, "color").toUpperCase(Locale.ROOT);
                                    try {
                                        TIMER_BAR.setColor(BossEvent.BossBarColor.valueOf(value));
                                        config.color = value;
                                        saveConfig();
                                        context.getSource().sendSuccess(() -> Component.literal("Timer bar color set to " + value.toLowerCase(Locale.ROOT) + "."), true);
                                        return 1;
                                    } catch (IllegalArgumentException exception) {
                                        context.getSource().sendFailure(Component.literal("Choose pink, blue, red, green, yellow, purple, or white."));
                                        return 0;
                                    }
                                })))
                        .then(literal("textcolor").then(argument("color", StringArgumentType.word())
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .suggests((context, builder) -> {
                                    for (String color : TEXT_COLORS) builder.suggest(color);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String value = StringArgumentType.getString(context, "color").toLowerCase(Locale.ROOT);
                                    if (textColorRgb(value) == null) {
                                        context.getSource().sendFailure(Component.literal("Choose white, yellow, gold, red, light_purple, aqua, green, blue, or gray."));
                                        return 0;
                                    }
                                    config.textColor = value;
                                    saveConfig();
                                    updateTimerLabel(Math.max(0, (config.intervalSeconds * 20 - elapsedTicks) / 20));
                                    context.getSource().sendSuccess(() -> Component.literal("Timer and item message text color set to " + value + "."), true);
                                    return 1;
                                })))
                        .then(literal("announce").then(argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> {
                                    config.announceItemInChat = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
                                    saveConfig();
                                    context.getSource().sendSuccess(() -> Component.literal("Item chat announcements " + (config.announceItemInChat ? "enabled" : "disabled") + "."), true);
                                    return 1;
                                })))
                        .then(literal("status").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("Timer " + (config.stopped ? "stopped" : "running")
                                    + "; every " + config.intervalSeconds + " seconds; Simply Swords chance " + config.simplySwordsChancePercent
                                    + "%; chat announcements " + (config.announceItemInChat ? "on" : "off") + "."), false);
                            return 1;
                        }))));
    }

    private static void giveRandomItem(ServerPlayer player) {
        List<Item> allItems = new ArrayList<>();
        List<Item> simplySwordsWeapons = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            allItems.add(item);
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("simplyswords") && !NON_WEAPON_SIMPLY_SWORDS_ITEMS.contains(id.getPath())) {
                simplySwordsWeapons.add(item);
            }
        }
        if (allItems.isEmpty()) return;

        boolean chooseSimply = !simplySwordsWeapons.isEmpty()
                && (allItems.size() == simplySwordsWeapons.size() || RANDOM.nextInt(100) < config.simplySwordsChancePercent);
        List<Item> pool;
        if (simplySwordsWeapons.isEmpty()) pool = allItems;
        else if (chooseSimply) pool = simplySwordsWeapons;
        else {
            pool = new ArrayList<>(allItems);
            pool.removeAll(simplySwordsWeapons);
            if (pool.isEmpty()) pool = simplySwordsWeapons;
        }

        ItemStack stack = new ItemStack(pool.get(RANDOM.nextInt(pool.size())));
        String itemName = stack.getHoverName().getString();
        if (!player.getInventory().add(stack)) player.drop(stack, false, Prediction.SERVER_ONLY);
        if (config.announceItemInChat) {
            player.sendSystemMessage(Component.literal("Random item: " + itemName)
                    .withStyle(style -> style.withColor(TextColor.fromRgb(textColorRgb(config.textColor)))));
        }
    }

    private static void updateTimerLabel(int secondsLeft) {
        String label = config.timerText.replace("%time%", Integer.toString(secondsLeft));
        TIMER_BAR.setName(Component.literal(label).withStyle(style -> style.withColor(TextColor.fromRgb(textColorRgb(config.textColor)))));
    }

    private static void loadConfig() {
        Path path = Path.of("config", "randomitem.json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) config = GSON.fromJson(Files.readString(path), Config.class);
        } catch (IOException exception) {
            System.err.println("[Random Item Timer] Could not read config: " + exception.getMessage());
        }
        if (config == null) config = new Config();
        config.intervalSeconds = Math.max(1, Math.min(MAX_INTERVAL_SECONDS, config.intervalSeconds));
        if (config.simplySwordsChancePercent < 10 || config.simplySwordsChancePercent > 100 || config.simplySwordsChancePercent % 10 != 0) config.simplySwordsChancePercent = 10;
        if (config.timerText == null || config.timerText.isBlank()) config.timerText = "Random item in %time% seconds";
        if (textColorRgb(config.textColor) == null) config.textColor = "white";
        if (config.color == null) config.color = "PURPLE";
        try { TIMER_BAR.setColor(BossEvent.BossBarColor.valueOf(config.color.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException exception) { config.color = "PURPLE"; TIMER_BAR.setColor(BossEvent.BossBarColor.PURPLE); }
        saveConfig();
    }

    private static void saveConfig() {
        Path path = Path.of("config", "randomitem.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config));
        } catch (IOException exception) {
            System.err.println("[Random Item Timer] Could not save config: " + exception.getMessage());
        }
    }

    private static Integer textColorRgb(String color) {
        if (color == null) return null;
        return switch (color.toLowerCase(Locale.ROOT)) {
            case "white" -> 0xFFFFFF;
            case "yellow" -> 0xFFFF55;
            case "gold" -> 0xFFAA00;
            case "red" -> 0xFF5555;
            case "light_purple" -> 0xFF55FF;
            case "aqua" -> 0x55FFFF;
            case "green" -> 0x55FF55;
            case "blue" -> 0x5555FF;
            case "gray" -> 0xAAAAAA;
            default -> null;
        };
    }

    private static final class Config {
        private int intervalSeconds = DEFAULT_INTERVAL_SECONDS;
        private int simplySwordsChancePercent = 10;
        private String timerText = "Random item in %time% seconds";
        private String color = "PURPLE";
        private String textColor = "white";
        private boolean announceItemInChat = true;
        private boolean stopped;
    }
}
