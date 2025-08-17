# NetworkJS

A powerful KubeJS addon that enables internet connectivity and Discord integration for your Minecraft server scripts.

> **Note**: This is my first Java mod! I'm still learning, so feedback and contributions are very welcome. :D

## Features

- **HTTP/FETCH Support** - Make HTTP requests from your KubeJS scripts
- **Discord Bot Integration** - Send messages, embeds, and listen to Discord events
- **Server Utilities** - Send colored messages to players and get server info
- **Easy Integration** - Works seamlessly with KubeJS with both global functions and class access

## Installation

1. Download the latest jar from the [releases page](https://github.com/SSnowly/NetworkJS/releases)
2. Place the jar in your `mods` folder
3. Make sure you have [KubeJS](https://github.com/KubeJS-Mods/KubeJS) installed
4. Restart your server

## API Reference

### Global Functions (New!)

```javascript
// HTTP Requests - Now available as global functions!
fetch('https://api.example.com/data')
fetchAsync('https://api.example.com/data', { 
    method: 'POST', 
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ key: 'value' })
})
```

### Global Classes

#### `DiscordBot` - Discord Integration
```javascript
const config = {
    token: "your-bot-token",
    guild: "your-guild-id", 
    channels: {
        chat: "channel-id",
        announcements: "channel-id"
    },
    sanitizeMessages: true
};

const bot = new DiscordBot(config);

// Send messages
bot.sendMessage("chat", "Hello from Minecraft!");

// Send embeds
bot.sendEmbed("announcements", {
    title: "Server Status",
    description: "Server is online!",
    color: 0x00ff00
});

// Listen for Discord messages
bot.onMessage(function(discordMessage) {
    console.log("Message from " + discordMessage.getAuthor() + ": " + discordMessage.getContent());
});

// Set bot activity
bot.setActivity("Minecraft Server Online");
```

#### `Server` - Minecraft Server Utilities
```javascript
// Send colored messages to all players
Server.sendRawMessage("&aWelcome to the server!");

// Send message to specific player
Server.sendRawMessageToPlayer("PlayerName", "&bHello there!");

// Get server info
const playerCount = Server.getPlayerCount();
const playerNames = Server.getPlayerNames(); // Returns array of strings
```

#### `FetchBinding` - HTTP Requests (Legacy)
```javascript
// You can still use the class-based approach
const response = FetchBinding.fetch("https://api.example.com");
console.log("Status:", response.getStatus());
console.log("Response:", response.text());

// With options
const options = new FetchOptions("POST", {"Content-Type": "application/json"}, '{"data": "value"}');
const response = FetchBinding.fetch("https://api.example.com", options);

// Async requests
const futureResponse = FetchBinding.fetchAsync("https://api.example.com");
```

### Complete API Exports

The following classes and functions are available globally in your KubeJS scripts:

| Export | Type | Description |
|--------|------|-------------|
| `fetch()` | Function | Make HTTP requests (global function) |
| `fetchAsync()` | Function | Make async HTTP requests (global function) |
| `DiscordBot` | Class | Discord bot functionality |
| `Server` | Class |  server utilities |
| `FetchBinding` | Class | HTTP request utilities (legacy) |
| `FetchOptions` | Class | HTTP request options |
| `FetchResponse` | Class | HTTP response object |

## Example Usage

### Discord-Minecraft Bridge
```javascript
const discordConfig = {
    token: "your-bot-token",
    guild: "your-guild-id",
    channels: { chat: "chat-channel-id" }
};

const bot = new DiscordBot(discordConfig);

// Relay Discord messages to Minecraft
bot.onMessage(function(msg) {
    if (msg.isFromConfiguredChannel() && !msg.isBot()) {
        Server.sendRawMessage(`&7[Discord] &f${msg.getAuthor()}: ${msg.getContent()}`);
    }
});

// Relay Minecraft chat to Discord
PlayerEvents.chat(event => {
    const playerName = event.player.name.getString();
    const message = event.message;
    bot.sendMessage("chat", `**${playerName}**: ${message}`);
});
```

### Server Status API
```javascript
// Create a simple API endpoint using fetch
ServerEvents.loaded(event => {
    const statusData = {
        online: true,
        players: Server.getPlayerCount(),
        playerList: Server.getPlayerNames()
    };
    
    // Post to your status API
    fetchAsync('https://your-api.com/server-status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(statusData)
    });
});
```

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.200+
- KubeJS 2101.7.1+

## Building

```bash
git clone https://github.com/your-username/NetworkJS.git
cd NetworkJS
./gradlew build
```

The built jar will be in `build/libs/networkjs-1.21.1-{version}.jar`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

As a new Java developer, I'm always looking to improve! Feel free to:

- Open issues for bugs or feature requests
- Submit pull requests with improvements
- Share feedback on the code structure
- Help with documentation

## Support

- Open an [issue](https://github.com/SSnowly/NetworkJS/issues) for bugs
- Check the [KubeJS Wiki](https://kubejs.com/) for general KubeJS help

---

Made with <3 by ME!
