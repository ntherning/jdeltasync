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

import java.util.Date;

/**
 * Represents a message on the server.
 */
public class Message {
    private final String id;
    private final Date dateReceived;
    private final long size;
    private final boolean read;
    private final String subject;
    private final String from;
    
    /**
     * Creates a new {@link Message}.
     * 
     * @param id the id.
     * @param dateReceived the {@link Date} and time when the message was 
     *        received.
     * @param size the size of the messages.
     * @param read <code>true</code> if the messages has been read, 
     *        <code>false</code> otherwise.
     * @param subject the message subject.
     * @param from the name and e-mail address of the sender.
     */
    public Message(String id, Date dateReceived, long size, boolean read,
            String subject, String from) {
        
        this.id = id;
        this.dateReceived = dateReceived;
        this.size = size;
        this.read = read;
        this.subject = subject;
        this.from = from;
    }

    /**
     * Returns the id of this {@link Message}.
     * 
     * @return the id.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the {@link Date} and time when this message was received.
     * 
     * @return the {@link Date} and time.
     */
    public Date getDateReceived() {
        return dateReceived;
    }

    /**
     * Returns the size of this {@link Message} in bytes.
     * 
     * @return the size.
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns whether this {@link Message} has been read.
     * 
     * @return <code>true</code> if the messages has been read, 
     *         <code>false</code> otherwise.
     */
    public boolean isRead() {
        return read;
    }

    /**
     * Returns the subject of this {@link Message}.
     * 
     * @return the subject.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Returns the name and e-mail address of the person who sent this 
     * {@link Message}.
     * 
     * @return the sender name and e-mail.
     */
    public String getFrom() {
        return from;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString() + "(");
        sb.append("id").append("=").append(id).append(",");
        sb.append("dateReceived").append("=").append(dateReceived).append(",");
        sb.append("size").append("=").append(size).append(",");
        sb.append("read").append("=").append(read).append(",");
        sb.append("subject").append("=").append(subject).append(",");
        sb.append("from").append("=").append(from);
        sb.append(")");
        return sb.toString();
    }
}
