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
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.ingest.ConfigurationUtils.readBooleanProperty;
import static org.elasticsearch.ingest.ConfigurationUtils.readOptionalStringProperty;
import static org.elasticsearch.ingest.ConfigurationUtils.readStringProperty;

public class LangDetectProcessor extends AbstractProcessor {

    public static final String TYPE = "langdetect";

    private final String field;
    private final String targetField;
    private final ByteSizeValue maxLength;
    private final boolean ignoreMissing;

    public LangDetectProcessor(String tag, String description, String field, String targetField,
                               ByteSizeValue maxLength, boolean ignoreMissing)
            throws IOException {
        super(tag, description);
        this.field = field;
        this.targetField = targetField;
        this.maxLength = maxLength;
        this.ignoreMissing = ignoreMissing;
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) throws Exception {
        Detector detector = DetectorFactory.create();
        detector.setMaxTextLength(maxLength.bytesAsInt());

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

        detector.append(content);
        String language = detector.detect();

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

            return new LangDetectProcessor(tag, description, field, targetField, maxLength, ignoreMissing);
        }
    }
}
