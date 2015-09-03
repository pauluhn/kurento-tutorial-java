package com.sparkhire;

import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Paul Uhn on 7/31/15.
 */
public class UserSession {
    private static final Logger log = LoggerFactory
            .getLogger(UserSession.class);

    private final String name;
    private final WebSocketSession session;
    private final String room;

    private String sdpOffer;
    private String callingTo;
    private String callingFrom;
    private WebRtcEndpoint webRtcEndpoint;
    private final List<IceCandidate> candidateList = new ArrayList<IceCandidate>();
    private boolean usePipeline = true;
    private Date lastActive = new Date();

    public UserSession(WebSocketSession session, String name, String room) {
        this.session = session;
        this.name = name;
        this.room = room;
    }

    public UserSession(WebSocketSession session, String name, String room, boolean usePipeline) {
        this(session, name, room);
        this.usePipeline = usePipeline;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public String getName() {
        return name;
    }

    public String getRoom() {
        return room;
    }

    public boolean getUsePipeline() {
        return usePipeline;
    }

    public Date getLastActive() {
        return lastActive;
    }

    private void resetLastActive() {
        lastActive = new Date();
    }

    public String getSdpOffer() {
        return sdpOffer;
    }

    public void setSdpOffer(String sdpOffer) {
        this.sdpOffer = sdpOffer;
    }

    public String getCallingTo() {
        return callingTo;
    }

    public void setCallingTo(String callingTo) {
        this.callingTo = callingTo;
    }

    public String getCallingFrom() {
        return callingFrom;
    }

    public void setCallingFrom(String callingFrom) {
        this.callingFrom = callingFrom;
    }

    public void sendMessage(JsonObject message) throws IOException {
        log.debug("Sending message from user '{}': {}", name, message);
        session.sendMessage(new TextMessage(message.toString()));
        resetLastActive();
    }

    public String getSessionId() {
        return session.getId();
    }

    public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
        this.webRtcEndpoint = webRtcEndpoint;

        for (IceCandidate e : candidateList) {
            this.webRtcEndpoint.addIceCandidate(e);
        }
        this.candidateList.clear();
    }

    public void addCandidate(IceCandidate e) {
        if (this.webRtcEndpoint != null) {
            this.webRtcEndpoint.addIceCandidate(e);
        } else {
            candidateList.add(e);
        }
    }
}
