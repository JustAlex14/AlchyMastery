# Custom Compounds

Everything about compounds comes from datapack files, so you can add materials, whole new families and higher tiers without touching any Java. This page goes through each case with a working example.

The full list of fields is on [Configuration and Datapacks](Configuration-and-Datapacks). This page is the step by step version.

## Where the files go

A datapack with this layout:

```
my_pack/
  pack.mcmeta
  data/
    mypack/
      compound_material/
        tin.json
        wheat.json
        ...
```

The file name is the material id, so `data/mypack/compound_material/tin.json` is the material `mypack:tin`. Use your own namespace instead of `alchymastery` so you never clash with the built-in files. Run `/reload` after editing (or rejoin the world) and the changes apply.

## Adding a material to an existing family

The simplest case: a tin ore from another mod, in the `mineral` family.

```json
{
  "color": "#B7C4C8",
  "tier": 1,
  "forms": {
    "raw": "#c:raw_materials/tin",
    "ore": "#c:ores/tin",
    "ingot": "#c:ingots/tin",
    "block": "#c:storage_blocks/tin",
    "raw_block": "#c:storage_blocks/raw_tin"
  }
}
```

`family` is left out, so it defaults to `mineral`. Tin can now be broken down, transmuted into iron or gold, and rebuilt. Only list the forms that exist: a gem uses `gem` and `gem_ore` instead of `ingot`, `raw` and `ore`.

## Creating a new family

A family is just a name. As soon as one material file uses a new name, the family exists. There is nothing to register.

Say you want crops to become their own family, so wheat can turn into carrots but never into iron. Give each crop the same `family`:

`data/mypack/compound_material/wheat.json`

```json
{
  "family": "crop",
  "color": "#D8B54A",
  "tier": 0,
  "forms": {
    "drop": "minecraft:wheat",
    "drop_block": "minecraft:hay_block"
  }
}
```

`data/mypack/compound_material/carrot.json`

```json
{
  "family": "crop",
  "color": "#E8892B",
  "tier": 0,
  "forms": {
    "drop": "minecraft:carrot"
  }
}
```

`data/mypack/compound_material/golden_carrot.json`

```json
{
  "family": "crop",
  "color": "#F2D23C",
  "tier": 2,
  "forms": {
    "drop": "minecraft:golden_carrot"
  }
}
```

Rules for the name: lowercase letters, digits and `_` only (`crop`, `nether_plant`, `gem_dust`). Anything else fails to load and the log says why.

For items that are not ores or ingots, use the `drop` form (1 compound per item) and `drop_block` for their storage block (9 per block). If a block holds a different amount, set it with `costs`, the same way the built-in bone block does: `"costs": { "drop_block": 3 }`.

A family needs at least two materials to be useful, since transmutation only works inside a family.

### Naming the family

Without a translation, the compound is called "Wheat Compound" and the tooltip shows the family name with a capital letter ("Crop"). To change that, add a resource pack (or the `assets` folder of your mod) with a lang file:

`assets/alchymastery/lang/en_us.json`

```json
{
  "family.alchymastery.crop": "Crop matter",
  "item.alchymastery.compound.of.crop": "%s Extract",
  "material.mypack.golden_carrot": "Golden Carrot"
}
```

| Key | What it changes |
|---|---|
| `family.alchymastery.<family>` | the family line in the tooltip |
| `item.alchymastery.compound.of.<family>` | the item name, `%s` is the material. "Wheat Extract" here, like "Rotten Flesh Essence" for the `mob` family |
| `material.<namespace>.<name>` | the material name. Without it, the file name is used with a capital letter and `_` turned into spaces |

The family keys always use the `alchymastery` namespace, whatever namespace your materials are in.

### Giving the family its own look

By default a new family uses the normal compound texture, tinted with each material's `color`. The `mob` family has its own essence texture. To do the same for your family, override `assets/alchymastery/items/compound.json` in a resource pack and add a case for it. Keep the `mob` case, since this file replaces the built-in one:

```json
{
  "model": {
    "type": "minecraft:select",
    "property": "minecraft:custom_model_data",
    "index": 0,
    "cases": [
      {
        "when": "mob",
        "model": {
          "type": "minecraft:model",
          "model": "alchymastery:item/mob_essence",
          "tints": [ { "type": "minecraft:custom_model_data", "index": 0, "default": 16777215 } ]
        }
      },
      {
        "when": "crop",
        "model": {
          "type": "minecraft:model",
          "model": "mypack:item/crop_extract",
          "tints": [ { "type": "minecraft:custom_model_data", "index": 0, "default": 16777215 } ]
        }
      }
    ],
    "fallback": {
      "type": "minecraft:model",
      "model": "alchymastery:item/compound",
      "tints": [ { "type": "minecraft:custom_model_data", "index": 0, "default": 16777215 } ]
    }
  }
}
```

Then add the model `assets/mypack/models/item/crop_extract.json` and its texture. Look at `alchymastery:item/mob_essence` for the layout: a greyscale layer that takes the tint, and an overlay that keeps its own colours.

### What a new family can and cannot do

- Destructuration, Transmutation and Reconstruction work with any family.
- The Rendering Cauldron only accepts the `mob` family, so a new family does not give experience.
- Transmutation never crosses families, in either direction.

## Adding a new tier

There is no list of tiers to edit. `tier` is just a number, and the value of a compound comes straight from it:

| Tier | Name shown | Value (in Abundant compounds) |
|---|---|---|
| 0 | Abundant | 1 |
| 1 | Common | 3 |
| 2 | Uncommon | 9 |
| 3 | Rare | 27 |
| 4 | Ancient | 81 |
| 5 | Tier 5 | 243 |
| 6 | Tier 6 | 729 |

So to add a tier above Ancient, give a material `"tier": 5`. For example, a modded end metal that should sit above netherite:

```json
{
  "color": "#6B3FA0",
  "tier": 5,
  "forms": {
    "ingot": "#c:ingots/void_alloy",
    "block": "#c:storage_blocks/void_alloy"
  }
}
```

One void alloy compound is now worth three Ancient compounds, and transmuting between them follows the usual rules (no loss between equal values, 20% loss otherwise, lowered by Productivity upgrades).

Things to know about tiers above 4:

- The tooltip shows "Tier 5", "Tier 6" and so on. The five built-in names are fixed in the code and can't be translated or extended from a datapack.
- For the `mob` family, the Rendering Cauldron's experience stops growing after tier 6.
- Values grow fast (each tier is three times the last), so a tier 7 or 8 material is very expensive to make from anything below it.

### A value between two tiers

If a material should be worth something that is not a power of three, keep its tier for display and set `worth` instead. Netherite does this: it shows as Ancient, but its value is five diamonds.

```json
{
  "color": "#5A4A4F",
  "tier": 4,
  "worth": { "of": "alchymastery:diamond", "count": 5 },
  "forms": { "ore": "#c:ores/netherite_scrap" },
  "costs": { "ore": 1 }
}
```

`worth` can point at any material, including one from your own pack, and chains are followed (up to 8 steps). A loop makes the material worth nothing, so avoid two materials pointing at each other.

## Changing a built-in material

To move a built-in material to another tier or family, put a file with the same path in your datapack: `data/alchymastery/compound_material/<name>.json`. Datapacks load after the mod, so yours wins. Copy the whole original file and change what you need, since the new file replaces it completely.

## Checking your work

- After `/reload`, the log has a line `AlchyMastery: loaded N compound material(s): [...]` listing every material id. Yours should be in it.
- If a file has a mistake (bad colour, unknown form, bad family name), that material is skipped and the log has a warning starting with `compound material <id>:` and the reason.
- In the Transmutation Chamber, if the input and the target are from different families, the screen says so instead of working. If you expected them to match, check that the `family` names are spelled exactly the same.
