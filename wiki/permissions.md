# Shop v2.0 Permissions

Shop permissions are unchanged in structure from earlier releases, but the v2.0 permission model is evaluated on PaperMC 26.2+, DivineMC, and Folia.

## Enable Permission Checks

Set `usePermissions` to `true` in `config.yml`:

```yaml
usePermissions: true
```

When permission checks are enabled, Shop grants no access by default except to operators and other players who have been given the relevant nodes. When `usePermissions` is `false`, Shop's permission restrictions are disabled.

Protection plugins can impose additional restrictions. For example, WorldGuard region flags may block shop creation or use even when the player has the appropriate Shop permission.

## Available Permissions

```yaml
permissions:
  shop.use:
    description: Allows player to use all shops.
    default: op
  shop.use.sell:
    description: Allows player to use selling shops.
    default: op
  shop.use.buy:
    description: Allows player to use buying shops.
    default: op
  shop.use.barter:
    description: Allows player to use barter shops.
    default: op
  shop.use.combo:
    description: Allows player to use combo buy/sell shops.
    default: op
  shop.use.gamble:
    description: Allows player to use gambling shops.
    default: op

  shop.create:
    description: Allows player to create all shops.
    default: op
  shop.create.sell:
    description: Allows player to create selling shops.
    default: op
  shop.create.buy:
    description: Allows player to create buying shops.
    default: op
  shop.create.barter:
    description: Allows player to create barter shops.
    default: op
  shop.create.combo:
    description: Allows player to create combo shops that both buy and sell.
    default: op
  shop.create.gamble:
    description: Allows player to create admin gamble shops. Only give this permission to Operators!
    default: op

  shop.destroy:
    description: Allows player to destroy their own shops.
    default: op
  shop.destroy.other:
    description: Allows player to destroy other player's shops.
    default: op

  shop.buildlimit.#:
    description: Restricts the number of shops a player can create. For example shop.buildlimit.10 would limit players to creating 10 shops maximum. If multiple buildlimit permissions exist, ONLY the highest will be used.
  shop.buildlimitextra.#:
    description: Add an additional number of shops to the players build limit. For example shop.buildlimitextra.5 would grant players an additional 5 shops to their limit. If multiple buildlimitextra permissions exist they will ALL be used and added together.

  shop.setdisplay:
    description: Allows player to set the display type on their shop (allows shop display cycling, see actionMappings in config.yml).
    default: op

  shop.gui.teleport:
    description: Allows player to use teleport to shops by using the gui.
    default: op

  shop.operator:
    description: Allows access to operator actions like setting currency, setting the gamble item, reloading configs, creating gamble/admin shops, etc.
    default: op
```

## Permission Groups

### Players

Grant only the actions your community should have:

```yaml
shop.use:
shop.use.buy:
shop.use.sell:
shop.create:
shop.create.buy:
shop.create.sell:
shop.destroy:
```

### Shop Managers

Add creation and administration rights:

```yaml
shop.use:
shop.create:
shop.destroy:
shop.setdisplay:
shop.gui.teleport:
```

### Operators

Grant `shop.operator` only to trusted staff. It enables sensitive actions such as reloading configuration, changing currency, setting gamble items, and creating operator-controlled admin shops.

## Dynamic Build Limits

`shop.buildlimit.#` and `shop.buildlimitextra.#` are templates rather than literal nodes:

- `shop.buildlimit.10` sets a maximum of 10 shops.
- If several `shop.buildlimit` nodes exist, Shop uses only the highest value.
- `shop.buildlimitextra.5` adds five shops beyond the normal limit.
- If several `shop.buildlimitextra` nodes exist, Shop adds all of their values together.

These limits supplement any world or other configuration-based restrictions.

## v2.0 Compatibility Notes

- Permission checks are independent of Folia scheduling; the same nodes apply on every supported server.
- WorldGuard, Towny, GriefPrevention3D, LWC, and other protection plugins may require their own permissions in addition to Shop's nodes.
- VaultUnlocked and the selected economy provider may require separate economy permissions.
- GeyserMC/Floodgate players use the same Shop permission nodes as Java Edition players.
- Reload configuration after changing `usePermissions` so the new mode is applied consistently.

For protection-related behavior, see [WorldGuard integration](worldguard.md) and [Trust Players](trust-players.md).
