There are multiple ways you can create a shop! You can alter configuration to disable creation modes if desired.

1. Shift+Punch a chest with your desired item in hand
2. Shift+Punch with an empty hand, select item from creative menu
3. Place a sign on a chest with your shop details, punch chest with desired item

## Chest vs sign interaction (important)

- **If clicking a shop chest opens the container**: that click did **not** buy/sell anything. This can happen if your server uses the Shop v1.11 **Trust Players** feature (Bolt/BlockProt trust integrations).
- **To buy/sell normally**: interact with the **shop sign** (your server may customize the exact click type via `config.yml` `actionMappings.*`).

More details: **[Trust Players](https://github.com/snowgears/shop/wiki/Trust-Players)**.

# Simple Create
## Create using item

1. Shift+Punch a chest with the item you want to buy/sell in your hand
2. Enter the Shop type you are creating into chat and send it
3. Enter the amount of the item you want to buy/sell
4. Enter the price you want to buy/sell the item for

> **Started a shop by accident?** While a chest-based creation is in progress the chest is protected from being broken. If you actually wanted to remove the chest (for example while tearing down a storage room), just **break the chest again** after the warning to cancel the creation; the chest will break on that second hit.

**`config.yml` Defaults:**

```yaml
creationMethod:
  hitChest: true # Allow shops to be created by punching the chest
```

## Create using Creative Selection

1. Shift+Punch a chest with an empty hand
2. Select the item you want to buy/sell from the creative menu and drop it outside the creative window
3. Enter the Shop type you are creating into chat and send it
4. Enter the amount of the item you want to buy/sell
5. Enter the price you want to buy/sell the item for

**`config.yml` Defaults:**

```yaml
creationMethod:
  hitChest: true # Allow shops to be created by punching the chest

allowCreativeSelection: true # This will allow players to use the limited creative selection tool to choose shop items
```

# Sign Create
## Create using a Sign & Item in hand

1. Place a **Wall Sign** on the chest block (attach the sign directly to the chest face) and enter the following details:
```
[Shop]
amount of item
price of item
buy/sell/barter
```
2. Punch the shop sign with the item you want to buy/sell

**`config.yml` Defaults:**

```yaml
creationMethod:
  placeSign: true # Allow shops to be created by placing a sign down
```

## Create using a Sign & Creative Selection

1. Place a sign and enter the following details:
   - **Wall Sign** (placed ON the chest block): Attach the sign directly to the chest face
   - **Sign Post** (freestanding on ground in front of chest): Place a sign on the ground one block away from the chest, facing the chest
   - Both sign types work! The plugin automatically converts sign posts to wall signs on creation.
```
[Shop]
amount of item
price of item
buy/sell/barter
```
2. Punch the shop sign with an empty hand to enter creative selection
3. Select the item from the creative menu and drop it outside the creative menu

**`config.yml` Defaults:**

```yaml
creationMethod:
  placeSign: true # Allow shops to be created by placing a sign down

allowCreativeSelection: true # This will allow players to use the limited creative selection tool to choose shop items
```
---

# Transaction History

`/transactions` (alias `/tx`) shows **your own** sales and purchases at your shops.

The command only ever reports the transactions of whoever ran it. There is no selector for another
player — browsing someone else's history is not a permission you can be granted, because the
argument to do it does not exist.

**Requires database logging.** With `logging.type: 'OFF'` the command reports no transactions; that
means the log is switched off, not that you have no sales.

## Selectors

Selectors combine freely, in any order:

| Selector | Meaning | Example |
|----------|---------|---------|
| `t:<time>` | Time frame (default `24h`) | `t:3d`, `t:1h`, `t:30m`, `t:90s` |
| `a:<type>` | Filter by shop type | `a:sell`, `a:buy` |
| `i:<items>` | Only these items | `i:stone,dirt` |
| `e:<items>` | Everything except these | `e:stone` |
| `u:<name>` | Only this customer | `u:Steve` |
| `s:<sort>` | Sort order | `s:value`, `s:purchases`, `s:quantity`, `s:all` |

## Examples

```
/tx                        last 24 hours, everything
/tx t:7d a:buy             your purchases over the last week
/tx top s:value            your biggest earners by total value
/tx i:stone s:quantity     only stone, most units first
/tx page 2                 the next page of the last result
/tx #verbose t:1h          every transaction, without grouping repeats
```

Consecutive sales of the same item at the same shop are grouped into one line with a count. Add
`#verbose` to list them individually.

`top` ranks your shops by earnings. It does not support `a:barter`, since barter trades have no
common currency to rank by.
