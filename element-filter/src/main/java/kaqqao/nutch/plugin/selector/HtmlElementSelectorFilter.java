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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlElementSelectorFilter implements ParseFilter {

    private Configuration conf;
    private String storageField;
    private Set<String> protectedURLs;
    private Collection<WebPage.Field> fields = new HashSet<WebPage.Field>();
    private Pattern cssSelectorPattern = Pattern.compile("(\\.|#|\\[|^)([a-zA-Z0-9-_]*)(?:=(.+)\\])?", Pattern.CASE_INSENSITIVE);

    private Set<Selector> blackListSelectors = new HashSet<Selector>();
    private Set<Selector> whiteListSelectors = new HashSet<Selector>();

    @Override
    public Parse filter(String s, WebPage webPage, Parse parse, HTMLMetaTags htmlMetaTags, DocumentFragment documentFragment) {
        DocumentFragment rootToIndex;
        StringBuilder strippedContent = new StringBuilder();
        if (whiteListSelectors.size() > 0 && !protectedURLs.contains(webPage.getBaseUrl())) {
            rootToIndex = (DocumentFragment) documentFragment.cloneNode(false);
            whitelisting(documentFragment, rootToIndex);
        } else if (blackListSelectors.size() > 0 && !protectedURLs.contains(webPage.getBaseUrl())) {
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

    private void blacklisting(Node node) {
        boolean wasStripped = false;

        for(Selector blackListSelector : blackListSelectors) {
            if(blackListSelector.matches(node)) {
                node.setNodeValue("");

                while (node.hasChildNodes()) {
                    node.removeChild(node.getFirstChild());
                }
                wasStripped = true;
                break;
            }
        }

        if (!wasStripped) {
            NodeList children = node.getChildNodes();
            if (children != null) {
                for (int i = 0; i < children.getLength(); i++) {
                    blacklisting(children.item(i));
                }
            }
        }
    }

    private void whitelisting(Node node, Node newNode) {
        boolean wasAppended = false;

        for(Selector whiteListSelector : whiteListSelectors) {
            if(whiteListSelector.matches(node)) {
                newNode.appendChild(node.cloneNode(true));
                wasAppended = true;
                break;
            }
        }

        if (!wasAppended) {
            NodeList children = node.getChildNodes();
            if (children != null) {
                for (int i = 0; i < children.getLength(); i++) {
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

    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;

        String elementsToExclude = getConf().get("parser.html.selector.blacklist", null);
        if ((elementsToExclude != null) && (elementsToExclude.trim().length() > 0)) {
            String[] blackListCssSelectors = elementsToExclude.split(",");
            for (String cssSelector : blackListCssSelectors) {
                blackListSelectors.add(parseCssSelector(cssSelector));
            }
        }

        String elementsToInclude = getConf().get("parser.html.selector.whitelist", null);
        if ((elementsToInclude != null) && (elementsToInclude.trim().length() > 0)) {
            String[] whiteListCssSelectors = elementsToInclude.split(",");
            for (String cssSelector : whiteListCssSelectors) {
                whiteListSelectors.add(parseCssSelector(cssSelector));
            }
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

    private Selector parseCssSelector(String cssSelector) {
        Set<Selector> selectors = new HashSet<Selector>();
        Matcher matcher = cssSelectorPattern.matcher(cssSelector);
        while (matcher.find()) {
            Discriminator discriminator = Discriminator.fromString(matcher.group(1));

            switch (discriminator) {
                case TYPE: selectors.add(new TypeSelector(matcher.group(2))); break;
                case ID: selectors.add(new IdSelector(matcher.group(2))); break;
                case CLASS: selectors.add(new ClassSelector(matcher.group(2))); break;
                case ATTRIBUTE: selectors.add(new AttributeSelector(matcher.group(2), matcher.group(3))); break;
            }
        }

        return new AggregatedSelector(selectors);
    }

    private interface Selector {
        boolean matches(Node node);
    }

    private enum Discriminator {
        TYPE(""), ID("#"), CLASS("."), ATTRIBUTE("[");

        private String discriminatorString;

        Discriminator(String discriminatorString) {
            this.discriminatorString = discriminatorString;
        }

        public static Discriminator fromString(String discriminatorString) {
            if (discriminatorString != null) {
                for (Discriminator discriminator : Discriminator.values()) {
                    if (discriminatorString.equalsIgnoreCase(discriminator.discriminatorString)) {
                        return discriminator;
                    }
                }
            }
            throw new IllegalArgumentException(String.format(
                    "String %s is an invalid CSS selector discriminator. " +
                    "Only \"#\", \".\", \"[\" or an empty string are allowed!", discriminatorString));
        }
    }

    private class TypeSelector implements Selector {

        private String type;

        TypeSelector(String type) {
            assert type != null && type.length() > 0;
            this.type = type;
        }

        @Override
        public boolean matches(Node node) {
            return type.equalsIgnoreCase(node.getNodeName());
        }
    }

    private class ClassSelector implements Selector {

        private String cssClass;

        ClassSelector(String cssClass) {
            assert cssClass != null && cssClass.length() > 0;
            this.cssClass = cssClass;
        }

        @Override
        public boolean matches(Node node) {
            Set<String> classes = new HashSet<String>();
            if (node.hasAttributes()) {
                Node classNode = node.getAttributes().getNamedItem("class");
                if (classNode != null && classNode.getNodeValue() != null) {
                    classes.addAll(Arrays.asList(classNode.getNodeValue().toLowerCase().split("\\s")));
                }
            }
            return classes.contains(cssClass);
        }
    }

    private class IdSelector implements Selector {

        private String id;

        IdSelector(String id) {
            assert id != null && id.length() > 0;
            this.id = id;
        }

        @Override
        public boolean matches(Node node) {
            if (node.hasAttributes()) {
                Node idNode = node.getAttributes().getNamedItem("id");
                return idNode != null ? id.equalsIgnoreCase(idNode.getNodeValue()) : false;
            }

            return false;
        }
    }

    private class AttributeSelector implements Selector {

        private String attributeName, attributeValue;

        AttributeSelector(String attributeName, String attributeValue) {
            assert attributeName != null && attributeName.length() > 0;
            assert attributeValue != null && attributeValue.length() > 0;
            this.attributeName = attributeName;
            this.attributeValue = attributeValue;
        }

        @Override
        public boolean matches(Node node) {
            for (int i = 0; i < node.getAttributes().getLength(); i++) {
                Node currentAttribute = node.getAttributes().item(i);
                if (attributeName.equalsIgnoreCase(currentAttribute.getNodeName()) && attributeValue.equalsIgnoreCase(currentAttribute.getNodeValue())) {
                    return true;
                }
            }

            return false;
        }
    }

    private class AggregatedSelector implements Selector {

        private Collection<Selector> selectors;

        AggregatedSelector(Collection<Selector> selectors) {
            assert selectors != null;
            this.selectors = selectors;
        }

        @Override
        public boolean matches(Node node) {
            for (Selector selector : selectors) {
                if (!selector.matches(node)) {
                    return false;
                }
            }
            return true;
        }
    }
}
