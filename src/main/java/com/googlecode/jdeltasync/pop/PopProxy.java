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

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import com.googlecode.jdeltasync.DeltaSyncClient;
import com.googlecode.jdeltasync.DiskStore;
import com.googlecode.jdeltasync.Store;

/**
 * POP3 proxy server which can be used to access Windows Live Hotmail accounts
 * using POP3.
 */
public class PopProxy {
    private static final Logger log = LoggerFactory.getLogger(PopProxy.class);
    
    private final InetSocketAddress bindAddress;
    private final DeltaSyncClient deltaSyncClient;
    private final Store store;
    private final ExecutorService executor;
    
    private ServerThread serverThread;
    
    public PopProxy(InetSocketAddress bindAddress, DeltaSyncClient deltaSyncClient, 
            Store store, ExecutorService executor) {
        
        this.bindAddress = bindAddress;
        this.deltaSyncClient = deltaSyncClient;
        this.store = store;
        this.executor = executor;
    }

    public synchronized void start() throws IOException {
        if (isStarted()) {
            throw new IllegalStateException("Already started");
        }
        
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setSoTimeout(1000);
        serverSocket.bind(bindAddress);
        
        serverThread = new ServerThread(serverSocket);
        serverThread.start();
    }
    
    public synchronized void stop() {
        if (!isStarted()) {
            return;
        }
        
        serverThread.interrupt();
        try {
            serverThread.join();
        } catch (InterruptedException e) {
        }
    }
    
    public boolean isStarted() {
        return serverThread != null;
    }
    
    private class ServerThread extends Thread {
        private final ServerSocket serverSocket;
        
        public ServerThread(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            try {
                log.info("Listening on " + bindAddress);
                
                while (!isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        PopHandler handler = new PopHandler(socket, deltaSyncClient, store);
                        executor.execute(handler);
                    } catch (SocketTimeoutException e) {
                    }
                }
                
            } catch (Throwable t) {
                log.error("Exception caught", t);
            }
        }
    }
    
    private static void printUsageAndExit(String error) {
        if (error != null) {
            System.err.println(error);
        }
        System.err.printf("Usage: %s [options]\n", PopProxy.class.getName());
        System.err.printf("    -logback <logback-config-file>\n");
        System.err.printf("    -interface <ip-or-hostname>\n");
        System.err.printf("    -port <port>\n");
        System.err.printf("    -datadir <path>\n");
        System.exit(error == null ? 0 : 1);
    }
    
    public static void main(String[] args) throws Exception {
        ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(
                SchemeRegistryFactory.createDefault());
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(100);
                
        String bindTo = "localhost";
        int port = 10110;
        String logbackFile = null;
        File datadir = new File(System.getProperty("java.io.tmpdir"), PopProxy.class.getName());
        
        try {
            for (int i = 0; i < args.length; i++) {
                if ("-interface".equals(args[i])) {
                    bindTo = args[++i];
                } else if ("-port".equals(args[i])) {
                    port = Integer.parseInt(args[++i]);
                } else if ("-logback".equals(args[i])) {
                    logbackFile = args[++i];
                } else if ("-datadir".equals(args[i])) {
                    datadir = new File(args[++i]);
                } else if ("-help".equals(args[i])) {
                    printUsageAndExit(null);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            printUsageAndExit("Missing argument");
        } catch (NumberFormatException e) {
            printUsageAndExit("Failed to parse number");
        }
        
        if (logbackFile != null) {
            log.info("Configuring logback from file '" + logbackFile + "'");
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            try {
               JoranConfigurator configurator = new JoranConfigurator();
               configurator.setContext(lc);
               lc.reset();
               configurator.doConfigure(logbackFile);
            } catch (JoranException je) {
               StatusPrinter.print(lc);
            } 
        }
        
        if ((datadir.exists() && !datadir.isDirectory()) || (!datadir.exists() && !datadir.mkdirs())) {
            log.error("Failed to create datadir {}", datadir.getCanonicalPath());
            System.exit(1);
        }
        
        log.info("Using datadir {}", datadir.getCanonicalPath());
        
        PopProxy proxy = new PopProxy(new InetSocketAddress(bindTo, port), 
                new DeltaSyncClient(connManager), new DiskStore(datadir),
                Executors.newCachedThreadPool());
        
        try {
            proxy.start();
        } catch (BindException e) {
            System.err.println("Error: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }
}
