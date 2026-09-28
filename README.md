# Keyboard Place

## Download

**[Download keyboardplace-1.6.0.jar](https://github.com/idkhowtonamemyselfasadev/keyboardplace/releases/latest/download/keyboardplace-1.6.0.jar)**

Drop the jar into your `mods` folder. You need:

- Minecraft **1.21.11** with **Fabric Loader**
- **Fabric API** (from Modrinth) in the same `mods` folder

Then start the game. Press `Y` in-game to toggle the mod on and off. All releases: [Releases page](https://github.com/idkhowtonamemyselfasadev/keyboardplace/releases).

---

A client-side Fabric mod for **Minecraft 1.21.11** that puts keyboard-place speed on
the mouse button you already use.

Built for the Modrinth App profile **"Better PvP"** (Fabric loader 0.18.2).

## Two ways to place, and they are not the same thing

**Place keys - ordinary input.** Three bindings, unbound out of the box. Bind them in
Controls, to keyboard keys or to spare mouse buttons, and each press places exactly one
block where you are aiming. Nothing is on a timer; a block only ever appears because a
finger moved. This is what keyboard place is, and the reason people use it openly: it is
not automation, it is just more than one key bound to placing, which vanilla will not
let you do on its own.

**Fast place - automation.** Hold the use button and the mod places for you on a timer.
It is genuinely quicker and genuinely different in kind, and no amount of tuning makes
the packets it sends look like a person pressing a button. Servers that care about this
are looking for exactly that. It ships restricted to singleplayer for that reason.

## What it does

**Fast place on the normal use button.** Vanilla only throttles *holding* the button:
after each block it parks a four-tick cooldown, capping you at five a second. (Clicking
was never throttled - a click runs down a different path with no cooldown on it, which
is the entire reason keyboard place is worth doing on a keyboard.)

This replaces that cooldown with a configurable interval, so **holding** right-click
places as fast as any amount of keyboard rolling would. Two ticks by default, so ten a
second. `/kbplace speed 1` for the maximum of twenty a second - one every tick, which is
as fast as the game can accept them. Above 4 is slower than vanilla.

**End crystals count too.** The same cooldown is what limits how fast crystals go
down, so the interval applies to them as well. Area place stays off for crystals -
filling a 3x3 would burn nine of them on a spot that only ever needed one.

**So do pots and XP.** Splash potions, lingering potions and bottles o' enchanting
are thrown off the very same four-tick timer, so holding right-click throws them at
the configured rate too - potting up or mending armour at ten or twenty a second
instead of five. Drinkable potions are untouched; those run on the drink timer, not
the cooldown. `/kbplace throw false` puts throwables back on vanilla's rate while
leaving blocks fast.

**Area place, off by default.** After each placement, fill the square around it on the
same face. This is a *building* convenience and has nothing to do with keyboard place -
left on it turns every placement into a slab of obsidian. `/kbplace area 1` for 3x3 if
you ever want it for building.

Both only run while you are holding the use button with a block in hand.

## Controls

| | |
|---|---|
| `Y` | toggle the mod on and off |
| unbound | Place 1, 2, 3 - one block per press, bind them yourself |

All under Options - Controls - Keyboard Place. The place keys ship unbound so nothing
changes for anyone who does not want them.

## Commands

    /kbplace                    what it is currently set to
    /kbplace on | off
    /kbplace speed <1-10>       ticks between placements; 1 is 20/sec, 2 is 10/sec, 4 is vanilla
    /kbplace area <0-4>         0 places a single block, 1 is 3x3, 2 is 5x5
    /kbplace throw <bool>       whether splash/lingering pots and XP bottles use the interval too
    /kbplace multiplayer <bool> whether to run on servers at all

Settings are saved to `config/keyboardplace.json` in the profile.

## What it deliberately does not do

**Nothing is faked client-side.** Every block, including every one in the square,
goes to the server as an ordinary placement packet. The server checks your reach
and takes the item out of your inventory the same way it always does, so this
cannot place blocks you do not have or reach blocks you could not reach.

**It only fires for blocks, end crystals, throwable pots and XP bottles.** If you
are holding food, a bow, a pearl, a bucket or a firework, the mod stays out of the
way, so holding right-click does not eat your whole stack.

**It knows an interaction from a placement.** Right-clicking a chest, a door or a
furnace with a block in hand is not a placement, and it will not spray blocks
around it.

**Singleplayer unless you say otherwise.** Placement speed is exactly what an
anti-cheat watches, so `allowMultiplayer` ships off and has to be turned on
deliberately with `/kbplace multiplayer true`.

## Building

    ./install.sh

Builds and drops the jar into the "Better PvP" profile. Safe to run with the game
open - it installs by rename, never over a jar the running JVM has open - but
Fabric reads the mods folder once at startup, so the new build takes effect on the
next launch.

There is no system JDK on this machine; the build uses the Adoptium 21 that Gradle
downloaded for toolchains.
