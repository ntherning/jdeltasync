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
package com.googlecode.jdeltasync.message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 *
 */
public class SyncRequest {
    private final List<Collection> collections;
    
    public SyncRequest(Collection ... collections) {
        this(new ArrayList<Collection>(Arrays.asList(collections)));
    }
    
    public SyncRequest(List<Collection> collections) {
        this.collections = collections;
    }

    public List<Collection> getCollections() {
        return collections;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString() + "(");
        sb.append("collections").append("=").append(collections);
        sb.append(")");
        return sb.toString();
    }
    
    public static class Collection {
        
        private final String syncKey;
        private final String collectionId;
        private final Clazz clazz;
        private final List<Command> commands;
        private final boolean getChanges;
        private final int windowSize;
        
        public Collection(String syncKey, Clazz clazz, boolean getChanges) {
            this.syncKey = syncKey;
            this.clazz = clazz;
            this.collectionId = null;
            this.commands = Collections.emptyList();
            this.getChanges = getChanges;
            this.windowSize = -1;
        }
        
        public Collection(String syncKey, Clazz clazz, String collectionId, 
                boolean getChanges, int windowSize) {
            
            this.syncKey = syncKey;
            this.clazz = clazz;
            this.collectionId = collectionId;
            this.commands = Collections.emptyList();
            this.getChanges = getChanges;
            this.windowSize = windowSize;
        }

        public Collection(String syncKey, Clazz clazz, String collectionId, 
                List<Command> commands) {
            
            this.syncKey = syncKey;
            this.clazz = clazz;
            this.collectionId = collectionId;
            this.commands = commands;
            this.getChanges = false;
            this.windowSize = -1;
        }
        
        public Collection(String syncKey, Clazz clazz, String collectionId, 
                List<Command> commands, boolean getChanges, int windowSize) {
            
            this.syncKey = syncKey;
            this.clazz = clazz;
            this.collectionId = collectionId;
            this.commands = commands;
            this.getChanges = getChanges;
            this.windowSize = windowSize;
        }
        
        public String getSyncKey() {
            return syncKey;
        }

        public Clazz getClazz() {
            return clazz;
        }

        public String getCollectionId() {
            return collectionId;
        }

        public List<Command> getCommands() {
            return commands;
        }

        public boolean isGetChanges() {
            return getChanges;
        }
        
        public int getWindowSize() {
            return windowSize;
        }
    }

}
