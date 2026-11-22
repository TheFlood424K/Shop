---
applyTo: "core/src/main/java/com/theflood424k/shoprevanced/integration/**/*.java,core/src/main/resources/plugin.yml,core/src/main/resources/config.yml,core/src/test/java/com/theflood424k/shoprevanced/integration/**/*.java"
---

# Integration, configuration, and plugin metadata instructions

- Treat each optional integration as one contract: Maven API dependency, runtime detection, `plugin.yml` soft-dependency metadata, configuration key and default, listener construction and registration, shutdown/unregistration, and absent-plugin fallback must agree.
- Keep optional integration APIs `provided`. Keep plugin detection runtime-based and isolate reflective access so the plugin still starts when an optional integration is absent or incompatible.
- Keep `plugin.yml` soft-dependency names aligned with the plugin names used by runtime detection. Do not turn an optional integration into a hard runtime dependency without an explicit requirement.
- Use one exact configuration key path for each enablement decision. Initialization and listener-side checks must not read differently named or differently defaulted keys.
- Register a listener only after its integration is enabled and available. Unregister it on shutdown or disable, and ensure delayed initialization cannot register a listener after teardown.
- Preserve graceful degradation: absence of an optional plugin must not prevent plugin startup or unrelated Shop features from working.
- Keep Folia-sensitive integration callbacks in the scheduler context that owns the affected entity, world, block, or region.
- Add focused integration tests for changed enabled, disabled, and absent-plugin paths where feasible. Include metadata/config parity or listener-lifecycle checks when those contracts change.
- Update directly affected user documentation and dependency metadata together; do not document a behavior that detection or lifecycle does not implement.
