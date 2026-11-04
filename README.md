# Karoo Light Profiles

Controls ANT+ bike lights from a Hammerhead Karoo, with a profile per kind of
ride instead of one set of rules that has to fit them all.

## What it does

A **profile** holds everything for one kind of ride. You switch profiles by
hand, with a handlebar button, or let the sun do it.

Per light, a profile says three things:

| | |
|---|---|
| **Normal mode** | what the light does most of the time |
| **Radar** | ignore it, warn on top of the normal mode, or stay dark and light up only when a vehicle is behind |
| **Bands** | whether the mode changes with how the riding goes |

The three bands are **slow / middle / fast**, following either speed or
gradient. With gradient, uphill counts as slow and downhill as fast, so a
headlight can dim on a climb and open up on the descent. Leave a band empty
and it keeps the normal mode. Turn bands off in a profile — for riding
through town, say — and the light holds one mode.

Typical profiles:

- **Day road** — front off or flashing, rear dark until the radar sees a car
- **Woods at night** — rear flashing, front following the gradient
- **Town transfer** — same lights, bands off, so you don't dazzle anyone

## Requirements

- Karoo 2 or Karoo 3
- Lights paired in Karoo's own sensor settings, with **Auto Control turned off
  for each light** — otherwise Karoo and this app fight over them
- Some lights sleep when switched off and drop their ANT+ connection. If a
  light stays dark when it should warn, turn off its auto shut-off in the
  manufacturer's app, or give it a dim idle mode instead of Off.

## Installing

Share the APK from your phone to the Hammerhead Companion app, with the Karoo
on and on Wi-Fi. Restart the Karoo if the extension does not show up.

The app is signed with a release key, so later versions install over it as
updates.

## Setting up

1. Open **Light Profiles** on the Karoo
2. **New profile**, give it a name
3. Switch on each light you want it to drive, set how it is mounted, and pick
   its normal mode. The ▶ button next to a mode tries it straight away.
4. Optionally set the radar behaviour and the bands
5. Back in the list, tap a profile to make it active

For switching while riding, map **Next light profile** to a hardware or AXS
button in Karoo's settings. The **Light Profile** data field shows which
profile is active and what each light is set to — Karoo gives extensions no
way to receive a tap on a data field, so the field reports and the button
acts.

## Notes

Light control goes through an internal Karoo interface that Hammerhead does
not document. The transaction numbers it uses moved in Karoo 1.5.x, so the app
looks for the right one and refuses to send anything if it cannot identify the
light interface, rather than commanding something else. A firmware update can
still break this; the log says plainly when it does.

## Licence

MIT. `KarooLightControl.kt` comes from
[KarooFireFly](https://github.com/derstrassi/karoofirefly) by derstrassi, also
MIT, with corrected transaction numbers.
