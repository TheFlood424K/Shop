# bStats Metrics — Shop v2.0

Shop v2.0 reports anonymous usage statistics to [bStats.org](https://bstats.org) so the maintainers can understand how the plugin is used across servers. Data is collected per-server and is fully anonymous — no player names, IP addresses, or server identifiers are transmitted.

## What Is Collected

The following custom charts are reported:

| Chart | Type | What It Shows |
| --- | --- | --- |
| Transactions | SingleLine | Recent transaction count |
| Item Volume | SingleLine | Recent item trade volume |
| Shops | SingleLine | Total number of active shops |
| Shop Types | Advanced Pie | Distribution of buy/sell/barter/combo/gamble shops |
| Shop Display Types | Advanced Pie | Distribution of display styles (floating, glass case, etc.) |
| Shop Containers | Advanced Pie | Container types used for shops |
| Economy Type | Simple Pie | Physical item, VaultUnlocked currency, or experience |
| Fractional Currency | Simple Pie | Whether fractional currency amounts are allowed |
| Use Permissions | Simple Pie | Whether permission checks are enabled |
| Allow Partial Sales | Simple Pie | Whether partial sales are allowed |
| Shop Creation Methods | Advanced Pie | Sign creation vs guided chest-punch/chat creation |
| Offline Purchase Notifications | Simple Pie | Whether offline purchase notifications are enabled |
| Shop GUI Enabled | Simple Pie | Whether the shop browsing GUI is enabled |
| Allow Searching Items | Simple Pie | Whether creative item selection is allowed |
| Check Item Durability | Simple Pie | Whether item durability is checked during matching |
| Ignore Item Repair Cost | Simple Pie | Whether anvil repair cost is ignored in item matching |
| Sounds and Effects | Advanced Pie | Configured shop sound and effect settings |
| WorldGuard Enabled | Simple Pie | Whether WorldGuard integration is active |
| Towny Enabled | Simple Pie | Whether Towny integration is active |
| Dynmap Enabled | Simple Pie | Whether Dynmap markers are enabled |
| BlueMap Enabled | Simple Pie | Whether BlueMap markers are enabled |
| Database Type | Simple Pie | H2, MySQL, or MariaDB for transaction logging |
| Item Hover Display Type | Simple Pie | Hover display type for items |
| Hover Text Activation Type | Simple Pie | How hover text is activated |
| Auto Cleanup Dead Shops | Simple Pie | Whether inactive shops are auto-removed |
| Destroy Requires Sneak | Simple Pie | Whether sneaking is required to destroy shops |
| Inverse Combo Shops | Simple Pie | Whether combo shop buy/sell sides are inverted |
| Enabled Containers | Advanced Pie | Which container types are enabled for shops |
| Economic Barriers | Advanced Pie | Creation/destruction cost configuration |
| Display Enhancements | Advanced Pie | Display-related feature flags |
| Transaction Action Mapping | Simple Pie | Click action mapping for transactions |
| Full Stack Transaction Action Mapping | Simple Pie | Full-stack click mapping |
| View Details Action Mapping | Simple Pie | View-details click mapping |
| Cycle Display Action Mapping | Simple Pie | Cycle-display click mapping |
| Display Processing Interval | Simple Pie | Display update interval in ticks |
| Display Movement Threshold | Simple Pie | Movement threshold for display updates |
| Display Max Shop Distance | Simple Pie | Maximum distance for shop displays |
| Display Shop Search Radius | Simple Pie | Search radius for shop discovery |
| Display Batch Size | Simple Pie | Display update batch size |
| Display Batch Delay | Simple Pie | Display update batch delay |

Platform data (server software, Java version, OS, core count, online mode, player count) and the plugin version are also reported, as is standard for all bStats-integrated plugins.

## Opting Out

bStats is enabled by default. To disable metrics collection:

1. Stop the server.
2. Open `plugins/Shop/bStats/config.yml`.
3. Set `enabled` to `false`:

   ```yaml
   enabled: false
   ```

4. Save the file and restart the server.

Shop will no longer send data to bStats. No other plugin behavior is affected.

## Viewing Metrics

Public metrics for Shop are available at:

[https://bstats.org/plugin/bukkit/Shop/NEW_PLUGIN_ID](https://bstats.org/plugin/bukkit/Shop/NEW_PLUGIN_ID)

Replace `NEW_PLUGIN_ID` with the plugin ID assigned when Shop is registered on bStats. Server and player count badges are also shown in the [README](../README.md).
