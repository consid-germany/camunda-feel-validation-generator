# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

A Maven plugin that reads an OpenAPI 3 document (JSON or YAML) and emits FEEL
validation expressions for Camunda webhook connectors (Inbound/Intermediate).
Rules cover required request-body fields (read from `request.body`) and
required query parameters (read from `request.params`). Output is consumed in
the Web Modeler in one of two modes:

- **Activation condition** (`addResponse=false`) — boolean FEEL.
- **Response expression** (`addResponse=true`) — context FEEL with body + status.

See [README.md](README.md) for usage, configuration, and example output.

## Layout

Single-module Maven project (Java 21):

- [pom.xml](pom.xml) — root POM (`com.consid.automation.camunda:feel-validation-generator`,
  `packaging=maven-plugin`, goal `generate-feel`).
- [src/main/java/com/consid/automation/camunda/](src/main/java/com/consid/automation/camunda/) — sources.
- [src/test/java/com/consid/automation/camunda/](src/test/java/com/consid/automation/camunda/) — tests.

## Architecture

Public entry points sit at the top of the package; everything else is under
`internal.*` and free to change between versions.

Public API:

- [FEELValidationGeneratorMojo.java](src/main/java/com/consid/automation/camunda/FEELValidationGeneratorMojo.java) —
  Maven entry point (`@Mojo(name = "generate-feel")`). Checks the spec file
  exists, maps `@Parameter`s onto the Builder, and translates
  `FEELGenerationException` into `MojoFailureException` (anything else into
  `MojoExecutionException`).
- [FEELValidationGenerator.java](src/main/java/com/consid/automation/camunda/FEELValidationGenerator.java) —
  Builder-based facade. Orchestrates: parse → scan operations → extract required
  body fields and query parameters → build rules → render → write file.
  `generate()` throws only
  [FEELGenerationException](src/main/java/com/consid/automation/camunda/FEELGenerationException.java)
  (unparseable spec, unresolved `$ref`, unwritable output). Parser validation
  messages go to the warning consumer.

Internal collaborators, split by responsibility:

- [internal/openapi/](src/main/java/com/consid/automation/camunda/internal/openapi/) —
  OpenAPI traversal. [OpenApiOperationScanner.java](src/main/java/com/consid/automation/camunda/internal/openapi/OpenApiOperationScanner.java)
  walks paths/operations for the configured methods and yields one
  [OperationInputs](src/main/java/com/consid/automation/camunda/internal/openapi/OperationInputs.java)
  (body schema + required query parameters, path-level merged with operation-level)
  per `Endpoint`; [MediaTypeMatcher.java](src/main/java/com/consid/automation/camunda/internal/openapi/MediaTypeMatcher.java)
  decides which request-body media type counts (parameters and case ignored,
  `+json` suffix accepted). [RequiredFieldsExtractor.java](src/main/java/com/consid/automation/camunda/internal/openapi/RequiredFieldsExtractor.java)
  walks the body schema (handling `$ref`, `allOf`, `anyOf`, `oneOf` with discriminator,
  nested objects, `dependentRequired`, `if`/`then`) to produce an
  [ExtractionResult](src/main/java/com/consid/automation/camunda/internal/openapi/ExtractionResult.java);
  recursion state travels in a private `Traversal` record, and required names are
  looked up through `allOf` branches before falling back to a presence-only rule.
  [QueryParameterExtractor.java](src/main/java/com/consid/automation/camunda/internal/openapi/QueryParameterExtractor.java)
  turns required query parameters into descriptors — string schemas keep their
  constraints, everything else is presence-only because `request.params` values
  are strings. [FieldTypeResolver.java](src/main/java/com/consid/automation/camunda/internal/openapi/FieldTypeResolver.java)
  maps each schema to a sealed [TypeInfo](src/main/java/com/consid/automation/camunda/internal/model/TypeInfo.java).
- [internal/model/](src/main/java/com/consid/automation/camunda/internal/model/) —
  internal domain model: sealed `TypeInfo` (Boolean / Number / String / Array /
  Object / Unknown), sealed `Trigger` (Presence / Value), sealed `FeelLiteral`
  (String / Number / Boolean / Null), `FieldDescriptor`, `Endpoint` (method +
  path, the grouping key between scanner and renderer), `ValidationRule` (carries
  an `InputSource` of BODY or QUERY). `FeelString.render()` is the single place
  a Java string is escaped into a FEEL string literal.
- [internal/feel/](src/main/java/com/consid/automation/camunda/internal/feel/) —
  FEEL rendering. [FEELRuleGenerator.java](src/main/java/com/consid/automation/camunda/internal/feel/FEELRuleGenerator.java)
  + [FEELExpressionBuilder.java](src/main/java/com/consid/automation/camunda/internal/feel/FEELExpressionBuilder.java)
  turn the descriptor map into `ValidationRule` objects and emit the final FEEL text,
  one block per `Endpoint` under a `# METHOD /path` heading. The `req: request.body`
  alias is bound only when a rule reads the body.
  Body rules read `req.<path>` (`req` aliases `request.body`); query-parameter
  rules read `request.params.<name>`, falling back to
  `get value(request.params, "<name>")` for non-identifier names and FEEL keywords.
- [internal/Diagnostics.java](src/main/java/com/consid/automation/camunda/internal/Diagnostics.java) —
  routes build-time warnings (OpenAPI parser messages and unsupported-but-detected
  constructs) to the consumer passed via `Builder.withWarningConsumer(...)`; the
  Mojo wires it to `getLog().warn`.

Keep this separation: OpenAPI traversal stays out of the FEEL renderer, and FEEL
syntax stays out of the traversal.

## Build & test

```bash
mvn verify              # build, run all tests, enforce 80% line-coverage gate
mvn test                # tests only
mvn install             # install the plugin into the local repository
```

Integration tests are driven by the `scenarios()` table in
[AbstractFEELValidationGeneratorIntegrationTest.java](src/test/java/com/consid/automation/camunda/AbstractFEELValidationGeneratorIntegrationTest.java).
Each scenario pairs an OpenAPI fixture from
[src/test/resources/openapi/](src/test/resources/openapi/) with a body payload
(`*-body.json`) and, for query-parameter cases, a params payload
(`*-params.json`) from [src/test/resources/payloads/](src/test/resources/payloads/),
plus the expected boolean verdict. The generated FEEL is executed against the
Camunda `feel-engine` with `request.body` / `request.params` bound to those
payloads — the assertion is the engine's verdict, not a string diff. When you
add a mechanism, add a fixture pair plus at least one valid and one invalid
scenario; the response snapshot under
[src/test/resources/response/](src/test/resources/response/) pins the body shape.

The Mojo test drives the plugin through a hand-written `RecordingLog`; there is
no mocking framework. [docs/examples/](docs/examples/) holds a consumer-side
template test (BPMN ↔ generated FEEL drift check) that is documentation, not part
of this build.

## Conventions

- **Java 21.** Use modern APIs: `java.nio.file.Files/Path`, records where they
  fit, `List.of`, switch expressions. Avoid resurrecting `java.io.File` chains.
- **Pure library — no DI framework.** Do not introduce Spring, Guice, CDI, or
  similar. Wiring is done by hand in constructors / the Builder. New
  collaborators get passed in, not autowired.
- **Minimal dependencies.** The only runtime dependency is `swagger-parser`
  (Jackson comes with it, pinned via `jackson-bom`); Maven APIs are `provided`;
  `feel-engine` and `slf4j-simple` are `test`. Don't add a dependency without a
  clear reason — prefer the JDK first, and don't reintroduce a mocking framework
  for something a ten-line stub covers.
- **Small, focused classes** with one responsibility, matching the existing
  split (parse vs. extract vs. build vs. render). Add a new collaborator before
  bloating an existing class.
- **Public API surface = `FEELValidationGenerator` + its `Builder` +
  `FEELGenerationException` + the Mojo's `@Parameter` fields.** Anything under
  `internal.*` may change between versions. If you add a Mojo parameter, mirror
  it on the Builder (and vice versa) so the two front doors stay aligned. Mojo
  path parameters are `File` so Maven resolves them against `${basedir}`; convert
  to `Path` immediately.
- **Validation at the boundary.** The Mojo validates inputs (file exists,
  status codes in range); internal classes can trust their arguments. Don't
  re-validate in three places.
- **Tests: JUnit 5 + AssertJ.** Fixture-driven integration tests are the
  safety net for FEEL output — prefer adding a fixture pair (OpenAPI in, payload
  + verdict) over asserting fragments in code. Test names read
  `test_<subject>_does_<behavior>`.
- **Explicit imports**, no wildcards, in main and test code.
- **Comments & Javadoc.** Class-level Javadoc says what a type does now; the
  history of how it got there belongs in git. Inside methods, only comment the
  non-obvious *why*. Javadoc is built with `doclint all,-missing` and fails the
  build on errors.
