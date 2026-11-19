package dev.applyops.orchestrator.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Java side of the cross-language contract: our records must serialize to the shared schemas. */
class EchoSchemaContractTest {

    private static final Path SCHEMAS = Path.of(System.getProperty("applyops.schemas.dir"));
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonSchema load(String relative) throws Exception {
        try (InputStream in = Files.newInputStream(SCHEMAS.resolve(relative))) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in);
        }
    }

    @Test
    void echoRequestMatchesSchema() throws Exception {
        JsonNode json = mapper.valueToTree(new EchoRequest("Hello, ApplyOps"));
        assertThat(load("echo/EchoRequest.v1.json").validate(json)).isEmpty();
    }

    @Test
    void echoResultMatchesSchema() throws Exception {
        JsonNode json = mapper.valueToTree(new EchoResult("Hello", "host-1", "python"));
        assertThat(load("echo/EchoResult.v1.json").validate(json)).isEmpty();
    }

    @Test
    void schemaRejectsMissingFields() throws Exception {
        JsonNode json = mapper.readTree("{\"message\":\"Hello\"}");
        assertThat(load("echo/EchoResult.v1.json").validate(json)).isNotEmpty();
    }
}
