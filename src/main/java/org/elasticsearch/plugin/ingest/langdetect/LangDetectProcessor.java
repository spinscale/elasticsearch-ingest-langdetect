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
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.ingest.core.AbstractProcessor;
import org.elasticsearch.ingest.core.AbstractProcessorFactory;
import org.elasticsearch.ingest.core.IngestDocument;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.ingest.core.ConfigurationUtils.readOptionalStringProperty;
import static org.elasticsearch.ingest.core.ConfigurationUtils.readStringProperty;

public class LangDetectProcessor extends AbstractProcessor {

    public static final String TYPE = "langdetect";

    private final String field;
    private final String targetField;
    private final ByteSizeValue maxLength;

    public LangDetectProcessor(String tag, String field, String targetField, ByteSizeValue maxLength) throws IOException {
        super(tag);
        this.field = field;
        this.targetField = targetField;
        this.maxLength = maxLength;
    }

    @Override
    public void execute(IngestDocument ingestDocument) throws Exception {
        Detector detector = DetectorFactory.create();
        detector.setMaxTextLength(maxLength.bytesAsInt());

        String content = ingestDocument.getFieldValue(field, String.class);
        detector.append(content);
        String language = detector.detect();

        ingestDocument.setFieldValue(targetField, language);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory extends AbstractProcessorFactory<LangDetectProcessor> {

        private static final ByteSizeValue DEFAULT_MAX_LENGTH = new ByteSizeValue(10, ByteSizeUnit.KB);

        @Override
        public LangDetectProcessor doCreate(String processorTag, Map<String, Object> config) throws Exception {
            String field = readStringProperty(TYPE, processorTag, config, "field");
            String targetField = readStringProperty(TYPE, processorTag, config, "target_field");
            String maxLengthStr = readOptionalStringProperty(TYPE, processorTag, config, "max_length");

            ByteSizeValue maxLength = ByteSizeValue.parseBytesSizeValue(maxLengthStr, DEFAULT_MAX_LENGTH, "max_length");

            return new LangDetectProcessor(processorTag, field, targetField, maxLength);
        }
    }
}
