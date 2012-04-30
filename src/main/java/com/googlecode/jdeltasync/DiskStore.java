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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Store} implementation which saves data in the file system.
 *
 */
public class DiskStore extends AbstractStore {
    private static final Logger log = LoggerFactory.getLogger(DiskStore.class);
    
    private static final int MAX_ENTRIES = 32;
    
    @SuppressWarnings("serial")
    private final Map<String, State> states = new LinkedHashMap<String, State>(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String,State> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private final File datadir;

    public DiskStore(File datadir) throws IOException {
        if ((datadir.exists() && !datadir.isDirectory()) || (!datadir.exists() && !datadir.mkdirs())) {
            throw new IOException("Failed to create datadir " + datadir.getCanonicalPath());
        }
        this.datadir = datadir;
    }

    private File getFile(String username) throws UnsupportedEncodingException {
        return new File(datadir, URLEncoder.encode(username, "UTF-8") + ".bin");
    }
    
    @Override
    protected State getState(String username) {
        State state = states.get(username);
        if (state == null) {
            ObjectInputStream in = null;
            try {
                File f = getFile(username);
                if (f.exists()) {
                    log.debug("Reading State for user {} from disk", username);
                    in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)));
                    state = (State) in.readObject();
                }
            } catch (IOException e) {
                log.error("Failed to read State object from file for user " + username, e);
            } catch (ClassNotFoundException e) {
                log.error("Failed to read State object from file for user " + username, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {}
                }
            }
            if (state == null) {
                state = new State();
            }
            states.put(username, state);
        }
        return state;
    }
    
    @Override
    protected void stateChanged(String username, State state) {
        ObjectOutputStream out = null;
        try {
            File f = getFile(username);
            File tmp = new File(f.getParent(), f.getName() + ".tmp");
            log.debug("Writing State for user {} to disk", username);
            out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
            out.writeObject(state);
            out.close();
            if (f.exists()) {
                f.delete();
            }
            tmp.renameTo(f);
        } catch (IOException e) {
            log.error("Failed to write State object to file for user " + username, e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e1) {}
            }
        }
    }
    
}