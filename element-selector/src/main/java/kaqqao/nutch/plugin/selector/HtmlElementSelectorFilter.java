package kaqqao.nutch.plugin.selector;

import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseFilter;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.util.NodeWalker;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class HtmlElementSelectorFilter implements ParseFilter {

    private Configuration conf;
    private String[] blacklist;
    private String[] whitelist;
    private String storageField;
    private Set<String> protectedURLs;
    private Collection<WebPage.Field> fields = new HashSet<WebPage.Field>();

    @Override
    public Parse filter(String s, WebPage webPage, Parse parse, HTMLMetaTags htmlMetaTags, DocumentFragment documentFragment) {
        DocumentFragment rootToIndex;
        StringBuilder strippedContent = new StringBuilder();
        if ((this.whitelist != null) && (this.whitelist.length > 0) && !protectedURLs.contains(webPage.getBaseUrl())) {
            rootToIndex = (DocumentFragment) documentFragment.cloneNode(false);
            whitelisting(documentFragment, rootToIndex);
        } else if ((this.blacklist != null) && (this.blacklist.length > 0) && !protectedURLs.contains(webPage.getBaseUrl())) {
            rootToIndex = (DocumentFragment) documentFragment.cloneNode(true);
            blacklisting(rootToIndex);
        } else {
            return parse;
        }

        getText(strippedContent, rootToIndex);
        if (storageField == null) {
            parse.setText(strippedContent.toString());
        } else {
            CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
            try {
                webPage.putToMetadata(new Utf8(storageField), encoder.encode(CharBuffer.wrap(strippedContent.toString())));
            } catch (CharacterCodingException e) {
                e.printStackTrace();
            }
        }
        return parse;
    }

    private void blacklisting(Node root) {
        boolean wasStripped = false;
        String type = root.getNodeName().toLowerCase();
        String id = null;
        String className = null;
        if (root.hasAttributes()) {
            Node node = root.getAttributes().getNamedItem("id");
            id = node != null ? node.getNodeValue().toLowerCase() : null;

            node = root.getAttributes().getNamedItem("class");
            className = node != null ? node.getNodeValue().toLowerCase() : null;
        }

        String typeAndId = new StringBuilder().append(type).append("#").append(id).toString();
        String typeAndClass = new StringBuilder().append(type).append(".").append(className).toString();

        boolean inList = false;
        if ((type != null) && (Arrays.binarySearch(this.blacklist, type) >= 0))
            inList = true;
        else if ((type != null) && (id != null) && (Arrays.binarySearch(this.blacklist, typeAndId) >= 0))
            inList = true;
        else if ((type != null) && (className != null) && (Arrays.binarySearch(this.blacklist, typeAndClass) >= 0)) {
            inList = true;
        }
        if (inList) {
            root.setNodeValue("");

            while (root.hasChildNodes())
                root.removeChild(root.getFirstChild());
            wasStripped = true;
        }

        if (!wasStripped) {
            NodeList children = root.getChildNodes();
            if (children != null) {
                int len = children.getLength();
                for (int i = 0; i < len; i++) {
                    blacklisting(children.item(i));
                }
            }
        }
    }

    private void whitelisting(Node pNode, Node newNode) {
        boolean wasStripped = false;
        String type = pNode.getNodeName().toLowerCase();
        String id = null;
        String className = null;
        if (pNode.hasAttributes()) {
            Node node = pNode.getAttributes().getNamedItem("id");
            id = node != null ? node.getNodeValue().toLowerCase() : null;

            node = pNode.getAttributes().getNamedItem("class");
            className = node != null ? node.getNodeValue().toLowerCase() : null;
        }

        String typeAndId = new StringBuilder().append(type).append("#").append(id).toString();
        String typeAndClass = new StringBuilder().append(type).append(".").append(className).toString();

        boolean inList = false;
        if ((type != null) && (Arrays.binarySearch(this.whitelist, type) >= 0))
            inList = true;
        else if ((type != null) && (id != null) && (Arrays.binarySearch(this.whitelist, typeAndId) >= 0))
            inList = true;
        else if ((type != null) && (className != null) && (Arrays.binarySearch(this.whitelist, typeAndClass) >= 0)) {
            inList = true;
        }
        if (inList) {
            newNode.appendChild(pNode.cloneNode(true));
            wasStripped = true;
        }

        if (!wasStripped) {
            NodeList children = pNode.getChildNodes();
            if (children != null) {
                int len = children.getLength();
                for (int i = 0; i < len; i++) {
                    whitelisting(children.item(i), newNode);
                }
            }
        }
    }

    private void getText(StringBuilder sb, Node node) {
        NodeWalker walker = new NodeWalker(node);

        while (walker.hasNext()) {
            Node currentNode = walker.nextNode();
            String nodeName = currentNode.getNodeName();
            short nodeType = currentNode.getNodeType();

            if ("script".equalsIgnoreCase(nodeName)) {
                walker.skipChildren();
            }
            if ("style".equalsIgnoreCase(nodeName)) {
                walker.skipChildren();
            }
            if (nodeType == 8) {
                walker.skipChildren();
            }
            if (nodeType == 3) {
                String text = currentNode.getNodeValue();
                text = text.replaceAll("\\s+", " ");
                text = text.trim();
                if (text.length() > 0) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(text);
                }
            }
        }
    }

    public void setConf(Configuration conf) {
        this.conf = conf;

        this.blacklist = null;
        String elementsToExclude = getConf().get("parser.html.selector.blacklist", null);
        if ((elementsToExclude != null) && (elementsToExclude.trim().length() > 0)) {
            elementsToExclude = elementsToExclude.toLowerCase();

            this.blacklist = elementsToExclude.split(",");
            Arrays.sort(this.blacklist);
        }

        this.whitelist = null;
        String elementsToInclude = getConf().get("parser.html.selector.whitelist", null);
        if ((elementsToInclude != null) && (elementsToInclude.trim().length() > 0)) {
            elementsToInclude = elementsToInclude.toLowerCase();

            this.whitelist = elementsToInclude.split(",");
            Arrays.sort(this.whitelist);
        }

        this.storageField = getConf().get("parser.html.selector.storage_field", null);

        this.protectedURLs = new HashSet<String>(Arrays.asList(getConf().get("parser.html.selector.protected_urls", "").split(",")));
    }

    @Override
    public Configuration getConf() {
        return this.conf;
    }

    @Override
    public Collection<WebPage.Field> getFields() {
        return fields;
    }
}
