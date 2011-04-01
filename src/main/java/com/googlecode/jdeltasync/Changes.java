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

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Contains information on added and deleted messages since the last time
 * {@link DeltaSyncClient#getChanges(DeltaSyncSession, Folder)} was called.
 */
public class Changes {
    private final List<Message> added;
    private final Set<String> deleted;
    private final boolean moreAvailable;
    
    /**
     * Creates a new {@link Changes} instance.
     * 
     * @param added the {@link Message}s that were added.
     * @param deleted the ids of the messages that were deleted.
     * @param moreAvailable <code>true</code> if more changes will be returned
     *        by an immediate call to 
     *        {@link DeltaSyncClient#getChanges(DeltaSyncSession, Folder)}.
     */
    public Changes(List<Message> added, Set<String> deleted, boolean moreAvailable) {
        this.added = added;
        this.deleted = deleted;
        this.moreAvailable = moreAvailable;
    }
    
    /**
     * Returns the {@link Message}s that were added.
     * 
     * @return the {@link Message}s.
     */
    public List<Message> getAdded() {
        return Collections.unmodifiableList(added);
    }
    
    /**
     * Returns the ids of the messages that were deleted.
     * 
     * @return the ids.
     */
    public Set<String> getDeleted() {
        return Collections.unmodifiableSet(deleted);
    }
    
    /**
     * <code>true</code> if more changes will be returned by an immediate call 
     * to {@link DeltaSyncClient#getChanges(DeltaSyncSession, Folder)}.
     * 
     * @return <code>true</code> or <code>false</code>.
     */
    public boolean isMoreAvailable() {
        return moreAvailable;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString() + "(");
        sb.append("added").append("=").append(added).append(",");
        sb.append("deleted").append("=").append(deleted).append(",");
        sb.append("moreAvailable").append("=").append(moreAvailable);
        sb.append(")");
        return sb.toString();
    }
}
