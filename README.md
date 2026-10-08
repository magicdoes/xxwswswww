# MagicSMP ElementalBlades

One Paper plugin containing both custom swords.

## Fire sword: Inferno Blade
- Right-click: Fire Burst (8s cooldown), area damage and fire.
- Sneak + right-click: Inferno Strike (20s cooldown), targeted area strike.
- Press F / swap hands: Flame Dash (6s cooldown), dash and damage enemies along the path.
- Normal hits ignite targets.

## Ice sword: Frostbite Blade
- Right-click: Ice Nova (8s cooldown), area damage and slowness/freezing.
- Sneak + right-click: Glacial Strike (20s cooldown), targeted area damage and stronger slowness.
- Press F / swap hands: Frost Dash (6s cooldown), dash with snow particles and cold damage.
- Normal hits apply a short freezing effect and slowness.

## Spawn-world restriction
All abilities and elemental melee damage are disabled when the player is in a world whose Bukkit world name is exactly `spawn` (case-insensitive). This is intended for a Multiverse world named `spawn`. It checks the actual world name, not a display name. In that world, right-click and swap-hand ability activation are blocked, and sword melee damage is cancelled.

## Build
Requires Java 21 and Maven 3.9+.
Open this project folder in VS Code and run:
```powershell
mvn clean package
```
Expected output: `target/ElementalBlades-1.0.0.jar`.

The Paper API dependency is configured as `26.2.build.112-stable`, based on the reported server build. If Maven cannot resolve it, check the exact Paper API artifact version for the installed server build and update the dependency version in `pom.xml`.

## Install
1. Stop the server.
2. Remove the old `InfernoBlade` plugin JAR from `plugins` to avoid duplicate/conflicting implementations.
3. Upload `ElementalBlades-1.0.0.jar` to `plugins`.
4. Start the server and check for `ElementalBlades enabled`.
5. Give swords using:
   - `/elementalblade give fire <player>`
   - `/elementalblade give ice <player>`

## Permissions
- `elementalblades.admin` (default OP): `/elementalblade give ...`
- `elementalblades.use` (default true): use abilities.

## Safety notes
The effects use particles and damage; they do not create world explosions or place fire/ice blocks. Player damage respects the world's built-in PvP setting. Test in a staging world first.
