package net.jirayu.fortify;

import dev.arbjerg.lavalink.api.IPlayer;
import dev.arbjerg.lavalink.api.ISocketContext;
import dev.arbjerg.lavalink.api.PluginEventHandler;
import net.jirayu.fortify.config.BypassConfig;
import net.jirayu.fortify.config.NotificationConfig;
import net.jirayu.fortify.config.PlayerLimitConfig;
import net.jirayu.fortify.notification.NotificationService;
import net.jirayu.fortify.monitor.ResourceMonitor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FortifySocket extends PluginEventHandler {
    private static final Logger log = LoggerFactory.getLogger(FortifySocket.class);
    private final Map<String, ISocketContext> connectedSockets = new ConcurrentHashMap<>();

    private final PlayerLimitConfig playerLimitConfig;
    private final NotificationConfig notificationConfig;
    private final BypassConfig bypassConfig;
    private final NotificationService notificationService;
    private final ResourceMonitor resourceMonitor;

    public FortifySocket(PlayerLimitConfig playerLimitConfig,
                         NotificationConfig notificationConfig,
                         BypassConfig bypassConfig,
                         NotificationService notificationService,
                         ResourceMonitor resourceMonitor) {
        this.playerLimitConfig = playerLimitConfig;
        this.notificationConfig = notificationConfig;
        this.bypassConfig = bypassConfig;
        this.notificationService = notificationService;
        this.resourceMonitor = resourceMonitor;

        log.info("Player limit configuration: enabled={}, maxPlayers={}",
                playerLimitConfig.isEnabled(), playerLimitConfig.getMaxPlayers());
    }

    @Override
    public void onNewPlayer(@NotNull ISocketContext context, @NotNull IPlayer player) {
        log.debug("New player request from userId={}, sessionId={}, current players={}/{}",
                context.getUserId(), context.getSessionId(), context.getPlayers().size(), playerLimitConfig.getMaxPlayers());

        if (!isAllowedId(context.getUserId()) &&
                playerLimitConfig.isEnabled() &&
                context.getPlayers().size() >= playerLimitConfig.getMaxPlayers()) {
            String sessionId = context.getSessionId();
            Long userId = context.getUserId();

            log.warn("Player rejected due to player limit: sessionId={}, userId={}",
                    sessionId, userId);

            if (notificationConfig.getEnabled().isPlayerLimit()) {
                notificationService.sendPlayerLimitNotification(sessionId, userId);
            }

            if (playerLimitConfig.isCloseOnExceed()) {
                context.closeWebSocket(1008, "Player limit exceeded");
            }

            player.getAudioPlayer().destroy();
            return;
        }

        super.onNewPlayer(context, player);
    }

    @Override
    public void onWebSocketOpen(@NotNull ISocketContext context, boolean resumed) {
        log.info("New connection: sessionId={}, userId={}, resumed={}, current total={}",
                context.getSessionId(), context.getUserId(), resumed, connectedSockets.size());

        if (resourceMonitor.isThrottling() && !isAllowedId(context.getUserId())) {
            try {
                int delay = resourceMonitor.getConnectionDelay();
                log.info("Throttling connection for user {} by {}ms", context.getUserId(), delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        this.connectedSockets.put(context.getSessionId(), context);
        log.debug("Socket connection opened: sessionId={}, current players={}/{}",
                context.getSessionId(), connectedSockets.size(), playerLimitConfig.getMaxPlayers());
    }

    @Override
    public void onSocketContextDestroyed(@NotNull ISocketContext context) {
        this.connectedSockets.remove(context.getSessionId());
        log.debug("Socket connection closed: sessionId={}, current players={}/{}",
                context.getSessionId(), context.getPlayers().size(), playerLimitConfig.getMaxPlayers());
    }

    public Map<String, ISocketContext> getConnectedSockets() {
        return connectedSockets;
    }

    public int getCurrentPlayerCount(String sessionId) {
        ISocketContext context = connectedSockets.get(sessionId);
        if (context != null) {
            return context.getPlayers().size();
        }

        log.warn("Session ID not found: {}", sessionId);
        return 0;
    }

    public boolean isAllowedId(Long clientId) {
        return Arrays.asList(bypassConfig.getAllowedClients()).contains(clientId);
    }
}