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

import java.util.List;


/**
 */
public class SyncResponse {
    private final List<Collection> collections;
    
    public SyncResponse(List<Collection> collections) {
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
        private final Clazz clazz;
        private final int status;
        private final List<Command> commands;
        private final boolean moreAvailable;
        private final List<Response> responses;
        
        public Collection(String syncKey, Clazz clazz, int status,
                List<Command> commands, boolean moreAvailable, 
                List<Response> responses) {
            
            this.syncKey = syncKey;
            this.clazz = clazz;
            this.status = status;
            this.commands = commands;
            this.moreAvailable = moreAvailable;
            this.responses = responses;
        }

        public String getSyncKey() {
            return syncKey;
        }

        public Clazz getClazz() {
            return clazz;
        }

        public int getStatus() {
            return status;
        }

        public List<Command> getCommands() {
            return commands;
        }

        public boolean isMoreAvailable() {
            return moreAvailable;
        }
        
        public List<Response> getResponses() {
            return responses;
        }
        
        public static abstract class Response {
        }
        
        public static class EmailDeleteResponse extends Response {
            private final String id;
            private final int status;
            
            public EmailDeleteResponse(String id, int status) {
                this.id = id;
                this.status = status;
            }
            
            public String getId() {
                return id;
            }
            
            public int getStatus() {
                return status;
            }
        }
    }

}
