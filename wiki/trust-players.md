# Trust Players (Shop v1.11)

## Summary (what this feature is)

**Trust Players** lets your server’s protection plugin decide whether a *non-owner* is allowed to open a shop’s container (chest/barrel/etc.).

- **If you’re trusted** by the protection plugin:
  - Clicking the shop’s **container** opens it normally.
  - **Shop does not run any click actions or transactions for that container click.**
  - You’ll see the "trusted open" message.
- **If you’re not trusted**:
  - Shop behaves normally for non-owners: it blocks opening the container and runs Shop’s configured interaction logic (permissions + click actions).

This feature exists to reduce friction in shared bases: you can be trusted to open your friend’s protected chest, while still needing to use the **shop sign** to buy/sell.

## Supported protection plugins

### Bolt

- **Requirements**:
  - Bolt must be installed and detected by Shop at startup.
  - `bolt.trustIntegration.enabled` must be `true` in `config.yml` (default is `true`).
- **What Shop checks**:
  - The shop container must be protected by Bolt.
  - Bolt must report the player can open/access the container (Bolt access check for opening).
- **How to become trusted**:
  - Use Bolt’s own protection/trust/friends system. (Shop does not provide Bolt commands.)

### BlockProt

- **Requirements**:
  - BlockProt must be installed and detected by Shop at startup.
  - `blockProt.trustIntegration.enabled` must be `true` in `config.yml` (default is `true`).
- **What Shop checks**:
  - The shop container must be protected by BlockProt.
  - BlockProt must report the player has access (owner or allowed friend/access).
- **How to become trusted**:
  - Use BlockProt’s own protection/trust/friends system. (Shop does not provide BlockProt commands.)

## How to use (as a player)

### Buying/selling normally

If a shop’s container opens when you click it, **that click did not buy/sell anything** — it was treated as a trusted container-open.

To buy/sell from the shop, interact with the **shop sign** using your server’s configured Shop action mapping (by default, Shop uses sign clicks for transactions).

### What you’ll see when trusted

- **Message key (admin configurable)**: `interaction.openTrusted` (in `chatConfig.yml`)
- **Default message text**:
  - `&7You have been trusted to open this shop by [owner].`

## How to configure (as an admin)

### Enable/disable toggles (config.yml)

These toggles only matter if the external plugin is installed. If the plugin is not present, Shop won’t register the integration listeners and the setting has no effect.

```yaml
bolt:
  trustIntegration:
    enabled: true

blockProt:
  trustIntegration:
    enabled: true
```

After changing `config.yml`, reload Shop (or restart the server) so the integration enable/disable is applied.

### Customize the player messages (chatConfig.yml)

**Trusted open message**

```yaml
interaction:
  openTrusted: "&7You have been trusted to open this shop by [owner]."
```

**Creation denied on protected container (other owner)**

This message is used when Shop denies creating a shop on a container that is protected by another player (per the protection plugin integration).

```yaml
interaction_issue:
  createOtherPlayer: "&cYou are not allowed to create a shop on this chest."
```

## FAQ / Troubleshooting

### “I clicked the chest and it opened instead of buying/selling”

- If you saw the trusted message (`interaction.openTrusted`), this is working as designed: **Shop did not run a transaction for that click**.
- Close the container and interact with the **shop sign** to buy/sell.
- If your server changed default controls, ask an admin which interaction mapping is used (see `config.yml` `actionMappings.*`).

### “I’m trusted, but it still doesn’t open”

Check these in order:

- **Plugin installed**: Is the protection plugin (Bolt/BlockProt) actually installed on the server?
- **Integration enabled**: Is the correct config toggle enabled?
  - `bolt.trustIntegration.enabled`
  - `blockProt.trustIntegration.enabled`
- **Container is protected**: Both integrations only apply if the container is protected by that plugin.
- **You’re trusted in that plugin**: You must be granted access by the protection plugin’s own system.
- **Another plugin may be denying open**: If some other plugin cancels the interaction, Shop can’t force the container to open.

### “I can’t create a shop on this chest”

If you see `&cYou are not allowed to create a shop on this chest.` (key: `interaction_issue.createOtherPlayer`), the container is protected by another player per the detected protection plugin integration.

Fixes:
- Create the shop on a container you own, or
- Transfer ownership / grant access using your protection plugin, or
- Remove the protection from that container (server policy permitting), or
- Disable the relevant integration if you do not want Shop to respect that plugin for shop creation.

### “I disabled the integration but it still acts the same”

Common causes:

- **Shop wasn’t reloaded/restarted** after changing `config.yml`.
- You’re testing as the **shop owner** (owners can open their own shop containers normally).
- You’re testing as a **Shop operator** (players with `shop.operator`, or server OP when permissions are off). Depending on the shop type, Shop may allow operators to open other players’ shop containers via Shop’s own logic.
