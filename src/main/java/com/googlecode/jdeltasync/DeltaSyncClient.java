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

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.descriptor.BodyDescriptor;
import org.apache.james.mime4j.message.SimpleContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.googlecode.jdeltasync.hu01.HU01DecompressorOutputStream;
import com.googlecode.jdeltasync.hu01.HU01Exception;
import com.googlecode.jdeltasync.message.Clazz;
import com.googlecode.jdeltasync.message.Command;
import com.googlecode.jdeltasync.message.EmailAddCommand;
import com.googlecode.jdeltasync.message.EmailDeleteCommand;
import com.googlecode.jdeltasync.message.FolderAddCommand;
import com.googlecode.jdeltasync.message.FolderDeleteCommand;
import com.googlecode.jdeltasync.message.SyncRequest;
import com.googlecode.jdeltasync.message.SyncResponse;

/**
 * Main class used to communicate with the Windows Live Hotmail service using
 * Microsoft's proprietary DeltaSync protocol. {@link #login(String, String)} 
 * has to be called to obtain a {@link DeltaSyncSession} which can then be used 
 * to query for the folders and messages on the server and to delete messages.
 */
public class DeltaSyncClient {
    
    private static final String LOGIN_BASE_URI = "https://login.live.com/RST2.srf";
    private static final String LOGIN_USER_AGENT = 
          "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; IDCRL 5.000.819.1; " 
        + "IDCRL-cfg 6.0.11409.0; App wlmail.exe, 14.0.8117.416, " 
        + "{47A6D4CF-5EB0-4B0E-9138-1B3F2DD40981})";
    private static final String DS_USER_AGENT = "WindowsLiveMail/1.0";
    private static final String DS_BASE_URI = "http://mail.services.live.com";
    private static final byte[] LINE_SEPARATOR;
    
    static {
        try {
            LINE_SEPARATOR = System.getProperty("line.separator").getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    private final HttpClient httpClient;
    
    /**
     * Creates a new {@link DeltaSyncClient} using a 
     * {@link ThreadSafeClientConnManager} with the default settings.
     */
    public DeltaSyncClient() {
        this(new ThreadSafeClientConnManager(SchemeRegistryFactory.createDefault()));
    }
    
    /**
     * Creates a new {@link DeltaSyncClient} using the specified 
     * {@link ClientConnectionManager}. 
     * 
     * @param connectionManager the {@link ClientConnectionManager}.
     */
    public DeltaSyncClient(ClientConnectionManager connectionManager) {
        this.httpClient = new DefaultHttpClient(connectionManager);
        setConnectionTimeout(5 * 1000);
        setSoTimeout(60 * 1000);
    }
    
    /**
     * Returns the {@link ClientConnectionManager} in use.
     * 
     * @return the {@link ClientConnectionManager}.
     */
    public ClientConnectionManager getConnectionManager() {
        return httpClient.getConnectionManager();
    }
    
    /**
     * Sets the connection timeout of the {@link HttpClient} instance. See
     * {@link CoreConnectionPNames#CONNECTION_TIMEOUT}.
     * 
     * @param timeout the timeout.
     */
    public void setConnectionTimeout(int timeout) {
        HttpConnectionParams.setConnectionTimeout(this.httpClient.getParams(), timeout);
    }
    
    /**
     * Sets the socket timeout (SO_TIMEOUT) of the {@link HttpClient} instance. See
     * {@link CoreConnectionPNames#SO_TIMEOUT}.
     * 
     * @param timeout the timeout.
     */
    public void setSoTimeout(int timeout) {
        HttpConnectionParams.setSoTimeout(this.httpClient.getParams(), timeout);
    }
    
    /**
     * Logs in using the specified username and password. Returns a 
     * {@link DeltaSyncSession} object on successful authentication. 
     * 
     * @param username the username.
     * @param password the password.
     * @return the session.
     * @throws AuthenticationException if authentication fails.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     */
    public DeltaSyncSession login(String username, String password) 
            throws AuthenticationException, DeltaSyncException, IOException {
        
        if (username == null) {
            throw new NullPointerException("username");
        }
        if (password == null) {
            throw new NullPointerException("password");
        }
        
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        Date created = new Date();
        Date expires = new Date(created.getTime() + 5 * 60 * 1000);
        
        Document request = XmlUtil.parse(getClass().getResourceAsStream("login-request.xml"));
        Element elSecurity = XmlUtil.getElement(request, "/s:Envelope/s:Header/wsse:Security");
        XmlUtil.setTextContent(elSecurity, "wsse:UsernameToken/wsse:Username", username);
        XmlUtil.setTextContent(elSecurity, "wsse:UsernameToken/wsse:Password", password);
        XmlUtil.setTextContent(elSecurity, "wsu:Timestamp/wsu:Created", format.format(created));
        XmlUtil.setTextContent(elSecurity, "wsu:Timestamp/wsu:Expires", format.format(expires));
        
        DeltaSyncSession session = new DeltaSyncSession(username, password);
        
        if (session.getLogger().isDebugEnabled()) {
            session.getLogger().debug("Sending login request: {}", 
                    XmlUtil.toString(request, false)
                        .replaceAll(Pattern.quote(password), "******"));
        }
        
        Document response = post(session, LOGIN_BASE_URI, LOGIN_USER_AGENT, "application/soap+xml", 
                request, new UriCapturingResponseHandler<Document>() {
            
            public Document handle(URI uri, HttpResponse response) throws DeltaSyncException, IOException {
                return XmlUtil.parse(response.getEntity().getContent());
            }
        });

        if (session.getLogger().isDebugEnabled()) {
            session.getLogger().debug("Received login response: {}", XmlUtil.toString(response, false));
        }
        if (XmlUtil.hasElement(response, "/s:Envelope/s:Body/s:Fault")) {
            throw new AuthenticationException(XmlUtil.getTextContent(response, "/s:Envelope/s:Body/s:Fault/s:Reason/s:Text"));
        }
        
        session.ticket = XmlUtil.getTextContent(response, "/s:Envelope/s:Body/wst:RequestSecurityTokenResponseCollection/" 
                        + "wst:RequestSecurityTokenResponse/wst:RequestedSecurityToken/wsse:BinarySecurityToken");
        session.dsBaseUri = DS_BASE_URI;
        
        return session;
    }
    
    /**
     * Renews the specified session and returns a new session. The old session
     * object must be discarded. This should be called when a {@link SessionExpiredException}
     * has been thrown indicating that a session has expired.
     * 
     * @param session the old session.
     * @return the new session.
     * @throws AuthenticationException if authentication fails.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     */
    public DeltaSyncSession renew(DeltaSyncSession session) 
        throws AuthenticationException, DeltaSyncException, IOException {
        
        return login(session.getUsername(), session.getPassword());
    }
    
    /**
     * Downloads the HU01 compressed content of the message with the specified 
     * id and writes it to the specified {@link OutputStream}.
     * 
     * @param session the session.
     * @param messageId the id of the message to download.
     * @param out the stream to write the HU01 compressed message content to.
     * @throws SessionExpiredException if the session has expired.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     */
    public void downloadRawMessageContent(DeltaSyncSession session, String messageId, OutputStream out) 
            throws DeltaSyncException, IOException {
        
        downloadMessageContent(session, messageId, out, true);
    }
    
    /**
     * Downloads the content of the message with the specified id and writes it 
     * to the specified {@link OutputStream}.
     * 
     * @param session the session.
     * @param messageId the id of the message to download.
     * @param out the stream to write the message content to.
     * @throws SessionExpiredException if the session has expired.
     * @throws DeltaSyncException on errors returned by the server.
     * @throws IOException on communication errors.
     */
    public void downloadMessageContent(DeltaSyncSession session, String messageId, OutputStream out) 
            throws DeltaSyncException, IOException {
        
        downloadMessageContent(session, messageId, out, false);
    }
    
    private void downloadMessageContent(final DeltaSyncSession session, 
            final String messageId, final OutputStream output, final boolean raw) 
            throws DeltaSyncException, IOException {
        
        String request = 
              "<ItemOperations xmlns=\"ItemOperations:\" xmlns:A=\"HMMAIL:\">"
            +   "<Fetch>"
            +     "<Class>Email</Class>"
            +     "<A:ServerId>" + messageId + "</A:ServerId>"
            +     "<A:Compression>hm-compression</A:Compression>"
            +     "<A:ResponseContentType>mtom</A:ResponseContentType>"
            +   "</Fetch>"
            + "</ItemOperations>";
        
        Document response = itemOperations(session, request, new UriCapturingResponseHandler<Document>() {
            public Document handle(URI uri, HttpResponse response)
                    throws DeltaSyncException, IOException {

                session.dsBaseUri = uri.getScheme() + "://" + uri.getHost();
                
                Header contentType = response.getFirstHeader("Content-Type");
                if (contentType == null || !contentType.getValue().equals("application/xop+xml")) {
                    if (contentType != null && contentType.getValue().equals("text/xml")) {
                        // If we receive a text/xml response it means an error has occurred
                        return XmlUtil.parse(response.getEntity().getContent());
                    }
                    throw new DeltaSyncException("Unexpected Content-Type received: " + contentType);
                }
                
                final Object[] result = new Object[1];
                MimeStreamParser parser = new MimeStreamParser();
                parser.setContentHandler(new SimpleContentHandler() {
                    
                    @Override
                    public void headers(org.apache.james.mime4j.message.Header header) {
                    }
                    
                    @Override
                    public void bodyDecoded(BodyDescriptor bd, InputStream is)
                            throws IOException {
                        
                        if ("application/xop+xml".equals(bd.getMimeType())) {
                            try {
                                result[0] = XmlUtil.parse(is);
                            } catch (XmlException e) {
                                result[0] = e;
                            }
                        } else if ("application/octet-stream".equals(bd.getMimeType())) {
                            OutputStream out = output;
                            if (!raw) {
                                out = new HU01DecompressorOutputStream(output);
                            }
                            byte[] buffer = new byte[4096];
                            int n = 0;
                            while ((n = is.read(buffer)) != -1) {
                                out.write(buffer, 0, n);
                            }
                            out.flush();
                        }
                    }
                });
                
                try {
                    parser.parse(response.getEntity().getContent());
                } catch (MimeException e) {
                    throw new DeltaSyncException("Failed to parse multipart xop+xml response", e);
                } catch (IOException e) {
                    if (e.getCause() != null && (e.getCause() instanceof HU01Exception)) {
                        session.getLogger().error("HU01 decompression failed: ", e.getCause());
                        session.getLogger().error("Dumping HU01 stream as BASE64 for message {}", messageId);
                        session.getLogger().error("Please submit the BASE64 encoded message content");
                        session.getLogger().error("and the plain text message content to the JDeltaSync");
                        session.getLogger().error("issue tracker. The plain text message content can");
                        session.getLogger().error("be retrieved by clicking \"View message source\" in");
                        session.getLogger().error("the Hotmail web UI.");
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
                        Base64OutputStream base64Out = new Base64OutputStream(baos, true, 72, LINE_SEPARATOR);
                        try {
                            downloadRawMessageContent(session, messageId, base64Out);
                            base64Out.close();
                            session.getLogger().error(new String(baos.toByteArray(), "ASCII"));
                        } catch (Throwable t) {
                            session.getLogger().error("Failed to dump HU01 stream", t);
                        }
                        throw (HU01Exception) e.getCause();
                    }
                    throw e;
                }
                
                if (result[0] instanceof DeltaSyncException) {
                    throw (DeltaSyncException) result[0];
                }
                return (Document) result[0];
            }
        });
        
        if (session.getLogger().isDebugEnabled()) {
            session.getLogger().debug("Received ItemOperations response: {}", 
                    XmlUtil.toString(response, false));
        }
        
        checkStatus(response);
        // No general error in the response. Check for a specific <Fetch> error.
        Element elStatus = XmlUtil.getElement(response, 
                "/itemop:ItemOperations/itemop:Responses/itemop:Fetch/itemop:Status");
        if (elStatus == null) {
            throw new DeltaSyncException("No <Status> element found in <Fetch> response: " + XmlUtil.toString(response, true));            
        }
        int code = Integer.parseInt(elStatus.getTextContent().trim());
        if (code == 4403) {
            throw new NoSuchMessageException(messageId);
        } else if (code != 1) {
            throw new UnrecognizedErrorCodeException(code, 
                    "Unrecognized error code in response for <Fetch> request. Response was: "
                    + XmlUtil.toString(response, true));
        }
    }
    
    public SyncResponse sync(DeltaSyncSession session, SyncRequest syncRequest) 
            throws DeltaSyncException, IOException {
        
        StringBuilder request = new StringBuilder("<Sync xmlns=\"AirSync:\"><Collections>");
        for (SyncRequest.Collection collection : syncRequest.getCollections()) {
            request.append("<Collection>");
            request.append("<Class>").append(collection.getClazz()).append("</Class>");
            if (collection.getCollectionId() != null) {
                request.append("<CollectionId>").append(collection.getCollectionId()).append("</CollectionId>");                
            }
            request.append("<SyncKey>").append(collection.getSyncKey()).append("</SyncKey>");
            if (collection.isGetChanges()) {
                request.append("<GetChanges/>");
            }
            if (collection.getWindowSize() > 0) {
                request.append("<WindowSize>").append(collection.getWindowSize()).append("</WindowSize>");
            }
            if (!collection.getCommands().isEmpty()) {
                request.append("<Commands>");
                for (Command command : collection.getCommands()) {
                    request.append("<Delete>").append("<ServerId>");
                    switch (collection.getClazz()) {
                    case Email:
                        request.append(((EmailDeleteCommand) command).getId());
                        break;
                    case Folder:
                        request.append(((FolderDeleteCommand) command).getId());
                        break;
                    }
                    request.append("</ServerId>").append("</Delete>");
                }
                request.append("</Commands>");
            }
            request.append("</Collection>");
        }
        request.append("</Collections></Sync>");
        
        Document response = sync(session, request.toString());

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        List<SyncResponse.Collection> collections = new ArrayList<SyncResponse.Collection>();
        for (Element elCollection : XmlUtil.getElements(response, "//airsync:Collection")) {
            String syncKey = XmlUtil.getTextContent(elCollection, "airsync:SyncKey");
            Clazz clazz = Clazz.valueOf(XmlUtil.getTextContent(elCollection, "airsync:Class"));
            int status = Integer.parseInt(XmlUtil.getTextContent(elCollection, "airsync:Status"));
            boolean moreAvailable = XmlUtil.hasElement(elCollection, "airsync:MoreAvailable");
            List<Command> commands = new ArrayList<Command>();
            
            switch (clazz) {
            case Email:
                for (Element elAdd : XmlUtil.getElements(elCollection, "airsync:Commands/airsync:Add")) {
                    try {
                        String id = XmlUtil.getTextContent(elAdd, "airsync:ServerId");
                        String folderId = XmlUtil.getTextContent(elAdd, "hmmail:FolderId");
                        Element elAppData = XmlUtil.getElement(elAdd, "airsync:ApplicationData");
                        long size = Long.parseLong(XmlUtil.getTextContent(elAppData, "hmmail:Size"));
                        boolean read = Integer.parseInt(XmlUtil.getTextContent(elAppData, "email:Read")) == 1;
                        Date dateReceived = format.parse(XmlUtil.getTextContent(elAppData, "email:DateReceived"));
                        String subject = XmlUtil.getTextContent(elAppData, "email:Subject");
                        String from = XmlUtil.getTextContent(elAppData, "email:From");
                        commands.add(new EmailAddCommand(id, folderId, dateReceived, size, read, subject, from));
                    } catch (ParseException e) {
                        throw new DeltaSyncException(e);
                    }
                }
                // TODO: AirSync:Change
                for (Element elDelete : XmlUtil.getElements(elCollection, "airsync:Commands/airsync:Delete")) {
                    String id = XmlUtil.getTextContent(elDelete, "airsync:ServerId");
                    commands.add(new EmailDeleteCommand(id));
                }
                break;
            case Folder:
                for (Element elAdd : XmlUtil.getElements(elCollection, "airsync:Commands/airsync:Add")) {
                    String id = XmlUtil.getTextContent(elAdd, "airsync:ServerId");
                    String displayName = XmlUtil.getTextContent(elAdd, "airsync:ApplicationData/hmfolder:DisplayName");
                    commands.add(new FolderAddCommand(id, displayName));

                }
                for (Element elDelete : XmlUtil.getElements(elCollection, "airsync:Commands/airsync:Delete")) {
                    String id = XmlUtil.getTextContent(elDelete, "airsync:ServerId");
                    commands.add(new FolderDeleteCommand(id));
                }
            }

            List<SyncResponse.Collection.Response> responses = new ArrayList<SyncResponse.Collection.Response>();
            // TODO: Support for other types of responses
            for (Element elDelete : XmlUtil.getElements(elCollection, "airsync:Responses/airsync:Delete")) {
                String id = XmlUtil.getTextContent(elDelete, "airsync:ServerId");
                int deleteStatus = Integer.parseInt(XmlUtil.getTextContent(elDelete, "airsync:Status"));
                responses.add(new SyncResponse.Collection.EmailDeleteResponse(id, deleteStatus));
            }            
            
            collections.add(new SyncResponse.Collection(syncKey, clazz, status, commands, moreAvailable, responses));
        }
        
        SyncResponse syncResponse = new SyncResponse(collections);
        
        session.getLogger().debug("Got SyncResponse: {}", syncResponse);
        
        return syncResponse;
    }
    
    private Document sync(final DeltaSyncSession session, String request) throws DeltaSyncException, IOException {
        return call("Sync", session, request, new UriCapturingResponseHandler<Document>() {
            
            public Document handle(URI uri, HttpResponse response)
                    throws DeltaSyncException, IOException {

                session.dsBaseUri = uri.getScheme() + "://" + uri.getHost();
                Document doc = XmlUtil.parse(response.getEntity().getContent());
                checkStatus(doc);
                if (session.getLogger().isDebugEnabled()) {
                    session.getLogger().debug("Received Sync response: {}", 
                            XmlUtil.toString(doc, false));
                }
                return doc;
            }
            
        });
    }
    
    private <T> T itemOperations(final DeltaSyncSession session, String request, 
            UriCapturingResponseHandler<T> handler) throws DeltaSyncException, IOException {
        
        return call("ItemOperations", session, request, handler);
    }
    
    private <T> T call(final String cmd, final DeltaSyncSession session, String request, 
            UriCapturingResponseHandler<T> handler) throws DeltaSyncException, IOException {
        
        if (session.getLogger().isDebugEnabled()) {
            try {
                Document document = XmlUtil.parse(new ByteArrayInputStream(request.getBytes()));
                session.getLogger().debug("Sending {} request: {}", cmd, XmlUtil.toString(document,false));
            } catch (XmlException e) {
                session.getLogger().debug("Sending {} request: {}", cmd, request);
            }
        }

        return post(session, session.dsBaseUri + "/DeltaSync_v2.0.0/" + cmd + ".aspx?" 
                + session.getTicket(), DS_USER_AGENT, "text/xml", request, handler);
    }
    
    private void checkStatus(Document doc) throws DeltaSyncException {
        Element status = XmlUtil.getElement(doc.getDocumentElement(), "*:Status");
        if (status == null) {
            // All responses should have a <Status> element
            throw new DeltaSyncException("No <Status> element found in response: " + XmlUtil.toString(doc, true));            
        }
        int code = Integer.parseInt(status.getTextContent().trim());
        if (code != 1) {
            String message = XmlUtil.getTextContent(doc.getDocumentElement(), "*:Fault/*:Faultstring");
            if (message == null) {
                message = "No Faultstring provided in response. Response was: " + XmlUtil.toString(doc, true);
            }
            switch (code) {
            case 3204:
                // Authentication failure. We assume this means that the session has expired.
                throw new SessionExpiredException(message);
            case 4102:
                // The server failed to understand the request due to a syntax error or an error in the parameters.
                throw new BadRequestException(message);
            case 4104:
                // Invalid sync key.
                throw new InvalidSyncKeyException(message);
            case 4402:
                throw new NoSuchFolderException(message);
            default:
                throw new UnrecognizedErrorCodeException(code, message);
            }
        }
    }
    
    private <T> T post(DeltaSyncSession session, String uri, String userAgent, String contentType, Document doc, 
            UriCapturingResponseHandler<T> handler) throws DeltaSyncException, IOException {
        
        return post(session, uri, userAgent, contentType, XmlUtil.toByteArray(doc), handler);
    }
    
    private <T> T post(DeltaSyncSession session, String uri, String userAgent,String contentType, String s, 
            UriCapturingResponseHandler<T> handler) throws DeltaSyncException, IOException {
        
        return post(session, uri, userAgent, contentType, s.getBytes("UTF-8"), handler);
    }
    
    private <T> T post(final DeltaSyncSession session, String uri, final String userAgent, final String contentType, 
            final byte[] data, final UriCapturingResponseHandler<T> handler) throws DeltaSyncException, IOException {
        
        final HttpPost post = createHttpPost(uri, userAgent, contentType, data);
        final HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_STORE, session.cookies);
        
        try {
        
            return httpClient.execute(post, new ResponseHandler<T>() {
                public T handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    try {
                        if (isRedirect(response)) {
                            URI redirectUri = getRedirectLocationURI(session, post, response, context);
                            return post(session, redirectUri.toString(), userAgent, contentType, data, handler);
                        }
                        
                        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                            throw new HttpException(response.getStatusLine().getStatusCode(), 
                                    response.getStatusLine().getReasonPhrase());
                        }
                        
                        HttpUriRequest request = (HttpUriRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
                        HttpHost host = (HttpHost)  context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                        URI uri = request.getURI();
                        if (!request.getURI().isAbsolute()) {
                            try {
                                uri = URIUtils.rewriteURI(uri, host);
                            } catch (URISyntaxException e) {
                                throw new DeltaSyncException(e);
                            }
                        }
                        return handler.handle(uri, response);
                    } catch (DeltaSyncException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, context);
            
        } catch (RuntimeException e) {
            Throwable t = e.getCause();
            while (t != null) {
                if (t instanceof DeltaSyncException) {
                    throw (DeltaSyncException) t;
                }
                t = t.getCause();
            }
            throw e;
        }
    }

    private HttpPost createHttpPost(String uri, String userAgent, String contentType, byte[] data) {
        ByteArrayEntity entity = new ByteArrayEntity(data);
        entity.setContentType(contentType);
        HttpPost post = new HttpPost(uri);
        post.setHeader("User-Agent", userAgent);
        post.setEntity(entity);
        return post;
    }

    /**
     * Modified version of {@link DefaultRedirectStrategy#isRedirected(HttpRequest, HttpResponse, HttpContext)}
     * which also returns <code>true</code> for POSTs being redirected, not only for GETs and HEADs.
     */
    private boolean isRedirect(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        Header locationHeader = response.getFirstHeader("location");
        switch (statusCode) {
        case HttpStatus.SC_MOVED_TEMPORARILY:
            return locationHeader != null;
        case HttpStatus.SC_MOVED_PERMANENTLY:
        case HttpStatus.SC_TEMPORARY_REDIRECT:
        case HttpStatus.SC_SEE_OTHER:
            return true;
        default:
            return false;
        }
    }
    
    /**
     * Slightly modified version of {@link DefaultRedirectStrategy#getLocationURI(HttpRequest, HttpResponse, HttpContext)}
     * which also adds the query string from the original request URI to the new URI.
     */
    private URI getRedirectLocationURI(DeltaSyncSession session, HttpUriRequest request, 
            HttpResponse response, HttpContext context) throws DeltaSyncException {
        
        //get the location header to find out where to redirect to
        Header locationHeader = response.getFirstHeader("location");
        if (locationHeader == null) {
            // got a redirect response, but no location header
            throw new DeltaSyncException("Received redirect response " + response.getStatusLine()
                    + " but no location header");
        }
        String location = locationHeader.getValue();
        if (session.getLogger().isDebugEnabled()) {
            session.getLogger().debug("Redirect requested to location '" + location + "'");
        }

        URI uri = null;
        try {
            uri = new URI(location);
            if (request.getURI().getRawQuery() != null) {
                String query = request.getURI().getRawQuery();
                uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), query, uri.getFragment());
            }
        } catch (URISyntaxException ex) {
            throw new DeltaSyncException("Invalid redirect URI: " + location, ex);
        }

        HttpParams params = response.getParams();
        // rfc2616 demands the location value be a complete URI
        // Location       = "Location" ":" absoluteURI
        if (!uri.isAbsolute()) {
            if (params.isParameterTrue(ClientPNames.REJECT_RELATIVE_REDIRECT)) {
                throw new DeltaSyncException("Relative redirect location '"
                        + uri + "' not allowed");
            }
            // Adjust location URI
            HttpHost target = (HttpHost) context.getAttribute(
                    ExecutionContext.HTTP_TARGET_HOST);
            if (target == null) {
                throw new IllegalStateException("Target host not available " +
                        "in the HTTP context");
            }
            try {
                URI requestURI = new URI(request.getRequestLine().getUri());
                URI absoluteRequestURI = URIUtils.rewriteURI(requestURI, target, true);
                uri = URIUtils.resolve(absoluteRequestURI, uri);
            } catch (URISyntaxException ex) {
                throw new DeltaSyncException(ex.getMessage(), ex);
            }
        }
        
        if (params.isParameterFalse(ClientPNames.ALLOW_CIRCULAR_REDIRECTS)) {

            RedirectLocations redirectLocations = (RedirectLocations) context.getAttribute(
                    "http.protocol.redirect-locations");

            if (redirectLocations == null) {
                redirectLocations = new RedirectLocations();
                context.setAttribute("http.protocol.redirect-locations", redirectLocations);
            }

            URI redirectURI;
            if (uri.getFragment() != null) {
                try {
                    HttpHost target = new HttpHost(
                            uri.getHost(),
                            uri.getPort(),
                            uri.getScheme());
                    redirectURI = URIUtils.rewriteURI(uri, target, true);
                } catch (URISyntaxException ex) {
                    throw new DeltaSyncException(ex.getMessage(), ex);
                }
            } else {
                redirectURI = uri;
            }

            if (redirectLocations.contains(redirectURI)) {
                throw new DeltaSyncException("Circular redirect to '" + redirectURI + "'");
            } else {
                redirectLocations.add(redirectURI);
            }
        }
        
        return uri;
    }
    
    
    private interface UriCapturingResponseHandler<T> {
        T handle(URI uri, HttpResponse response) throws DeltaSyncException, IOException;
    }
}
