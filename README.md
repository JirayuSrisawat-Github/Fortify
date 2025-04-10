# Fortify - Security Plugin for Lavalink

Fortify is a comprehensive security plugin for [Lavalink](https://github.com/lavalink-devs/Lavalink), designed to protect your Lavalink server from abuse and unauthorized access. It provides essential security features including rate limiting, player limits, path blocking, and notifications.

## Features

- **Rate Limiting**: Protects your server from API abuse by limiting the number of requests from each IP address.
- **Player Limits**: Controls the maximum number of players each client can create.
- **Path Blocking**: Prevents access to specific paths or path patterns.
- **IP and Client Bypass**: Allow specific IPs and client IDs to bypass restrictions.
- **Proxy Support**: Correctly identifies client IPs when using reverse proxies.
- **Notifications**: Receive alerts when security incidents occur.
- **Firewall Integration**: Automatically block abusive IPs using system firewall (iptables or UFW).
- **Connection Throttling**: Automatically slow down connections during high server load.
- **Management API**: Secure API for monitoring and managing security settings.

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
      blockWithFirewall: false  # Set to true to block IPs using system firewall
      firewallType: "iptables"  # Options: "iptables", "ufw", or "windows"
      maxRequests: 100          # Maximum requests allowed in the duration window
      duration: 60              # Duration window in seconds
      blockThreshold: 5         # Block IP after exceeding rate limit this many times
      blockDuration: 300        # How long to block IPs (in seconds)
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

### Path Blocking

```yml
plugins:
  fortify:
    pathblock:
      enabled: true
      blockedPaths:            # Exact paths to block
        - "/youtube"
      blockedPathPatterns:     # Regex patterns to block
        - "^/youtube/.*$"
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
        pathBlock: true      # Enable notifications for path blocking violations
```

### Throttling Configuration

```yml
plugins:
  fortify:
    throttle:
      enabled: true
      cpuThreshold: 80.0        # CPU usage percentage threshold
      memoryThreshold: 80.0     # Memory usage percentage threshold
      connectionDelay: 1000     # Delay in milliseconds to apply when throttling
```

### API Configuration

```yml
plugins:
  fortify:
    api:
      enabled: true
      apiKey: "your-secure-api-key-here"  # Required for API authentication
```

## Firewall Integration

Fortify supports integrating with system firewalls on both Linux and Windows:

- **Linux**: Uses either iptables or ufw
- **Windows**: Uses Windows Firewall (netsh advfirewall)

⚠️ **Important for Windows Users**:
- The application **must be run as Administrator** when using Windows Firewall integration
- For service installations, configure the service to run with administrator privileges
- Blocking/unblocking operations will fail without elevation

Set `blockWithFirewall: true` and choose the appropriate `firewallType` for your operating system.

## API Endpoints

Fortify provides management API endpoints that require authentication via the `X-Fortify-Key` header:

### Status and Management

- `GET /fortify/status` - Get system status and configuration information
- `GET /fortify/blocked` - List currently blocked IPs
- `POST /fortify/block/{ip}` - Manually block an IP address
- `POST /fortify/unblock/{ip}` - Manually unblock an IP address

### Path Blocking Management

- `GET /fortify/pathblock/paths` - List currently blocked paths and patterns
- `POST /fortify/pathblock/add/path` - Add a path to the block list
- `POST /fortify/pathblock/add/pattern` - Add a regex pattern to the block list
- `POST /fortify/pathblock/remove/path` - Remove a path from the block list
- `POST /fortify/pathblock/remove/pattern` - Remove a pattern from the block list

## Headers

Fortify adds the following headers to HTTP responses:

- `X-RateLimit-Limit`: The maximum number of requests you can make
- `X-RateLimit-Remaining`: The number of requests remaining in the current window
- `X-RateLimit-Reset`: When the rate limit window resets (Unix timestamp in milliseconds)
- `X-RateLimit-Used`: The number of requests already used in the current window
- `Retry-After`: How long to wait before making another request (when rate limited)

## Requirements

- Lavalink server v4 or higher
- Java 17 or higher
- **Windows systems**: Administrator privileges for firewall operations

## Building from Source

1. Clone this repository
2. Run `./gradlew build` (for Windows: `./gradlew.bat build`)
3. The jar file will be in `build/libs/`

## License

This project is licensed under the MIT License - see the LICENSE file for details.