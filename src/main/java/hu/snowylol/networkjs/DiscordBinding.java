package hu.snowylol.networkjs;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.EmbedBuilder;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DiscordBinding {
    private JDA jda;
    private String token;
    private Map<String, String> channels;
    private boolean sanitizeMessages;
    private List<Consumer<DiscordMessage>> messageCallbacks;
    
    public DiscordBinding(Map<String, Object> configMap) {
        this.token = (String) configMap.get("token");
        this.channels = (Map<String, String>) configMap.getOrDefault("channels", new HashMap<>());
        this.sanitizeMessages = (Boolean) configMap.getOrDefault("sanitizeMessages", true);
        this.messageCallbacks = new ArrayList<>();
        
        if (this.token == null) {
            throw new IllegalArgumentException("Discord token is required");
        }
        
        initialize();
    }
    
    private void initialize() {
        try {
            this.jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new MessageListener())
                    .build();
                    
            this.jda.awaitReady();
            NetworkJS.LOGGER.info("Discord bot connected successfully");
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to initialize Discord bot: " + e.getMessage());
            throw new RuntimeException("Discord bot initialization failed", e);
        }
    }
    
    public String sanitizeMessage(String message) {
        if (message == null) return "";
        String sanitized = message
            .replaceAll("@everyone", "@\u200Beveryone")     
            .replaceAll("@here", "@\u200Bhere")             
            .replaceAll("@&\\d+", "@\u200Brole")            
            .replaceAll("```", "\\`\\`\\`");
            
        Pattern userMentionPattern = Pattern.compile("<@!?(\\d+)>");
        Matcher matcher = userMentionPattern.matcher(sanitized);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String userId = matcher.group(1);
            String replacement = "@\u200Buser";
            try {
                var user = jda.getUserById(userId);
                if (user != null) {
                    replacement = "@\u200B" + user.getName();
                }
            } catch (Exception e) {}
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        sanitized = sb.toString();
        
        sanitized = sanitized.replaceAll("<#\\d+>", "#\u200Bchannel");
        return sanitized.substring(0, Math.min(sanitized.length(), 2000)); // do not use message. i was stupid! :D
    }
    
    public boolean sendMessage(String channelKey, String message) {
        try {
            String channelId = channels.get(channelKey);
            if (channelId == null) {
                NetworkJS.LOGGER.warn("Channel key '" + channelKey + "' not found in configuration");
                return false;
            }
            
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                NetworkJS.LOGGER.warn("Discord channel not found: " + channelId);
                return false;
            }
            
            String finalMessage = sanitizeMessages ? sanitizeMessage(message) : message;
            channel.sendMessage(finalMessage).queue();
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to send Discord message: " + e.getMessage());
            return false;
        }
    }
    // i love embeds because they are almost the same as typescript lol (code wise)
    public boolean sendEmbed(String channelKey, Map<String, Object> embedData) {
        try {
            String channelId = channels.get(channelKey);
            if (channelId == null) {
                NetworkJS.LOGGER.warn("Channel key '" + channelKey + "' not found in configuration");
                return false;
            }
            
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                NetworkJS.LOGGER.warn("Discord channel not found: " + channelId);
                return false;
            }
            
            EmbedBuilder builder = new EmbedBuilder();
            
            if (embedData.containsKey("title")) {
                builder.setTitle((String) embedData.get("title"));
            }
            if (embedData.containsKey("description")) {
                String desc = (String) embedData.get("description");
                builder.setDescription(sanitizeMessages ? sanitizeMessage(desc) : desc);
            }
            if (embedData.containsKey("color")) {
                Object colorObj = embedData.get("color");
                if (colorObj instanceof Number) {
                    builder.setColor(((Number) colorObj).intValue());
                }
            }
            if (embedData.containsKey("footer")) {
                builder.setFooter((String) embedData.get("footer"));
            }
            
            channel.sendMessageEmbeds(builder.build()).queue();
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to send Discord embed: " + e.getMessage());
            return false;
        }
    }
    
    public void setActivity(String activity) {
        try {
            jda.getPresence().setActivity(net.dv8tion.jda.api.entities.Activity.playing(activity));
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to set Discord activity: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }
    
    public void onMessage(Consumer<DiscordMessage> callback) {
        this.messageCallbacks.add(callback);
    }
    
    public static class DiscordMessage {
        private final String content;
        private final String author;
        private final String channelId;
        private final String channelName;
        private final boolean isFromConfiguredChannel;
        private final boolean isBot;
        private final Map<String, Object> user;
        
        public DiscordMessage(String content, String author, String channelId, String channelName, boolean isFromConfiguredChannel, boolean isBot, Map<String, Object> user) {
            this.content = content;
            this.author = author;
            this.channelId = channelId;
            this.channelName = channelName;
            this.isFromConfiguredChannel = isFromConfiguredChannel;
            this.isBot = isBot;
            this.user = user;
        }
        
        public String getContent() { return content; }
        public String getAuthor() { return author; }
        public String getChannelId() { return channelId; }
        public String getChannelName() { return channelName; }
        public boolean isFromConfiguredChannel() { return isFromConfiguredChannel; }
        public boolean isBot() { return isBot; }
        public Map<String, Object> getUser() { return user; }
    }
    
    private class MessageListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;
            
            String content = event.getMessage().getContentDisplay();
            String author = event.getAuthor().getName();
            String channelId = event.getChannel().getId();
            String channelName = event.getChannel().getName();
            boolean isBot = event.getAuthor().isBot();
            
            boolean isFromConfiguredChannel = channels.containsValue(channelId);
            
            Map<String, Object> user = new HashMap<>();
            user.put("id", event.getAuthor().getId());
            user.put("name", event.getAuthor().getName());
            user.put("displayName", event.getAuthor().getEffectiveName());
            user.put("discriminator", event.getAuthor().getDiscriminator());
            user.put("avatarUrl", event.getAuthor().getAvatarUrl());
            user.put("isBot", event.getAuthor().isBot());
            user.put("isSystem", event.getAuthor().isSystem());
            
            if (event.getMember() != null) {
                user.put("nickname", event.getMember().getNickname());
                user.put("colorRaw", event.getMember().getColorRaw());
                user.put("isOwner", event.getMember().isOwner());
                user.put("isPending", event.getMember().isPending());
                user.put("joinedAt", event.getMember().getTimeJoined().toString());
                user.put("boostedAt", event.getMember().getTimeBoosted() != null ? event.getMember().getTimeBoosted().toString() : null);
                
                List<String> roleNames = new ArrayList<>();
                List<String> roleIds = new ArrayList<>();
                String highestRole = null;
                String highestRoleId = null;
                
                if (!event.getMember().getRoles().isEmpty()) {
                    var roles = event.getMember().getRoles();
                    if (!roles.isEmpty()) {
                        highestRole = roles.get(0).getName();
                        highestRoleId = roles.get(0).getId();
                    }
                    for (var role : roles) {
                        roleNames.add(role.getName());
                        roleIds.add(role.getId());
                    }
                }
                
                user.put("roles", roleNames.toArray(new String[0]));
                user.put("roleIds", roleIds.toArray(new String[0]));
                user.put("highestRole", highestRole);
                user.put("highestRoleId", highestRoleId);
                user.put("roleCount", roleNames.size());
            } else {
                user.put("nickname", null);
                user.put("roles", new String[0]);
                user.put("roleIds", new String[0]);
                user.put("highestRole", null);
                user.put("highestRoleId", null);
                user.put("roleCount", 0);
            }
            
            DiscordMessage discordMessage = new DiscordMessage(content, author, channelId, channelName, isFromConfiguredChannel, isBot, user);
            
            for (Consumer<DiscordMessage> callback : messageCallbacks) {
                try {
                    callback.accept(discordMessage);
                } catch (Exception e) {
                    NetworkJS.LOGGER.error("Error in Discord message callback: " + e.getMessage());
                }
            }
            
            NetworkJS.LOGGER.info("Discord message received from " + author + ": " + content);
        }
    }
}
