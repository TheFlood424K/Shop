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

1. Place a sign onto the chest/in front of it and enter the following details
```
[Shop]
amount of item
price of item
buy/sell/barter
```
2. Punch the shop with the item you want to buy/sell

**`config.yml` Defaults:**

```yaml
creationMethod:
  placeSign: true # Allow shops to be created by placing a sign down
```

## Create using a Sign & Creative Selection

1. Place a sign onto the chest/in front of it and enter the following details
```
[Shop]
amount of item
price of item
buy/sell/barter
```
2. Punch the shop with an empty hand to enter creative selection
3. Select the item from the creative menu and drop it outside the creative menu

**`config.yml` Defaults:**

```yaml
creationMethod:
  placeSign: true # Allow shops to be created by placing a sign down

allowCreativeSelection: true # This will allow players to use the limited creative selection tool to choose shop items
```