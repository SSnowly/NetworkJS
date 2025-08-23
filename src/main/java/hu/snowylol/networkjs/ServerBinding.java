package hu.snowylol.networkjs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;

public class ServerBinding {
    
    /**
     * Sends a raw message to all players with color code support
     * Supports Minecraft color codes like &a, &c, etc.
     */
    public static void sendRawMessage(String message) {
        sendSystemMessage(message);
    }
    
    /**
     * Sends a system message to all players (bypasses registry check)
     * Used for warnings and system notifications
     */
    public static void sendSystemMessage(String message) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                String coloredMessage = translateColorCodes(message);
                Component textComponent = Component.literal(coloredMessage);
                
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                NetworkJS.LOGGER.info("Attempting to send system message. Players online: {}", players.size());
                
                if (players.isEmpty()) {
                    // No players online, just return
                    NetworkJS.LOGGER.info("No players online, message not sent to chat");
                    return;
                }
                
                for (ServerPlayer player : players) {
                    NetworkJS.LOGGER.info("Sending message to player: {}", player.getName().getString());
                    player.sendSystemMessage(textComponent);
                }
                
                NetworkJS.LOGGER.info("System message sent to {} players: {}", players.size(), message);
            } else {
                NetworkJS.LOGGER.warn("Cannot send system message: Server not available");
            }
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to send system message: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sends a raw message to a specific player with color code support
     */
    public static void sendRawMessageToPlayer(String playerName, String message) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
                if (player != null) {
                    String coloredMessage = translateColorCodes(message);
                    Component textComponent = Component.literal(coloredMessage);
                    player.sendSystemMessage(textComponent);
                    
                    NetworkJS.LOGGER.info("Raw message sent to player {}: {}", playerName, message);
                } else {
                    NetworkJS.LOGGER.warn("Player {} not found", playerName);
                }
            } else {
                NetworkJS.LOGGER.warn("Cannot send raw message: Server not available");
            }
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to send raw message to player: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the number of online players
     */
    public static int getPlayerCount() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                return server.getPlayerList().getPlayerCount();
            }
            return 0;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to get player count: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Gets a list of online player names
     */
    public static String[] getPlayerNames() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                return players.stream()
                    .map(player -> player.getName().getString())
                    .toArray(String[]::new);
            }
            return new String[0];
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to get player names: " + e.getMessage());
            return new String[0];
        }
    }
    
    /**
     * Converts color codes from & to § for Minecraft formatting
     * Also handles some basic formatting codes
     */
    private static String translateColorCodes(String message) {
        if (message == null) return "";
        
        return message
            .replaceAll("&([0-9a-fk-or])", "§$1")
            .replaceAll("&([A-FK-OR])", "§$1"); // Also handle uppercase color codes
    }
}
