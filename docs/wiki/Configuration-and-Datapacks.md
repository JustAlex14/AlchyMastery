# Configuration and Datapacks

The full reference. For a step by step guide to editing these for a modpack, see the [Modpack Guide](Modpack-Guide).

## Common config

`config/alchymastery-common.toml`:

| Setting | Default | Meaning |
|---|---|---|
| `distortionFuels` | `["minecraft:lapis_lazuli=100", "minecraft:lapis_block=900"]` | items the Distortion Matrix burns, as `item_id=energy`. Fuel burns at 5 DE per tick |
| `compoundTiers` | `["Abundant=1", "Common=3", "Uncommon=9", "Rare=27", "Ancient=81"]` | fallback tiers, as `Name=value` from tier 0 up, for families that have no lines in `familyTiers` (new families from datapacks) |
| `familyTiers` | mineral, mob and food tiers | each family's tiers, as `family:Name=value`, in order from tier 0. The value counts in the family's own tier 0. See [Custom Compounds](Custom-Compounds#adding-a-new-tier) |

## Client config

`config/alchymastery-client.toml`, section `nexusFormation`: `enabled`, `stagger`, `riftOpenTicks`, `emergeTicks`, `riftCloseTicks`, `emergeSpin`, `riftSize`, `revealDelay`, `revealSpread`, `revealRiftTicks`, `veilOpacity`, `bindTicks`, `particles`, `sounds`.

The forming wave and general particle amounts are in AlchyX's own config, see the [AlchyX wiki](https://github.com/JustAlex14/AlchyX/wiki/For-Players).

## Compound materials

Each material is a datapack file: `data/<namespace>/compound_material/<name>.json`. The file name is the material id. A datapack can add materials from other mods, or change the built-in ones.

```json
{
  "color": "#D9D9D9",
  "tier": 1,
  "forms": {
    "raw": "#c:raw_materials/iron",
    "ore": "#c:ores/iron",
    "ingot": "#c:ingots/iron",
    "block": "#c:storage_blocks/iron",
    "raw_block": "#c:storage_blocks/raw_iron"
  }
}
```

| Field | Meaning |
|---|---|
| `color` | the compound's colour |
| `tier` | the tier number in the material's family, from `familyTiers` (minerals: 0 Abundant, 1 Common, 2 Uncommon, 3 Rare, 4 Ancient) |
| `forms` | the items it breaks down from and rebuilds into. Each takes an item id, a `#tag` or a list. Forms: `raw`, `ore`, `ingot`, `block`, `raw_block`, `gem`, `gem_ore`, `drop`, `drop_block` |
| `costs` | optional, compounds per form when the default does not fit, e.g. `{"ore": 6}` |
| `worth` | optional, a special value: `{"of": "alchymastery:diamond", "count": 5}` |
| `catalysts` | optional, the Reconstruction base block per form, e.g. `{"ingot": "minecraft:netherrack"}` |
| `outputs` | optional, what the Reconstruction Chamber makes per form, e.g. `{"ingot": "minecraft:netherite_ingot"}` |
| `family` | optional, default `mineral`. Compounds only transmute within a family. `mob` for essences. Any lowercase name makes a new family |

A mob drop material:

```json
{
  "family": "mob",
  "color": "#A6573A",
  "tier": 0,
  "forms": {"drop": "minecraft:rotten_flesh"}
}
```

## Machine structures and animations

The machines are AlchyX multiblocks, so a datapack can change them:

- Structures: `data/alchymastery/multiblock/<machine>.json`. See [Pattern Format](https://github.com/JustAlex14/AlchyX/wiki/Pattern-Format).
- Animations: `data/alchymastery/machine_animation/<machine>.json`. See [Animations](https://github.com/JustAlex14/AlchyX/wiki/Animations).

The machine ids are `distortion_chamber`, `destructuration_chamber`, `transmutation_chamber`, `reconstruction_chamber`, `distortion_condensator`, `rendering_cauldron`, `alchemical_nexus` and `experience_nexus`.

Run `/reload` after changing a datapack.
