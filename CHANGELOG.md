# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Pre-release work toward the project's first public Maven Central artifact. Significant feature, refactor, and packaging changes since the initial commit.

### Added

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
- **`OpenApiOperationScanner`** (internal) now yields `OperationInputs` — body schema plus required query parameters — per endpoint instead of a bare schema; `ValidationRuleBuilder` gained `createQueryParameterRule`.
- **Internal classes reorganized** under `com.consid.automation.camunda.internal.{openapi,model,feel}`; only `FEELValidationGenerator`, its `Builder`, and the Mojo are part of the public API.
- **Repository flattened** from a multi-module reactor to a single root module.
- **Dependency versions** managed via BOMs (`jackson-bom`, `junit-bom`, `mockito-bom`).
- **Coverage gate** raised: instruction / line / method coverage now ≥ 90%, branch coverage ≥ 80% (current: 96% / 96% / 97% / 85%).
- **CI workflow** now runs `mvn verify` instead of `mvn package`, so the coverage gate is enforced on every PR.

### Removed

- **`example/` module** (replaced by the README quick-start example).

## [Initial commit]

Initial scaffold and first generator implementation. Not published.
