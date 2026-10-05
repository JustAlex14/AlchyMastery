# AlchyMastery

An alchemy mod for NeoForge (Minecraft 26.1.2). Tear reality open, hold the distortion in a chamber, and use it to run the alchemical cycle: break matter down into compounds, transmute them, and rebuild them into ores and ingots.

![The Alchemical Nexus](docs/screenshots/07_nexus_formed.jpg)

- **Distortion Chamber**: burns lapis into distortion energy (DE)
- **Wormholes**: carry the energy to machines up to 124 blocks away
- **Machines**: Destructuration, Transmutation, Condensator, Reconstruction and the Rendering Cauldron (mob essences into liquid experience)
- **Upgrades**: Speed, Productivity, Efficiency and Parallel, with visible corruption as you push the machines
- **Nexuses**: whole chambers folded into one machine
- **The Alchemist's Codex**: an in-game journal (Patchouli) with an animated void theme, unlocking chapter by chapter

## Screenshots

<table>
<tr><td width="50%"><img src="docs/screenshots/01_distortion_chamber.jpg" alt="Distortion Chamber"><br><b>Distortion Chamber</b>: lapis burns behind the glass, rifts already tearing through the panes</td><td width="50%"><img src="docs/screenshots/02_destructuration.jpg" alt="Destructuration Chamber"><br><b>Destructuration Chamber</b>: pistons crush matter over a lava pool into pure compounds</td></tr>
<tr><td width="50%"><img src="docs/screenshots/03_reconstruction.jpg" alt="Reconstruction Chamber"><br><b>Reconstruction Chamber</b>: a cottage around a cauldron, where compounds become items again</td><td width="50%"><img src="docs/screenshots/04_condensator.jpg" alt="Distortion Condensator"><br><b>Distortion Condensator</b>: under dripstone, water thickens into distortion fluid</td></tr>
<tr><td width="50%"><img src="docs/screenshots/05_rendering_cauldron.jpg" alt="Rendering Cauldron"><br><b>Rendering Cauldron</b>: a soul tends the crystal, rendering mob essences into liquid experience</td><td width="50%"><img src="docs/screenshots/06_nexus_forming.jpg" alt="A Nexus forming"><br><b>A Nexus forming</b>: the chambers phase out and come back through rifts, folded into miniatures</td></tr>
<tr><td width="50%"><img src="docs/screenshots/08_experience_nexus.jpg" alt="Experience Nexus"><br><b>Experience Nexus</b>: mob drops in, liquid experience out</td><td></td></tr>
</table>

## Requirements

- NeoForge 26.1.2
- [AlchyX](https://github.com/JustAlex14/AlchyX)
- Optional: Patchouli (the Codex), JEI (recipes), Jade

## Building

Build and publish AlchyX to your local Maven first (`./gradlew publishToMavenLocal` in AlchyX), then:

```
./gradlew build
```

The jar ends up in `build/libs`.

## License

MIT, see [LICENSE](LICENSE).

A few textures are recolored from Minecraft and Patchouli textures; those stay under their original owners' terms.
