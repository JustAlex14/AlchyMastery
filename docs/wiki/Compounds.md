# Compounds

The Destructuration Chamber strips matter down to pure substance: compounds. Iron ore becomes iron compound, rotten flesh becomes a flesh essence. Compounds are what the Transmutation Chamber changes and the Reconstruction Chamber rebuilds.

## Tiers

By default each tier is worth three of the tier below. The values and names can be changed, and tiers added, in the config (see [Custom Compounds](Custom-Compounds#adding-a-new-tier)).

| Tier | Materials |
|---|---|
| Abundant | coal, copper, redstone, lapis |
| Common | iron, quartz |
| Uncommon | gold |
| Rare | diamond, emerald |
| Ancient | netherite |

Netherite has a special value: it is worth five diamonds.

## Yields

The more an item holds, the more compounds it gives.

| Form | Compounds |
|---|---|
| Ingot or gem | 1 |
| Raw ore | 3 |
| Gem ore | 3 |
| Ore block | 6 |
| Storage block | 9 |
| Raw storage block | 27 |
| Mob drop | 1 |

Breaking down the biggest form is always worth it.

## Essences

Mob drops break down into essences on the same tiers: flesh, bone, string, spider eyes, gunpowder, leather, feathers, slime, magma cream, ender pearls, blaze rods, ghast tears, phantom membranes and shulker shells. The Rendering Cauldron turns essences into experience.

## Families

Matter keeps its family. Compounds only transmute into materials of the same family: iron can become gold, flesh can become bone, but iron never becomes flesh. The built-in families are `mineral` (ores, ingots, gems) and `mob` (essences). A datapack can add more families and tiers, see [Custom Compounds](Custom-Compounds).
