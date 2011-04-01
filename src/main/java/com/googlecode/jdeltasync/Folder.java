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

/**
 * Represents a folder on the server.
 */
public class Folder {
    private final String id;
    private final String name;
    
    /**
     * Creates a new {@link Folder}.
     * 
     * @param id the id of the {@link Folder}.
     * @param name the name.
     */
    public Folder(String id, String name) {
        this.id = id;
        this.name = name;
    }
    
    /**
     * Returns the id of this {@link Folder}.
     * 
     * @return the id.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the name of this {@link Folder}.
     * 
     * @return the name.
     */
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString() + "(");
        sb.append("id").append("=").append(id).append(",");
        sb.append("name").append("=").append(name);
        sb.append(")");
        return sb.toString();
    }
}
