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
package com.googlecode.jdeltasync.pop;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.googlecode.jdeltasync.AuthenticationException;
import com.googlecode.jdeltasync.DeltaSyncClient;
import com.googlecode.jdeltasync.DeltaSyncClientHelper;
import com.googlecode.jdeltasync.DeltaSyncException;
import com.googlecode.jdeltasync.Folder;
import com.googlecode.jdeltasync.Message;

/**
 * Handles a POP3 connection.
 */
class PopHandler extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(PopHandler.class);
    
    private static final String GREETING = "+OK JDeltaSync POP3 server ready";
    private static final String OK = "+OK";
    private static final String OK_UIDL = "+OK %d %s";
    private static final String OK_STAT = "+OK %d %d";
    private static final String OK_QUIT = "+OK %d messages deleted from server";
    private static final String ERR_BAD_COMMAND = "-ERR Unrecognized or unexpected command";
    private static final String ERR_UNKNOWN_MESSAGE_NUMBER = "-ERR Unknown message number %d";
    private static final String ERR_COMMAND_SYNTAX_ERROR = "-ERR Command syntax error";
    private static final String ERR_UNKNOWN_ERROR = "-ERR Unknown error";
    private static final String ERR_AUTHENTICATION_FAILED = "-ERR Authentication failed";
    private static final String ERR_EXPECTED_USER_BEFORE_PASS = "-ERR Expected USER before PASS";
    private static final String ERR_MAILBOX_LOCKED = "-ERR Mailbox locked by another session";
    
    private static final Pattern USER = Pattern.compile("^USER\\s+([^\\s]+)$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern PASS = Pattern.compile("^PASS\\s+(.+)$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern QUIT = Pattern.compile("^QUIT$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern STAT = Pattern.compile("^STAT$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern UIDL = Pattern.compile("^UIDL(\\s+[^\\s]+)?$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern LIST = Pattern.compile("^LIST(\\s+[\\d]+)?$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern DELE = Pattern.compile("^DELE\\s+([\\d]+)$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern RETR = Pattern.compile("^RETR\\s+([\\d]+)$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern RSET = Pattern.compile("^RSET$", Pattern.CASE_INSENSITIVE); 
    private static final Pattern NOOP = Pattern.compile("^NOOP$", Pattern.CASE_INSENSITIVE); 

    private static final Set<String> connectedUsers = new HashSet<String>();
    
    private final String sessionId = UUID.randomUUID().toString();
    private final Socket socket;
    private final DeltaSyncClient deltaSyncClient;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final DeltaSyncClientHelper.Store store;

    private DeltaSyncClientHelper client;
    private String username;
    private String password;
    private Folder inbox;
    private Message[] messages;
    private Set<String> deleted = new HashSet<String>();
    
    public PopHandler(Socket socket, DeltaSyncClient deltaSyncClient, DeltaSyncClientHelper.Store store) throws IOException {
        this.socket = socket;
        this.deltaSyncClient = deltaSyncClient;
        this.store = store;
        
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ASCII"));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "ASCII"));
    }

    private void writeln(String s, Object ... args) {
        s = String.format(s, args);
        logger.debug("WRITE: {}", s);
        writer.printf(s + "\r\n");
    }
    
    private void user(String line) throws Exception {
        Matcher matcher = USER.matcher(line);
        if (!matcher.matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);
        } else {
            username = matcher.group(1);
            writeln(OK);
        }
    }
    
    private void pass(String line) throws Exception {
        if (username == null) {
            writeln(ERR_EXPECTED_USER_BEFORE_PASS);
        } else {
            Matcher matcher = PASS.matcher(line);
            if (!matcher.matches()) {
                writeln(ERR_COMMAND_SYNTAX_ERROR);
            } else {
                password = matcher.group(1);
                synchronized (connectedUsers) {
                    try {
                        DeltaSyncClientHelper helper = new DeltaSyncClientHelper(
                                deltaSyncClient, username, password, store);
                        helper.login();
                        if (connectedUsers.contains(username)) {
                            writeln(ERR_MAILBOX_LOCKED);
                        } else {
                            this.client = helper;
                            connectedUsers.add(username);
                            MDC.put("username", username);
                            logger.info("User {} logged in", username);
                            writeln(OK);
                        }
                    } catch (AuthenticationException e) {
                        writeln(ERR_AUTHENTICATION_FAILED);
                    }
                }
            }
        }
    }
    
    private boolean quit(String line) throws Exception {
        if (!QUIT.matcher(line).matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);
            return false;
        } else {
            if (client != null && !deleted.isEmpty()) {
                logger.info("Deleting {} messages from Inbox", deleted.size());
                client.delete(getInbox(), getDeletedMessages());
                logger.info("{} messages deleted from Inbox", deleted.size());
            }
            writeln(OK_QUIT, deleted.size());
            synchronized (connectedUsers) {
                connectedUsers.remove(username);
            }            
            return true;
        }
    }
    
    private void stat(String line) throws Exception {
        if (!STAT.matcher(line).matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);            
        } else {
            int n = 0;
            long octets = 0;
            for (Message msg : getAllMessages()) {
                if (!deleted.contains(msg.getId())) {
                    n++;
                    octets += msg.getSize();
                }
            }
            writeln(OK_STAT, n, octets);
        }
    }
    
    private void retr(String line) throws Exception {
        Matcher matcher = RETR.matcher(line);
        if (!matcher.matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);
        } else {
            int n = Integer.parseInt(matcher.group(1));
            Message[] msgs = getAllMessages();
            if (n <= 0 || n > msgs.length || deleted.contains(msgs[n - 1].getId())) {
                writeln(ERR_UNKNOWN_MESSAGE_NUMBER, n);
            } else {
                writeln(OK);
                writer.flush();
                OutputStream out = new ExtraDotOutputStream(new BufferedOutputStream(
                    new FilterOutputStream(socket.getOutputStream()) {
                        @Override
                        public void write(byte[] b, int off, int len)
                                throws IOException {
    
                            if (logger.isTraceEnabled()) {
                                logger.trace("READ: {}", 
                                        new String(b, off, len, "ISO8859-1"));
                            } else if (logger.isDebugEnabled()) {
                                logger.debug("READ: \"{}\" ({} bytes in total)", 
                                        new String(b, off, Math.min(len, 64), "ISO8859-1"),
                                        len);
                            }
                            this.out.write(b, off, len);
                        }
                    }
                ));
                client.downloadMessageContent(msgs[n - 1], out);
                out.flush();
                writeln("\r\n.");
            }
        }
    }
    
    private void dele(String line) throws Exception {
        Matcher matcher = DELE.matcher(line);
        if (!matcher.matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);
        } else {
            int n = Integer.parseInt(matcher.group(1));
            Message[] msgs = getAllMessages();
            if (n <= 0 || n > msgs.length || deleted.contains(msgs[n - 1].getId())) {
                writeln(ERR_UNKNOWN_MESSAGE_NUMBER, n);
            } else {
                deleted.add(msgs[n - 1].getId());
                writeln(OK);
            }
        }
    }
    
    private void uidl(String line) throws Exception {
        Matcher matcher = UIDL.matcher(line);
        if (!matcher.matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);
        } else if (matcher.group(1) != null) {
            int n = Integer.parseInt(matcher.group(1).trim());
            Message[] msgs = getAllMessages();
            if (n <= 0 || n > msgs.length || deleted.contains(msgs[n - 1].getId())) {
                writeln(ERR_UNKNOWN_MESSAGE_NUMBER, n);
            } else {
                writeln(OK_UIDL, n, msgs[n - 1].getId());
            }
        } else {
            Message[] msgs = getAllMessages();
            writeln(OK);
            int n = 1;
            for (Message msg : msgs) {
                if (!deleted.contains(msg.getId())) {
                    writeln("%d %s", n, msg.getId());
                }
                n++;
            }
            writeln(".");
        }
    }
    
    private void list(String line) throws Exception {
        Matcher matcher = LIST.matcher(line);
        if (!matcher.matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);
        } else if (matcher.group(1) != null) {
            int n = Integer.parseInt(matcher.group(1).trim());
            Message[] msgs = getAllMessages();
            if (n <= 0 || n > msgs.length || deleted.contains(msgs[n - 1].getId())) {
                writeln(ERR_UNKNOWN_MESSAGE_NUMBER, n);
            } else {
                writeln(OK_STAT, n, msgs[n - 1].getSize());
            }
        } else {
            Message[] msgs = getAllMessages();
            writeln(OK);
            int n = 1;
            for (Message msg : msgs) {
                if (!deleted.contains(msg.getId())) {
                    writeln("%d %d", n, msg.getSize());
                }
                n++;
            }
            writeln(".");
        }
    }
    
    private void rset(String line) throws Exception {
        if (!RSET.matcher(line).matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);            
        } else {
            deleted.clear();
            writeln(OK);
        }
    }
    
    private void noop(String line) throws Exception {
        if (!NOOP.matcher(line).matches()) {
            writeln(ERR_COMMAND_SYNTAX_ERROR);            
        } else {
            writeln(OK);
        }
    }
    
    private Folder getInbox() throws Exception {
        if (inbox == null) {
            inbox = client.getInbox();
        }
        return inbox;
    }

    private Message[] getAllMessages() throws Exception {
        if (messages == null) {
            messages = client.getMessages(getInbox());
            Arrays.sort(messages, new Comparator<Message>() {
                public int compare(Message m1, Message m2) {
                    return m1.getDateReceived().compareTo(m2.getDateReceived());
                }
            });
            
            logger.info("{} messages in Inbox", messages.length);        
        }
        return messages;
    }
    
    private Message[] getDeletedMessages() throws Exception {
        List<Message> result = new ArrayList<Message>();
        for (Message msg : getAllMessages()) {
            if (deleted.contains(msg.getId())) {
                result.add(msg);
            }
        }
        return result.toArray(new Message[result.size()]);
    }
    
    @Override
    public void run() {
        try {
            MDC.put("session", sessionId);
            
            logger.info("Connection from {}", socket.getRemoteSocketAddress());
            
            writeln(GREETING);
            boolean done = false;
            while (!done && !isInterrupted()) {
                writer.flush();
                String line = reader.readLine();
                if (line == null) {
                    logger.warn("End of stream. Closing socket.");
                    break;
                }
                if (logger.isDebugEnabled()) {
                    Matcher matcher = PASS.matcher(line);
                    if (matcher.matches()) {
                        logger.debug("READ: {}", line.replaceAll(Pattern.quote(matcher.group(1)), "<password omitted>"));
                    } else {
                        logger.debug("READ: {}", line);
                    }
                }
                line = line.trim();
                int spaceIndex = line.indexOf(' ');
                String cmd = (spaceIndex != -1 ? line.substring(0, spaceIndex) : line).toUpperCase();
                try {
                    if ("QUIT".equals(cmd)) {
                        done = quit(line);
                    } else if (client == null) {
                        if ("USER".equals(cmd)) {
                            user(line);
                        } else if ("PASS".equals(cmd)) {
                            pass(line);
                        } else {
                            writeln(ERR_BAD_COMMAND);
                        }
                    } else {
                        if ("STAT".equals(cmd)) {
                            stat(line);
                        } else if ("RETR".equals(cmd)) {
                            retr(line);
                        } else if ("DELE".equals(cmd)) {
                            dele(line);
                        } else if ("UIDL".equals(cmd)) {
                            uidl(line);
                        } else if ("LIST".equals(cmd)) {
                            list(line);
                        } else if ("RSET".equals(cmd)) {
                            rset(line);
                        } else if ("NOOP".equals(cmd)) {
                            noop(line);
                        } else {
                            writeln(ERR_BAD_COMMAND);
                        }
                    }
                } catch (DeltaSyncException e) {
                    logger.error("Got DeltaSyncException while processing command: " + line, e);
                    writeln(ERR_UNKNOWN_ERROR);
                } catch (IOException e) {
                    logger.error("Got IOException while processing command: " + line, e);
                    writeln(ERR_UNKNOWN_ERROR);
                }
                
                writer.flush();
                /*
                 * PrintWriter doesn't throw IOException. Instead one has to use 
                 * checkError() to check for IO errors.
                 */
                if (writer.checkError()) {
                    // The writer got an exception. Rethrow it.
                    throw new IOException();
                }
            }
        } catch (Throwable t) {
            logger.error("Exception caught in PopHandler", t);
        } finally {
            if (client != null) {
                synchronized (connectedUsers) {
                    connectedUsers.remove(username);
                }
            }
            try {
                socket.close();
            } catch (Throwable t) {}
            logger.info("Session ended");
            MDC.clear();
        }
    }
}
