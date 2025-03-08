package org.example;

import org.json.JSONObject;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.example.SimulationWorker.stopSimulationHelper;
import static org.example.SimulationWorker.workers;

public class SimulationWebSocketHandler extends TextWebSocketHandler {

    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    public static WebSocketSession getSession(String sessionId) {
        for (WebSocketSession session : sessions) {
            if (session.getId().equals(sessionId)) {
                return session;
            }
        }
        return null;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("New WebSocket connection established: " + session.getId());

        try {
            session.sendMessage(new TextMessage(new JSONObject()
                    .put("type", "sessionId")
                    .put("sessionId", session.getId())
                    .toString()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        JSONObject json = new JSONObject(payload);

        String type = json.optString("type");
        String sessionId = json.optString("sessionId");

        if ("signal".equals(type)) {
            String signalData = json.optString("data");
            SimulationWorker worker = workers.get(sessionId);

            if (worker == null) {
                System.out.println("Error: No simulation running for sessionId " + sessionId);
                sendErrorMessage(session, "No simulation running for sessionId " + sessionId);
                return;
            }

            try {
                sendAckMessage(session, "Signal received successfully.");
                worker.sendSignal(signalData);
            } catch (IOException e) {
                e.printStackTrace();
                sendErrorMessage(session, "Error sending signal: " + e.getMessage());
            }
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        JSONObject errorResponse = new JSONObject()
                .put("type", "error")
                .put("message", errorMessage);
        session.sendMessage(new TextMessage(errorResponse.toString()));
    }

    private void sendAckMessage(WebSocketSession session, String message) throws IOException {
        JSONObject ackResponse = new JSONObject()
                .put("type", "ack")
                .put("message", message);
        session.sendMessage(new TextMessage(ackResponse.toString()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        boolean f = stopSimulationHelper(session.getId());
        if (f) {
            System.out.println("successfully mannual release: " + session.getId());
        }
        System.out.println("WebSocket connection closed: " + session.getId());
    }
}
