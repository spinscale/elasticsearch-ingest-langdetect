package org.elasticsearch.plugin.ingest.langdetect;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("slow")
public class LangDetectProcessorIntegrationTests {

    // at some point this should be split into three tests with the container starting only once
    // and proper JSON parsing, but it is good enough for now
    @Test
    public void testLangDetectPlugin() throws Exception {
        final ImageFromDockerfile image = new ImageFromDockerfile().withDockerfile(Paths.get(System.getenv("PWD"), "Dockerfile"));

        try (GenericContainer container = new GenericContainer(image)) {
            container.addEnv("discovery.type", "single-node");
            container.withEnv("ELASTIC_PASSWORD", "changeme");
            container.withEnv("xpack.security.enabled", "true");
            container.withEnv("ES_JAVA_OPTS", "-Xms4g -Xmx4g");
            container.addExposedPorts(9200);
            container.setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*(\"message\":\\s?\"started\".*|] started\n$)"));

            container.start();

            String endpoint = String.format("http://localhost:%s/", container.getMappedPort(9200));

            HttpRequest request =  HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(endpoint))
                    .header("Authorization", basicAuth("elastic", "changeme"))
                    .build();

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // check initial connection works
            assertThat(response.body()).startsWith("{");
            assertThat(response.statusCode()).isEqualTo(200);

            // check for langdetect plugin and available processor
            request =  HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(endpoint + "_nodes/plugins,ingest"))
                    .header("Authorization", basicAuth("elastic", "changeme"))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // too lazy to parse JSON currently...
            assertThat(response.body()).contains("\"name\":\"ingest-langdetect\"");
            assertThat(response.body()).contains("\"type\":\"langdetect\"");

            // test lang detection in a processor
            String putPipelineBody = """
          {
            "description": "_description",
            "processors": [
              {
                "langdetect" : {
                  "field" : "field1",
                  "target_field" : "field1_language"
                }
              },
              {
                "langdetect" : {
                  "field" : "field1",
                  "target_field" : "field1_lingua",
                  "implementation" : "lingua"
                }
              }
            ]
          }
                    """;
            request =  HttpRequest.newBuilder()
                    .PUT(HttpRequest.BodyPublishers.ofString(putPipelineBody))
                    .uri(URI.create(endpoint + "_ingest/pipeline/my_pipeline"))
                    .header("Authorization", basicAuth("elastic", "changeme"))
                    .header("Content-Type", "application/json")
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);

            // index document
            String documentBody = "{ \"field1\": \"This is hopefully an english text\" }";
            request =  HttpRequest.newBuilder()
                    .PUT(HttpRequest.BodyPublishers.ofString(documentBody))
                    .uri(URI.create(endpoint + "test/_doc/1?pipeline=my_pipeline"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", basicAuth("elastic", "changeme"))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(201);

            // retrieve document
            request =  HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(endpoint + "test/_doc/1"))
                    .header("Authorization", basicAuth("elastic", "changeme"))
                    .header("Content-Type", "application/json")
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);

            // too lazy to parse JSON currently...
            assertThat(response.body()).contains("\"field1_language\":\"en\"");
            assertThat(response.body()).contains("\"field1_lingua\":\"en\"");
        }
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }
}
