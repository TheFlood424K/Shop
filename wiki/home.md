![Shop v2.0](https://github.com/user-attachments/assets/075aaff3-2328-4672-89af-32bc86ec3fcd)

<p align="center"><strong>Shop v2.0</strong> — chest-based shops for Minecraft Java Edition 26.2</p>

<p align="center">Create buy, sell, barter, combo, and gamble shops through a familiar sign or guided chat flow. PaperMC 26.2+, DivineMC, Folia, and GeyserMC/Floodgate are supported.</p>

<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/1fe15aee-b29f-47ef-b13d-79e493aef08c" alt="Buy Shop" width="400"/>
      <br>Buy Shop
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/ef525ef0-5452-43dd-93f9-2ee2368127d8" alt="Sell Shop" width="400"/>
      <br>Sell Shop
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/dfa4cad8-1da7-4523-9912-77650efc3b8e" alt="Combo Shop" width="400"/>
      <br>Combo Shop
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/8617a81c-2902-4417-aeac-ad69024c755d" alt="Barter Shop" width="400"/>
      <br>Barter Shop
    </td>
  </tr>
</table>

<div align="center">
  <img src="https://github.com/user-attachments/assets/4a949cd7-e63f-4c4a-bb5c-b85c8e3f6551" alt="Gamble Shop" width="50%"/>
  <br/>Gamble/Random Item Shops!
  <br/>
</div>

## v2.0 Compatibility

| Platform | Support |
| --- | --- |
| PaperMC | 26.2 or newer |
| DivineMC | 26.2–26.3 |
| Folia | Supported with region-aware scheduling |
| GeyserMC/Floodgate | Bedrock-connected player support |
| Java | 25 |

v2.0 replaces legacy chat components with Adventure Components, uses VaultUnlocked for virtual currency, integrates with GriefPrevention3D, and removes obsolete Spigot API dependencies.

## Features

- Choose a physical item, VaultUnlocked-backed virtual currency, or experience points.
- Create shops from a sign or with the guided chest-punch/chat flow; both methods are configurable.
- Create admin shops that do not need to be stocked.
- Fully customize signs, descriptions, holograms, messages, colors, and shop behavior.
- Create buy, sell, barter, combo, and gamble shops without needing a command for every action.
- Optional permission controls for shop types, creation, use, limits, displays, teleportation, and operator actions.
- Restrict shop creation by world, build limit, creation/destruction cost, and plugin-specific permissions.
- Handle renamed items, custom descriptions, enchantments, durability, repair cost, and custom player heads.
- Select from floating item, glass case, large item, item frame, or no-display styles.
- Browse and optionally teleport to shops through the Shop GUI.
- Log transactions to embedded H2, MySQL, or MariaDB and notify players about offline purchases.
- Integrate with WorldGuard, Towny, GriefPrevention3D, LWC, PlotSquared, BentoBox, AdvancedRegionMarket, Bolt, BlockProt, and CashierLock.
- Show shops on [DynMap](https://dynmap.io/) and [BlueMap](https://bluemap.bluecolored.de/).
- Support GeyserMC/Floodgate players.
- Work with chests, barrels, and other configured container types.

## Documentation

- [Player instructions](player-instructions.md)
- [Trust Players (v2.0)](trust-players.md)
- [Configuration (`config.yml`)](config.md)
- [Permissions](permissions.md)
- [Language configuration](languageConfigs.md)
- [WorldGuard integration](worldguard.md)

## Display Types
<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/3095bd19-cfd1-47f8-8e5c-36be324c1a28" alt="Item Display" width="400"/>
      <br>Item Display
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/c6bec141-efb4-4550-b7b3-6f93ec170290" alt="Glass Case Display" width="400"/>
      <br>Glass Case Display
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/1af00832-3128-44c1-8e7c-f01d44a23f47" alt="Large Item Display" width="400"/>
      <br>Large Item Display
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/58c3d422-75ba-4c52-b4c3-edcb74f8285f" alt="Item Frame Display" width="400"/>
      <br>Item Frame Display
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/a4a7ca82-ce55-4dce-83b5-7dcce401b329" alt="No Display" width="400"/>
      <br>No Display
    </td>
  </tr>
</table>

