# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Pre-release work toward the project's first public Maven Central artifact. Significant feature, refactor, and packaging changes since the initial commit.

### Added

- **`FEELGenerationException`**: the single exception type `generate()` throws — unparseable spec, unresolved `$ref`, unwritable output. The Mojo maps it to `MojoFailureException` (a build failure, no stack dump); anything else remains a `MojoExecutionException`.
- **OpenAPI parser messages surface as warnings.** A misspelled keyword (`minLenght`) or a missing `info` block used to be swallowed; it now reaches the warning consumer / Maven log.
- **Media type matching** ignores parameters and case, and accepts structured-syntax suffixes: `application/json` matches `application/json; charset=utf-8` and `application/vnd.acme+json`, preferring an exact match when both are declared.
- **`required` names declared beside `allOf`** are resolved from the branch schemas (previously dropped). A name declared nowhere yields a presence-only rule plus a warning.
- **Build guards**: `maven-enforcer-plugin` (Java 21+, Maven 3.9+), Dependabot for Maven and GitHub Actions, Javadoc `doclint` enforced on the published artifact.
- **Required query parameters**: `in: query` + `required: true` parameters (operation- or path-level, `$ref` resolved) emit rules against `request.params`. String schemas keep their constraints (`enum`, `pattern`, length, `format`); other types are presence-only because query values arrive as strings. Names that are not FEEL identifiers or collide with FEEL keywords are read via `get value(request.params, "<name>")`. Operations with required query parameters but no request body now produce a block; response-mode rules are identified as `params.<name>`.
- **Array constraints**: `minItems`, `maxItems`, and `items` recursion — each element is validated against its full schema, including the element's own required fields.
- **String constraints**: `minLength`, `maxLength`, `pattern`, plus built-in permissive regexes for `format: email` / `uuid` / `uri`.
- **Temporal string formats**: `format: date` / `date-time` / `time` (via FEEL's `date(X)` / `date and time(X)` / `time(X)` parsers).
- **Number constraints**: `minimum`, `maximum`, `exclusiveMinimum` / `exclusiveMaximum` (both OpenAPI 3.0 boolean and 3.1 numeric forms), `multipleOf`.
- **`const` outside `if`** (treated as a single-value enum) and **`additionalProperties: false`** (emits a separate `rootObject-invalid` rule when set on the root request schema).
- **`oneOf` with `discriminator` + `mapping`**: per-branch conditional rules guarded by the discriminator value; the discriminator property itself is pinned to the mapping keys.
- **Build-time diagnostics**: warnings (not silent skips) for `if`/`then` outside the supported subset, `oneOf` without `discriminator.mapping`, and schema-form `additionalProperties`.
- **`Builder.withWarningConsumer(Consumer<String>)`** for programmatic diagnostic consumption; the Maven Mojo wires it to `getLog().warn(...)` automatically.
- **Sources and Javadoc jars** attached during the `package` phase.
- **Public-artifact metadata** in the POM: license (Apache-2.0), SCM, developers, organization, issue tracker, inception year.

### Changed

- **Composition implies object**: `allOf` / `oneOf` / `anyOf` without an explicit `type: object` is now treated as an object, so inner required fields are honored without workarounds.
- **Unresolved `$ref` errors** include the endpoint heading (e.g. `POST /customers`) so the broken reference is locatable in multi-endpoint specs.
- **Internal model overhauled** to use sealed type hierarchies (`TypeInfo`, `Trigger`, `FeelLiteral`) — adding a new variant is one switch arm in one place instead of touching multiple files.
- **Mojo path parameters** `openApiSpec` / `outputFile` are `File`-typed, so relative paths resolve against `${basedir}` as with any Maven plugin.
- **Runtime dependencies reduced to `swagger-parser`.** `jackson-databind` is no longer declared directly (it arrives transitively, pinned by `jackson-bom`); the unused `maven-model` / `maven-artifact` and the test-only Mockito dependencies are gone.
- **Version** is `1.0.0-SNAPSHOT` until the first tag.
- **Output**: endpoints with only query-parameter rules no longer bind an unused `req: request.body` alias; response-mode `id` / `field` values are escaped as FEEL strings.
- **Internal**: `Endpoint` (method + path) replaces heading strings as the scanner→renderer key; the single-implementation `ValidationRuleBuilder` interface and an unused rule-builder seam are removed; `RequiredFieldsExtractor` threads its recursion state through a `Traversal` record; FEEL string escaping lives only in `FeelString`; test-only constructors and constants are removed from production code.
- **Repository layout**: screenshots moved to `docs/images/`; the consumer-side BPMN drift test moved to `docs/examples/` (it never ran as part of this build).
- **`OpenApiOperationScanner`** (internal) now yields `OperationInputs` — body schema plus required query parameters — per endpoint instead of a bare schema; `FEELRuleGenerator` gained `createQueryParameterRule`.
- **Internal classes reorganized** under `com.consid.automation.camunda.internal.{openapi,model,feel}`; only `FEELValidationGenerator`, its `Builder`, and the Mojo are part of the public API.
- **Repository flattened** from a multi-module reactor to a single root module.
- **Dependency versions** managed via BOMs (`jackson-bom`, `junit-bom`, `mockito-bom`).
- **Coverage gate** raised: instruction / line / method coverage now ≥ 90%, branch coverage ≥ 80% (current: 96% / 96% / 97% / 85%).
- **CI workflow** now runs `mvn verify` instead of `mvn package`, so the coverage gate is enforced on every PR.

### Removed

- **`example/` module** (replaced by the README quick-start example).

## [Initial commit]

Initial scaffold and first generator implementation. Not published.
