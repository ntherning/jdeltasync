/*
 * Copyright (c) 2011, the JDeltaSync project. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.jdeltasync;

import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a particular user's session.
 */
public class DeltaSyncSession {
    private final String username;
    private final String password;
    private Logger logger;
    protected String ticket;
    protected String dsBaseUri = null;
    protected CookieStore cookies = new BasicCookieStore();
    
    /**
     * Creates a new {@link DeltaSyncSession}.
     * 
     * @param username the user's username.
     * @param password the user's password.
     */
    public DeltaSyncSession(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Returns the username.
     * 
     * @return the username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the password.
     * 
     * @return the password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the ticket associated with this session. This is what the login
     * process returns and the ticket has to be used in every subsequent
     * <code>Sync</code>, <code>ItemOperations</code>, etc request to the 
     * server.
     * 
     * @return the ticket.
     */
    public String getTicket() {
        return ticket;
    }
    
    /**
     * Returns the {@link Logger} which will be used by {@link DeltaSyncClient}
     * to log things for this session.
     * 
     * @return the {@link Logger}.
     */
    public Logger getLogger() {
        if (logger == null) {
            logger = LoggerFactory.getLogger(DeltaSyncSession.class.getName() + "." + username);
        }
        return logger;
    }

    /**
     * Sets the {@link Logger} which will be used by {@link DeltaSyncClient}
     * to log things for this session.
     * 
     * @param logger the {@link Logger}.
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString() + "(");
        sb.append("username").append("=").append(username).append(",");
        sb.append("password").append("=").append("******").append(",");
        sb.append("ticket").append("=").append(ticket);
        sb.append(")");
        return sb.toString();
    }
}
