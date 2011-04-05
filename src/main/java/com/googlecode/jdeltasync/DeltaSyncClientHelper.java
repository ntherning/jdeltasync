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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.googlecode.jdeltasync.message.Clazz;
import com.googlecode.jdeltasync.message.Command;
import com.googlecode.jdeltasync.message.EmailAddCommand;
import com.googlecode.jdeltasync.message.EmailDeleteCommand;
import com.googlecode.jdeltasync.message.FolderAddCommand;
import com.googlecode.jdeltasync.message.FolderDeleteCommand;
import com.googlecode.jdeltasync.message.SyncRequest;
import com.googlecode.jdeltasync.message.SyncResponse;
import com.googlecode.jdeltasync.message.SyncResponse.Collection.EmailDeleteResponse;

/**
 *
 */
public class DeltaSyncClientHelper {
    private static final Map<String, String> STANDARD_FOLDERS_MAPPINGS;
    
    /**
     * The default number of messages to request at a time in 
     * {@link #getMessages(Folder)}. Max value seems to be 2000. If a higher 
     * value is used the server never returns more than 2000 in each Sync 
     * response.
     * <p>
     * Each Add in the response is about 1 kB so 256 should mean each 
     * response is about 256 kB maximum.
     */
    public static final int DEFAULT_WINDOW_SIZE = 256;
    
    static {
        STANDARD_FOLDERS_MAPPINGS = new HashMap<String, String>();
        STANDARD_FOLDERS_MAPPINGS.put("ACTIVE", "Inbox");
        STANDARD_FOLDERS_MAPPINGS.put("drAfT", "Drafts");
        STANDARD_FOLDERS_MAPPINGS.put("HM_BuLkMail_", "Junk");
        STANDARD_FOLDERS_MAPPINGS.put("sAVeD", "Sent");
        STANDARD_FOLDERS_MAPPINGS.put("trAsH", "Deleted");
        STANDARD_FOLDERS_MAPPINGS.put(".!!OIM", "Offline Instant Messages");
    }

    private final DeltaSyncClient client;
    private final Store store;
    private final String username;
    private final String password;
    private DeltaSyncSession session;

    private int windowSize = DEFAULT_WINDOW_SIZE;
    
    public DeltaSyncClientHelper(DeltaSyncClient client, String username, String password) {
        this(client, username, password, new InMemoryStore());
    }
    
    public DeltaSyncClientHelper(DeltaSyncClient client, String username, String password, Store store) {
        if (client == null) {
            throw new NullPointerException("client");
        }
        if (username == null) {
            throw new NullPointerException("username");
        }
        if (password == null) {
            throw new NullPointerException("password");
        }
        if (store == null) {
            throw new NullPointerException("store");
        }
        this.client = client;
        this.username = username;
        this.password = password;
        this.store = store;
    }
    
    public DeltaSyncSession getSession() {
        return session;
    }
    
    /**
     * Returns the current <code>windowSize</code> which specifies the maximum 
     * number of {@link Command} returned by a call to 
     * {@link DeltaSyncClient#sync(DeltaSyncSession, SyncRequest)} made by
     * {@link #getMessages(Folder)}. {@link #getMessages(Folder)} needs to do
     * <code>totalNumberOfMessagesInFolder / windowSize</code> requests to
     * get all messages in a folder.
     * 
     * @return the current <code>windowSize</code>.
     * @see #DEFAULT_WINDOW_SIZE
     */
    public int getWindowSize() {
        return windowSize;
    }
    
    /**
     * Sets the <code>windowSize</code>.
     * 
     * @param windowSize the new <code>windowSize</code>.
     * @throws IllegalArgumentException if the specified value is negative or 0.
     * @see #DEFAULT_WINDOW_SIZE
     * @see #getWindowSize()
     */
    public void setWindowSize(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize");
        }
        this.windowSize = windowSize;
    }
    
    /**
     * Returns the <code>DisplayName</code> of a folder mapped to a nicer name. 
     * The standard folders have funny display names (e.g. drAfT).
     */    
    private String getMappedDisplayName(String displayName) {
        if (STANDARD_FOLDERS_MAPPINGS.containsKey(displayName)) {
            return STANDARD_FOLDERS_MAPPINGS.get(displayName);
        }
        return displayName;
    }
    
    private void checkLoggedIn() {
        if (session == null) {
            throw new IllegalStateException("Not logged in");
        }
    }
    
    /**
     * Logs in. 
     * 
     * @throws AuthenticationException if authentication fails.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     * @throws IllegalStateException if already logged in.
     */
    public void login() throws AuthenticationException, DeltaSyncException, IOException {
        if (session != null) {
            throw new IllegalStateException("Already logged in");
        }
        this.session = client.login(username, password);
    }
    
    /**
     * Returns all {@link Folder}s.
     * 
     * @return all {@link Folder}s.
     * @throws SessionExpiredException if the session has expired and couldn't 
     *         be renewed.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     * @throws IllegalStateException if not logged in.
     */
    public Folder[] getFolders() throws DeltaSyncException, IOException {
        checkLoggedIn();
        try {
            return doGetFolders();
        } catch (SessionExpiredException e) {
            session = client.renew(session);
            return doGetFolders();
        } catch (InvalidSyncKeyException e) {
            session.getLogger().debug("Invalid folders sync key. All folders " 
                    + "will be retrieved anew.");
            store.resetFolders(username);
            return doGetFolders();
        }
    }

    private Folder[] doGetFolders() throws DeltaSyncException, IOException {
        
        while (true) {
            
            SyncRequest syncRequest = new SyncRequest(new SyncRequest.Collection(
                    store.getFoldersSyncKey(username), Clazz.Folder, true));
            SyncResponse response = client.sync(session, syncRequest);
            
            if (response.getCollections().isEmpty()) {
                throw new DeltaSyncException("No <Collection> in Sync response");
            }
            SyncResponse.Collection collection = response.getCollections().get(0);
            if (collection.getStatus() != 1) {
                throw new DeltaSyncException("Sync request failed with status " 
                        + collection.getStatus());
            }
        
            List<Folder> added = new ArrayList<Folder>();
            List<String> deleted = new ArrayList<String>();
            for (Command cmd : collection.getCommands()) {
                if (cmd instanceof FolderAddCommand) {
                    FolderAddCommand addCmd = (FolderAddCommand) cmd;
                    added.add(new Folder(addCmd.getId(), 
                            getMappedDisplayName(addCmd.getDisplayName())));
                } else if (cmd instanceof FolderDeleteCommand) {
                    FolderDeleteCommand delCmd = (FolderDeleteCommand) cmd;
                    deleted.add(delCmd.getId());
                }
            }
            
            store.updateFolders(username, collection.getSyncKey(), added, deleted);
            
            if (!collection.isMoreAvailable()) {
                break;
            }
        }

        Collection<Folder> folders = store.getFolders(username);
        return folders.toArray(new Folder[folders.size()]);
    }
    
    /**
     * Returns the Inbox {@link Folder}.
     * 
     * @return the Inbox {@link Folder} or <code>null</code> if not found.
     * @throws SessionExpiredException if the session has expired and couldn't 
     *         be renewed.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     * @throws IllegalStateException if not logged in.
     */
    public Folder getInbox() throws DeltaSyncException, IOException {
        checkLoggedIn();
        
        for (Folder folder : store.getFolders(username)) {
            if ("Inbox".equals(folder.getName())) {
                return folder;
            }
        }
        getFolders();
        for (Folder folder : store.getFolders(username)) {
            if ("Inbox".equals(folder.getName())) {
                return folder;
            }
        }
        return null;
    }
    

    /**
     * Returns all messages in the specified {@link Folder}.
     * 
     * @param folder the {@link Folder}.
     * @return all messages in the specified {@link Folder}.
     * @throws SessionExpiredException if the session has expired and couldn't 
     *         be renewed.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     * @throws IllegalStateException if not logged in.
     */
    public Message[] getMessages(Folder folder) throws DeltaSyncException, IOException {
        checkLoggedIn();
        try {
            return doGetMessages(folder);
        } catch (SessionExpiredException e) {
            session = client.renew(session);
            return doGetMessages(folder);
        } catch (InvalidSyncKeyException e) {
            session.getLogger().debug("Invalid messages sync key. All messages " 
                    + "will be retrieved anew.");
            store.resetMessages(username, folder);
            return doGetMessages(folder);
        }
    }

    private Message[] doGetMessages(Folder folder) throws DeltaSyncException, IOException {
        
        while (true) {
            
            SyncRequest syncRequest = new SyncRequest(new SyncRequest.Collection(
                    store.getMessagesSyncKey(username, folder), Clazz.Email, folder.getId(), true, windowSize));
            SyncResponse response = client.sync(session, syncRequest);
            
            if (response.getCollections().isEmpty()) {
                throw new DeltaSyncException("No <Collection> in Sync response");
            }
            SyncResponse.Collection collection = response.getCollections().get(0);
            if (collection.getStatus() != 1) {
                throw new DeltaSyncException("Sync request failed with status " 
                        + collection.getStatus());
            }
            
            List<Message> added = new ArrayList<Message>();
            List<String> deleted = new ArrayList<String>();
            for (Command cmd : collection.getCommands()) {
                if (cmd instanceof EmailAddCommand) {
                    EmailAddCommand addCmd = (EmailAddCommand) cmd;
                    added.add(new Message(addCmd.getId(), 
                            addCmd.getDateReceived(), addCmd.getSize(), addCmd.isRead(), 
                            addCmd.getSubject(), addCmd.getFrom()));
                } else if (cmd instanceof EmailDeleteCommand) {
                    EmailDeleteCommand delCmd = (EmailDeleteCommand) cmd;
                    deleted.add(delCmd.getId());
                }
            }
            
            store.updateMessages(username, folder, collection.getSyncKey(), added, deleted);
            
            if (!collection.isMoreAvailable()) {
                break;
            }
        }

        Collection<Message> messages = store.getMessages(username, folder);
        return messages.toArray(new Message[messages.size()]);
    }

    /**
     * Deletes the specified {@link Message}s from the specified {@link Folder}.
     * 
     * @param folder the {@link Folder}.
     * @param messages the {@link Message}s to be deleted.
     * @return the ids of the {@link Message}s actually deleted.
     * @throws SessionExpiredException if the session has expired and couldn't 
     *         be renewed.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     * @throws IllegalStateException if not logged in.
     */
    public String[] delete(Folder folder, Message[] messages) throws DeltaSyncException, IOException {
        String[] ids = new String[messages.length];
        for (int i = 0; i < messages.length; i++) {
            ids[i] = messages[i].getId();
        }
        return delete(folder, ids);
    }
    
    /**
     * Deletes the {@link Message}s with the specified ids from the specified 
     * {@link Folder}.
     * 
     * @param folder the {@link Folder}.
     * @param ids the ids to be deleted.
     * @return the ids of the {@link Message}s actually deleted.
     * @throws SessionExpiredException if the session has expired and couldn't 
     *         be renewed.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     * @throws IllegalStateException if not logged in.
     */
    public String[] delete(Folder folder, String[] ids) throws DeltaSyncException, IOException {
        checkLoggedIn();
        try {
            return doDelete(folder, ids);
        } catch (SessionExpiredException e) {
            session = client.renew(session);
            return doDelete(folder, ids);
        } catch (InvalidSyncKeyException e) {
            session.getLogger().debug("Invalid messages sync key. Delete will " 
                    + "be retried with sync key 0");
            store.resetMessages(username, folder);
            return doDelete(folder, ids);
        }
    }
    
    private String[] doDelete(Folder folder, String[] ids) throws DeltaSyncException, IOException {    
        if (ids.length == 0) {
            return new String[0];
        }
        
        List<Command> commands = new ArrayList<Command>();
        for (String id : ids) {
            commands.add(new EmailDeleteCommand(id));
        }
        
        SyncRequest syncRequest = new SyncRequest(new SyncRequest.Collection(
                store.getMessagesSyncKey(username, folder), Clazz.Email, folder.getId(), commands));
        SyncResponse response = client.sync(session, syncRequest);

        if (response.getCollections().isEmpty()) {
            throw new DeltaSyncException("No <Collection> in Sync response");
        }
        SyncResponse.Collection collection = response.getCollections().get(0);
        if (collection.getStatus() != 1) {
            throw new DeltaSyncException("Sync request failed with status " 
                    + collection.getStatus());
        }
    
        if (collection.getCommands() != null && !collection.getCommands().isEmpty()) {
            // We don't send <GetChanges> so we shouldn't get any Commands in the response
            store.resetMessages(username, folder);
            throw new DeltaSyncException("Delete should not return any Commands");
        }
        
        List<String> deleted = new ArrayList<String>();
        for (SyncResponse.Collection.Response rsp : collection.getResponses()) {
            if (rsp instanceof SyncResponse.Collection.EmailDeleteResponse) {
                SyncResponse.Collection.EmailDeleteResponse delRsp = 
                    (EmailDeleteResponse) rsp;
                deleted.add(delRsp.getId());
                // status == 4403 means no such message found
                if (delRsp.getStatus() == 1) {
                    deleted.add(delRsp.getId());
                }
            }
        }

        store.updateMessages(username, folder, collection.getSyncKey(), 
                new ArrayList<Message>(), deleted);
        
        return deleted.toArray(new String[deleted.size()]);
    }
    
    /**
     * Downloads the content of the message with the specified id and writes it 
     * to the specified {@link OutputStream}.
     * 
     * @param session the session.
     * @param message the {@link Message} to download the content for.
     * @param out the stream to write the message content to.
     * @throws SessionExpiredException if the session has expired and couldn't 
     *         be renewed.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     * @throws IllegalStateException if not logged in.
     */
    public void downloadMessageContent(Message message, OutputStream out) 
            throws DeltaSyncException, IOException {
        
        checkLoggedIn();
        try {
            client.downloadMessageContent(session, message.getId(), out);
        } catch (SessionExpiredException e) {
            session = client.renew(session);
            client.downloadMessageContent(session, message.getId(), out);
        }
    }
    
    public interface Store {
        String getFoldersSyncKey(String username);
        String getMessagesSyncKey(String username, Folder folder);
        void updateFolders(String username, String syncKey, Collection<Folder> added, Collection<String> deleted);
        void resetFolders(String username);
        void updateMessages(String username, Folder folder, String syncKey, Collection<Message> added, Collection<String> deleted);
        void resetMessages(String username, Folder folder);
        Collection<Folder> getFolders(String username);
        Collection<Message> getMessages(String username, Folder folder);
    }
    
    public static abstract class AbstractStore implements Store {
        
        public static class State implements Serializable {
            public String foldersSyncKey = "0";
            public Map<String, String> messagesSyncKeys = new HashMap<String, String>();
            public Map<String, Folder> folders = new HashMap<String, Folder>();
            public Map<String, Map<String, Message>> messages = new HashMap<String, Map<String,Message>>();
        }

        protected abstract State getState(String username);
        
        protected void stateChanged(String username, State state) {
        }

        private Map<String, Message> getMessagesMap(State state, Folder folder) {
            Map<String, Message> map = state.messages.get(folder.getId());
            if (map == null) {
                map = new HashMap<String, Message>();
                state.messages.put(folder.getId(), map);
            }
            return map;
        }
        
        public String getFoldersSyncKey(String username) {
            return getState(username).foldersSyncKey;
        }
        
        public String getMessagesSyncKey(String username, Folder folder) {
            String syncKey = getState(username).messagesSyncKeys.get(folder.getId());
            if (syncKey == null) {
                syncKey = "0";
                getState(username).messagesSyncKeys.put(folder.getId(), syncKey);
            }
            return syncKey;
        }
        
        public void updateFolders(String username, String syncKey,
                Collection<Folder> added, Collection<String> deleted) {

            State state = getState(username);
            for (Folder folder : added) {
                state.folders.put(folder.getId(), folder);
            }
            for (String id : deleted) {
                state.folders.remove(id);
            }
            stateChanged(username, state);
        }
        
        public void resetFolders(String username) {
            State state = getState(username);
            state.foldersSyncKey = "0";
            state.folders.clear();
            stateChanged(username, state);
        }
        
        public void updateMessages(String username, Folder folder,
                String syncKey, Collection<Message> added,
                Collection<String> deleted) {

            State state = getState(username);
            state.messagesSyncKeys.put(folder.getId(), syncKey);
            Map<String, Message> map = getMessagesMap(state, folder);
            for (Message message : added) {
                map.put(message.getId(), message);
            }
            for (String id : deleted) {
                map.remove(id);
            }
            stateChanged(username, state);
        }
        
        public void resetMessages(String username, Folder folder) {
            State state = getState(username);
            state.messagesSyncKeys.put(folder.getId(), "0");
            getMessagesMap(state, folder).clear();
            stateChanged(username, state);
        }
        
        public Collection<Folder> getFolders(String username) {
            return new ArrayList<Folder>(getState(username).folders.values());
        }
        
        public Collection<Message> getMessages(String username, Folder folder) {
            return new ArrayList<Message>(getMessagesMap(getState(username), folder).values());
        }
    }
    
    public static class InMemoryStore extends AbstractStore {
        private Map<String, State> states = new HashMap<String, State>();
        
        protected State getState(String username) {
            State state = states.get(username);
            if (state == null) {
                state = new State();
                states.put(username, state);
            }
            return state;
        }
    }
}
