# Language Configuration Files — Shop v2.0

Shop's text system lets server administrators localize messages, sign formats, item descriptions, and floating shop labels. In v2.0, formatted text is rendered through Kyori Adventure Components while preserving legacy `&` formatting for existing configurations.

## Configuration Files

Shop uses three language files in its data folder:

1. **`chatConfig.yml`** — transaction results, errors, commands, hover text, and shop interaction messages
2. **`signConfig.yml`** — creation keywords, sign layouts, stock colors, and shop text
3. **`displayConfig.yml`** — floating labels shown above shop containers

These files are separate from the main `config.yml`, which controls plugin behavior.

## Text Formatting in v2.0

Shop supports the following styles:

- **Legacy color and formatting codes** such as `&a`, `&l`, and `&r`
- **Legacy hexadecimal colors** such as `#FF00FF`
- **MiniMessage tags** such as `<red>`, `<bold>`, and `<#FF0000>`

Shop detects a string that contains MiniMessage-style tags and parses it as MiniMessage. Strings without those tags use the legacy code parser. Use one formatting style consistently within a message; do not mix `&` codes and MiniMessage tags in the same string.

### Legacy Color Codes

| Code | Meaning | Code | Meaning |
| --- | --- | --- | --- |
| `&0` | Black | `&8` | Dark gray |
| `&1` | Dark blue | `&9` | Blue |
| `&2` | Dark green | `&a` | Green |
| `&3` | Dark aqua | `&b` | Aqua |
| `&4` | Dark red | `&c` | Red |
| `&5` | Dark purple | `&d` | Light purple |
| `&6` | Gold | `&e` | Yellow |
| `&7` | Gray | `&f` | White |

Legacy formatting codes:

| Code | Meaning |
| --- | --- |
| `&k` | Obfuscated |
| `&l` | Bold |
| `&m` | Strikethrough |
| `&n` | Underlined |
| `&o` | Italic |
| `&r` | Reset color and formatting |

For example:

```yaml
message: '&aPurchase complete! &7You paid &e[price]'
```

### Legacy Hex Colors

Use six-digit RGB colors:

```yaml
message: '&#FF00FFA legendary item costs &e[price]'
```

or without the legacy ampersand prefix:

```yaml
message: '#FF00FFA legendary item costs &e[price]'
```

### MiniMessage

Adventure's MiniMessage format is supported in v2.0:

```yaml
message: '<red>Purchase complete!</red> <gray>You paid <yellow>[price]</yellow></gray>'
```

Common examples:

```yaml
message: '<bold><gradient:#FF0000:#FFAA00>Limited offer</gradient></bold>'
message: '<italic>Click the shop sign to buy.</italic>'
message: '<dark_gray>Shop: <white>[item]</white>'
```

MiniMessage supports Adventure's standard color, decoration, hover, and click tags. Shop's own interactive placeholders, such as `[location]` and `[item]`, provide the built-in click or hover behavior described below.

### Formatting Persistence and Reset

Legacy formatting codes continue to affect the rest of the string until another color is selected or `&r` is used:

```yaml
# Everything after &l is bold.
message: '&l&cThis entire message stays bold and red'

# Formatting stops at &r.
message: '&l&cThis part is bold and red.&r This part is normal.'
```

For MiniMessage, close or reset tags explicitly:

```yaml
message: '<bold><red>Warning</red></bold> Normal text'
```

## Placeholders

Placeholders use square brackets, for example `[item]` or `[price]`. Shop replaces them with live shop, transaction, player, or inventory data before sending the Adventure Component.

### Item Placeholders

- `[item]`
- `[item amount]`
- `[item type]`
- `[item enchants]`
- `[item lore]`
- `[item durability]`
- `[currency item]`
- `[gamble item]`
- `[gamble item amount]`

### Barter Placeholders

- `[barter item]`
- `[barter item amount]`
- `[barter item type]`
- `[barter item enchants]`
- `[barter item lore]`
- `[barter item durability]`

### Price and Stock Placeholders

- `[price]`
- `[price per item]`
- `[price sell]`
- `[price sell per item]`
- `[price combo]`
- `[amount]`
- `[stock]`
- `[stock color]`
- `[currency name]`

### Player and Shop Placeholders

- `[user]`
- `[player]`
- `[owner]`
- `[shop type]`
- `[shop types]`
- `[user amount]`
- `[build limit]`
- `[world]`
- `[location]`
- `[server name]`
- `[plugin]`
- `[total shops]`
- `[tp time remaining]`

### Notification and Offline Transaction Placeholders

- `[notify user]`
- `[notify owner]`
- `[notify stock]`
- `[offline transactions]`
- `[offline profit]`
- `[offline spent]`
- `[offline items sold]`
- `[offline items bought]`
- `[shops out of stock]`

### Interactive Placeholders

Some placeholders automatically add Adventure interaction data:

- `[location]` can provide hoverable shop information.
- `[item]` and `[barter item]` can provide item hover information.
- `[offline transactions]` can provide a hover list of recent transactions.

Placeholders are case-insensitive, but the canonical spelling shown above is recommended for maintainability. Keep brackets and placeholder names intact when translating text.

## Message Organization

Messages use structured keys so Shop can select the correct text for each shop type and context:

- **Transaction messages:** `{shopType}_{recipient}`, such as `SELL_user`
- **Error messages:** `{shopType}_{errorType}`, such as `SELL_shopNoStock`
- **Interaction messages:** creation, destruction, and container-open feedback
- **Permission messages:** separate use, create, destroy, and build-limit responses

## `chatConfig.yml`

Controls messages sent through chat and GUI interactions.

### Key sections

- `transaction:` — successful purchases, sales, barters, combo transactions, and gambles
- `transaction_issue:` — insufficient funds, missing stock, missing space, and other failures
- `interaction:` — creation, initialization, destruction, admin access, and trusted-open messages
- `interaction_issue:` — creation and interaction errors
- `description:` — item descriptions shown for each shop type
- `permission:` — permission-denied messages
- `creativeSelection:` — creative item-selection prompts
- `guiSearchSelection:` — GUI search prompts
- `hover:` — hover text for shop information
- `command:` — command help and command feedback
- `transaction.OFFLINE_TRANSACTIONS_NOTIFICATION:` — offline purchase summaries
- `stock_color` values in `signConfig.yml` — in-stock and out-of-stock sign colors

Example trusted-open message:

```yaml
interaction:
  openTrusted: '&7You have been trusted to open this shop by [owner].'
```

## `signConfig.yml`

Controls creation keywords and the text placed on shop signs.

### Key sections

- `sign_creation:` — keywords used during sign and guided creation
- `sign_text:` — normal, admin, and no-display layouts for each shop type
- `stock_color.in_stock` and `stock_color.out_of_stock` — stock-state colors
- `sign_text.zeroPrice` — text used for a free shop
- `sign_text.adminStock` — text used for unlimited admin stock
- `sign_text.serverDisplayName` — server name used by owner/admin placeholders

Sign lines are length-limited. Keep them concise and test every shop type after changing a layout.

## `displayConfig.yml`

Controls floating labels displayed above shop containers.

### Key sections

- `display_tag_text:` — normal labels for buy, sell, barter, combo, and gamble shops
- `targetMaxLength` — target width used when Shop splits a long label

The legacy `[lshift]` and `[rshift]` markers can still be used to position side-by-side content.

## Adventure Components in v2.0

- Chat output is represented internally as an Adventure `Component`.
- Legacy `&` and hex colors remain supported for existing configurations.
- MiniMessage provides richer colors, gradients, decorations, hover text, and click actions.
- Shop-specific placeholders remain available in both formatting styles.
- If an interactive component cannot be sent, Shop falls back to serialized legacy text so the player still receives the message.

Avoid using literal angle brackets in translated values unless they are intentional MiniMessage tags. Test custom player-name, item-name, and economy-format combinations after changing the text pipeline.

## Applying Changes

1. Save the edited YAML file.
2. Restart the server for the safest result, or run `/shop reload` if your permission and command configuration allows it.
3. Test one successful transaction, one failed transaction, and one message of each shop type.
4. Check signs, floating labels, hover text, and clickable actions separately.

The reload path is compatible with PaperMC 26.2, DivineMC, and Folia. A restart is still recommended when changing structural sign layouts or when a live server has many active shops.

## Translation and Style Tips

- Translate only text outside placeholder brackets.
- Keep placeholder names unchanged.
- Use the same formatting style throughout one message.
- Keep sign text short enough for the sign layout.
- Use Unicode symbols such as `✦`, `❖`, or `•` sparingly for visual separation.
- Use YAML's `|-` block form for multi-line values.
- Avoid embedding untrusted player-provided text as MiniMessage tags.

## Troubleshooting

### Formatting does not appear

- Confirm the file is valid YAML.
- Confirm `&` is the intended legacy prefix and is not escaped by YAML quoting.
- Do not mix legacy codes and MiniMessage tags in one value.
- Reload Shop or restart the server after saving.

### A placeholder is missing

- Confirm the placeholder spelling matches one of the names listed above.
- Confirm the current context supports it; for example, barter-only values are blank outside a barter message.
- Check the server log for a placeholder replacement error.

### MiniMessage displays as plain text

- Confirm both opening and closing tags are valid.
- Confirm there is no stray `<` or `>` in surrounding text.
- Try a simple `<red>test</red>` value to isolate the problem.

### Text renders but hover or click behavior is missing

- Confirm the placeholder used by Shop provides that interaction; ordinary text does not gain hover behavior automatically.
- Check for another plugin or client setting that prevents the interaction.
- Test with a short message that contains only the interactive placeholder.
