/*
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.keycloak.adapters.undertow;

import io.undertow.security.api.AuthenticatedSessionManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.handlers.security.CachedAuthenticatedSessionHandler;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages relationship to users and sessions so that forced admin logout can be implemented
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class UndertowUserSessionManagement implements SessionListener {
    private static final Logger log = Logger.getLogger(UndertowUserSessionManagement.class);
    private static final String AUTH_SESSION_NAME = CachedAuthenticatedSessionHandler.class.getName() + ".AuthenticatedSession";
    protected volatile boolean registered;

    public void login(SessionManager manager) {
        if (!registered) {
            manager.registerSessionListener(this);
            registered = true;
        }
    }

    public void logoutAll(SessionManager manager) {
        Set<String> allSessions = manager.getAllSessions();
        for (String sessionId : allSessions) logoutSession(manager, sessionId);
    }

    public void logoutHttpSessions(SessionManager manager, List<String> sessionIds) {
        log.debug("logoutHttpSessions: " + sessionIds);

        for (String sessionId : sessionIds) {
            logoutSession(manager, sessionId);
        }
    }

    protected void logoutSession(SessionManager manager, String httpSessionId) {
        log.debug("logoutHttpSession: " + httpSessionId);
        Session session = getSessionById(manager, httpSessionId);
        try {
            session.invalidate(null);
        } catch (Exception e) {
            log.warnf("Session %s not present or already invalidated.", httpSessionId);
        }
    }

    protected Session getSessionById(SessionManager manager, final String sessionId) {
        // TODO: Workaround for WFLY-3345. Remove this once we move to wildfly 8.2
        if (manager.getClass().getName().equals("org.wildfly.clustering.web.undertow.session.DistributableSessionManager")) {
            return manager.getSession(null, new SessionConfig() {

                @Override
                public void setSessionId(HttpServerExchange exchange, String sessionId) {
                }

                @Override
                public void clearSession(HttpServerExchange exchange, String sessionId) {
                }

                @Override
                public String findSessionId(HttpServerExchange exchange) {
                    return sessionId;
                }

                @Override
                public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
                    return null;
                }

                @Override
                public String rewriteUrl(String originalUrl, String sessionId) {
                    return null;
                }

            });

        } else {
            return manager.getSession(sessionId);
        }
    }


    @Override
    public void sessionCreated(Session session, HttpServerExchange exchange) {
    }

    @Override
    public void sessionDestroyed(Session session, HttpServerExchange exchange, SessionDestroyedReason reason) {
        // Look up the single session id associated with this session (if any)
        String username = getUsernameFromSession(session);
        log.debugf("Session destroyed for user: %s, sessionId: %s", username, session.getId());
    }

    protected String getUsernameFromSession(Session session) {
        AuthenticatedSessionManager.AuthenticatedSession authSession = (AuthenticatedSessionManager.AuthenticatedSession) session.getAttribute(AUTH_SESSION_NAME);
        if (authSession == null) return null;
        return authSession.getAccount().getPrincipal().getName();

    }


    @Override
    public void sessionIdChanged(Session session, String oldSessionId) {
    }

    @Override
    public void attributeAdded(Session session, String name, Object value) {
    }

    @Override
    public void attributeUpdated(Session session, String name, Object newValue, Object oldValue) {
    }

    @Override
    public void attributeRemoved(Session session, String name, Object oldValue) {
    }

}
