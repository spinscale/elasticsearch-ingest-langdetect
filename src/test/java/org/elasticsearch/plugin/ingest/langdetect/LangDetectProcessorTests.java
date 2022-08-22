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

import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.SecureDetectorFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LangDetectProcessorTests {

    @TempDir
    public static Path folder;

    @BeforeAll
    public static void loadProfiles() throws Exception {
        Settings settings = Settings.builder().put("path.home", folder).build();
        Environment environment = new Environment(settings, folder);
        SecureDetectorFactory.loadProfileFromClassPath(environment);
    }

    @Test
    public void testThatProcessorWorks() throws Exception {
        Map<String, Object> data = ingestDocument(config("source_field", "language", false),
                "source_field", "This is hopefully an english text, that will be detected.");

        assertThat(data).containsEntry("language", "en");
    }

    @Test
    public void testMaxLengthConfiguration() throws Exception {
        Map<String, Object> config = config("source_field", "language", false);
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

        assertThat(data).containsEntry("language", "en");
    }

    @Test
    public void testIgnoreMissingConfiguration() throws Exception {
        Map<String, Object> data = ingestDocument(config("missing_source_field", "language", true),
                "source_field", "This is hopefully an english text, that will be detected.");

        assertThat(data).doesNotContainEntry("language", "en");
    }

    @Test
    public void testEmptyString() throws Exception {
        Map<String, Object> data = ingestDocument(config("source_field", "language", true),"source_field", "");

        assertThat(data).doesNotContainEntry("language", "en");
    }

    @Test
    public void testNumbersOnlyThrowsException() throws Exception {
        Map<String, Object> config = config("source_field", "language", false);
        assertThatThrownBy(() -> ingestDocument(config, "source_field", "124 56456 546 3432"))
                .isInstanceOf(LangDetectException.class)
                .hasMessage("no features in text");
    }

    private Map<String, Object> ingestDocument(Map<String, Object> config, String field, String value) throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put(field, value);
        IngestDocument ingestDocument = new IngestDocument(document, Collections.emptyMap());

        Processor processor = new LangDetectProcessor.Factory()
                .create(Collections.emptyMap(), "my-tag", "desc", config);
        return processor.execute(ingestDocument).getSourceAndMetadata();
    }

    private Map<String, Object> config(String sourceField, String targetField, boolean ignoreMissing) {
        final Map<String, Object> config = new HashMap<>();
        config.put("field", sourceField);
        config.put("target_field", targetField);
        config.put("ignore_missing", ignoreMissing);
        return config;
    }
}
