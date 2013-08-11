package kaqqao.nutch.plugin.selector;

import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.indexer.IndexingException;
import org.apache.nutch.indexer.IndexingFilter;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.storage.WebPage;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Collection;
import java.util.HashSet;

public class HtmlElementSelectorIndexer implements IndexingFilter {

    private Configuration conf;
    private String storageField;

    @Override
    public NutchDocument filter(NutchDocument document, String s, WebPage webPage) throws IndexingException {
        if (storageField != null) {
            CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
            try {
                String strippedContent = decoder.decode(webPage.getFromMetadata(new Utf8(storageField))).toString();
                if (strippedContent != null) {
                    document.add(storageField, strippedContent);
                }
            } catch (CharacterCodingException e) {
                e.printStackTrace();
            }
        }

        return document;
    }

    @Override
    public void setConf(Configuration entries) {
        this.conf = entries;

        this.storageField = getConf().get("parser.html.selector.storage_field", null);
    }

    @Override
    public Configuration getConf() {
        return this.conf;
    }

    @Override
    public Collection<WebPage.Field> getFields() {
        return new HashSet<WebPage.Field>();
    }
}
