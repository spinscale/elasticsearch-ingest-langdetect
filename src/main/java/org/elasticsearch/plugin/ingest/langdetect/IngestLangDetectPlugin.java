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
import com.cybozu.labs.langdetect.LangDetectException;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.node.NodeModule;
import org.elasticsearch.plugins.Plugin;

import java.io.IOException;
import java.net.URISyntaxException;

public class IngestLangDetectPlugin extends Plugin {

    @Override
    public String name() {
        return "ingest-langdetect";
    }

    @Override
    public String description() {
        return "Ingest processor doing language detection";
    }

    public void onModule(NodeModule nodeModule) throws IOException {
        try {
            SecureDetectorFactory.loadProfileFromClassPath(nodeModule.getNode().getEnvironment());
        } catch (LangDetectException | URISyntaxException e) {
            throw new ElasticsearchException(e);
        }

        nodeModule.registerProcessor(LangDetectProcessor.TYPE,
                (templateService, registry) -> new LangDetectProcessor.Factory());
    }

}
