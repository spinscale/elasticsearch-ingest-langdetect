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

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.core.CheckedFunction;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.util.Map;

import static org.elasticsearch.ingest.ConfigurationUtils.*;

public class LangDetectProcessor extends AbstractProcessor {

    public static final String TYPE = "langdetect";

    private final String field;
    private final String targetField;
    private final boolean ignoreMissing;
    private final CheckedFunction<String, String, Exception> detector;

    public LangDetectProcessor(String tag, String description, String field, String targetField,
                               boolean ignoreMissing, CheckedFunction<String, String, Exception> detector) {
        super(tag, description);
        this.field = field;
        this.targetField = targetField;
        this.ignoreMissing = ignoreMissing;
        this.detector = detector;
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) throws Exception {
        String content;
        try {
            content = ingestDocument.getFieldValue(field, String.class);
        } catch (IllegalArgumentException e) {
            if (ignoreMissing) {
                return ingestDocument;
            }
            throw e;
        }
        if (Strings.isEmpty(content)) {
            return ingestDocument;
        }

        String language = detector.apply(content);
        ingestDocument.setFieldValue(targetField, language);

        return ingestDocument;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        private static final ByteSizeValue DEFAULT_MAX_LENGTH = new ByteSizeValue(10, ByteSizeUnit.KB);

        @Override
        public Processor create(Map<String, Processor.Factory> processorFactories, String tag, String description,
                                Map<String, Object> config) throws Exception {
            String field = readStringProperty(TYPE, tag, config, "field");
            String targetField = readStringProperty(TYPE, tag, config, "target_field");
            String maxLengthStr = readOptionalStringProperty(TYPE, tag, config, "max_length");
            ByteSizeValue maxLength = ByteSizeValue.parseBytesSizeValue(maxLengthStr, DEFAULT_MAX_LENGTH, "max_length");
            boolean ignoreMissing = readBooleanProperty(TYPE, tag, config, "ignore_missing", false);

            CheckedFunction<String, String, Exception> langDetector = input -> {
                Detector detector = DetectorFactory.create();
                detector.setMaxTextLength(Long.valueOf(maxLength.getBytes()).intValue());
                detector.append(input);
                return detector.detect();
            };

            return new LangDetectProcessor(tag, description, field, targetField, ignoreMissing, langDetector);
        }
    }
}
