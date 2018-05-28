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

package com.cybozu.labs.langdetect;

import com.cybozu.labs.langdetect.util.LangProfile;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.env.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Allows loading of the detector factory languages when the security manager is enabled
 * Copies the jar file over to a tmp file, so no additional permissions are required
 * Does not use jsonic either as it would use reflection and again require additional permissions
 *
 * Needs to be in this package in order to call DetectorFactory.addProfile()
 */
public class SecureDetectorFactory {

    public static void loadProfileFromClassPath(Environment environment) throws LangDetectException, URISyntaxException, IOException {
        Path tmp = Files.createTempFile(environment.tmpFile(), "langdetect", ".jar");
        URL resource = SecureDetectorFactory.class.getClassLoader().getResource("profiles/");
        // ugly hack to get back the jar file only and then copy it
        String jarName = resource.toURI().getSchemeSpecificPart().replaceFirst("!/profiles/", "").replaceFirst("^file:", "");
        try (InputStream in = FileSystemUtils.openFileURLStream(new URL("file://" + jarName))) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        FileSystem fileSystem =
                FileSystems.newFileSystem(new URI("jar:" + tmp.toAbsolutePath().toUri().toString()), Collections.emptyMap());

        DirectoryStream<Path> ds = Files.newDirectoryStream(fileSystem.getPath("profiles/"));
        Iterator<Path> iter = ds.iterator();
        Map<String, LangProfile> profiles = new HashMap<>();
        while (iter.hasNext()) {
            String path = iter.next().toString().replaceFirst("/","");
            InputStream stream = SecureDetectorFactory.class.getClassLoader().getResourceAsStream(path);

            Map<String, Object> data = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY,
                    DeprecationHandler.THROW_UNSUPPORTED_OPERATION, stream).map();
            profiles.put(path, createLangProfile(data));
        }
        ds.close();
        int langsize = profiles.size(), index = 0;
        for (String path : profiles.keySet()) {
            LangProfile profile = profiles.get(path);
            DetectorFactory.addProfile(profile, index, langsize);
            ++index;
        }
    }

    @SuppressWarnings("unchecked")
    private static LangProfile createLangProfile(Map<String, Object> data) {
        LangProfile langProfile = new LangProfile();
        List<Integer> nWords = (List<Integer>) data.get("n_words");
        langProfile.n_words = new int[nWords.size()];
        for(int i = 0;i < langProfile.n_words.length;i++)
            langProfile.n_words[i] = nWords.get(i);

        langProfile.name = (String) data.get("name");
        langProfile.freq = (HashMap<String, Integer>) data.get("freq");

        return langProfile;
    }
}
