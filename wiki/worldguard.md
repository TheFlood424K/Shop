# WorldGuard Integration — Shop v2.0

Shop integrates with WorldGuard to control where players can create and use shops. The integration is active when WorldGuard is installed, Shop's `worldGuard.enabled` option is `true`, and the relevant WorldGuard flags are available.

The v2.0 integration runs on PaperMC 26.2+, DivineMC, and Folia. On Folia, use a WorldGuard build compatible with your server software.

## Default Behavior

With the default v2.0 configuration:

- Players must pass the configured creation flags before they can create a shop.
- The default creation rules require `BUILD` and `CHEST_ACCESS` to be allowed, or permit creation through `PASSTHROUGH`.
- Players can use a shop unless the `USE` flag denies them.
- Server operators and players with `shop.operator` bypass WorldGuard checks when Shop permissions are enabled.
- Shop's own [permissions](permissions.md) can still restrict a player independently of WorldGuard.

## The `allow-shop` Flag

Use `requireAllowShopFlag` when shops should exist only in explicitly approved regions.

```yaml
worldGuard:
  enabled: true
  requireAllowShopFlag: true
```

When enabled:

1. Shop registers the `allow-shop` state flag with WorldGuard during startup.
2. A player can create a shop only where the region grants `allow-shop: allow`.
3. The normal creation flag checks still apply; `allow-shop` does not replace them.

Set the flag in a region:

```text
/rg flag <region> allow-shop allow
```

Shop also recognizes an existing `allow-shop` flag registered by another plugin. If that flag uses the older boolean form, Shop logs a compatibility warning; migrate the region to the `allow`/`deny` state flag when possible.

## How Flag Checks Work

Shop evaluates each configured list in priority order:

1. **Hard allow flags** — if any listed state flag is `ALLOW`, the action is immediately allowed.
2. **Deny flags** — if any listed state flag is `DENY`, the action is blocked.
3. **Allow flags** — if any listed state flag is `ALLOW`, the action is allowed.
4. **Default action** — if no previous rule matched, Shop uses `ALLOW` or `DENY` from the configuration.

Creation and shop use have separate rule sets.

## Default Configuration

```yaml
worldGuard:
  enabled: true
  requireAllowShopFlag: false

  createShopFlagChecks:
    hardAllowFlags: []
    denyFlags:
      - 'BUILD'
      - 'CHEST_ACCESS'
    allowFlags:
      - 'PASSTHROUGH'
      - 'BUILD'
    defaultAction: 'DENY'

  useShopFlagChecks:
    hardAllowFlags: []
    denyFlags:
      - 'USE'
    allowFlags: []
    defaultAction: 'ALLOW'
```

### What the Defaults Mean

- **Creation:** a player is denied when `BUILD` or `CHEST_ACCESS` is denied. Creation is allowed when `PASSTHROUGH` or `BUILD` is allowed. If no flag decides the result, `DENY` applies.
- **Use:** a player is blocked when `USE` is denied. If no flag decides the result, `ALLOW` applies.
- **`allow-shop`:** not required unless `requireAllowShopFlag` is enabled.

## Common WorldGuard Flags

Only WorldGuard state flags can be used in these lists. Common choices include:

- `BUILD` — place or break blocks
- `CHEST_ACCESS` — open containers
- `USE` — use doors, buttons, and similar interactions
- `PASSTHROUGH` — bypass region protection
- `NO_SHOPS` — a common custom deny flag

See the [WorldGuard flag documentation](https://worldguard.enginehub.org/en/latest/regions/flags/) for the complete list.

## Custom Rules

Custom flags registered by other plugins can be used when they are WorldGuard state flags.

```yaml
worldGuard:
  enabled: true
  requireAllowShopFlag: false

  createShopFlagChecks:
    hardAllowFlags: ['VIP_SHOPS']
    denyFlags: ['BUILD', 'NO_SHOPS']
    allowFlags: ['SHOP_ZONE']
    defaultAction: 'DENY'

  useShopFlagChecks:
    hardAllowFlags: ['VIP_SHOPS']
    denyFlags: ['USE', 'NO_SHOP_USE']
    allowFlags: []
    defaultAction: 'ALLOW'
```

### Example: Creation Only in Shop Zones

```yaml
worldGuard:
  requireAllowShopFlag: true
  createShopFlagChecks:
    hardAllowFlags: []
    denyFlags:
      - 'BUILD'
      - 'CHEST_ACCESS'
    allowFlags:
      - 'SHOP_ZONE'
    defaultAction: 'DENY'
```

Grant `allow-shop` only in the intended regions and use `SHOP_ZONE` or another state flag to control where players may build.

## Troubleshooting

### Shop creation is blocked

1. Run `/rg flags <region>` and review `BUILD`, `CHEST_ACCESS`, `PASSTHROUGH`, and any custom flags.
2. Confirm `worldGuard.enabled` is `true`.
3. If `requireAllowShopFlag` is enabled, confirm the region has `allow-shop: allow`.
4. Check the startup log for missing or conflicting flags.
5. Confirm the player is not being blocked by another plugin such as GriefPrevention3D, Towny, LWC, or a container-protection plugin.

### Shop use is blocked

1. Check the `USE` state in the region.
2. Check `useShopFlagChecks.denyFlags` and `defaultAction`.
3. Confirm that another plugin is not cancelling the sign interaction.
4. Test with `shop.operator` to determine whether the problem is region-specific.

### A flag is reported as missing

- Confirm the spelling and capitalization match the registered WorldGuard flag.
- Confirm the flag is a state flag rather than a boolean flag.
- Confirm the plugin that registers the flag loaded before Shop registered its own `allow-shop` flag.
- Restart after changing the flag list so Shop can rebuild its flag cache.

### The `allow-shop` flag conflicts with another plugin

Shop reuses an existing `allow-shop` state flag when one is already registered. If another plugin registered an incompatible flag type, Shop cannot enforce the flag and logs an error. Rename or replace the conflicting flag in the other plugin.

## v2.0 Compatibility Notes

- WorldGuard checks are based on the player's location and region membership, so behavior is consistent on PaperMC, DivineMC, and Folia.
- Operator and `shop.operator` bypass behavior is part of Shop's integration, not a WorldGuard region setting.
- The integration is separate from Shop's general world blacklist and item restrictions.
- GriefPrevention3D can add claim-based creation, build, and access checks; having a valid WorldGuard region does not override a GriefPrevention3D denial.
- The Trust Players feature for Bolt, BlockProt, and CashierLock handles protected container access separately from WorldGuard shop use.

