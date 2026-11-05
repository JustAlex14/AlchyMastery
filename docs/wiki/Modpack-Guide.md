# Modpack Guide

Everything a modpack maker usually wants to change in AlchyMastery: tier names and values, which items break down into what, new families, fuels and names. None of it needs code.

## Where each thing lives

| What | Where | Format |
|---|---|---|
| Tier names and values | `config/alchymastery-common.toml` | `familyTiers`, `compoundTiers` |
| Distortion Matrix fuels | `config/alchymastery-common.toml` | `distortionFuels` |
| Materials (what breaks down into what, tier, family, colour) | a datapack, `data/<namespace>/compound_material/<name>.json` | JSON, see [Custom Compounds](Custom-Compounds) |
| Names shown in game (materials, families, tiers) | a resource pack, `assets/alchymastery/lang/<language>.json` | lang JSON |
| Compound textures per family | a resource pack, `assets/alchymastery/items/compound.json` | item model, see [Custom Compounds](Custom-Compounds#giving-the-family-its-own-look) |

If the pack uses KubeJS, its `kubejs/data/` and `kubejs/assets/` folders work as the datapack and resource pack.

## Editing tiers

All tiers are in `config/alchymastery-common.toml`. The file is created on the first launch with these defaults:

```toml
familyTiers = [
  "mineral:Abundant=1", "mineral:Common=3", "mineral:Uncommon=9", "mineral:Rare=27", "mineral:Ancient=81",
  "mob:Weak essence=1", "mob:Strong essence=3", "mob:Rare essence=9", "mob:Mythic essence=27",
  "food:Raw food=1", "food:Exotic food=4", "food:Golden food=16", "food:Enchanted meal=64"
]
compoundTiers = ["Abundant=1", "Common=3", "Uncommon=9", "Rare=27", "Ancient=81"]
```

How to read it:

- Each line of `familyTiers` is `family:Name=value`.
- A family's lines are its tiers in order: the first `mineral:` line is mineral tier 0, the second is mineral tier 1, and so on. The order of the families between them does not matter.
- The value is what one compound of that tier is worth, counted in the same family's tier 0. `mineral:Rare=27` means one diamond compound is worth 27 coal compounds.
- `compoundTiers` is only used by families that have no lines in `familyTiers`, for example a new family added by a datapack.

Families never transmute into each other, so each family can be balanced on its own.

### Common edits

**Rename a tier.** Change the name, keep the value: `"mob:Weak essence=1"` to `"mob:Common essence=1"`.

**Make a tier more expensive.** Raise its value: `"mineral:Rare=40"` makes diamonds and emeralds cost 40 coal compounds instead of 27. The tiers above should stay higher than it.

**Add a tier.** Add a line after the family's last one: `"mineral:Ancient=81", "mineral:Mythic=243",`. Mineral tier 5 now exists. Give a material `"tier": 5` in its datapack file to put it there.

**Give a new family its own tiers.** Add lines with its name: `"flower:Common flower=1", "flower:Rare flower=5",`. Without them, the family uses `compoundTiers`.

**Put a family back on the fallback.** Remove all its lines from `familyTiers`.

### What tier values change

- **Transmutation:** the cost of turning one material into another. No value is lost between equal values, 20% is lost otherwise (lowered by Productivity upgrades), and the machine uses 5 DE per unit of value it makes.
- **Rendering Cauldron:** a mob essence gives 2 experience points per unit of its tier's value, up to 1458 points.
- **Tooltips and JEI:** the tier name and the transmutation pages.

Breaking down and rebuilding don't depend on tiers. They depend on the material's form (an ore block gives 6 compounds, see [Compounds](Compounds#yields)).

### Rules and mistakes

- A tier past the end of its family's list is worth 3 times the last tier per step and shows as "Tier N".
- If a line is malformed (no `=`, a value that is not a positive whole number, a family name with capitals or spaces), NeoForge corrects the setting when it loads the file: the bad line is dropped or the whole list goes back to its default. The log says which setting was corrected, so check it after editing.
- Edit tiers before players build up stock. Compounds already made keep their tier number, so a renumbered tier can show the wrong name on old items.

## Adding, changing and removing materials

Materials are datapack files. Full field list and examples: [Custom Compounds](Custom-Compounds) and [Configuration and Datapacks](Configuration-and-Datapacks#compound-materials).

- **Add a material** (a modded ore, a new food): a new file under your own namespace, e.g. `data/mypack/compound_material/tin.json`.
- **Change a built-in material** (move it to another tier, change what it breaks down from): a file with the same path as the built-in one, `data/alchymastery/compound_material/<name>.json`. Copy the original and edit it, since your file replaces it completely.
- **Turn a built-in material off**: replace its file with one that has no forms, so no item breaks down into it:

```json
{
  "color": "#FFFFFF",
  "forms": {}
}
```

The built-in files are in the mod jar under `data/alchymastery/compound_material/`, or browse them on [GitHub](https://github.com/JustAlex14/AlchyMastery/tree/main/src/main/resources/data/alchymastery/compound_material).

After `/reload`, the log lists every loaded material: `AlchyMastery: loaded N compound material(s): [...]`. A file with a mistake is skipped, with a warning that starts with `compound material <id>:`.

## Fuels

`distortionFuels` lists what the Distortion Matrix burns, as `item_id=energy`:

```toml
distortionFuels = ["minecraft:lapis_lazuli=100", "minecraft:lapis_block=900", "minecraft:amethyst_shard=150"]
```

Fuel burns at 5 DE per tick, so an item worth 900 DE burns for 9 seconds. An item id that doesn't exist is treated like a malformed line: NeoForge corrects the setting and logs it.

## Names

Names come from lang files, so a resource pack can rename anything, in any language:

```json
{
  "material.alchymastery.wheat": "Grain",
  "family.alchymastery.food": "Provisions",
  "item.alchymastery.compound.of.food": "%s Tincture",
  "tier.alchymastery.food.3": "Feast of the Gods",
  "tier.alchymastery.mineral.4": "Primordial"
}
```

| Key | What it names |
|---|---|
| `material.<namespace>.<name>` | a material |
| `family.alchymastery.<family>` | a family, in the tooltip |
| `item.alchymastery.compound.of.<family>` | the compound item, `%s` is the material ("%s Essence", "%s Extract") |
| `tier.alchymastery.<family>.<tier>` | a tier of a family that has lines in `familyTiers` |
| `tier.alchymastery.<tier>` | a tier from `compoundTiers` |

Without a tier translation, the name written in the config is shown. For a pack in one language, changing the name in the config is enough.

## Shipping it

- Put your edited `config/alchymastery-common.toml` in the pack's `config` folder, and the same file on the server.
- Players and the server must have the same tier lists. The machines use the server's, but tooltips and JEI use the player's, so a different file shows wrong values.
- Material files are server data and are sent to players when they join, so they only need to be on the server (or in the world's datapacks).
- Lang files and textures are client side: ship them in a resource pack enabled by default, or in KubeJS `assets`.
