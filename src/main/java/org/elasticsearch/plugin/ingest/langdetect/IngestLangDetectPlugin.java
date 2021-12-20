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
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class IngestLangDetectPlugin extends Plugin implements IngestPlugin {

    private AtomicReference<LanguageDetector> languageDetector = new AtomicReference<>();

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        try {
            SecureDetectorFactory.loadProfileFromClassPath(parameters.env);
        } catch (LangDetectException | URISyntaxException | IOException e) {
            throw new ElasticsearchException(e);
        }

        // this lazy loads the lingua supplier, as it needs crazy amounts of memory, which should only be used, if the user uses
        // the lingua implementation in one of the processors
        Supplier<LanguageDetector> supplier = () -> {
            final LanguageDetector languageDetector = this.languageDetector.get();
            if (languageDetector == null) {
                final LanguageDetector detector = LanguageDetectorBuilder.fromAllLanguages().withPreloadedLanguageModels().build();
                final boolean updatedSuccessfully = this.languageDetector.compareAndSet(null, detector);
                if (updatedSuccessfully == false) {
                    detector.destroy();
                }
                return this.languageDetector.get();
            }
            return languageDetector;
        };

        Map<String, Processor.Factory> factoryMap = new HashMap<>(1);
        factoryMap.put(LangDetectProcessor.TYPE, new LangDetectProcessor.Factory(supplier));
        return factoryMap;
    }

    @Override
    public void close() throws IOException {
        super.close();
        final LanguageDetector detector = this.languageDetector.get();
        if (detector != null) {
            detector.destroy();
        }
    }
}
