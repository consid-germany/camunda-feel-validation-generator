# FEEL Validation Generator

Maven plugin that reads an OpenAPI 3.x document (JSON or YAML) and emits FEEL validation expressions for Camunda webhook connectors (Inbound and Intermediate). Drop the output into the connector's `activationCondition` or `responseExpression` field to keep payload validation aligned with your API contract.

Java 21. Three runtime dependencies: `swagger-parser`, `jackson-databind`, and `slf4j-simple` (test-only). No DI framework.

> **Development transparency.** Claude (Anthropic's AI assistant) is used to support development on this project. Every change still follows standard engineering practices — TDD, clean code, small focused classes — and all code is reviewed for correctness, security, and architectural fit before it lands on `main`.

## Usage

Wire the plugin into your build:

```xml
<plugin>
  <groupId>com.consid.automation.camunda</groupId>
  <artifactId>feel-validation-generator</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution>
      <goals><goal>generate-feel</goal></goals>
      <configuration>
        <openApiSpec>${project.basedir}/src/main/resources/openapi.yaml</openApiSpec>
        <outputFile>${project.build.directory}/feel/validation.feel</outputFile>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Or run it once from the command line:

```bash
mvn com.consid.automation.camunda:feel-validation-generator:1.0.0:generate-feel \
  -DfeelValidationGenerator.openApiSpec=openapi.yaml \
  -DfeelValidationGenerator.outputFile=target/validation.feel
```

## Configuration

| Parameter | Property | Default | Notes |
|---|---|---|---|
| `openApiSpec` | `feelValidationGenerator.openApiSpec` | — | **Required.** Path to the OpenAPI 3.x document. |
| `outputFile` | `feelValidationGenerator.outputFile` | — | **Required.** FEEL output destination; parent dirs are created. |
| `addResponse` | `feelValidationGenerator.addResponse` | `false` | `true` emits a response expression, `false` an activation condition. |
| `successStatusCode` | `feelValidationGenerator.successStatusCode` | `201` | HTTP status returned in response mode when validation passes. |
| `failStatusCode` | `feelValidationGenerator.failStatusCode` | `400` | HTTP status returned in response mode when validation fails. |
| `methods` | `feelValidationGenerator.methods` | `POST,PUT,PATCH` | Comma-separated HTTP methods to scan. |
| `mediaType` | `feelValidationGenerator.mediaType` | `application/json` | Request body media type to read schemas from. |

Status codes must fall in 100–599 or the build fails fast.

### Programmatic use

```java
FEELValidationGenerator.builder()
    .withOpenApiPath(Path.of("openapi.yaml"))
    .withOutputFilePath(Path.of("target/validation.feel"))
    .build()
    .generate();
```

The Builder mirrors the Mojo parameters (`withResponse`, `withMediaType`, …) and adds `withWarningConsumer(Consumer<String>)` for [diagnostics](#diagnostics). Only `FEELValidationGenerator`, its `Builder`, and the Mojo are part of the public API — everything under `com.consid.automation.camunda.internal.*` may change between versions.

## Output modes

### Activation condition (`addResponse=false`)

Boolean FEEL for the connector's `activationCondition` field. Invalid payloads never start a process instance — the caller receives the connector's static `422` fallback.

```feel
# POST /customers
{
  req: request.body,
  rules: [
    {invalid: req.customerId=null or not(req.customerId instance of string)},
    {invalid: req.email=null or not(req.email instance of string) or not(matches(req.email, "^[^@\s]+@[^@\s]+\.[^@\s]+$"))},
    {invalid: req.age=null or not(req.age instance of number) or req.age<18}
  ],
  isValid: count(rules[invalid=true])=0
}.isValid
```

![activation condition example](activationCondition.png)

### Response expression (`addResponse=true`)

Context FEEL for the connector's `responseExpression` field. The webhook **always** starts a process instance; the FEEL only shapes the response body and status code. To halt the BPMN on invalid input, add a script task that re-validates and terminates.

```feel
# POST /customers
{
  req: request.body,
  rules: [
    { id: "customerId-invalid", field: "customerId", invalid: req.customerId=null or not(req.customerId instance of string) },
    { id: "age-invalid", field: "age", invalid: req.age=null or not(req.age instance of number) or req.age<18 }
  ],
  isValid: count(rules[invalid=true])=0,
  body: {
    message: if isValid then "Process successfully started." else "Process creation failed.",
    processInstanceKey: if isValid then correlation.processInstanceKey else null,
    details: rules[invalid=true]
  }, statusCode: if isValid then 201 else 400
}
```

![response expression example](responseExpression.png)

## What is supported

**Every clause the generator emits describes when the field is _invalid_** — the rule evaluates to `true` to reject the payload. The default body is `field=null or <type-violation>`; constraints, modifiers, and triggers extend it while preserving that reading.

### Types

| OpenAPI type / format | Violation clause |
|---|---|
| `type: string` | `not(X instance of string)` |
| `type: string, format: date` / `date-time` / `time` | `date(X)=null` / `date and time(X)=null` / `time(X)=null` |
| `type: number` / `type: integer` | `not(X instance of number)` |
| `type: boolean` | `not(X instance of boolean)` |
| `type: array` | `not(X instance of list)` |
| `type: object` | `not(X instance of context)` |

Modifiers that layer on top of the type clause:

- `enum` adds `or not(X in (…))`. `const: v` is treated as a single-value enum.
- `nullable: true` (3.0) / `type: [<t>, "null"]` (3.1) flips the rule to `field!=null and (…)` — missing is allowed, only present-but-malformed is rejected.

### Value constraints

Each keyword is only emitted when declared — there is no implicit "non-empty" assumption.

**Strings** (including `date` / `date-time` / `time`):

| Keyword | Violation clause |
|---|---|
| `minLength: N` / `maxLength: N` | `string length(X)<N` / `string length(X)>N` |
| `pattern: <regex>` | `not(matches(X, "<regex>"))` |
| `format: email` / `uuid` / `uri` | matches a built-in regex (only when no explicit `pattern` is set) |

**Arrays:**

| Keyword | Violation clause |
|---|---|
| `minItems: N` / `maxItems: N` | `count(X)<N` / `count(X)>N` |
| `items: <schema>` | `(some e in X satisfies (<element-violation>))` — recurses into the element schema, including its own required fields |

**Numbers** (`number` and `integer`):

| Keyword | Violation clause |
|---|---|
| `minimum: N` / `maximum: N` | `X<N` / `X>N` |
| `exclusiveMinimum` / `exclusiveMaximum` | `X<=N` / `X>=N` — both 3.0 boolean and 3.1 numeric forms are recognized |
| `multipleOf: N` | `modulo(X, N)!=0` |

**Objects:**

| Keyword | Violation clause |
|---|---|
| `additionalProperties: false` | `(not(every k in get entries(X).key satisfies (k in (<declared keys>))))` — emits a separate `rootObject-invalid` rule when set at the root |

### Composition

- **`$ref`** is resolved against `#/components/schemas/*`. An unresolved ref fails the build; the error names the endpoint.
- **`allOf`** merges every branch's `required` list into the parent.
- **`oneOf` + `discriminator.mapping`** guards each branch's required fields by the discriminator value and pins the discriminator property to the mapping keys:
  ```yaml
  oneOf:
    - $ref: "#/components/schemas/InvoicePaid"
    - $ref: "#/components/schemas/InvoiceFailed"
  discriminator:
    propertyName: type
    mapping:
      invoice.paid:   "#/components/schemas/InvoicePaid"
      invoice.failed: "#/components/schemas/InvoiceFailed"
  ```
- **`oneOf` / `anyOf` without a discriminator** are union-merged (all branches' required fields accumulated). The generated FEEL is stricter than the spec implies; a warning is emitted.
- A property using `allOf` / `oneOf` / `anyOf` without an explicit `type: object` is still treated as an object so inner required fields are honored.

### Conditional requirements

Two JSON Schema keywords scope a requirement to a runtime condition; multiple triggers on the same field OR-merge.

- **`dependentRequired: { trigger: [<dependent>, …] }`** — if `trigger` is present, the dependents are required. The dependent's rule becomes `trigger!=null and (<dependent-violation>)`.
- **`if`/`then`** — if a single property in `if.properties` matches its `const` / `enum`, the `then.required` fields are required:
  ```yaml
  if:
    properties: { paymentMethod: { const: card } }
    required:   [paymentMethod]
  then:
    required:   [cardNumber]
  ```
  Emits `req.paymentMethod="card" and (<cardNumber-violation>)`. `enum` predicates render as `in (…)`; boolean `const` triggers render as the bare path (`req.flag` / `not(req.flag)`).

Nested-object required fields inherit a conditionally-required parent's triggers, so inner rules only fire when the parent's condition holds. A plain-optional parent's inner required fields are omitted.

### Restrictions

- `if`/`then` outside the single-property `const` / `enum` subset is skipped — no multi-property `if`, no nested logic, no `pattern` / range / length predicates, no `else`.
- `if`/`then` dependents must be sibling property names. To scope a conditional to nested fields, place the `if`/`then` inside the nested object's schema.
- Schema-form `additionalProperties` (a sub-schema, not a boolean) is not honored — only `additionalProperties: false`.
- Not yet supported: `uniqueItems`, `minProperties` / `maxProperties`, `readOnly` / `writeOnly`, format-driven validations beyond `date` / `date-time` / `time` / `email` / `uuid` / `uri`, request inputs other than `request.body`.

### Diagnostics

When the generator detects a supported-but-warned construct it emits a build warning instead of silently dropping the rule. Each message is prefixed `[<location>] …` (field path, `$ref`, or `(root)`). Warned constructs:

- `if`/`then` outside the supported subset.
- `oneOf` without `discriminator.mapping`.
- Schema-form `additionalProperties`.

The Maven Mojo logs warnings via `getLog().warn(...)`. Programmatic callers consume them with `Builder.withWarningConsumer(Consumer<String>)`.

## Pinning BPMN to the generated FEEL

For consumer projects that own both the OpenAPI spec and the BPMN process models, a self-contained helper test at [src/test/java/com/consid/automation/camunda/WebhookActivationConditionTest.java](src/test/java/com/consid/automation/camunda/WebhookActivationConditionTest.java) walks every `*.bpmn` under `src/test/resources/bpmn/`, reads each webhook event's `activationCondition`, and compares it against the matching block in `src/test/resources/feel/expected-activation.feel`. Copy the file into your consumer project, wire the plugin's `outputFile` to that fixture path, and any drift between BPMN and the generated FEEL fails the build. Only JUnit 5, AssertJ, and the JDK XML parser are required.

## Build

```bash
mvn verify              # tests + 80% line-coverage gate
mvn install             # install the plugin into the local repository
```
