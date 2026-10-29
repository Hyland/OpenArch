# Bootstrap content models

Bootstrap and test models use the same JSON representation as the content-model REST API:
`schemas`, `types`, `mixinTypes`, and `primaryTypes`, each keyed by name. A file can contain
any subset of those sections. Do not include the API's optimistic-locking `version`.
The complete core bootstrap model is in `openarch-core/src/main/resources/model/bootstrap-model.json`.

Register resources with TypeService's `model` extension point. Name JSON Patch resources
with the `.patch.json` suffix:

```xml
<component name="example.model">
  <require>com.hyland.openarch.ecm.core.schema.bootstrap</require>
  <extension target="com.hyland.openarch.ecm.core.schema.TypeService" point="model">
    <model src="model/example.json" />
    <model src="model/example.patch.json" patch="true" />
  </extension>
</component>
```

For example, `model/example.json` can contribute an independent schema and document type:

```json
{
  "schemas": {
    "article": {
      "fields": {
        "art_author": { "type": "string" }
      }
    }
  },
  "primaryTypes": {
    "Article": {
      "extends": "SysFile",
      "schemas": ["article"]
    }
  }
}
```

The field prefix is part of each top-level field name; `prefix` is informational.
Reusable named value types belong in `types`. Mixins belong in `mixinTypes`, with
`schemas` and optionally `noPerDocumentQuery: true`.

## Composition

All fragments are merged first, then all RFC 6902 JSON patches are applied in contribution
order. References are resolved only after composition, so a fragment can refer to definitions
in another fragment regardless of registration order. Duplicate names are rejected, even
when their definitions are identical. Define shared value types once in a common contribution
and reference them from other fragments. Use patches for intentional replacements.

Patches use the same paths and values as the model API:

```json
[
  {
    "op": "add",
    "path": "/primaryTypes/SysFile/mixins/-",
    "value": "MyMarker"
  }
]
```

Declare `MyMarker` in a fragment first. Use component `<require>` dependencies when a patch
depends on another component's patch. An array must exist before appending with `/-`;
use `add` with the whole array when introducing it. `add` to an existing object member
replaces that member; `replace` requires it to exist. JSON Pointer escapes `/` as `~1`
and `~` as `~0`.

There is no implicit recursive merge, XML `append`, or `enabled="false"` behavior. Remove
references as well as definitions when deleting schemas or mixins. Missing references,
malformed resources, conflicting definitions and failed patch operations fail bootstrap.
The core bootstrap model explicitly defines `SysContent` with the `system` schema.
As in the public API, primary types without `extends` default to extending `SysContent`;
`SysContent` itself is the root type; specifying `extends` on it is an error. Schemas are never attached
implicitly. Every composed bootstrap model, including schema-only test models, must explicitly
contribute `SysContent`; patches cannot remove it.

Managed field characteristics are supported in the JSON `managed` object and remain
reserved for core system schemas. As with the former XSD bootstrap, an optional resolver
whose bundle is not deployed is logged and omitted during bootstrap. REST model validation
continues to reject unresolved resolvers.

## Tests and existing repositories

Tests deploy the XML resource registration through the existing class- or method-level
`@Deploy` mechanism. Registrations are replayed from scratch on redeployment; patches do not
mutate another test's model and do not need inverse patches.

Bootstrap composition does not call the REST update API or apply its persisted-model diff
restrictions. Repository initialization still loads an existing persisted model, or persists
the composed bootstrap model for a new repository. Bootstrap patches are not migrations for
existing repositories. The existing protected system-schema refresh policy is unchanged.

The former `schema` and `doctype` extension points, XSD resources, and XSD loader have been
removed. All in-repository contributions now register JSON resources at `model`.
