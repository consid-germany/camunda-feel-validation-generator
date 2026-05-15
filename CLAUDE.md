# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

A Maven plugin that reads an OpenAPI 3 document (JSON or YAML) and emits FEEL
validation expressions for Camunda webhook connectors (Inbound/Intermediate).
Output is consumed in the Web Modeler in one of two modes:

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
  Maven entry point (`@Mojo(name = "generate-feel")`). Validates inputs, then
  delegates.
- [FEELValidationGenerator.java](src/main/java/com/consid/automation/camunda/FEELValidationGenerator.java) —
  Builder-based facade. Orchestrates: parse → extract required fields →
  build rules → render → write file.

Internal collaborators, split by responsibility:

- [internal/openapi/](src/main/java/com/consid/automation/camunda/internal/openapi/) —
  OpenAPI traversal. [RequiredFieldsExtractor.java](src/main/java/com/consid/automation/camunda/internal/openapi/RequiredFieldsExtractor.java)
  walks the schema (handling `$ref`, `allOf`, `anyOf`, `oneOf` with discriminator,
  nested objects, `dependentRequired`, `if`/`then`) to produce an
  [ExtractionResult](src/main/java/com/consid/automation/camunda/internal/openapi/ExtractionResult.java).
  [FieldTypeResolver.java](src/main/java/com/consid/automation/camunda/internal/openapi/FieldTypeResolver.java)
  maps each schema to a sealed [TypeInfo](src/main/java/com/consid/automation/camunda/internal/model/TypeInfo.java).
- [internal/model/](src/main/java/com/consid/automation/camunda/internal/model/) —
  internal domain model: sealed `TypeInfo` (Boolean / Number / String / Array /
  Object / Unknown), sealed `Trigger` (Presence / Value), sealed `FeelLiteral`
  (String / Number / Boolean / Null), `FieldDescriptor`, `ValidationRule`.
- [internal/feel/](src/main/java/com/consid/automation/camunda/internal/feel/) —
  FEEL rendering. [FEELRuleGenerator.java](src/main/java/com/consid/automation/camunda/internal/feel/FEELRuleGenerator.java)
  (implements [ValidationRuleBuilder](src/main/java/com/consid/automation/camunda/internal/feel/ValidationRuleBuilder.java))
  + [FEELExpressionBuilder.java](src/main/java/com/consid/automation/camunda/internal/feel/FEELExpressionBuilder.java)
  turn the descriptor map into `ValidationRule` objects and emit the final FEEL text.
- [internal/Diagnostics.java](src/main/java/com/consid/automation/camunda/internal/Diagnostics.java) —
  routes build-time warnings (unsupported-but-detected constructs) to the consumer
  passed via `Builder.withWarningConsumer(...)`; the Mojo wires it to `getLog().warn`.

Keep this separation: OpenAPI traversal stays out of the FEEL renderer, and FEEL
syntax stays out of the traversal.

## Build & test

```bash
mvn verify              # build, run all tests, enforce 80% line-coverage gate
mvn test                # tests only
mvn install             # install the plugin into the local repository
```

Integration tests under
[AbstractFEELValidationGeneratorIntegrationTest.java](src/test/java/com/consid/automation/camunda/AbstractFEELValidationGeneratorIntegrationTest.java)
compare generated output against fixtures in
[src/test/resources/feel/](src/test/resources/feel/) and execute the generated
FEEL against the Camunda `feel-engine` with payloads from
[src/test/resources/payloads/](src/test/resources/payloads/). When you change FEEL
output, update both the expected `.txt` fixture and any payload that depends on
the new shape — running the engine catches divergence the string-diff alone
misses.

## Conventions

- **Java 21.** Use modern APIs: `java.nio.file.Files/Path`, records where they
  fit, `List.of`, switch expressions. Avoid resurrecting `java.io.File` chains.
- **Pure library — no DI framework.** Do not introduce Spring, Guice, CDI, or
  similar. Wiring is done by hand in constructors / the Builder. New
  collaborators get passed in, not autowired.
- **Minimal dependencies.** Current runtime deps are `swagger-parser`,
  `jackson-databind`, and `slf4j-simple`; Maven APIs are `provided`. Don't add
  a dependency without a clear reason — and prefer the JDK first. Test-only
  additions go in `test` scope.
- **Small, focused classes** with one responsibility, matching the existing
  split (parse vs. extract vs. build vs. render). Add a new collaborator before
  bloating an existing class.
- **Public API surface = `FEELValidationGenerator` + its `Builder` + the Mojo's
  `@Parameter` fields.** Anything under `internal.*` may change between
  versions. If you add a Mojo parameter, mirror it on the Builder (and vice
  versa) so the two front doors stay aligned.
- **Validation at the boundary.** The Mojo validates inputs (file exists,
  status codes in range); internal classes can trust their arguments. Don't
  re-validate in three places.
- **Tests: JUnit 5 + AssertJ + Mockito.** Fixture-driven integration tests are
  the safety net for FEEL output — prefer adding a fixture pair (OpenAPI in,
  expected FEEL out) over asserting fragments in code.
- **Comments & Javadoc.** Keep the existing class-level Javadoc style on public
  types. Inside methods, only comment the non-obvious *why*.
