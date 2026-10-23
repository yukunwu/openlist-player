package site.openlist.player;

import org.junit.Test;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class LocalizationTest {
    private Map<String, String> strings(String folder) throws Exception {
        NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/" + folder + "/strings.xml")).getElementsByTagName("string");
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String key = nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
            assertNull("Duplicate resource: " + key, result.put(key, nodes.item(i).getTextContent()));
        }
        return result;
    }

    private TreeSet<String> placeholders(String value) {
        TreeSet<String> result = new TreeSet<>();
        Matcher matcher = Pattern.compile("%[0-9]+\\$[sd]").matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    @Test public void englishAndChineseHaveMatchingKeysAndArguments() throws Exception {
        Map<String, String> english = strings("values"), chinese = strings("values-zh-rCN");
        assertEquals(english.keySet(), chinese.keySet());
        for (String key : english.keySet()) {
            assertFalse("Chinese in English resource: " + key, Pattern.compile("[\\p{IsHan}]").matcher(english.get(key)).find());
            assertEquals(key, placeholders(english.get(key)), placeholders(chinese.get(key)));
        }
    }

    @Test public void metadataUsesSuppliedLanguage() {
        androidx.media3.common.Format audio = new androidx.media3.common.Format.Builder()
                .setSampleMimeType("audio/ac3").setChannelCount(6).build();
        assertEquals("AC-3 / 6 channels", VideoPresentation.details(null, audio, "Loading", "%1$d channels"));
        assertEquals("Loading", VideoPresentation.details(null, null, "Loading", "%1$d channels"));
    }
}
