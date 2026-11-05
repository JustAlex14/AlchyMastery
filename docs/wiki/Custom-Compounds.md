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

### Example: the food family

AlchyMastery ships a `food` family built exactly this way, so it doubles as a full example. Its files are in `data/alchymastery/compound_material/`. Wheat can become carrots or golden apples, but never iron or rotten flesh.

| Tier | Materials |
|---|---|
| 0 Abundant | wheat, carrot, potato, beetroot, melon, pumpkin |
| 1 Common | apple, cocoa |
| 2 Uncommon | golden_carrot, glistering_melon |
| 3 Rare | golden_apple |
| 5 | enchanted_golden_apple |

`wheat.json`: a tier 0 material with a storage block. The hay bale gives 9 wheat extracts, like the 9 wheat it is crafted from.

```json
{
  "family": "food",
  "color": "#D8B54A",
  "tier": 0,
  "forms": {
    "drop": "minecraft:wheat",
    "drop_block": "minecraft:hay_block"
  },
  "catalysts": {
    "drop": "minecraft:dirt"
  }
}
```

`golden_apple.json`: the same shape on a higher tier.

```json
{
  "family": "food",
  "color": "#F5D547",
  "tier": 3,
  "forms": {
    "drop": "minecraft:golden_apple"
  },
  "catalysts": {
    "drop": "minecraft:dirt"
  }
}
```

`enchanted_golden_apple.json`: a material on tier 5, above Ancient. It is worth whatever tier 5 is worth in the config (see [Adding a new tier](#adding-a-new-tier)).

```json
{
  "family": "food",
  "color": "#E86BE0",
  "tier": 5,
  "forms": {
    "drop": "minecraft:enchanted_golden_apple"
  },
  "catalysts": {
    "drop": "minecraft:dirt"
  }
}
```

What each part does:

- `"family": "food"` puts the material in the family. Every food file uses the same name.
- Food items are not ores or ingots, so they use the `drop` form (1 extract per item) and `drop_block` for storage blocks (9 per block). If a block holds a different amount, set it with `costs`, the same way the built-in bone block does: `"costs": { "drop_block": 3 }`.
- `catalysts` sets the Reconstruction base block. Drops are rebuilt on soul sand by default, which suits mob drops. Food is rebuilt on dirt instead.

And the names, in `assets/alchymastery/lang/en_us.json`:

```json
{
  "family.alchymastery.food": "Food",
  "item.alchymastery.compound.of.food": "%s Extract",
  "material.alchymastery.wheat": "Wheat",
  "material.alchymastery.golden_apple": "Golden Apple",
  "material.alchymastery.enchanted_golden_apple": "Enchanted Golden Apple"
}
```

With these, a wheat extract is called "Wheat Extract" and its tooltip says "Tier: Abundant" and "Food".

### Making your own

Copy the food files into your datapack under your own namespace, give them a new family name, and point the forms at other items. For example `data/mypack/compound_material/dandelion.json` with `"family": "flower"` and `"drop": "minecraft:dandelion"`, then poppy, cornflower and so on. Each item should belong to one material only, so don't reuse items that are already in a built-in file.

Rules for the name: lowercase letters, digits and `_` only (`food`, `flower`, `nether_plant`). Anything else fails to load and the log says why.

A family needs at least two materials to be useful, since transmutation only works inside a family.

### Naming the family

Without a translation, the compound is called "Dandelion Compound" and the tooltip shows the family name with a capital letter ("Flower"). To change that, add a resource pack (or the `assets` folder of your mod) with a lang file, like the food one above:

`assets/alchymastery/lang/en_us.json`

```json
{
  "family.alchymastery.flower": "Flower",
  "item.alchymastery.compound.of.flower": "%s Petals",
  "material.mypack.dandelion": "Dandelion"
}
```

| Key | What it changes |
|---|---|
| `family.alchymastery.<family>` | the family line in the tooltip |
| `item.alchymastery.compound.of.<family>` | the item name, `%s` is the material. "Wheat Extract" for `food`, "Rotten Flesh Essence" for `mob` |
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
        "when": "food",
        "model": {
          "type": "minecraft:model",
          "model": "mypack:item/food_extract",
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

Then add the model `assets/mypack/models/item/food_extract.json` and its texture. Look at `alchymastery:item/mob_essence` for the layout: a greyscale layer that takes the tint, and an overlay that keeps its own colours.

### What a new family can and cannot do

- Destructuration, Transmutation and Reconstruction work with any family.
- The Rendering Cauldron only accepts the `mob` family, so a new family does not give experience.
- Transmutation never crosses families, in either direction.

## Adding a new tier

Tiers are set in `config/alchymastery-common.toml`, in the `compoundTiers` list. Each line is `Name=value`, from tier 0 up. The value is what one compound of that tier is worth, counted in tier 0 compounds. The default list is:

```toml
compoundTiers = ["Abundant=1", "Common=3", "Uncommon=9", "Rare=27", "Ancient=81"]
```

To add tiers, add lines. Each new line is the next tier number. For a tier 5 worth two Ancient compounds and a tier 6 worth three:

```toml
compoundTiers = ["Abundant=1", "Common=3", "Uncommon=9", "Rare=27", "Ancient=81", "Mythic=162", "Legendary=243"]
```

Then give a material that tier in its datapack file. The built-in enchanted golden apple is on tier 5, so with the list above its tooltip says "Tier: Mythic" and it is worth two Ancient compounds. Without the extra line it shows as "Tier 5" and is worth 243. Your own materials work the same way:

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

Transmuting follows the usual rules: no loss between equal values, 20% loss otherwise, lowered by Productivity upgrades.

You can also change the existing tiers. `"Rare=30"` makes every Rare compound worth 30 instead of 27. Changing values changes every transmutation cost, so do it before players build up stock.

Things to know:

- Keep values going up with each tier. Nothing checks it, but a tier worth less than the one below is confusing to play with.
- A material whose tier is past the end of the list is worth 3 times the last tier per step, and shows as "Tier 7", "Tier 8" and so on.
- To translate a tier name, add the lang key `tier.alchymastery.<tier number>`, e.g. `"tier.alchymastery.5": "Mythique"` in `fr_fr.json`. Without it, the name from the config is shown.
- For the `mob` family, the Rendering Cauldron gives 2 experience points per unit of value, capped at 1458 points per essence.
- The config is read on each side. On a server, give players the same `compoundTiers` (a modpack ships it in its config folder), or their tooltips and JEI will show the wrong values. The machines always use the server's list.

### A value between two tiers

`worth` is a per-material override: the material keeps its tier for the tooltip, but its value is a count of another material instead of its tier's value. Use it for one-off materials. For a whole tier, change `compoundTiers` instead. Netherite uses it: it shows as Ancient, but it is worth five diamonds (5 x 27 = 135) instead of 81.

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
