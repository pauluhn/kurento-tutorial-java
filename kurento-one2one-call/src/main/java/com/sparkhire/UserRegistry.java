package com.sparkhire;

import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Paul Uhn on 7/31/15.
 */
public class UserRegistry {
    private ConcurrentHashMap<String, UserSession> usersByName = new ConcurrentHashMap<String, UserSession>();
    private ConcurrentHashMap<String, UserSession> usersBySessionId = new ConcurrentHashMap<String, UserSession>();

    public void register(UserSession user) {
        usersByName.put(user.getName(), user);
        usersBySessionId.put(user.getSession().getId(), user);
    }

    public UserSession getByName(String name) {
        return usersByName.get(name);
    }

    public UserSession getBySession(WebSocketSession session) {
        return usersBySessionId.get(session.getId());
    }

    public boolean exists(String name) {
        return usersByName.keySet().contains(name);
    }

    public UserSession removeBySession(WebSocketSession session) {
        final UserSession user = getBySession(session);
        if (user != null) {
            usersByName.remove(user.getName());
            usersBySessionId.remove(session.getId());
        }
        return user;
    }

    public List<UserSession> getUsersByRoom(String room) {
        List<UserSession> foundUserSessions = new ArrayList<>();
        for (Enumeration<UserSession> e = usersByName.elements(); e.hasMoreElements();) {
            UserSession userSession = e.nextElement();
            if (userSession.getRoom().equalsIgnoreCase(room)) {
                foundUserSessions.add(userSession);
            }
        }
        return foundUserSessions;
    }
}
