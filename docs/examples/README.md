# Examples

## `WebhookActivationConditionTest.java`

A self-contained JUnit 5 test for **consumer projects** that own both the OpenAPI
spec and the BPMN models. It walks every `*.bpmn` under `src/test/resources/bpmn/`,
reads each webhook event's `activationCondition`, and compares it against the
matching `# METHOD /path` block in a checked-in generator output file
(`src/test/resources/feel/expected-activation.feel` by default).

To use it:

1. Copy the file into your project's test sources and adjust the package.
2. Point the plugin's `outputFile` at the fixture path the test reads.
3. Run the build. Any drift between a BPMN `activationCondition` and the
   generated FEEL fails the test with both versions in the message.

It is kept here rather than in this repository's test tree because it is a
template for your project, not a test of this library. Only JUnit 5, AssertJ,
and the JDK XML parser are required.
