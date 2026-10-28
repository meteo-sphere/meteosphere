package org.meteosphere.data.local;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NearestCitiesTest {

    // geonameid, name, asciiname, alternatenames, latitude, longitude, feature class, feature code, country code, ...
    private static final String DUMP =
            "524901\tMoscow\tMoscow\tMoskva\t55.75222\t37.61556\tP\tPPLC\tRU\t\t48\t\t\t\t10381222\t\t144\tEurope/Moscow\t2022-12-10\n" +
            "1\tOld Moscow\tOld Moscow\t\t55.7\t37.6\tP\tPPLH\tRU\n" +
            "498817\tSaint Petersburg\tSaint Petersburg\t\t59.93863\t30.31413\tP\tPPLA\tRU\n" +
            "broken\tline\n" +
            "2\tNo coordinates\tNo coordinates\t\t-\t-\tP\tPPL\tRU\n" +
            "550280\tKhimki\tKhimki\t\t55.89704\t37.42969\tP\tPPLA2\tRU\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static byte[] zip(String entryName, String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            zipOut.putNextEntry(new ZipEntry(entryName));
            zipOut.write(text.getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    public void parsesDumpAndFindsNearest() throws Exception {
        NearestCities.Cities cities = NearestCities.parseDump(new ByteArrayInputStream(zip("cities1000.txt", DUMP)));
        assertArrayEquals(new int[]{524901, 498817, 550280}, cities.ids);

        List<NearestCities.Nearest> nearest = NearestCities.find(cities, 55.9, 37.4, 2);
        assertEquals(2, nearest.size());
        assertEquals(550280, nearest.get(0).id);
        assertEquals(1.8f, nearest.get(0).distance, 0.1f);
        assertEquals(524901, nearest.get(1).id);
    }

    @Test(expected = IOException.class)
    public void failsWithoutDumpEntry() throws Exception {
        NearestCities.parseDump(new ByteArrayInputStream(zip("readme.txt", DUMP)));
    }

    @Test
    public void usesSavedIndex() throws Exception {
        File dir = folder.newFolder();
        NearestCities.Cities cities = NearestCities.parseDump(new ByteArrayInputStream(zip("cities1000.txt", DUMP)));
        NearestCities.writeIndex(cities, new File(dir, "cities1000.bin"));

        NearestCities.Cities saved = NearestCities.readIndex(new File(dir, "cities1000.bin"));
        assertArrayEquals(cities.ids, saved.ids);
        assertArrayEquals(cities.lats, saved.lats, 0f);
        assertArrayEquals(cities.lngs, saved.lngs, 0f);

        // No download when the index is there
        List<NearestCities.Nearest> nearest = new NearestCities(dir).find(59.9, 30.3, 1);
        assertEquals(1, nearest.size());
        assertEquals(498817, nearest.get(0).id);
    }
}
