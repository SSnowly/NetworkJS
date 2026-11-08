package hu.snowylol.networkjs;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discord binding cho KubeJS (JDA 5.x).
 *
 * Config (configMap):
 *   token            (bat buoc)
 *   channels         map key -> channelId
 *   sanitizeMessages mac dinh true
 *   guildId          (tuy chon) server mac dinh cho slash command / role / kick / ban...
 *   enableMembers    (tuy chon, mac dinh false) bat intent GUILD_MEMBERS + cache member
 *                    (phai bat "Server Members Intent" trong Discord Developer Portal)
 *   status           (tuy chon) online | idle | dnd | invisible
 *   activity         (tuy chon) text hien thi duoi ten bot
 *
 * Luu y: moi callback chay tren thread cua JDA, KHONG phai server thread cua Minecraft.
 * Neu callback can thay doi world/player thi hay dua vao server.execute(...) trong script.
 */
public class DiscordBinding {
    private JDA jda;
    private final String token;
    private final String guildId;
    private final boolean enableMembers;
    private final Map<String, String> channels = new HashMap<>();
    private final boolean sanitizeMessages;

    private final List<Consumer<DiscordMessage>> messageCallbacks = new CopyOnWriteArrayList<>();
    private volatile Consumer<DiscordButton> buttonCallback;
    private volatile Consumer<DiscordSelect> selectCallback;
    private volatile Consumer<DiscordCommand> commandCallback;
    private volatile Consumer<DiscordAutocomplete> autocompleteCallback;
    private volatile Consumer<DiscordModal> modalCallback;

    @SuppressWarnings("unchecked")
    public DiscordBinding(Map<String, Object> configMap) {
        Object tokenObj = configMap.get("token");
        this.token = tokenObj == null ? null : String.valueOf(tokenObj);
        if (this.token == null || this.token.isBlank()) {
            throw new IllegalArgumentException("Discord token is required");
        }

        Object rawChannels = configMap.get("channels");
        if (rawChannels instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    this.channels.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }

        this.sanitizeMessages = !Boolean.FALSE.equals(configMap.get("sanitizeMessages"));
        this.guildId = configMap.get("guildId") == null ? null : String.valueOf(configMap.get("guildId"));
        this.enableMembers = Boolean.TRUE.equals(configMap.get("enableMembers"));

        initialize(configMap);
    }

    private void initialize(Map<String, Object> configMap) {
        try {
            EnumSet<GatewayIntent> intents = EnumSet.of(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.DIRECT_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT);
            if (enableMembers) intents.add(GatewayIntent.GUILD_MEMBERS);

            // Chan @everyone/@here/role/user ping tu noi dung do script gui di.
            if (sanitizeMessages) {
                MessageRequest.setDefaultMentions(EnumSet.of(
                        Message.MentionType.CHANNEL,
                        Message.MentionType.EMOJI,
                        Message.MentionType.SLASH_COMMAND));
            }

            JDABuilder builder = JDABuilder.createDefault(token)
                    .enableIntents(intents)
                    .addEventListeners(new MessageListener());

            if (enableMembers) {
                builder.setMemberCachePolicy(MemberCachePolicy.ALL)
                        .setChunkingFilter(ChunkingFilter.ALL);
            }

            OnlineStatus status = parseStatus(configMap.get("status"));
            if (status != null) builder.setStatus(status);
            if (configMap.get("activity") != null) {
                builder.setActivity(Activity.playing(String.valueOf(configMap.get("activity"))));
            }

            this.jda = builder.build();
            this.jda.awaitReady();
            NetworkJS.LOGGER.info("Discord bot connected successfully as " + jda.getSelfUser().getName());
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to initialize Discord bot: " + e.getMessage(), e);
            throw new RuntimeException("Discord bot initialization failed", e);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static OnlineStatus parseStatus(Object value) {
        if (value == null) return null;
        OnlineStatus status = OnlineStatus.fromKey(String.valueOf(value).toLowerCase());
        return status == OnlineStatus.UNKNOWN ? null : status;
    }

    private static Consumer<Throwable> logFailure(String what) {
        return err -> NetworkJS.LOGGER.error("Discord: failed to " + what + ": " + err.getMessage());
    }

    /** channelKey la key trong config.channels, hoac truc tiep la ID cua kenh. */
    private MessageChannel resolveChannel(String keyOrId) {
        if (keyOrId == null) return null;
        String id = channels.getOrDefault(keyOrId, keyOrId);
        if (!id.matches("\\d{15,25}")) {
            NetworkJS.LOGGER.warn("Channel key '" + keyOrId + "' not found in configuration");
            return null;
        }
        MessageChannel channel = jda.getChannelById(MessageChannel.class, id);
        if (channel == null) {
            NetworkJS.LOGGER.warn("Discord channel not found: " + id);
        }
        return channel;
    }

    private Guild getGuild() {
        if (guildId != null) return jda.getGuildById(guildId);
        List<Guild> guilds = jda.getGuilds();
        return guilds.isEmpty() ? null : guilds.get(0);
    }

    public String sanitizeMessage(String message) {
        return sanitizeMessage(message, 2000);
    }

    public String sanitizeMessage(String message, int maxLength) {
        if (message == null) return "";
        String sanitized = message
                .replace("@everyone", "@\u200Beveryone")
                .replace("@here", "@\u200Bhere")
                .replaceAll("<@&\\d+>", "@\u200Brole")
                .replaceAll("`{3,}", "`\u200B`\u200B`");

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
            } catch (Exception ignored) {}
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        sanitized = sb.toString();

        sanitized = sanitized.replaceAll("<#\\d+>", "#\u200Bchannel");
        return truncate(sanitized, maxLength);
    }

    private String prepareText(String message) {
        return sanitizeMessages ? sanitizeMessage(message) : truncate(message, 2000);
    }

    // ------------------------------------------------------------------
    // Embed / Button / Dropdown / Modal builders
    // ------------------------------------------------------------------

    private static Integer parseColor(Object color) {
        if (color instanceof Number n) return n.intValue();
        if (color instanceof String s) {
            try {
                String hex = s.trim().replace("#", "").replace("0x", "");
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * embedData: title, url, description, color (number hoac "#RRGGBB"), footer, footerIcon,
     * author, authorUrl, authorIcon, thumbnail, image, timestamp (true), fields:[{name,value,inline}]
     */
    MessageEmbed buildEmbed(Map<String, Object> embedData) {
        EmbedBuilder builder = new EmbedBuilder();
        if (embedData.get("title") != null) {
            String title = truncate(String.valueOf(embedData.get("title")), 256);
            String url = embedData.get("url") != null ? String.valueOf(embedData.get("url")) : null;
            builder.setTitle(title, url);
        }
        if (embedData.get("description") != null) {
            String desc = String.valueOf(embedData.get("description"));
            builder.setDescription(sanitizeMessages ? sanitizeMessage(desc, 4096) : truncate(desc, 4096));
        }
        Integer color = parseColor(embedData.get("color"));
        if (color != null) builder.setColor(color);

        if (embedData.get("footer") != null) {
            String icon = embedData.get("footerIcon") != null ? String.valueOf(embedData.get("footerIcon")) : null;
            builder.setFooter(truncate(String.valueOf(embedData.get("footer")), 2048), icon);
        }
        if (embedData.get("author") != null) {
            String url = embedData.get("authorUrl") != null ? String.valueOf(embedData.get("authorUrl")) : null;
            String icon = embedData.get("authorIcon") != null ? String.valueOf(embedData.get("authorIcon")) : null;
            builder.setAuthor(truncate(String.valueOf(embedData.get("author")), 256), url, icon);
        }
        if (embedData.get("thumbnail") != null) builder.setThumbnail(String.valueOf(embedData.get("thumbnail")));
        if (embedData.get("image") != null) builder.setImage(String.valueOf(embedData.get("image")));
        if (Boolean.TRUE.equals(embedData.get("timestamp"))) builder.setTimestamp(Instant.now());

        if (embedData.get("fields") instanceof List<?> fieldList) {
            int count = 0;
            for (Object item : fieldList) {
                if (count >= 25) break;
                if (item instanceof Map<?, ?> field) {
                    String value = String.valueOf(field.get("value"));
                    if (sanitizeMessages) value = sanitizeMessage(value, 1024);
                    builder.addField(
                            truncate(String.valueOf(field.get("name")), 256),
                            truncate(value, 1024),
                            Boolean.TRUE.equals(field.get("inline")));
                    count++;
                }
            }
        }
        return builder.build();
    }

    /**
     * Button: {id, label, style: primary|secondary|success|danger, disabled, emoji}
     * Link button: {url, label}
     */
    static Button buildButton(Map<String, Object> data) {
        if (data == null || data.get("label") == null) return null;
        String label = truncate(String.valueOf(data.get("label")), 80);
        Button button;

        if (data.get("url") != null) {
            button = Button.link(String.valueOf(data.get("url")), label);
        } else {
            if (data.get("id") == null) return null;
            String id = truncate(String.valueOf(data.get("id")), 100);
            String style = String.valueOf(data.getOrDefault("style", "primary")).toLowerCase();
            button = switch (style) {
                case "secondary" -> Button.secondary(id, label);
                case "success" -> Button.success(id, label);
                case "danger" -> Button.danger(id, label);
                default -> Button.primary(id, label);
            };
        }
        if (data.get("emoji") != null) {
            try {
                button = button.withEmoji(Emoji.fromFormatted(String.valueOf(data.get("emoji"))));
            } catch (Exception ignored) {}
        }
        if (Boolean.TRUE.equals(data.get("disabled"))) {
            button = button.asDisabled();
        }
        return button;
    }

    /**
     * Dropdown (string select menu):
     * {type:"select", id, placeholder, min, max, disabled,
     *  options:[{label, value, description, emoji, default}]}   (toi da 25 option)
     */
    static StringSelectMenu buildSelect(Map<String, Object> data) {
        if (data == null || data.get("id") == null) return null;
        if (!(data.get("options") instanceof List<?> options) || options.isEmpty()) return null;

        StringSelectMenu.Builder builder = StringSelectMenu.create(truncate(String.valueOf(data.get("id")), 100));
        if (data.get("placeholder") != null) {
            builder.setPlaceholder(truncate(String.valueOf(data.get("placeholder")), 150));
        }

        int count = 0;
        for (Object item : options) {
            if (count >= 25) break;
            if (!(item instanceof Map<?, ?> o) || o.get("label") == null) continue;

            String label = truncate(String.valueOf(o.get("label")), 100);
            String value = truncate(String.valueOf(o.get("value") != null ? o.get("value") : o.get("label")), 100);
            SelectOption option = SelectOption.of(label, value);
            if (o.get("description") != null) {
                option = option.withDescription(truncate(String.valueOf(o.get("description")), 100));
            }
            if (o.get("emoji") != null) {
                try {
                    option = option.withEmoji(Emoji.fromFormatted(String.valueOf(o.get("emoji"))));
                } catch (Exception ignored) {}
            }
            if (Boolean.TRUE.equals(o.get("default"))) {
                option = option.withDefault(true);
            }
            builder.addOptions(option);
            count++;
        }
        if (count == 0) return null;

        int minValues = data.get("min") instanceof Number n ? n.intValue() : 1;
        int maxValues = data.get("max") instanceof Number n ? n.intValue() : 1;
        maxValues = Math.max(1, Math.min(maxValues, count));
        minValues = Math.max(0, Math.min(minValues, maxValues));
        builder.setRequiredRange(minValues, maxValues);

        if (Boolean.TRUE.equals(data.get("disabled"))) builder.setDisabled(true);
        return builder.build();
    }

    /**
     * Gop button + dropdown thanh cac hang (ActionRow).
     * - Button xep toi da 5 cai / hang.
     * - Moi dropdown chiem tron 1 hang.
     * - Toi da 5 hang.
     * Phan tu co type:"select" (hoac "dropdown") la dropdown, con lai la button.
     */
    static List<ActionRow> buildRows(List<Map<String, Object>> components) {
        List<ActionRow> rows = new ArrayList<>();
        if (components == null) return rows;
        List<Button> current = new ArrayList<>();

        for (Map<String, Object> data : components) {
            if (rows.size() >= 5) break;
            if (data == null) continue;

            String type = String.valueOf(data.getOrDefault("type", "button")).toLowerCase();
            if (type.equals("select") || type.equals("dropdown")) {
                StringSelectMenu menu = buildSelect(data);
                if (menu == null) continue;
                if (!current.isEmpty()) {
                    rows.add(ActionRow.of(current));
                    current = new ArrayList<>();
                    if (rows.size() >= 5) break;
                }
                rows.add(ActionRow.of(menu));
            } else {
                Button button = buildButton(data);
                if (button == null) continue;
                current.add(button);
                if (current.size() == 5) {
                    rows.add(ActionRow.of(current));
                    current = new ArrayList<>();
                }
            }
        }
        if (!current.isEmpty() && rows.size() < 5) {
            rows.add(ActionRow.of(current));
        }
        return rows;
    }

    /**
     * Modal: {id, title, fields:[{id, label, style: short|paragraph, placeholder, value,
     *  required (mac dinh true), min, max}]}  (toi da 5 field)
     */
    static Modal buildModal(Map<String, Object> data) {
        if (data == null || data.get("id") == null || data.get("title") == null) return null;
        if (!(data.get("fields") instanceof List<?> fields)) return null;

        Modal.Builder builder = Modal.create(
                truncate(String.valueOf(data.get("id")), 100),
                truncate(String.valueOf(data.get("title")), 45));

        int count = 0;
        for (Object item : fields) {
            if (count >= 5) break;
            if (!(item instanceof Map<?, ?> f) || f.get("id") == null || f.get("label") == null) continue;

            TextInputStyle style = "paragraph".equalsIgnoreCase(String.valueOf(f.get("style")))
                    ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT;
            TextInput.Builder input = TextInput.create(
                    truncate(String.valueOf(f.get("id")), 100),
                    truncate(String.valueOf(f.get("label")), 45),
                    style);
            if (f.get("placeholder") != null) input.setPlaceholder(truncate(String.valueOf(f.get("placeholder")), 100));
            if (f.get("value") != null) input.setValue(truncate(String.valueOf(f.get("value")), 4000));
            input.setRequired(!Boolean.FALSE.equals(f.get("required")));
            if (f.get("min") instanceof Number n) input.setMinLength(n.intValue());
            if (f.get("max") instanceof Number n) input.setMaxLength(n.intValue());

            builder.addActionRow(input.build());
            count++;
        }
        if (count == 0) return null;
        return builder.build();
    }

    /**
     * Slash command: {name, description, adminOnly, guildOnly,
     *  options:[{name, description, type: string|integer|number|boolean|user|channel|role|mentionable,
     *            required, autocomplete, choices:[{name,value}]}]}
     */
    private static SlashCommandData buildCommand(Map<String, Object> data) {
        if (data == null || data.get("name") == null || data.get("description") == null) return null;
        String name = String.valueOf(data.get("name")).toLowerCase();
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            NetworkJS.LOGGER.warn("Invalid slash command name: " + name);
            return null;
        }

        SlashCommandData command = Commands.slash(name, truncate(String.valueOf(data.get("description")), 100));
        if (Boolean.TRUE.equals(data.get("adminOnly"))) {
            command.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
        }
        if (Boolean.TRUE.equals(data.get("guildOnly"))) {
            command.setGuildOnly(true);
        }

        if (data.get("options") instanceof List<?> optionList) {
            List<OptionData> required = new ArrayList<>();
            List<OptionData> optional = new ArrayList<>();
            for (Object item : optionList) {
                if (!(item instanceof Map<?, ?> o) || o.get("name") == null || o.get("description") == null) continue;
                OptionType type = parseOptionType(String.valueOf(o.get("type") == null ? "string" : o.get("type")));
                boolean isRequired = Boolean.TRUE.equals(o.get("required"));
                boolean autocomplete = Boolean.TRUE.equals(o.get("autocomplete"))
                        && (type == OptionType.STRING || type == OptionType.INTEGER || type == OptionType.NUMBER);

                OptionData option = new OptionData(
                        type,
                        String.valueOf(o.get("name")).toLowerCase(),
                        truncate(String.valueOf(o.get("description")), 100),
                        isRequired,
                        autocomplete);

                if (!autocomplete && o.get("choices") instanceof List<?> choices) {
                    int c = 0;
                    for (Object choice : choices) {
                        if (c >= 25) break;
                        if (!(choice instanceof Map<?, ?> ch) || ch.get("name") == null || ch.get("value") == null) continue;
                        String choiceName = truncate(String.valueOf(ch.get("name")), 100);
                        try {
                            switch (type) {
                                case INTEGER -> option.addChoice(choiceName, ((Number) toNumber(ch.get("value"))).longValue());
                                case NUMBER -> option.addChoice(choiceName, ((Number) toNumber(ch.get("value"))).doubleValue());
                                default -> option.addChoice(choiceName, truncate(String.valueOf(ch.get("value")), 100));
                            }
                            c++;
                        } catch (Exception ignored) {}
                    }
                }
                (isRequired ? required : optional).add(option);
            }
            // Discord bat buoc option "required" phai dung truoc.
            command.addOptions(required);
            command.addOptions(optional);
        }
        return command;
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number n) return n;
        return Double.valueOf(String.valueOf(value));
    }

    private static OptionType parseOptionType(String type) {
        return switch (type.toLowerCase()) {
            case "integer", "int", "long" -> OptionType.INTEGER;
            case "number", "double", "float" -> OptionType.NUMBER;
            case "boolean", "bool" -> OptionType.BOOLEAN;
            case "user" -> OptionType.USER;
            case "channel" -> OptionType.CHANNEL;
            case "role" -> OptionType.ROLE;
            case "mentionable" -> OptionType.MENTIONABLE;
            default -> OptionType.STRING;
        };
    }

    private static Object optionValue(OptionMapping m) {
        return switch (m.getType()) {
            case INTEGER -> m.getAsLong();
            case NUMBER -> m.getAsDouble();
            case BOOLEAN -> m.getAsBoolean();
            case USER -> m.getAsUser().getId();
            case CHANNEL -> m.getAsChannel().getId();
            case ROLE -> m.getAsRole().getId();
            case MENTIONABLE -> m.getAsMentionable().getId();
            default -> m.getAsString();
        };
    }

    // ------------------------------------------------------------------
    // Gui / sua / xoa tin nhan
    // ------------------------------------------------------------------

    public boolean sendMessage(String channelKey, String message) {
        return sendMessageWithComponents(channelKey, message, null, null);
    }

    public boolean sendMessageWithComponents(String channelKey, String message, List<Map<String, Object>> components) {
        return sendMessageWithComponents(channelKey, message, components, null);
    }

    /** onSent nhan messageId cua tin vua gui (co the null). */
    public boolean sendMessageWithComponents(String channelKey, String message,
                                             List<Map<String, Object>> components, Consumer<String> onSent) {
        try {
            MessageChannel channel = resolveChannel(channelKey);
            if (channel == null) return false;

            var action = channel.sendMessage(prepareText(message));
            if (components != null) action = action.setComponents(buildRows(components));
            action.queue(msg -> {
                if (onSent != null) {
                    try { onSent.accept(msg.getId()); } catch (Exception e) {
                        NetworkJS.LOGGER.error("Error in onSent callback: " + e.getMessage());
                    }
                }
            }, logFailure("send message"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to send Discord message: " + e.getMessage());
            return false;
        }
    }

    /** Tra loi (reply) mot tin nhan co san. */
    public boolean replyToMessage(String channelKey, String messageId, String text) {
        try {
            MessageChannel channel = resolveChannel(channelKey);
            if (channel == null) return false;
            channel.sendMessage(prepareText(text))
                    .setMessageReference(messageId)
                    .mentionRepliedUser(false)
                    .queue(null, logFailure("reply to message"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to reply to Discord message: " + e.getMessage());
            return false;
        }
    }

    public boolean sendEmbed(String channelKey, Map<String, Object> embedData) {
        return sendEmbedWithComponents(channelKey, embedData, null, null);
    }

    /** Giu lai cho script cu. Dung chung voi sendEmbedWithComponents. */
    public boolean sendEmbedWithButtons(String channelKey, Map<String, Object> embedData, List<Map<String, Object>> buttons) {
        return sendEmbedWithComponents(channelKey, embedData, buttons, null);
    }

    public boolean sendEmbedWithComponents(String channelKey, Map<String, Object> embedData, List<Map<String, Object>> components) {
        return sendEmbedWithComponents(channelKey, embedData, components, null);
    }

    public boolean sendEmbedWithComponents(String channelKey, Map<String, Object> embedData,
                                           List<Map<String, Object>> components, Consumer<String> onSent) {
        try {
            MessageChannel channel = resolveChannel(channelKey);
            if (channel == null) return false;

            var action = channel.sendMessageEmbeds(buildEmbed(embedData));
            if (components != null) action = action.setComponents(buildRows(components));
            action.queue(msg -> {
                if (onSent != null) {
                    try { onSent.accept(msg.getId()); } catch (Exception e) {
                        NetworkJS.LOGGER.error("Error in onSent callback: " + e.getMessage());
                    }
                }
            }, logFailure("send embed"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to send Discord embed: " + e.getMessage());
            return false;
        }
    }

    public boolean editMessage(String channelKey, String messageId, String text) {
        try {
            MessageChannel channel = resolveChannel(channelKey);
            if (channel == null) return false;
            channel.editMessageById(messageId, prepareText(text)).queue(null, logFailure("edit message"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to edit Discord message: " + e.getMessage());
            return false;
        }
    }

    /** components = null -> giu nguyen component cu; [] -> xoa het component. */
    public boolean editEmbed(String channelKey, String messageId, Map<String, Object> embedData,
                             List<Map<String, Object>> components) {
        try {
            MessageChannel channel = resolveChannel(channelKey);
            if (channel == null) return false;
            var action = channel.editMessageEmbedsById(messageId, buildEmbed(embedData));
            if (components != null) action = action.setComponents(buildRows(components));
            action.queue(null, logFailure("edit embed"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to edit Discord embed: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteMessage(String channelKey, String messageId) {
        try {
            MessageChannel channel = resolveChannel(channelKey);
            if (channel == null) return false;
            channel.deleteMessageById(messageId).queue(null, logFailure("delete message"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to delete Discord message: " + e.getMessage());
            return false;
        }
    }

    /** emoji: "👍" hoac "<:name:id>" cho emoji server. */
    public boolean addReaction(String channelKey, String messageId, String emoji) {
        try {
            MessageChannel channel = resolveChannel(channelKey);
            if (channel == null) return false;
            channel.addReactionById(messageId, Emoji.fromFormatted(emoji)).queue(null, logFailure("add reaction"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to add Discord reaction: " + e.getMessage());
            return false;
        }
    }

    public boolean pinMessage(String channelKey, String messageId) {
        try {
            MessageChannel channel = resolveChannel(channelKey);
            if (channel == null) return false;
            channel.pinMessageById(messageId).queue(null, logFailure("pin message"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to pin Discord message: " + e.getMessage());
            return false;
        }
    }

    /** Nhan tin rieng (DM). Se that bai (chi ghi log) neu nguoi dung chan DM tu server. */
    public boolean sendDM(String userId, String text) {
        try {
            String finalText = prepareText(text);
            jda.retrieveUserById(userId).queue(
                    user -> user.openPrivateChannel().queue(
                            channel -> channel.sendMessage(finalText).queue(null, logFailure("send DM")),
                            logFailure("open DM")),
                    logFailure("find user " + userId));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to send Discord DM: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Slash command
    // ------------------------------------------------------------------

    /**
     * Dang ky slash command. Neu co guildId trong config -> chi dang ky cho server do (co hieu luc ngay),
     * neu khong -> dang ky global (co the mat toi ~1 gio de hien).
     * Lenh nay GHI DE toan bo lenh cu cua bot o pham vi do.
     */
    public boolean registerCommands(List<Map<String, Object>> commandList) {
        try {
            List<SlashCommandData> data = new ArrayList<>();
            if (commandList != null) {
                for (Map<String, Object> c : commandList) {
                    SlashCommandData d = buildCommand(c);
                    if (d != null) data.add(d);
                }
            }
            if (guildId != null) {
                Guild guild = jda.getGuildById(guildId);
                if (guild == null) {
                    NetworkJS.LOGGER.warn("Discord guild not found: " + guildId);
                    return false;
                }
                guild.updateCommands().addCommands(data).queue(
                        ok -> NetworkJS.LOGGER.info("Registered " + data.size() + " guild slash commands"),
                        logFailure("register guild commands"));
            } else {
                jda.updateCommands().addCommands(data).queue(
                        ok -> NetworkJS.LOGGER.info("Registered " + data.size() + " global slash commands"),
                        logFailure("register global commands"));
            }
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to register Discord commands: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean clearCommands() {
        return registerCommands(Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // Role / moderation (can quyen tuong ung cho bot trong server)
    // ------------------------------------------------------------------

    public boolean addRole(String userId, String roleId) {
        try {
            Guild guild = getGuild();
            Role role = guild == null ? null : guild.getRoleById(roleId);
            if (role == null) {
                NetworkJS.LOGGER.warn("Discord role/guild not found for addRole");
                return false;
            }
            guild.addRoleToMember(UserSnowflake.fromId(userId), role).queue(null, logFailure("add role"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to add Discord role: " + e.getMessage());
            return false;
        }
    }

    public boolean removeRole(String userId, String roleId) {
        try {
            Guild guild = getGuild();
            Role role = guild == null ? null : guild.getRoleById(roleId);
            if (role == null) {
                NetworkJS.LOGGER.warn("Discord role/guild not found for removeRole");
                return false;
            }
            guild.removeRoleFromMember(UserSnowflake.fromId(userId), role).queue(null, logFailure("remove role"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to remove Discord role: " + e.getMessage());
            return false;
        }
    }

    public boolean kick(String userId, String reason) {
        try {
            Guild guild = getGuild();
            if (guild == null) return false;
            guild.kick(UserSnowflake.fromId(userId)).reason(reason).queue(null, logFailure("kick"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to kick Discord user: " + e.getMessage());
            return false;
        }
    }

    /** deleteDays: xoa tin nhan cua nguoi do trong N ngay gan nhat (0-7). */
    public boolean ban(String userId, String reason, int deleteDays) {
        try {
            Guild guild = getGuild();
            if (guild == null) return false;
            int days = Math.max(0, Math.min(7, deleteDays));
            guild.ban(UserSnowflake.fromId(userId), days, TimeUnit.DAYS).reason(reason).queue(null, logFailure("ban"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to ban Discord user: " + e.getMessage());
            return false;
        }
    }

    public boolean timeout(String userId, int minutes, String reason) {
        try {
            Guild guild = getGuild();
            if (guild == null) return false;
            guild.timeoutFor(UserSnowflake.fromId(userId), Duration.ofMinutes(Math.max(1, minutes)))
                    .reason(reason).queue(null, logFailure("timeout"));
            return true;
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to timeout Discord user: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Presence / thong tin bot
    // ------------------------------------------------------------------

    public void setActivity(String activity) {
        setActivity(activity, "playing");
    }

    /** type: playing | watching | listening | competing */
    public void setActivity(String activity, String type) {
        try {
            Activity a = switch (String.valueOf(type).toLowerCase()) {
                case "watching" -> Activity.watching(activity);
                case "listening" -> Activity.listening(activity);
                case "competing" -> Activity.competing(activity);
                default -> Activity.playing(activity);
            };
            jda.getPresence().setActivity(a);
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Failed to set Discord activity: " + e.getMessage());
        }
    }

    /** status: online | idle | dnd | invisible */
    public void setStatus(String status) {
        OnlineStatus s = parseStatus(status);
        if (s == null) {
            NetworkJS.LOGGER.warn("Unknown Discord status: " + status);
            return;
        }
        jda.getPresence().setStatus(s);
    }

    public boolean isReady() { return jda != null && jda.getStatus() == JDA.Status.CONNECTED; }
    public long getPing() { return jda.getGatewayPing(); }
    public String getBotId() { return jda.getSelfUser().getId(); }
    public String getBotName() { return jda.getSelfUser().getName(); }

    public int getMemberCount() {
        Guild guild = getGuild();
        return guild == null ? 0 : guild.getMemberCount();
    }

    // ------------------------------------------------------------------
    // Dang ky callback
    // ------------------------------------------------------------------

    public void onMessage(Consumer<DiscordMessage> callback) { this.messageCallbacks.add(callback); }
    public void onButton(Consumer<DiscordButton> callback) { this.buttonCallback = callback; }
    public void onSelect(Consumer<DiscordSelect> callback) { this.selectCallback = callback; }
    public void onSlashCommand(Consumer<DiscordCommand> callback) { this.commandCallback = callback; }
    public void onAutocomplete(Consumer<DiscordAutocomplete> callback) { this.autocompleteCallback = callback; }
    public void onModal(Consumer<DiscordModal> callback) { this.modalCallback = callback; }

    /** Goi khi /reload script de tranh callback bi nhan doi. */
    public void clearCallbacks() {
        messageCallbacks.clear();
        buttonCallback = null;
        selectCallback = null;
        commandCallback = null;
        autocompleteCallback = null;
        modalCallback = null;
    }

    public void shutdown() {
        clearCallbacks();
        if (jda != null) {
            jda.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Wrapper cho interaction (script dung)
    // ------------------------------------------------------------------

    /** Chuc nang chung cho moi loai interaction: reply, defer, modal, check quyen. */
    public static class DiscordInteraction {
        protected final DiscordBinding owner;
        protected final IReplyCallback callback;

        protected DiscordInteraction(DiscordBinding owner, IReplyCallback callback) {
            this.owner = owner;
            this.callback = callback;
        }

        public String getUserId() { return callback.getUser().getId(); }
        public String getUserName() { return callback.getUser().getName(); }
        public String getChannelId() { return callback.getChannel() != null ? callback.getChannel().getId() : null; }
        public String getGuildId() { return callback.getGuild() != null ? callback.getGuild().getId() : null; }
        public boolean isAcknowledged() { return callback.isAcknowledged(); }

        public boolean hasRole(String roleId) {
            Member m = callback.getMember();
            return m != null && m.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
        }

        public String[] getRoleIds() {
            Member m = callback.getMember();
            if (m == null) return new String[0];
            return m.getRoles().stream().map(Role::getId).toArray(String[]::new);
        }

        /** name: ten Permission cua JDA, vd "ADMINISTRATOR", "MANAGE_SERVER", "KICK_MEMBERS". */
        public boolean hasPermission(String name) {
            Member m = callback.getMember();
            if (m == null || name == null) return false;
            try {
                return m.hasPermission(Permission.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        public boolean isAdmin() { return hasPermission("ADMINISTRATOR"); }

        /** Bao Discord "dang xu ly" (can lam neu xu ly > 3 giay), sau do dung reply(). */
        public boolean deferReply(boolean ephemeral) {
            try {
                if (callback.isAcknowledged()) return false;
                callback.deferReply(ephemeral).queue();
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to defer Discord interaction: " + e.getMessage());
                return false;
            }
        }

        /** Tra loi chi nguoi bam thay (ephemeral). */
        public boolean reply(String text) { return sendReply(text, true); }

        /** Tra loi cong khai cho ca kenh. */
        public boolean replyPublic(String text) { return sendReply(text, false); }

        public boolean replyEmbed(Map<String, Object> embedData, boolean ephemeral) {
            return replyEmbed(embedData, null, ephemeral);
        }

        public boolean replyEmbed(Map<String, Object> embedData, List<Map<String, Object>> components, boolean ephemeral) {
            try {
                MessageEmbed embed = owner.buildEmbed(embedData);
                List<ActionRow> rows = buildRows(components);
                if (callback.isAcknowledged()) {
                    callback.getHook().sendMessageEmbeds(embed).setComponents(rows).setEphemeral(ephemeral).queue();
                } else {
                    callback.replyEmbeds(embed).setComponents(rows).setEphemeral(ephemeral).queue();
                }
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to reply embed to Discord interaction: " + e.getMessage());
                return false;
            }
        }

        /** Hien modal (form nhap chu). Phai la phan hoi dau tien, chua defer/reply. */
        public boolean showModal(Map<String, Object> modalData) {
            try {
                Modal modal = buildModal(modalData);
                if (modal == null) return false;
                if (callback.isAcknowledged() || !(callback instanceof IModalCallback modalCallback)) return false;
                modalCallback.replyModal(modal).queue();
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to show Discord modal: " + e.getMessage());
                return false;
            }
        }

        private boolean sendReply(String text, boolean ephemeral) {
            try {
                String finalText = text == null || text.isEmpty() ? "\u200B" : text;
                if (finalText.length() > 2000) finalText = finalText.substring(0, 2000);
                if (callback.isAcknowledged()) {
                    callback.getHook().sendMessage(finalText).setEphemeral(ephemeral).queue();
                } else {
                    callback.reply(finalText).setEphemeral(ephemeral).queue();
                }
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to reply to Discord interaction: " + e.getMessage());
                return false;
            }
        }
    }

    /** Chung cho button va dropdown: sua lai chinh tin nhan chua component. */
    public static class DiscordComponentInteraction extends DiscordInteraction {
        protected final GenericComponentInteractionCreateEvent event;

        protected DiscordComponentInteraction(DiscordBinding owner, GenericComponentInteractionCreateEvent event) {
            super(owner, event);
            this.event = event;
        }

        public String getCustomId() { return event.getComponentId(); }
        public String getMessageId() { return event.getMessageId(); }

        /** Xac nhan "da nhan" nhung khong thay doi gi tren tin nhan. */
        public boolean deferEdit() {
            try {
                if (event.isAcknowledged()) return false;
                event.deferEdit().queue();
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to defer Discord component: " + e.getMessage());
                return false;
            }
        }

        /** Luu y: ban 1 tham so se XOA het component cu (giu hanh vi cu). */
        public boolean editEmbed(Map<String, Object> embedData) {
            return editEmbed(embedData, Collections.emptyList());
        }

        /** components = null -> giu nguyen; [] -> xoa; danh sach -> thay moi (button + dropdown). */
        public boolean editEmbed(Map<String, Object> embedData, List<Map<String, Object>> components) {
            try {
                MessageEmbed embed = owner.buildEmbed(embedData);
                if (event.isAcknowledged()) {
                    var action = event.getHook().editOriginalEmbeds(embed);
                    if (components != null) action = action.setComponents(buildRows(components));
                    action.queue();
                } else {
                    var action = event.editMessageEmbeds(embed);
                    if (components != null) action = action.setComponents(buildRows(components));
                    action.queue();
                }
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to edit Discord component message: " + e.getMessage());
                return false;
            }
        }

        public boolean editMessage(String text) {
            return editMessage(text, null);
        }

        public boolean editMessage(String text, List<Map<String, Object>> components) {
            try {
                String finalText = text == null ? "" : text;
                if (finalText.length() > 2000) finalText = finalText.substring(0, 2000);
                if (event.isAcknowledged()) {
                    var action = event.getHook().editOriginal(finalText);
                    if (components != null) action = action.setComponents(buildRows(components));
                    action.queue();
                } else {
                    var action = event.editMessage(finalText);
                    if (components != null) action = action.setComponents(buildRows(components));
                    action.queue();
                }
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to edit Discord message: " + e.getMessage());
                return false;
            }
        }

        /** Vo hieu hoa toan bo button/dropdown cua tin nhan (vd sau khi da chon xong). */
        public boolean disableComponents() {
            try {
                List<ActionRow> rows = new ArrayList<>();
                for (ActionRow row : event.getMessage().getActionRows()) {
                    rows.add(row.asDisabled());
                }
                if (event.isAcknowledged()) {
                    event.getHook().editOriginalComponents(rows).queue();
                } else {
                    event.editComponents(rows).queue();
                }
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to disable Discord components: " + e.getMessage());
                return false;
            }
        }
    }

    public static class DiscordButton extends DiscordComponentInteraction {
        public DiscordButton(DiscordBinding owner, ButtonInteractionEvent event) {
            super(owner, event);
        }
    }

    /** Dropdown (string select menu) da duoc chon. */
    public static class DiscordSelect extends DiscordComponentInteraction {
        private final StringSelectInteractionEvent selectEvent;

        public DiscordSelect(DiscordBinding owner, StringSelectInteractionEvent event) {
            super(owner, event);
            this.selectEvent = event;
        }

        /** Cac value da chon (dropdown cho chon nhieu thi co nhieu phan tu). */
        public String[] getValues() { return selectEvent.getValues().toArray(new String[0]); }

        /** Value dau tien da chon (tien cho dropdown chon 1). */
        public String getValue() {
            List<String> values = selectEvent.getValues();
            return values.isEmpty() ? null : values.get(0);
        }

        public String[] getLabels() {
            return selectEvent.getSelectedOptions().stream().map(SelectOption::getLabel).toArray(String[]::new);
        }
    }

    public static class DiscordCommand extends DiscordInteraction {
        private final SlashCommandInteractionEvent event;

        public DiscordCommand(DiscordBinding owner, SlashCommandInteractionEvent event) {
            super(owner, event);
            this.event = event;
        }

        public String getName() { return event.getName(); }
        public String getSubcommandName() { return event.getSubcommandName(); }

        /** Gia tri option: String / Long / Double / Boolean / ID (voi user, channel, role). */
        public Object getOption(String name) {
            OptionMapping m = event.getOption(name);
            return m == null ? null : optionValue(m);
        }

        public String getString(String name) {
            OptionMapping m = event.getOption(name);
            return m == null ? null : m.getAsString();
        }

        public Map<String, Object> getOptions() {
            Map<String, Object> map = new LinkedHashMap<>();
            for (OptionMapping m : event.getOptions()) {
                map.put(m.getName(), optionValue(m));
            }
            return map;
        }
    }

    public static class DiscordAutocomplete {
        private final CommandAutoCompleteInteractionEvent event;

        public DiscordAutocomplete(DiscordBinding owner, CommandAutoCompleteInteractionEvent event) {
            this.event = event;
        }

        public String getCommandName() { return event.getName(); }
        public String getFocusedName() { return event.getFocusedOption().getName(); }
        public String getFocusedValue() { return event.getFocusedOption().getValue(); }
        public String getUserId() { return event.getUser().getId(); }

        /** Goi y toi da 25 lua chon. */
        public boolean respond(List<Object> choices) {
            try {
                List<String> list = new ArrayList<>();
                if (choices != null) {
                    for (Object c : choices) {
                        if (list.size() >= 25) break;
                        list.add(truncate(String.valueOf(c), 100));
                    }
                }
                event.replyChoiceStrings(list).queue();
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to reply Discord autocomplete: " + e.getMessage());
                return false;
            }
        }
    }

    public static class DiscordModal extends DiscordInteraction {
        private final ModalInteractionEvent event;

        public DiscordModal(DiscordBinding owner, ModalInteractionEvent event) {
            super(owner, event);
            this.event = event;
        }

        public String getModalId() { return event.getModalId(); }

        public String getValue(String fieldId) {
            var mapping = event.getValue(fieldId);
            return mapping == null ? null : mapping.getAsString();
        }

        public Map<String, String> getValues() {
            Map<String, String> map = new LinkedHashMap<>();
            event.getValues().forEach(v -> map.put(v.getId(), v.getAsString()));
            return map;
        }
    }

    public static class DiscordMessage {
        private final String content;
        private final String author;
        private final String channelId;
        private final String channelName;
        private final boolean isFromConfiguredChannel;
        private final boolean isBot;
        private final Map<String, Object> user;
        private Message message;

        public DiscordMessage(String content, String author, String channelId, String channelName, boolean isFromConfiguredChannel, boolean isBot, Map<String, Object> user) {
            this.content = content;
            this.author = author;
            this.channelId = channelId;
            this.channelName = channelName;
            this.isFromConfiguredChannel = isFromConfiguredChannel;
            this.isBot = isBot;
            this.user = user;
        }

        void attach(Message message) { this.message = message; }

        public String getContent() { return content; }
        public String getAuthor() { return author; }
        public String getChannelId() { return channelId; }
        public String getChannelName() { return channelName; }
        public boolean isFromConfiguredChannel() { return isFromConfiguredChannel; }
        public boolean isBot() { return isBot; }
        public Map<String, Object> getUser() { return user; }

        public String getId() { return message == null ? null : message.getId(); }
        public String getRawContent() { return message == null ? content : message.getContentRaw(); }
        public String getGuildId() { return message != null && message.isFromGuild() ? message.getGuild().getId() : null; }
        public boolean isDirectMessage() { return message != null && !message.isFromGuild(); }

        /** Tra loi truc tiep tin nhan nay (khong ping nguoi gui). */
        public boolean reply(String text) {
            if (message == null) return false;
            try {
                String t = text == null ? "" : text;
                if (t.length() > 2000) t = t.substring(0, 2000);
                message.reply(t).mentionRepliedUser(false).queue(null, logFailure("reply"));
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to reply to Discord message: " + e.getMessage());
                return false;
            }
        }

        public boolean react(String emoji) {
            if (message == null) return false;
            try {
                message.addReaction(Emoji.fromFormatted(emoji)).queue(null, logFailure("react"));
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to react to Discord message: " + e.getMessage());
                return false;
            }
        }

        public boolean delete() {
            if (message == null) return false;
            try {
                message.delete().queue(null, logFailure("delete"));
                return true;
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Failed to delete Discord message: " + e.getMessage());
                return false;
            }
        }
    }

    // ------------------------------------------------------------------
    // Listener
    // ------------------------------------------------------------------

    private void dispatch(IReplyCallback event, String what, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            NetworkJS.LOGGER.error("Error in Discord " + what + " callback: " + e.getMessage(), e);
            try {
                if (!event.isAcknowledged()) {
                    event.reply("Co loi khi xu ly yeu cau nay.").setEphemeral(true).queue();
                }
            } catch (Exception ignored) {}
        }
    }

    private class MessageListener extends ListenerAdapter {
        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            Consumer<DiscordButton> handler = buttonCallback;
            if (handler == null) return;
            dispatch(event, "button", () -> handler.accept(new DiscordButton(DiscordBinding.this, event)));
        }

        @Override
        public void onStringSelectInteraction(StringSelectInteractionEvent event) {
            Consumer<DiscordSelect> handler = selectCallback;
            if (handler == null) return;
            dispatch(event, "select", () -> handler.accept(new DiscordSelect(DiscordBinding.this, event)));
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            Consumer<DiscordCommand> handler = commandCallback;
            if (handler == null) return;
            dispatch(event, "slash command", () -> handler.accept(new DiscordCommand(DiscordBinding.this, event)));
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent event) {
            Consumer<DiscordModal> handler = modalCallback;
            if (handler == null) return;
            dispatch(event, "modal", () -> handler.accept(new DiscordModal(DiscordBinding.this, event)));
        }

        @Override
        public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
            Consumer<DiscordAutocomplete> handler = autocompleteCallback;
            if (handler == null) return;
            try {
                handler.accept(new DiscordAutocomplete(DiscordBinding.this, event));
            } catch (Exception e) {
                NetworkJS.LOGGER.error("Error in Discord autocomplete callback: " + e.getMessage(), e);
            }
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (messageCallbacks.isEmpty()) return;
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
                Member member = event.getMember();
                user.put("nickname", member.getNickname());
                user.put("colorRaw", member.getColorRaw());
                user.put("isOwner", member.isOwner());
                user.put("isPending", member.isPending());
                user.put("joinedAt", member.getTimeJoined().toString());
                user.put("boostedAt", member.getTimeBoosted() != null ? member.getTimeBoosted().toString() : null);
                user.put("isAdmin", member.hasPermission(Permission.ADMINISTRATOR));

                List<String> roleNames = new ArrayList<>();
                List<String> roleIds = new ArrayList<>();
                String highestRole = null;
                String highestRoleId = null;

                var roles = member.getRoles();
                if (!roles.isEmpty()) {
                    highestRole = roles.get(0).getName();
                    highestRoleId = roles.get(0).getId();
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
                user.put("isAdmin", false);
                user.put("roles", new String[0]);
                user.put("roleIds", new String[0]);
                user.put("highestRole", null);
                user.put("highestRoleId", null);
                user.put("roleCount", 0);
            }

            DiscordMessage discordMessage = new DiscordMessage(content, author, channelId, channelName, isFromConfiguredChannel, isBot, user);
            discordMessage.attach(event.getMessage());

            for (Consumer<DiscordMessage> callback : messageCallbacks) {
                try {
                    callback.accept(discordMessage);
                } catch (Exception e) {
                    NetworkJS.LOGGER.error("Error in Discord message callback: " + e.getMessage());
                }
            }
        }
    }
}
