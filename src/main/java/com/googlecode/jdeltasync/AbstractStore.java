/*
 * Copyright (c) 2012, the JDeltaSync project. All Rights Reserved.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract {@link Store} implementation.
 */
public abstract class AbstractStore implements Store {
    
    @SuppressWarnings("serial")
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