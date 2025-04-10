package net.jirayu.fortify.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import net.jirayu.fortify.config.NotificationConfig;
import net.jirayu.fortify.config.PlayerLimitConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationConfig config;
    private final PlayerLimitConfig playerLimitConfig;
    private final ObjectMapper objectMapper;
    private final HttpInterfaceManager httpInterfaceManager;

    public NotificationService(NotificationConfig config, PlayerLimitConfig playerLimitConfig) {
        this.config = config;
        this.playerLimitConfig = playerLimitConfig;
        this.objectMapper = new ObjectMapper();
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Async
    public void sendBlockNotification(String ip, String path) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost httpPost = new HttpPost(config.getDiscordWebhookUrl());

            Map<String, Object> field1 = new HashMap<>();
            field1.put("name", "IP Address");
            field1.put("value", ip);
            field1.put("inline", true);

            Map<String, Object> field2 = new HashMap<>();
            field2.put("name", "Last Path");
            field2.put("value", path);
            field2.put("inline", true);

            Map<String, Object> field3 = new HashMap<>();
            field3.put("name", "Time");
            field3.put("value", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            field3.put("inline", false);

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "IP Address Blocked");
            embed.put("description", "An IP address has been blocked due to rate limit violations");
            embed.put("color", 16711680);
            embed.put("fields", new Object[]{field1, field2, field3});

            Map<String, Object> webhookData = new HashMap<>();
            webhookData.put("username", "Fortify Security");
            webhookData.put("embeds", new Object[]{embed});

            String jsonPayload = objectMapper.writeValueAsString(webhookData);
            httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpInterface.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("Discord notification sent successfully, status code: {}", statusCode);
                } else {
                    log.error("Failed to send Discord notification, status code: {}", statusCode);
                }
            }
        } catch (Exception e) {
            log.error("Error sending notification", e);
        }
    }

    @Async
    public void sendPlayerLimitNotification(String sessionId, Long userId) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost httpPost = new HttpPost(config.getDiscordWebhookUrl());

            Map<String, Object> field1 = new HashMap<>();
            field1.put("name", "Session ID");
            field1.put("value", sessionId);
            field1.put("inline", true);

            Map<String, Object> field2 = new HashMap<>();
            field2.put("name", "User ID");
            field2.put("value", userId);
            field2.put("inline", true);

            Map<String, Object> field3 = new HashMap<>();
            field3.put("name", "Max Players");
            field3.put("value", playerLimitConfig.getMaxPlayers());
            field3.put("inline", true);

            Map<String, Object> field4 = new HashMap<>();
            field4.put("name", "Time");
            field4.put("value", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            field4.put("inline", false);

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "Player Limit Reached");
            embed.put("description", "A player was rejected because the player limit was reached");
            embed.put("color", 15105570);
            embed.put("fields", new Object[]{field1, field2, field3, field4});

            Map<String, Object> webhookData = new HashMap<>();
            webhookData.put("username", "Fortify Security");
            webhookData.put("embeds", new Object[]{embed});

            String jsonPayload = objectMapper.writeValueAsString(webhookData);
            httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpInterface.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("Player limit notification sent successfully, status code: {}", statusCode);
                } else {
                    log.error("Failed to send player limit notification, status code: {}", statusCode);
                }
            }
        } catch (Exception e) {
            log.error("Error sending player limit notification", e);
        }
    }

    @Async
    public void sendPathBlockNotification(String ip, String path) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost httpPost = new HttpPost(config.getDiscordWebhookUrl());

            Map<String, Object> field1 = new HashMap<>();
            field1.put("name", "IP Address");
            field1.put("value", ip);
            field1.put("inline", true);

            Map<String, Object> field2 = new HashMap<>();
            field2.put("name", "Blocked Path");
            field2.put("value", path);
            field2.put("inline", true);

            Map<String, Object> field3 = new HashMap<>();
            field3.put("name", "Time");
            field3.put("value", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            field3.put("inline", false);

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "Path Access Blocked");
            embed.put("description", "An attempt to access a restricted path was blocked");
            embed.put("color", 15158332);
            embed.put("fields", new Object[]{field1, field2, field3});

            Map<String, Object> webhookData = new HashMap<>();
            webhookData.put("username", "Fortify Security");
            webhookData.put("embeds", new Object[]{embed});

            String jsonPayload = objectMapper.writeValueAsString(webhookData);
            httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpInterface.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("Path block notification sent successfully, status code: {}", statusCode);
                } else {
                    log.error("Failed to send path block notification, status code: {}", statusCode);
                }
            }
        } catch (Exception e) {
            log.error("Error sending path block notification", e);
        }
    }
}