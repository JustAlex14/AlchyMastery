# Machines

All numbers are before upgrades. Every machine below needs a [wormhole](Distortion-Energy) linked to a Distortion Chamber. The Codex has the exact structure of each one, and its Visualize button shows it in the world.

## Distortion Condensator

Turns water into distortion fluid. Distortion fluid soaks amethyst shards into distortion shards and feeds the Reconstruction Chamber.

- **Core:** Condensator Core (looks like a conduit)
- **Structure:** cobblestone and cobblestone walls, a back wall of copper grates (any oxidation), glass sides, water cauldrons with pointed dripstone hanging over them, the core over a cauldron basin, a cobblestone stair roof with a wormhole in it
- **Work:** 1,000 mB of water and 200 DE make 500 mB of distortion fluid in 5 seconds
- **Tanks:** 8,000 mB each. The basin shows the water, the ring of cauldrons shows the fluid
- **Automation:** water in and fluid out by bucket or pipe on any block

## Destructuration Chamber

Breaks items down into compounds. Mob drops become essences.

- **Core:** Destructuration Core
- **Structure:** cobblestone, pistons facing the core, redstone torches on the walls, glass panes, a lava source under the core and a wormhole above it. The glass disappears when it forms
- **Work:** an ingot costs 10 DE and 2 seconds, an ore 40 DE and 8 seconds (bigger items take longer)
- **Byproduct:** one time in twenty, an extra compound goes to the byproduct slot
- **Automation:** items in from above, compounds out of the sides. Pipes and hoppers can take from any block. It stops when its outputs are full or it runs out of energy

## Transmutation Chamber

Turns compounds into another compound of the same family.

- **Core:** Transmutation Core (a book that reads itself)
- **Structure:** a small library in front of the book: stone bricks, spruce and oak logs, bookshelves of both woods facing the front, a redstone lamp and lanterns. The book faces whoever placed it
- **Target:** set a compound as the target. It is only an image and is never used up. Every compound fed in becomes that target
- **Cost:** value is kept, but a fifth of it is lost when input and target are worth different amounts. What is left over waits for the next change. Each change takes 2 seconds and 5 DE per unit of value made (one tier 0 compound of the family)
- **Families:** minerals only become minerals, essences only become essences. Netherite is worth five diamonds, so the two trade freely

## Reconstruction Chamber

Rebuilds compounds into items, with distortion fluid and a base block that acts as the mold.

- **Core:** Reconstruction Core
- **Structure:** a cottage around a water cauldron on a campfire: oak planks, logs and stairs, bookshelves, glass panes, barrels with potted flowers, lanterns. One plank becomes a beehive when it forms, candles light themselves and a spore blossom opens
- **Molds:**

| Base block | Result |
|---|---|
| Stone | raw ore |
| Cobblestone | ingot or gem |
| Deepslate | ore block |
| Soul sand or soul soil | a mob drop |
| Netherrack | netherite ingot (the only way netherite comes back) |

- **Fluid:** by bucket or pipe. The cauldron behind the core shows how much is left

## Rendering Cauldron

Dissolves mob essences in distortion fluid to make liquid experience.

- **Core:** Rendering Core (a crystal turning over obsidian, tended by a soul)
- **Structure:** a witch's hut open to the sky, the core in its back wall facing the door, beams across the room holding the wormhole, an obsidian pedestal under it and a cauldron on each side
- **Cost:** each essence takes 100 mB of fluid and 10 DE
- **Yield:** the lowest tier gives 2 experience, and each tier above gives three times more (2, 6, 18, 54...)
- **Taking the experience:** by bucket or pipe, from its window (one level or all at once), or with:
  - **Experience Tap:** placed on any block of the cauldron. Open, it pours up to 20 points a second as falling orbs. An empty bottle held to it becomes a bottle o' enchanting for 10 points
  - **Experience Siphon:** use it on the cauldron to bind them. Carried, or worn as a charm with Curios, it repairs your Mending items from the cauldron's experience wherever you are, as long as the cauldron's chunk is loaded. Each cauldron serves one player. Sneak-use in the air to unbind
