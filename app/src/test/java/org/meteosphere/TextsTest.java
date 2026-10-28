package org.meteosphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

// The app's texts in its 14 languages, read from the resources: each language has every translatable text of the
// default one, with as many items and the same arguments, and the plural quantities its rules need
public class TextsTest {

    // Gradle runs the tests in the module's folder, an IDE may run them in the project's
    private static final File RES = new File("src/main/res").isDirectory() ? new File("src/main/res") : new File("app/src/main/res");
    // The languages' folders and the quantities their plural rules need, as Android's PluralRules, which isn't on the
    // JVM, has them: French, Spanish, Italian and Portuguese have "many" for millions
    private static final Map<String, Set<String>> QUANTITIES = new LinkedHashMap<>();
    // %s, %d, %1$s, %2$d: an index, its flags, width and precision, the conversion
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])");
    // A placeholder the code would replace, @name@ or @name
    private static final Pattern PLACEHOLDER = Pattern.compile("@[a-zA-Z]+@?");

    static {
        Set<String> oneOther = Set.of("one", "other");
        Set<String> oneManyOther = Set.of("one", "many", "other");
        Set<String> slavic = Set.of("one", "few", "many", "other");
        Set<String> other = Set.of("other");
        QUANTITIES.put("values", oneOther);
        QUANTITIES.put("values-ru", slavic);
        QUANTITIES.put("values-uk", slavic);
        QUANTITIES.put("values-pl", slavic);
        QUANTITIES.put("values-de", oneOther);
        QUANTITIES.put("values-fr", oneManyOther);
        QUANTITIES.put("values-es", oneManyOther);
        QUANTITIES.put("values-it", oneManyOther);
        QUANTITIES.put("values-pt", oneManyOther);
        QUANTITIES.put("values-tr", oneOther);
        QUANTITIES.put("values-zh", other);
        QUANTITIES.put("values-ja", other);
        QUANTITIES.put("values-nl", oneOther);
        QUANTITIES.put("values-b+zh+Hant", other);
    }

    // One folder's texts by their names
    private static final class Texts {
        final Map<String, String> strings = new LinkedHashMap<>();
        final Map<String, List<String>> arrays = new LinkedHashMap<>();
        final Map<String, Map<String, String>> plurals = new LinkedHashMap<>();
        // The default's texts that the translations don't have
        final Set<String> untranslatable = new TreeSet<>();
    }

    private static Texts read(String folder) throws IOException, XmlPullParserException {
        File[] files = new File(RES, folder).listFiles((dir, name) -> name.endsWith(".xml"));
        assertNotNull(folder, files);
        Texts texts = new Texts();
        for (File file : files) {
            try (InputStream in = new FileInputStream(file)) {
                KXmlParser parser = new KXmlParser();
                parser.setInput(in, "UTF-8");
                for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                    if (event != XmlPullParser.START_TAG || parser.getDepth() != 2) {
                        continue;
                    }
                    String name = parser.getAttributeValue(null, "name");
                    if ("false".equals(parser.getAttributeValue(null, "translatable"))) {
                        texts.untranslatable.add(name);
                    }
                    switch (parser.getName()) {
                        case "string":
                            texts.strings.put(name, parser.nextText());
                            break;
                        case "string-array":
                            texts.arrays.put(name, items(parser, null));
                            break;
                        case "plurals":
                            Map<String, String> forms = new LinkedHashMap<>();
                            items(parser, forms);
                            texts.plurals.put(name, forms);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        return texts;
    }

    // The items up to the element's end, by their quantities into the forms given
    private static List<String> items(KXmlParser parser, Map<String, String> forms) throws IOException, XmlPullParserException {
        List<String> items = new ArrayList<>();
        int depth = parser.getDepth();
        for (int event = parser.next(); !(event == XmlPullParser.END_TAG && parser.getDepth() == depth); event = parser.next()) {
            if (event == XmlPullParser.START_TAG && "item".equals(parser.getName())) {
                String quantity = parser.getAttributeValue(null, "quantity");
                String text = parser.nextText();
                items.add(text);
                if (forms != null) {
                    forms.put(quantity, text);
                }
            }
        }
        return items;
    }

    /** The text's arguments, "1$s" and "2$d", numbered as Android numbers the ones without an index */
    static Set<String> arguments(String text) {
        Set<String> arguments = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(text);
        int next = 1;
        while (matcher.find()) {
            if (matcher.group(2).equals("%")) {
                continue;
            }
            int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : next++;
            arguments.add(index + "$" + matcher.group(2));
        }
        return arguments;
    }

    @Test
    public void everyLanguageHasEveryText() throws Exception {
        Texts english = read("values");
        for (String folder : QUANTITIES.keySet()) {
            Texts texts = read(folder);
            List<String> missing = new ArrayList<>();
            for (String name : english.strings.keySet()) {
                if (!english.untranslatable.contains(name) && !texts.strings.containsKey(name)) {
                    missing.add(name);
                }
            }
            for (String name : english.arrays.keySet()) {
                if (!english.untranslatable.contains(name) && !texts.arrays.containsKey(name)) {
                    missing.add(name);
                }
            }
            for (String name : english.plurals.keySet()) {
                if (!texts.plurals.containsKey(name)) {
                    missing.add(name);
                }
            }
            assertEquals(folder, List.of(), missing);
        }
    }

    @Test
    public void arraysHaveAsManyItems() throws Exception {
        Texts english = read("values");
        for (String folder : QUANTITIES.keySet()) {
            for (Map.Entry<String, List<String>> array : read(folder).arrays.entrySet()) {
                List<String> items = english.arrays.get(array.getKey());
                assertNotNull(folder + " " + array.getKey() + " isn't in the default language", items);
                assertEquals(folder + " " + array.getKey(), items.size(), array.getValue().size());
            }
        }
    }

    @Test
    public void stringsTakeTheSameArguments() throws Exception {
        Texts english = read("values");
        for (String folder : QUANTITIES.keySet()) {
            for (Map.Entry<String, String> string : read(folder).strings.entrySet()) {
                String text = english.strings.get(string.getKey());
                assertNotNull(folder + " " + string.getKey() + " isn't in the default language", text);
                assertEquals(folder + " " + string.getKey(), arguments(text), arguments(string.getValue()));
            }
        }
    }

    // Each quantity's text takes what the default's "other" takes: favorites_limit its number, the words none
    @Test
    public void pluralsTakeTheSameArguments() throws Exception {
        Texts english = read("values");
        for (String folder : QUANTITIES.keySet()) {
            for (Map.Entry<String, Map<String, String>> plural : read(folder).plurals.entrySet()) {
                Map<String, String> forms = english.plurals.get(plural.getKey());
                assertNotNull(folder + " " + plural.getKey() + " isn't in the default language", forms);
                for (Map.Entry<String, String> form : plural.getValue().entrySet()) {
                    assertEquals(folder + " " + plural.getKey() + " " + form.getKey(), arguments(forms.get("other")),
                            arguments(form.getValue()));
                }
            }
        }
    }

    // Exactly the quantities the language's rules have: one without its quantity falls back to "other"
    @Test
    public void pluralsHaveTheLanguagesQuantities() throws Exception {
        for (Map.Entry<String, Set<String>> language : QUANTITIES.entrySet()) {
            for (Map.Entry<String, Map<String, String>> plural : read(language.getKey()).plurals.entrySet()) {
                assertEquals(language.getKey() + " " + plural.getKey(), new TreeSet<>(language.getValue()),
                        new TreeSet<>(plural.getValue().keySet()));
            }
        }
    }

    // The code fills texts in with Android's arguments only
    @Test
    public void noPlaceholders() throws Exception {
        for (String folder : QUANTITIES.keySet()) {
            Texts texts = read(folder);
            List<String> all = new ArrayList<>(texts.strings.values());
            texts.arrays.values().forEach(all::addAll);
            texts.plurals.values().forEach(forms -> all.addAll(forms.values()));
            for (String text : all) {
                assertFalse(folder + ": " + text, PLACEHOLDER.matcher(text).find());
            }
        }
    }

    // The languages offered to the system, Simplified Chinese as zh-Hans there, and the ones the texts are in
    @Test
    public void languageCodesAreTheAppsLanguages() throws Exception {
        Texts english = read("values");
        List<String> codes = english.arrays.get("language_codes");
        assertEquals(english.arrays.get("language_names").size(), codes.size());
        assertEquals(codes.size(), new TreeSet<>(codes).size());

        Set<String> offered = new TreeSet<>();
        try (InputStream in = new FileInputStream(new File(RES, "xml/locales_config.xml"))) {
            KXmlParser parser = new KXmlParser();
            parser.setInput(in, "UTF-8");
            for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG && "locale".equals(parser.getName())) {
                    Locale locale = Locale.forLanguageTag(parser.getAttributeValue(null, "android:name"));
                    offered.add(locale.getLanguage().equals("zh") && locale.getScript().equals("Hans") ? "zh" : locale.toLanguageTag());
                }
            }
        }
        assertEquals(new TreeSet<>(codes), offered);

        Set<String> written = new TreeSet<>();
        for (String folder : QUANTITIES.keySet()) {
            written.add(read(folder).strings.get("app_language"));
        }
        assertEquals(new TreeSet<>(codes), written);
    }

    // Numbered or not, a percent sign of the text itself none
    @Test
    public void argumentsByIndex() {
        assertEquals(Set.of("1$s", "2$d"), arguments("%2$d %1$s"));
        assertEquals(Set.of("1$d"), arguments("%d km"));
        assertEquals(Set.of("1$s", "2$s", "3$s"), arguments("%1$s, %2$s. %3$s"));
        assertEquals(Set.of("1$s", "2$s"), arguments("%s and %s"));
        assertTrue(arguments("Moon illumination, %").isEmpty());
        assertTrue(arguments("50%% off").isEmpty());
    }
}
