# Player Instructions — Shop v2.0

Shop lets you create buy, sell, barter, combo, and gamble shops using a container and sign or a guided chat flow. Your server administrator can disable either creation method and can restrict where or which shop types you may create.

## Before You Start

You will need:

- A supported container, such as a chest or barrel
- The `shop.create.<type>` permission for the shop type you want; some servers grant a broader `shop.create.*` permission
- Permission to build in the area and to open the container
- Access to the required economy or item currency
- Any required region or land-claim permission

If Shop is installed on PaperMC 26.2+, DivineMC, or Folia, normal player instructions are the same on each platform.

## Shop Types

| Type | Behavior |
| --- | --- |
| **Buy** | Other players buy the item from the shop's stock |
| **Sell** | Other players sell the item to the shop |
| **Barter** | Players exchange one item for another |
| **Combo** | The shop can both buy and sell the same item |
| **Gamble** | Players risk the configured price for a random configured result |

The exact creation words can be translated in `signConfig.yml` under `sign_creation.*`.

## Create a Shop

### Option 1: Guided Creation with an Item

1. Hold the item you want to buy or sell.
2. Shift-punch a supported container.
3. Enter the shop type in chat and send it.
4. Enter the item amount.
5. Enter the price and complete the remaining type-specific prompts.

For a barter shop, Shop also asks for the item you want in return.

For a combo shop, Shop asks for both the buy and sell prices. For a gamble shop, follow the prompts for the amount and price; the random result is configured by the server.

Default `config.yml` settings:

```yaml
creationMethod:
  hitChest: true
```

### Option 2: Guided Creation with Creative Selection

1. Shift-punch a supported container with an empty hand.
2. Select the item from Shop's limited creative menu and drop it outside the creative window.
3. Enter the shop type in chat and send it.
4. Enter the amount, price, and any remaining type-specific answers.

Default `config.yml` settings:

```yaml
creationMethod:
  hitChest: true
allowCreativeSelection: true
```

Creative selection is a server-authorized item chooser; it does not give the player an unrestricted creative inventory.

### Option 3: Sign Creation with an Item

1. Place a sign on the front of the supported container.
2. Enter the shop details:

   ```text
   [Shop]
   amount of item
   price of item
   buy/sell/barter/combo/gamble
   ```

3. Hold the item you want to trade and punch the sign.
4. Complete any type-specific prompts, such as the item requested for a barter or the second price for a combo shop.

Default `config.yml` settings:

```yaml
creationMethod:
  placeSign: true
```

### Option 4: Sign Creation with Creative Selection

1. Place a sign on the container and enter the same four lines shown above.
2. Punch the sign with an empty hand to open creative selection.
3. Select the item and drop it outside the creative window.
4. Complete any type-specific prompts.

Default `config.yml` settings:

```yaml
creationMethod:
  placeSign: true
allowCreativeSelection: true
```

## Using a Shop

Interact with the **shop sign** to buy, sell, barter, gamble, or choose the side of a combo shop. The server can customize the required click or interaction under `actionMappings.*` in `config.yml`.

Opening the shop's container is not normally a transaction. If a protection plugin trusts you to open the container, Shop treats that click as a container open and sends the `interaction.openTrusted` message. Close the container and use the shop sign to trade.

See [Trust Players](trust-players.md) for Bolt, BlockProt, and CashierLock details.

## Canceling a Creation

While guided chest-based creation is in progress, the container is temporarily protected from being broken. If you started by mistake and want to remove it, break it again after Shop's warning; the second break cancels creation and breaks the container.

You can also finish the chat prompts or ask an administrator to cancel/reset an incomplete shop if your server provides a command for that.

## Bedrock Players

Shop v2.0 detects Floodgate players connected through GeyserMC. Bedrock players can use supported Shop features when the server, GeyserMC, and Floodgate are configured correctly.

- Use the sign interaction that matches your client and the server's `actionMappings` configuration.
- If your Bedrock client cannot perform a punch action in the same way as Java Edition, use the other enabled creation method or ask an administrator for help.
- Shop does not change the transaction rules based on whether a player joined through Floodgate.

## Troubleshooting

### The container will not start a shop

- Confirm you have the required `shop.create.<type>` permission.
- Confirm the container type is enabled and you may build in the area.
- Check whether WorldGuard, GriefPrevention3D, Towny, LWC, or another protection plugin denied the action.
- Ask an administrator to review the relevant integration and permission configuration.

### The chat prompts reject my answer

- Enter a valid positive item amount.
- Enter a valid price for your configured currency.
- Use the creation word expected by your server's `signConfig.yml` translations.
- For barter and combo shops, complete each type-specific prompt before continuing.

### Clicking the container does not buy or sell

Use the shop sign instead. If the container opened normally, the click was treated as a trusted open rather than a Shop action.

### The shop is not initialized

Shop removes an incomplete shop after the configured shop-creation timeout. Recreate it and complete every prompt before the timer expires.

### A message is missing or incorrectly formatted

Ask the server administrator to check `chatConfig.yml` and `signConfig.yml`. Shop v2.0 supports Adventure Components with legacy `&` codes and MiniMessage formatting.
