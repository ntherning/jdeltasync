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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Utility methods for working with XML {@link Document}s.
 * <p>
 * Some of the methods take an XPath like <code>path</code> parameter. A path
 * only matches {@link Element}s and is always relative to a root {@link Node}.
 * Paths can only contain local names, qualified names, '/' and '//'. 
 * '/' is used to find an immediate child while '//' is used to find a 
 * descendant. Namespace prefixes can be used to match {@link Element} in a 
 * particular namespace. The special '*' prefix matches an {@link Element} in 
 * any namespace or without a namespace. The following namespace prefixes can be 
 * used:
 * <table>
 *   <tr>
 *     <th>Prefix</th><th>Namespace URI</th>
 *   </tr>
 *   <tr>
 *     <td>s</td><td>http://www.w3.org/2003/05/soap-envelope</td>
 *   </tr>
 *   <tr>
 *     <td>wsse</td><td>http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd</td>
 *   </tr>
 *   <tr>
 *     <td>wsu</td><td>http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd</td>
 *   </tr>
 *   <tr>
 *     <td>wst</td><td>http://schemas.xmlsoap.org/ws/2005/02/trust</td>
 *   </tr>
 *   <tr>
 *     <td>airsync</td><td>AirSync:</td>
 *   </tr>
 *   <tr>
 *     <td>itemop</td><td>ItemOperations:</td>
 *   </tr>
 *   <tr>
 *     <td>hmfolder</td><td>HMFOLDER:</td>
 *   </tr>
 *   <tr>
 *     <td>hmmail</td><td>HMMAIL:</td>
 *   </tr>
 *   <tr>
 *     <td>email</td><td>EMAIL:</td>
 *   </tr>
 *   <tr>
 *     <td>hmsync</td><td>HMSYNC:</td>
 *   </tr>
 * </table>
 */
class XmlUtil {

    private static final DocumentBuilderFactory DOM_FACTORY;
    private static final Map<String, String> NAMESPACES;
    
    static {
        DOM_FACTORY = DocumentBuilderFactory.newInstance();
        DOM_FACTORY.setNamespaceAware(true); 
        
        NAMESPACES = new HashMap<String, String>();
        NAMESPACES.put("s", "http://www.w3.org/2003/05/soap-envelope");
        NAMESPACES.put("wsse", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
        NAMESPACES.put("wsu", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd");
        NAMESPACES.put("wst", "http://schemas.xmlsoap.org/ws/2005/02/trust");
        NAMESPACES.put("airsync", "AirSync:");
        NAMESPACES.put("itemop", "ItemOperations:");
        NAMESPACES.put("hmfolder", "HMFOLDER:");
        NAMESPACES.put("hmmail", "HMMAIL:");
        NAMESPACES.put("email", "EMAIL:");
        NAMESPACES.put("hmsync", "HMSYNC:");
    }
    
    /**
     * Parses XML data from a stream and returns a {@link Document}.
     * 
     * @param input the stream to read from.
     * @return the parsed {@link Document}.
     * @throws XmlException on parse errors.
     * @throws IOException on I/O errors.
     */
    public static Document parse(InputStream input) throws XmlException, IOException {
        try {
            return DOM_FACTORY.newDocumentBuilder().parse(input);
        } catch (SAXException e) {
            throw new XmlException(e);
        } catch (ParserConfigurationException e) {
            throw new XmlException(e);
        }
    }

    private static String[] split(String s, String delim, boolean includeDelim) {
        StringTokenizer tok = new StringTokenizer(s, delim, includeDelim);
        String[] parts = new String[tok.countTokens()];
        for (int i = 0; tok.hasMoreTokens(); i++) {
            parts[i] = tok.nextToken();
        }
        return parts;
    }
    
    /**
     * Returns the first {@link Element} which matches the specified
     * <code>path</code>. The search starts at the specified root {@link Node}.
     * 
     * @param root the {@link Node} where the search will start.
     * @param path the path to search for.
     * @return the first {@link Element} that matches the path or 
     *         <code>null</code> if no match could be found.
     */
    public static Element getElement(Node root, String path) {
        List<Element> l = getElements(root, path);
        if (l.isEmpty()) {
            return null;
        }
        return l.get(0);
    }
    
    /**
     * Returns all {@link Element}s which matches the specified
     * <code>path</code>. The search starts at the specified root {@link Node}.
     * 
     * @param root the {@link Node} where the search will start.
     * @param path the path to search for.
     * @return the {@link Element}s that matches the path.
     */
    public static List<Element> getElements(Node root, String path) {
        return getElements(root, new LinkedList<String>(Arrays.asList(split(path, "/", true))));
    }
    
    private static List<Element> find(Node root, String nsUri, String localName, boolean recursive) {
        List<Element> result = new ArrayList<Element>();
        NodeList nl = root.getChildNodes();
        if (nl != null) {
            int len = nl.getLength();
            for (int i = 0; i < len; i++) {
                Node child = nl.item(i);
                if (child instanceof Element) {
                    String childUri = child.getNamespaceURI();
                    String childLocalName = child.getLocalName();
                    if (("*".equals(nsUri) || nsUri == null && childUri == null || nsUri.equals(childUri)) 
                            && localName.equals(childLocalName)) {
                        result.add((Element) child);
                    } else if (recursive) {
                        result.addAll(find(child, nsUri, localName, recursive));
                    }
                }
            }
        }
        return result;
    }
    
    private static List<Element> getElements(Node root, LinkedList<String> parts) {
        boolean recursive = false;
        if (parts.getFirst().equals("/")) {
            parts.removeFirst();
            if (parts.getFirst().equals("/")) {
                parts.removeFirst();
                recursive = true;
            }
        }
        
        String[] qname = split(parts.removeFirst(), ":", false);
        String nsUri = null;
        if (qname.length > 1) {
            if ("*".equals(qname[0])) {
                nsUri = "*";
            } else {
                nsUri = NAMESPACES.get(qname[0]);
                if (nsUri == null) {
                    throw new IllegalArgumentException("Unbound prefix " + qname[0]);
                }
            }
        }
        String localName = qname.length > 1 ? qname[1] : qname[0];
        List<Element> matches = find(root, nsUri, localName, recursive);
        if (!matches.isEmpty()) {
            if (parts.isEmpty()) {
                return matches;
            } else {
                List<Element> result = new ArrayList<Element>(); 
                for (Element match : matches) {
                    result.addAll(getElements(match, new LinkedList<String>(parts)));
                }
                return result;
            }
        }
        return new ArrayList<Element>();
    }
    
    /**
     * Returns <code>true</code> if there is at least one {@link Element} which 
     * matches the specified <code>path</code>. The search starts at the 
     * specified root {@link Node}.
     * 
     * @param root the {@link Node} where the search will start.
     * @param path the path to search for.
     * @return <code>true</code> if a match was found. <code>false</code>
     *         otherwise.
     */
    public static boolean hasElement(Node root, String path) {
        return getElement(root, path) != null;
    }
    
    /**
     * Returns the text content of the first {@link Element} which matches the 
     * specified <code>path</code>. The search starts at the specified root 
     * {@link Node}.
     * 
     * @param root the {@link Node} where the search will start.
     * @param path the path to search for.
     * @return the text content of the first {@link Element} that matches the 
     *         path or <code>null</code> if no match could be found.
     */
    public static String getTextContent(Node root, String path) {
        Element el = getElement(root, path);
        String s = el == null ? null : el.getTextContent();
        return s != null ? s.trim() : null;
    }
    
    /**
     * Sets the text content of the first {@link Element} which matches the 
     * specified <code>path</code>. The search starts at the specified root 
     * {@link Node}.
     * 
     * @param root the {@link Node} where the search will start.
     * @param path the path to search for.
     * @param value the new text content.
     */
    public static void setTextContent(Node root, String path, String value) {
        Element el = getElement(root, path);
        el.setTextContent(value);
    }
    
    /**
     * Writes the specified {@link Document} to an {@link OutputStream} in 
     * compact format.
     * 
     * @param doc the {@link Document}.
     * @param out the stream to write to.
     * @throws XmlException on XML errors.
     */
    public static void writeDocument(Document doc, OutputStream out) throws XmlException {
        writeDocument(doc, out, true);
    }
    
    /**
     * Writes the specified {@link Document} to an {@link OutputStream} in 
     * the specified format.
     * 
     * @param doc the {@link Document}.
     * @param out the stream to write to.
     * @param compact if <code>true</code> the XML will be written in compact
     *        format without any extra whitespaces.
     * @throws XmlException on XML errors.
     */
    public static void writeDocument(Document doc, OutputStream out, boolean compact) throws XmlException {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer serializer = tf.newTransformer();
            serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            serializer.setOutputProperty(OutputKeys.INDENT, compact ? "no" : "yes");
            serializer.transform(new DOMSource(doc), new StreamResult(out));
        } catch (TransformerException e) {
            throw new XmlException(e);
        }
    }
    
    /**
     * Writes the specified {@link Document} to a {@link Writer} in 
     * compact format.
     * 
     * @param doc the {@link Document}.
     * @param writer the {@link Writer} to write to.
     * @throws XmlException on XML errors.
     */
    public static void writeDocument(Document doc, Writer writer) throws XmlException {
        writeDocument(doc, writer, true);
    }
    
    /**
     * Writes the specified {@link Document} to a {@link Writer} in 
     * the specified format.
     * 
     * @param doc the {@link Document}.
     * @param out the stream to write to.
     * @param compact if <code>true</code> the XML will be written in compact
     *        format without any extra whitespaces.
     * @throws XmlException on XML errors.
     */
    public static void writeDocument(Document doc, Writer writer, boolean compact) throws XmlException {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer serializer = tf.newTransformer();
            serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            serializer.setOutputProperty(OutputKeys.INDENT, compact ? "no" : "yes");
            serializer.transform(new DOMSource(doc), new StreamResult(writer));
        } catch (TransformerException e) {
            throw new XmlException(e);
        }
    }
    
    /**
     * Serializes a {@link Document} to a byte array in compact format.
     * 
     * @param doc the {@link Document}.
     * @return the byte array.
     * @throws XmlException on XML errors.
     */
    public static byte[] toByteArray(Document doc) throws XmlException {
        return toByteArray(doc, true);
    }
    
    /**
     * Serializes a {@link Document} to a byte array in the specified format.
     * 
     * @param doc the {@link Document}.
     * @param compact if <code>true</code> the XML serialized in compact
     *        format without any extra whitespaces.
     * @return the byte array.
     * @throws XmlException on XML errors.
     */
    public static byte[] toByteArray(Document doc, boolean compact) throws XmlException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeDocument(doc, baos, compact);
        return baos.toByteArray();
    }
    
    /**
     * Serializes a {@link Document} to a {@link String} in compact format.
     * 
     * @param doc the {@link Document}.
     * @return the {@link String}.
     * @throws XmlException on XML errors.
     */
    public static String toString(Document doc) throws XmlException {
        return toString(doc, true);
    }
    
    /**
     * Serializes a {@link Document} to a {@link String} in the specified format.
     * 
     * @param doc the {@link Document}.
     * @param compact if <code>true</code> the XML serialized in compact
     *        format without any extra whitespaces.
     * @return the {@link String}.
     * @throws XmlException on XML errors.
     */
    public static String toString(Document doc, boolean compact) throws XmlException {
        StringWriter sw = new StringWriter();
        writeDocument(doc, sw, compact);
        return sw.toString();
    }
}
