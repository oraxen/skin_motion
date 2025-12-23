package dev.th0rgal.skinmotion.core.websocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * WebSocket client for connecting to the SkinMotion API.
 * Handles automatic reconnection and heartbeat.
 */
public class SkinWebSocketClient extends WebSocketClient {

    private final Logger logger;
    private final Consumer<WsMessage> messageHandler;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> reconnectTask;
    
    private volatile boolean shouldReconnect = true;
    private volatile boolean isConnecting = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;

    public SkinWebSocketClient(
            @NotNull URI serverUri,
            @NotNull String apiKey,
            @NotNull Logger logger,
            @NotNull Consumer<WsMessage> messageHandler
    ) {
        super(serverUri, Map.of("X-API-Key", apiKey));
        this.logger = logger;
        this.messageHandler = messageHandler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SkinWS-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // Set connection timeout
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("[WebSocket] Connected to API");
        isConnecting = false;
        reconnectAttempts = 0;
        
        // Start heartbeat
        startHeartbeat();
    }

    @Override
    public void onMessage(String message) {
        WsMessage wsMessage = WsMessage.fromJson(message);
        if (wsMessage != null) {
            try {
                messageHandler.accept(wsMessage);
            } catch (Exception e) {
                logger.warning("[WebSocket] Error handling message: " + e.getMessage());
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("[WebSocket] Disconnected: " + reason + " (code=" + code + ", remote=" + remote + ")");
        isConnecting = false;
        stopHeartbeat();
        
        if (shouldReconnect) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        logger.warning("[WebSocket] Error: " + ex.getMessage());
    }

    /**
     * Connect with auto-reconnect enabled.
     */
    public void connectAsync() {
        if (isConnecting || isOpen()) {
            return;
        }
        
        isConnecting = true;
        shouldReconnect = true;
        
        try {
            connect();
        } catch (Exception e) {
            logger.warning("[WebSocket] Failed to connect: " + e.getMessage());
            isConnecting = false;
            scheduleReconnect();
        }
    }

    /**
     * Disconnect and disable auto-reconnect.
     */
    public void disconnect() {
        shouldReconnect = false;
        stopHeartbeat();
        
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
        
        if (isOpen()) {
            close();
        }
        
        scheduler.shutdown();
    }

    /**
     * Send a player online notification.
     */
    public void sendPlayerOnline(String uuid, String username) {
        if (isOpen()) {
            send(WsMessage.playerOnline(uuid, username));
        }
    }

    /**
     * Send a player offline notification.
     */
    public void sendPlayerOffline(String uuid) {
        if (isOpen()) {
            send(WsMessage.playerOffline(uuid));
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (isOpen()) {
                send(WsMessage.ping());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect || reconnectTask != null) {
            return;
        }
        
        reconnectAttempts++;
        int delay = Math.min(reconnectAttempts * 5, MAX_RECONNECT_DELAY_SECONDS);
        
        logger.info("[WebSocket] Reconnecting in " + delay + " seconds (attempt " + reconnectAttempts + ")");
        
        reconnectTask = scheduler.schedule(() -> {
            reconnectTask = null;
            if (shouldReconnect && !isOpen() && !isConnecting) {
                try {
                    reconnect();
                } catch (Exception e) {
                    logger.warning("[WebSocket] Reconnect failed: " + e.getMessage());
                    scheduleReconnect();
                }
            }
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * Check if the client is connected.
     */
    public boolean isConnected() {
        return isOpen();
    }
}
