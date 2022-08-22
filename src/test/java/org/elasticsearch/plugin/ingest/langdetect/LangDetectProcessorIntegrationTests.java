package org.elasticsearch.plugin.ingest.langdetect;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.PluginStats;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.nodes.NodesInfoResponse;
import co.elastic.clients.elasticsearch.nodes.info.NodeInfo;
import co.elastic.clients.elasticsearch.nodes.info.NodeInfoIngestProcessor;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("slow")
public class LangDetectProcessorIntegrationTests {

    private static GenericContainer container;
    private static RestClient restClient;
    private static ElasticsearchClient client;

    @BeforeAll
    public static void startContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile().withDockerfile(Paths.get("./Dockerfile"));
        container = new GenericContainer(image);
        container.addEnv("discovery.type", "single-node");
        container.withEnv("xpack.security.enabled", "false");
        container.withEnv("ES_JAVA_OPTS", "-Xms4g -Xmx4g");
        container.addExposedPorts(9200);
        container.setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*(\"message\":\\s?\"started[\\s?|\"].*|] started\n$)"));

        container.start();
        container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger(LangDetectProcessorIntegrationTests.class)));

        // Create the low-level client
        restClient = RestClient.builder(new HttpHost("localhost", container.getMappedPort(9200))).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);
    }

    @AfterAll
    public static void stopContainer() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
        if (container != null) {
            container.close();
        }
    }

    // at some point this should be split into three tests with the container starting only once
    // and proper JSON parsing, but it is good enough for now
    @Disabled
    @Test
    public void testLangDetectPlugin() throws Exception {
        // this currently breaks in the Elasticsearch Java Client, so let's wait after 8.2.0
        // until this is fixed...
        NodesInfoResponse nodesInfoResponse = client.nodes().info();
        NodeInfo nodeInfo = nodesInfoResponse.nodes().values().iterator().next();
        assertThat(nodeInfo.ingest().processors()).map(NodeInfoIngestProcessor::type).contains("langdetect");
        assertThat(nodeInfo.plugins()).map(PluginStats::name).contains("ingest-langdetect");
    }

    @Test
    public void testLangDetectProcessorInPipeline() throws Exception {
        String putPipelineBody = """
      {
        "description": "_description",
        "processors": [
          {
            "langdetect" : {
              "field" : "field1",
              "target_field" : "field1_language"
            }
          }
        ]
      }
                """;
        HttpRequest request =  HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofString(putPipelineBody))
                .uri(URI.create("http://localhost:" + container.getMappedPort(9200) + "/_ingest/pipeline/my-pipeline"))
                .header("Content-Type", "application/json")
                .build();
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        // index document
        client.index(b -> b.index("test")
                .id("1")
                .pipeline("my-pipeline")
                .document(Map.of("field1", "This is hopefully an english text"))
        );

        GetResponse<Map> getResponse = client.get(b -> b.index("test").id("1"), Map.class);
        Map<String, Object> source = getResponse.source();
        assertThat(source).containsEntry("field1_language", "en");
    }
}
