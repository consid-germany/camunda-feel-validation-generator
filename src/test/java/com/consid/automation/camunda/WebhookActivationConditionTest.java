package com.consid.automation.camunda;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every BPMN process under {@link #BPMN_RESOURCE_DIR} with a Camunda
 * inbound webhook (start event or intermediate catch event) has an
 * {@code activationCondition} matching the FEEL the plugin emitted into
 * {@link #EXPECTED_FEEL_RESOURCE}.
 *
 * <p><b>Conventions</b>:
 * <ul>
 *   <li>BPMN: the event carries one of
 *       {@code zeebe:modelerTemplate="io.camunda.connectors.webhook.WebhookConnectorStartMessage.v1"}
 *       (start event) or
 *       {@code zeebe:modelerTemplate="io.camunda.webhook.WebhookConnectorIntermediate.v1"}
 *       (intermediate catch event).
 *   <li>The event's {@code bpmn:extensionElements/zeebe:properties} block
 *       supplies {@code inbound.method}, {@code inbound.context} and
 *       {@code activationCondition}. The FEEL marker {@code =} prefix on
 *       {@code activationCondition} is stripped before comparison.
 *   <li>The FEEL fixture is the plugin's raw output: one block per endpoint,
 *       each preceded by a {@code # <METHOD> <path>} heading.
 *   <li>BPMN-side key: {@code <inbound.method> /inbound/<inbound.context>}. The
 *       FEEL heading reflects the webhook runtime path ({@code /inbound/<context>})
 *       while the BPMN only stores the bare {@code <context>}, so the test prepends
 *       the {@code /inbound/} segment when looking up the matching block. The
 *       lookup is <b>case-insensitive</b>, so a BPMN context of {@code customers}
 *       matches a FEEL heading like {@code # POST /inbound/Customers}. Adjust
 *       {@link BpmnWebhook#endpointKey()} if your spec path scheme differs.
 * </ul>
 */
class WebhookActivationConditionTest {

    /**
     * {@code zeebe:modelerTemplate} substrings that identify a webhook-triggered
     * event — start event or intermediate catch event flavours.
     */
    private static final Set<String> WEBHOOK_TEMPLATE_IDS = Set.of(
        "io.camunda.connectors.webhook.WebhookConnectorStartMessage.v1",
        "io.camunda.webhook.WebhookConnectorIntermediate.v1"
    );

    /** BPMN element local-names that can carry a webhook connector template. */
    private static final Set<String> WEBHOOK_EVENT_LOCAL_NAMES = Set.of(
        "startEvent",
        "intermediateCatchEvent"
    );

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String ZEEBE_NS = "http://camunda.org/schema/zeebe/1.0";

    /** Folder (under {@code src/test/resources/}) walked for {@code *.bpmn} files. */
    private static final String BPMN_RESOURCE_DIR = "bpmn";

    /** Plugin-generated FEEL file, checked in. */
    private static final String EXPECTED_FEEL_RESOURCE = "feel/expected-activation.feel";

    @TestFactory
    Collection<DynamicTest> activation_conditions_in_bpmn_match_generated_feel() throws Exception {
        if (resourceMissing(BPMN_RESOURCE_DIR) || resourceMissing(EXPECTED_FEEL_RESOURCE)) {
            // Template mode — fixtures haven't been added yet. Don't fail the build.
            return List.of();
        }

        Map<String, String> expectedByEndpoint = loadFeelByEndpoint(EXPECTED_FEEL_RESOURCE);
        List<BpmnWebhook> webhooks = loadWebhooks(BPMN_RESOURCE_DIR);

        assertThat(webhooks)
            .as("No webhook events discovered under '%s' — check the folder and that "
                + "modelerTemplate contains one of %s", BPMN_RESOURCE_DIR, WEBHOOK_TEMPLATE_IDS)
            .isNotEmpty();

        List<DynamicTest> tests = new ArrayList<>();
        for (BpmnWebhook webhook : webhooks) {
            String key = webhook.endpointKey();
            tests.add(DynamicTest.dynamicTest(
                webhook.bpmnFile() + " :: " + key,
                () -> {
                    String expected = expectedByEndpoint.get(key);
                    assertThat(expected)
                        .as("No FEEL block headed '# %s' in %s. Either the generator didn't "
                            + "emit one for this endpoint, or the BPMN context doesn't match "
                            + "any OpenAPI path. Known FEEL endpoints: %s",
                            key, EXPECTED_FEEL_RESOURCE, expectedByEndpoint.keySet())
                        .isNotNull();
                    assertThat(normalize(stripFeelMarker(webhook.activationCondition())))
                        .as("activationCondition in %s (event '%s') drifted from the FEEL "
                            + "generated for '# %s'. Regenerate via `mvn ... generate-feel` and "
                            + "paste the new block into the BPMN.",
                            webhook.bpmnFile(), webhook.eventId(), key)
                        .isEqualTo(normalize(expected));
                }));
        }
        return tests;
    }

    // ---- BPMN parsing ----

    private List<BpmnWebhook> loadWebhooks(String resourceDir) throws Exception {
        Path dir = resourceAsPath(resourceDir);
        DocumentBuilder builder = hardenedDocumentBuilder();
        List<BpmnWebhook> webhooks = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> bpmnFiles = stream
                .filter(p -> p.getFileName().toString().endsWith(".bpmn"))
                .sorted()
                .toList();
            for (Path file : bpmnFiles) {
                Document doc = builder.parse(file.toFile());
                String fileName = file.getFileName().toString();
                for (String localName : WEBHOOK_EVENT_LOCAL_NAMES) {
                    collectWebhookEvents(doc, localName, fileName, webhooks);
                }
            }
        }
        return webhooks;
    }

    private void collectWebhookEvents(Document doc, String localName, String fileName,
                                      List<BpmnWebhook> sink) {
        NodeList events = doc.getElementsByTagNameNS(BPMN_NS, localName);
        for (int i = 0; i < events.getLength(); i++) {
            Element event = (Element) events.item(i);
            String template = event.getAttributeNS(ZEEBE_NS, "modelerTemplate");
            if (!hasWebhookTemplate(template)) {
                continue;
            }
            String eventId = event.getAttribute("id");
            Map<String, String> properties = readZeebeProperties(event);
            String method = require(properties.get("inbound.method"),
                "inbound.method", fileName, eventId);
            String context = require(properties.get("inbound.context"),
                "inbound.context", fileName, eventId);
            String activation = require(properties.get("activationCondition"),
                "activationCondition", fileName, eventId);
            sink.add(new BpmnWebhook(fileName, eventId, method, context, activation));
        }
    }

    private static boolean hasWebhookTemplate(String template) {
        if (template.isEmpty()) {
            return false;
        }
        return WEBHOOK_TEMPLATE_IDS.stream().anyMatch(template::contains);
    }

    private Map<String, String> readZeebeProperties(Element event) {
        Map<String, String> props = new LinkedHashMap<>();
        NodeList propertyElements = event.getElementsByTagNameNS(ZEEBE_NS, "property");
        for (int i = 0; i < propertyElements.getLength(); i++) {
            Element prop = (Element) propertyElements.item(i);
            String name = prop.getAttribute("name");
            if (!name.isEmpty()) {
                props.put(name, prop.getAttribute("value"));
            }
        }
        return props;
    }

    private static String require(String value, String name, String file, String eventId) {
        assertThat(value)
            .as("zeebe:property '%s' missing or empty in %s (event '%s')",
                name, file, eventId)
            .isNotNull()
            .isNotBlank();
        return value;
    }

    private static DocumentBuilder hardenedDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    // ---- FEEL parsing ----

    private Map<String, String> loadFeelByEndpoint(String resource) throws Exception {
        String content = Files.readString(resourceAsPath(resource), StandardCharsets.UTF_8);
        // Case-insensitive keys: BPMN inbound.context may be all-lowercase while the
        // OpenAPI path (and therefore the FEEL heading) may use mixed case.
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String currentKey = null;
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            if (line.startsWith("# ")) {
                if (currentKey != null) {
                    result.put(currentKey, current.toString().stripTrailing());
                }
                currentKey = line.substring(2).trim();
                current = new StringBuilder();
            } else if (currentKey != null) {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        if (currentKey != null) {
            result.put(currentKey, current.toString().stripTrailing());
        }
        return result;
    }

    // ---- Helpers ----

    private boolean resourceMissing(String name) {
        return getClass().getClassLoader().getResource(name) == null;
    }

    private Path resourceAsPath(String resource) throws URISyntaxException {
        URL url = getClass().getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Test resource not found: " + resource);
        }
        return Path.of(url.toURI());
    }

    /** Drop Camunda's FEEL marker {@code =} prefix from a property value, if present. */
    private static String stripFeelMarker(String value) {
        return value.startsWith("=") ? value.substring(1) : value;
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }

    private record BpmnWebhook(String bpmnFile,
                               String eventId,
                               String method,
                               String context,
                               String activationCondition) {
        /**
         * Builds the lookup key matching the plugin's FEEL heading. The plugin
         * emits {@code # <METHOD> /inbound/<context>} because the webhook runtime
         * exposes connectors under {@code /inbound/<context>}; the BPMN side only
         * stores the bare {@code <context>}, so the prefix is added here.
         */
        String endpointKey() {
            return method + " /inbound/" + context;
        }
    }
}
