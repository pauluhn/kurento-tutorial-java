package com.sparkhire;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.jsonrpc.JsonUtils;
import org.kurento.tutorial.one2onecall.CallMediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Paul Uhn on 7/31/15.
 */

public class CallHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
    private static final Gson gson = new GsonBuilder().create();

    private final ConcurrentHashMap<String, CallMediaPipeline> pipelines = new ConcurrentHashMap<String, CallMediaPipeline>();

    @Autowired
    private KurentoClient kurento;

    @Autowired
    private UserRegistry registry;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        UserSession user = registry.getBySession(session);

        if (user != null) {
            log.debug("Incoming message from user '{}': {}", user.getName(), jsonMessage);
        } else {
            log.debug("Incoming message from new user: {}", jsonMessage);
        }

        switch (jsonMessage.get("id").getAsString()) {
            case "register":
                try {
                    if (register(session, jsonMessage)) {
                        List<UserSession> roomUsers = registry.getUsersByRoom("TEST ROOM");
                        if (roomUsers.size() > 1) {
                            Thread.sleep(2000);
                            startCommunication(roomUsers);
                        }
                    }

                } catch (Throwable t) {
                    log.error(t.getMessage(), t);
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "registerResponse");
                    response.addProperty("response", "rejected");
                    response.addProperty("message", t.getMessage());
                    session.sendMessage(new TextMessage(response.toString()));
                }
                break;
            case "onIceCandidate": {
                JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
                if (user != null) {
                    IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
                            candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
                    user.addCandidate(cand);
                }
                break;
            }
            case "stop":
                stop(session);
                break;
            default:
                break;
        }
    }

    private boolean register(WebSocketSession session, JsonObject jsonMessage) throws IOException {
        String name = jsonMessage.getAsJsonPrimitive("name").getAsString();
        String room = jsonMessage.getAsJsonPrimitive("room").getAsString();

        UserSession caller = new UserSession(session, name, room);
        String responseMsg = "accepted";
        boolean registered = false;
        if (name.isEmpty()) {
            responseMsg = "rejected: empty user name";
        } else if (registry.exists(name)) {
            responseMsg = "rejected: user '" + name + "' already registered";
        } else {
            caller.setSdpOffer(jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString());
            registry.register(caller);
            registered = true;
        }

        JsonObject response = new JsonObject();
        response.addProperty("id", "registerResponse");
        response.addProperty("response", responseMsg);
        caller.sendMessage(response);

        return registered;
    }

    private void startCommunication(List<UserSession> userSessions) throws IOException {
        if (userSessions.size() < 2) {
            return;
        }
        final UserSession caller = userSessions.get(0);
        final UserSession callee = userSessions.get(1);

        String from = caller.getName();
        callee.setCallingFrom(from);
        String to = callee.getName();
        caller.setCallingTo(to);

        CallMediaPipeline pipeline = null;
        try {
            pipeline = new CallMediaPipeline(kurento);
            pipelines.put(caller.getSessionId(), pipeline);
            pipelines.put(callee.getSessionId(), pipeline);

            callee.setWebRtcEndpoint(pipeline.getCalleeWebRtcEP());
            pipeline.getCalleeWebRtcEP().addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
                @Override
                public void onEvent(OnIceCandidateEvent event) {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "iceCandidate");
                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                    try {
                        synchronized (callee.getSession()) {
                            callee.getSession().sendMessage(new TextMessage(response.toString()));
                        }
                    } catch (IOException e) {
                        log.debug(e.getMessage());
                    }
                }
            });

            caller.setWebRtcEndpoint(pipeline.getCallerWebRtcEP());
            pipeline.getCallerWebRtcEP().addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
                @Override
                public void onEvent(OnIceCandidateEvent event) {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "iceCandidate");
                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                    try {
                        synchronized (caller.getSession()) {
                            caller.getSession().sendMessage(new TextMessage(response.toString()));
                        }
                    } catch (IOException e) {
                        log.debug(e.getMessage());
                    }
                }
            });

            String calleeSdpAnswer = pipeline.generateSdpAnswerForCallee(callee.getSdpOffer());
            String callerSdpAnswer = pipeline.generateSdpAnswerForCaller(caller.getSdpOffer());

            JsonObject response = new JsonObject();
            response.addProperty("id", "startCommunication");
            response.addProperty("sdpAnswer", calleeSdpAnswer);

            synchronized (callee) {
                callee.sendMessage(response);
            }

            response = new JsonObject();
            response.addProperty("id", "startCommunication");
            response.addProperty("sdpAnswer", callerSdpAnswer);

            synchronized (caller) {
                caller.sendMessage(response);
            }

            pipeline.getCalleeWebRtcEP().gatherCandidates();
            pipeline.getCallerWebRtcEP().gatherCandidates();

        } catch (Throwable t) {
            log.error(t.getMessage(), t);

            if (pipeline != null) {
                pipeline.release();
            }

            pipelines.remove(caller.getSessionId());
            pipelines.remove(callee.getSessionId());

            JsonObject response = new JsonObject();
            response.addProperty("id", "stopCommunication");
            caller.sendMessage(response);

            response = new JsonObject();
            response.addProperty("id", "stopCommunication");
            callee.sendMessage(response);
        }
    }

    public void stop(WebSocketSession session) throws IOException {
        String sessionId = session.getId();
        if (pipelines.containsKey(sessionId)) {
            pipelines.get(sessionId).release();
            CallMediaPipeline pipeline = pipelines.remove(sessionId);
            pipeline.release();

            // Both users can stop the communication. A 'stopCommunication'
            // message will be sent to the other peer.
            UserSession stopperUser = registry.getBySession(session);
            UserSession stoppedUser = (stopperUser.getCallingFrom() != null)
                    ? registry.getByName(stopperUser.getCallingFrom()) : registry.getByName(stopperUser.getCallingTo());

            JsonObject message = new JsonObject();
            message.addProperty("id", "stopCommunication");
            stoppedUser.sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        registry.removeBySession(session);
    }
}
