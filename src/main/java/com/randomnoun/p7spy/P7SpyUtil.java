package com.randomnoun.p7spy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** P7Spy utilities.
 * 
 * <p>A command line utility to generate a p7spy-ed versions of a jboss datasource definition 
 * document. The file containing the datasource definition should be specified as the first argument 
 * to the program.
 * 
 * <p><i>Implementation note:</i> Could use XSLT for this, but I find using that akin to be punched repeatedly
 * in the face with a brick.
 * 
 * @TODO update the log4j config as well. But that's not going to be as generic
 * as I envisage this package eventually becoming.
 * 
 * @author knoxg
 */
public class P7SpyUtil {

    /**
     * Reads a file, and returns its contents in a String
     *
     * @param filename The file to read
     *
     * @return The contents of the string,
     *
     * @throws IOException A problem occurred whilst attempting to read the string
     */
    private static String getFileContents(String filename)
        throws IOException {
        File file = new File(filename);
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        int len = fis.read(data);

        if (len < file.length()) {
            /* this should never happen -- file has changed underneath us */
            throw new IOException("Buffer read != size of file");
        }

        return new String(data);
    }

	/** Return a DOM document object from an XML string
	 * 
	 * @param text the string representation of the XML to parse 
	 */
	private static Document toDocument(String text) throws SAXException {
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(new ByteArrayInputStream(text.getBytes()));
			doc.getDocumentElement().normalize(); // Collapses adjacent text nodes into one node.
			return doc;
		} catch (ParserConfigurationException pce) {
			// this can never happen 
			throw (IllegalStateException) new IllegalStateException("Error creating DOM parser").initCause(pce);
		} catch (IOException ioe) {
			// this can also never happen
			throw (IllegalStateException) new IllegalStateException("Error retrieving information").initCause(ioe);
		} 
	}	

	/**
	 * Iterates through the child nodes of the specified element, and returns the contents
	 * of all Text and CDATA elements among those nodes, concatenated into a string.
	 *
	 * <p>Elements are recursed into.
	 *
	 * @param element the element that contains, as child nodes, the text to be returned.
	 * @return the contents of all the CDATA children of the specified element.
	 */
	private static String getXmlText(Element element)
	{
		if (element == null) { throw new NullPointerException("null element"); }
		StringBuffer buf = new StringBuffer();
		NodeList children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			org.w3c.dom.Node child = children.item(i);
			short nodeType = child.getNodeType();
			if (nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
				buf.append(((org.w3c.dom.Text) child).getData());			
			} else if (nodeType == org.w3c.dom.Node.TEXT_NODE) {
				buf.append(((org.w3c.dom.Text) child).getData());
			} else if (nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
				buf.append(getXmlText((Element) child));
			}
		}
		return buf.toString();
	}

	/** Converts a document node subtree back into an XML string 
	 * 
	 * @param node a DOM node 
	 * 
	 * @return the XML for this node
	 * 
	 * @throws TransformerException if the transformation to XML failed
	 * @throws IllegalStateException if the transformer could not be initialised 
	 */
	private static String writeXml(Node node, boolean omitXmlDeclaration) 
		throws TransformerException 
	{
		// Use a Transformer for output
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer();
			DOMSource source = new DOMSource(node);
			StreamResult result = new StreamResult(baos);
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, omitXmlDeclaration ? "yes": "no");
			transformer.transform(source, result);
			return baos.toString();
		} catch (TransformerConfigurationException tce) {
			throw (IllegalStateException) new IllegalStateException("Could not initialise transfoermer").initCause(tce);
		}
			
	}

	
	public static void main(String args[]) throws Exception {
		String dataSourceDefinition = getFileContents(args[0]);
		Document doc = toDocument(dataSourceDefinition);
		NodeList dataSources = XPathAPI.selectNodeList(doc, ".//local-tx-datasource");

		for (int i = 0; i < dataSources.getLength(); i++) {
			Node node = dataSources.item(i);

			Element driverClassElement = (Element) XPathAPI.selectSingleNode(node, "driver-class");
			Element connectionUrlElement = (Element) XPathAPI.selectSingleNode(node, "connection-url");
			
			if (driverClassElement==null) { throw new ParseException("driver-class missing from local-tx-datasource element", 0); }
			if (connectionUrlElement==null) { throw new ParseException("connection-url missing from local-tx-datasource element", 0); }

			String driverClass = getXmlText(driverClassElement);
			String connectionUrl = getXmlText(connectionUrlElement);
			
			if (driverClass.indexOf("p7spy")==-1) {
				if (connectionUrl.startsWith("jdbc:")) {
					// good driver !
					connectionUrl = "jdbc:p7spy#" + driverClass + ":" + connectionUrl.substring(5);
				} else {
					// weird driver !
					connectionUrl = "jdbc:p7spy#" + driverClass + ":-:" + connectionUrl;
				}
				driverClassElement.getFirstChild().setNodeValue("net.sf.p7spy.P7SpyDriver");
				connectionUrlElement.getFirstChild().setNodeValue(connectionUrl);
			} 
		}
		
		System.out.println(writeXml(doc, false));
	}


}