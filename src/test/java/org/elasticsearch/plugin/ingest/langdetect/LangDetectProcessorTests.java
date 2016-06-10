/*
 * Copyright [2016] [Alexander Reelsen]
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
 *
 */

package org.elasticsearch.plugin.ingest.langdetect;

import com.cybozu.labs.langdetect.SecureDetectorFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.ingest.RandomDocumentPicks;
import org.elasticsearch.ingest.core.IngestDocument;
import org.elasticsearch.test.ESTestCase;
import org.junit.BeforeClass;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasEntry;

public class LangDetectProcessorTests extends ESTestCase {

    @BeforeClass
    public static void loadProfiles() throws Exception {
        Settings settings = Settings.builder().put("path.home", createTempDir()).build();
        Environment environment = new Environment(settings);
        SecureDetectorFactory.loadProfileFromClassPath(environment);
    }

    public void testThatProcessorWorks() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("field", "source_field");
        config.put("target_field", "language");

        Map<String, Object> data = ingestDocument(config,
                "source_field", "This is hopefully an english text, that will be detected.");

        assertThat(data, hasEntry("language", "en"));
    }

    public void testMaxLengthConfiguration() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("field", "source_field");
        config.put("target_field", "language");
        config.put("max_length", "20b");

        // a document with a lot of german text at the end, that should be ignored due to max length
        // copied from https://de.wikipedia.org/wiki/Unwetter_in_Mitteleuropa_2016
        String germanText = "Ab dem 28. Mai 2016 kam es in Folge eines Tiefdruckgebiets, in Deutschland als Elvira bezeichnet, das " +
                "feuchtwarme Luft aus Frankreich in den Südwesten Deutschlands brachte, zu schweren Unwettern mit Starkregen und " +
                "Blitzeinschlägen, Überschwemmungen, Schlammlawinen, Windböen, Hagel und Tornados. Auch in weiteren europäischen Staaten " +
                "kam es zu Extremwetterereignissen und Überschwemmungen, etwa in Paris. Wissenschaftler sagen, dass derartige extreme " +
                "Regenfälle aufgrund des Klimawandels zugenommen haben und mit hoher Wahrscheinlichkeit – besonders in Europa – weiter " +
                "zunehmen werden.";
        Map<String, Object> data = ingestDocument(config,
                "source_field", "This is hopefully an english text, that will be detected. " + germanText);

        assertThat(data, hasEntry("language", "en"));
    }

    private Map<String, Object> ingestDocument(Map<String, Object> config, String field, String value) throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put(field, value);
        IngestDocument ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), document);

        LangDetectProcessor processor = new LangDetectProcessor.Factory().doCreate(randomAsciiOfLength(10), config);
        processor.execute(ingestDocument);
        return ingestDocument.getSourceAndMetadata();
    }
}

