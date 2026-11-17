# Trust Players — Shop v2.0

## Overview

**Trust Players** lets a protection plugin decide whether a non-owner can open a shop container without treating that click as a Shop transaction.

- When the protection plugin grants access, the container can open normally and Shop sends the `interaction.openTrusted` message.
- A trusted container-open does **not** run a Shop purchase, sale, barter, combo, gamble, or other click action.
- To trade, interact with the **shop sign** using the server's configured Shop action mapping.
- When access is not granted, Shop's normal non-owner handling applies: the container click is blocked and Shop runs its configured interaction logic.

This is useful in shared bases where a player is trusted to inspect a friend's protected chest but should still trade through the shop sign.

## Supported Protection Plugins

### Bolt

Shop uses Bolt's `OPEN` access permission for the protected shop container.

- **Requirements:** Bolt installed and enabled; `bolt.trustIntegration.enabled` is `true`.
- **Trust source:** Bolt's own protection, friend, and access system.
- **Shop behavior:** a protected container that Bolt says the player may open is treated as a trusted open rather than a Shop action.

### BlockProt

Shop checks the BlockProt handler for the protected shop container.

- **Requirements:** BlockProt installed and enabled; `blockProt.trustIntegration.enabled` is `true`.
- **Trust source:** BlockProt's own owner, friend, and container-access records.
- **Shop behavior:** a protected container that BlockProt says the player may access is treated as a trusted open.

### CashierLock

Shop provides a best-effort CashierLock integration for protected containers and shop creation.

- **Requirements:** CashierLock installed and enabled; `cashierLock.trustIntegration.enabled` is `true`.
- **Trust source:** CashierLock's own owner and access system.
- **Shop behavior:** Shop checks whether the container is protected and whether the player has access. Shop also prevents a non-owner from creating a shop on a protected CashierLock container.

CashierLock's API is accessed reflectively to remain compatible with API variations between releases. If its expected API methods are unavailable, check the startup log; Shop cannot use the integration until a compatible API is exposed.

## Player Instructions

### Trading from a shop

If clicking a shop container opens it, that click did not perform a transaction. Close the container and interact with the **shop sign** to buy or sell.

The exact control can be customized under `actionMappings.*` in `config.yml`.

### Trusted-open message

Administrators can customize this message in `chatConfig.yml`:

```yaml
interaction:
  openTrusted: '&7You have been trusted to open this shop by [owner].'
```

## Administrator Configuration

Each integration has an independent switch in `config.yml`:

```yaml
bolt:
  trustIntegration:
    enabled: true

blockProt:
  trustIntegration:
    enabled: true

cashierLock:
  trustIntegration:
    enabled: true
```

These switches matter only when the corresponding plugin is installed. Shop does not register an active integration listener for a missing plugin.

After changing `config.yml`, reload Shop or restart the server so the listener registrations are updated.

### Protected-container creation message

When Shop detects that a player is trying to create a shop on another player's protected container, it uses `interaction_issue.createOtherPlayer`:

```yaml
interaction_issue:
  createOtherPlayer: '&cYou are not allowed to create a shop on this chest.'
```

## Relationship to Other Protections

Trust Players does not replace Shop's other integration checks:

- **WorldGuard** can restrict where shops are created or used.
- **GriefPrevention3D** can restrict claims, building, and container access.
- **Towny**, **LWC**, **Residence**, and other land or container-protection plugins can still deny access.
- Shop's own [permissions](permissions.md) remain in force.

A player may be trusted by Bolt, BlockProt, or CashierLock and still be denied by another plugin. Trust grants container access only; it does not grant permission to create, destroy, or administer a shop.

## v2.0 Compatibility

- The trust listeners run on PaperMC 26.2+, DivineMC, and Folia-compatible server builds.
- Protection plugins must themselves support the server platform and Folia when used on a Folia server.
- Shop v2.0 uses Adventure Components for the trusted-open message, including support for legacy `&` codes and MiniMessage formatting.
- The Bolt, BlockProt, and CashierLock integrations are optional. Shop does not require them at runtime.

## Troubleshooting

### “The chest opened instead of buying or selling”

- If the `interaction.openTrusted` message appeared, this is the trusted-open path by design.
- The container click did not execute a transaction.
- Use the shop sign and the action mapping configured by your server administrator.

### “I am trusted, but the container still does not open”

Check the following:

1. The protection plugin is installed, enabled, and protecting this container.
2. The matching `trustIntegration.enabled` switch is `true`.
3. Shop was reloaded or restarted after the configuration change.
4. Your access/trust record in the protection plugin is current.
5. No other plugin is cancelling the inventory-open event.
6. The protection plugin reports access for the same container Shop recognizes as the shop's container.

### “I cannot create a shop on this chest”

The `interaction_issue.createOtherPlayer` message means the container is protected by another player according to the detected integration. Create the shop on a container you own, transfer ownership, grant appropriate access, or change the protection policy.

For CashierLock, Shop intentionally fails closed during shop creation when protection exists but ownership cannot be established. This prevents another player from creating a shop on an unknown owner's protected container.

### “I disabled the integration, but behavior did not change”

- Reload Shop or restart the server.
- Confirm the configuration path exactly matches the integration (`bolt`, `blockProt`, or `cashierLock`).
- Remember that the protection plugin itself can still deny access when Shop's integration is disabled.
- Test with a non-owner; shop owners and operators follow separate paths.

