# Fortify - Security Plugin for Lavalink

Fortify is a comprehensive security plugin for [Lavalink](https://github.com/lavalink-devs/Lavalink), designed to protect your Lavalink server from abuse and unauthorized access. It provides essential security features including rate limiting, player limits, and notifications.

## Features

- **Rate Limiting**: Protects your server from API abuse by limiting the number of requests from each IP address.
- **Player Limits**: Controls the maximum number of players each client can create.
- **IP and Client Bypass**: Allow specific IPs and client IDs to bypass restrictions.
- **Proxy Support**: Correctly identifies client IPs when using reverse proxies.
- **Notifications**: Receive alerts when security incidents occur.

## Installation

Add Fortify to your Lavalink server configuration:

```yml
lavalink:
  plugins:
    - dependency: com.github.JirayuSrisawat-Github:Fortify:{VERSION} # replace {VERSION} with the latest version
      repository: https://jitpack.io
```

## Configuration

### Rate Limiting

```yml
plugins:
  fortify:
    ratelimit:
      enabled: true
      blockWithIptables: false # Set to true to block IPs using iptables (Linux only)
      maxRequests: 100         # Maximum requests allowed in the duration window
      duration: 60             # Duration window in seconds
      blockThreshold: 5        # Block IP after exceeding rate limit this many times
      blockDuration: 300       # How long to block IPs (in seconds)
```

### Player Limits

```yml
plugins:
  fortify:
    playerlimit:
      enabled: true
      maxPlayers: 10           # Maximum players per connection
      closeOnExceed: true      # Close the WebSocket when limit is exceeded
```

### Bypass Configuration

```yml
plugins:
  fortify:
    bypass:
      allowedIps:              # IPs that bypass rate limiting
        - "127.0.0.1"
        - "10.0.0.10"
      allowedClients:          # Client IDs that bypass player limits
        - 123456789012345678
        - 876543210987654321
```

### Proxy Configuration

```yml
plugins:
  fortify:
    proxy:
      trustProxy: false               # Set to true if behind a reverse proxy
      proxyHeader: "X-Forwarded-For"  # Header containing the real IP
```

### Notification Configuration

```yml
plugins:
  fortify:
    notification:
      discordWebhookUrl: "https://discord.com/api/webhooks/your-webhook-url"
      enabled:
        ratelimit: true      # Enable notifications for rate limit violations
        playerLimit: true    # Enable notifications for player limit violations
```

## Headers

Fortify adds the following headers to HTTP responses:

- `X-RateLimit-Limit`: The maximum number of requests you can make
- `X-RateLimit-Remaining`: The number of requests remaining in the current window
- `X-RateLimit-Reset`: When the rate limit window resets (Unix timestamp in milliseconds)
- `Retry-After`: How long to wait before making another request (when rate limited)

## Requirements

- Lavalink server v4 or higher
- Java 17 or higher

## Building from Source

1. Clone this repository
2. Run `./gradlew build` (for Windows: `./gradlew.bat build`)
3. The jar file will be in `build/libs/`

## License

This project is licensed under the MIT License - see the LICENSE file for details.
