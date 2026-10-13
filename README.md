# ProjectCell (offline-owner build)

A patched build of [ProjectCell](https://github.com/gongmouren214/projectcell) 1.0.1 for Forge 1.20.1.
An **EMC Storage Cell keeps working while the player it is bound to is offline**: AE2 still
lists its contents, and import/export, autocrafting and other players can still insert and
extract through it.

**Release jar:** `dist/ProjectCell-1.20.1-1.0.1-offline.2.jar`

It is a drop-in replacement: same mod ID (`projectcell`), the same item (`projectcell:emc_storage_cell`),
the same cell NBT (`owner_uuid`, `nbt_filter`) and the same recipe. Existing cells and worlds keep working.

## Why cells went empty

The original mod reads the owner's EMC and knowledge from the owner's **live player entity**. Once
the owner logs out there is no entity. The cell then reports nothing, and every insert and extract fails.
The EMC is still there; the mod just couldn't reach it.

## How this build fixes it

This build needs **Team ProjectE**, which the Project Infinity pack already includes. Team ProjectE moves every player's
EMC and learned items out of the player file into one world save file (`world/data/teamprojecte.dat`).
That data can be read and written whether or not the player is online.

- **Owner online:** exactly as before. The cell uses the player's live ProjectE data.
- **Owner offline:** the cell reads and writes the same Team ProjectE data directly.

There is only **one copy** of the EMC balance. Offline cell transfers, Power Flower income and
normal play all change the same number on the server thread. So nothing has to be merged on login,
a stale balance can never overwrite a newer one, and no transaction can be applied twice.

### Behaviour while the owner is offline
- AE2 sees the same items as when the owner is online: every learned item, with the amount the current EMC can pay for.
- **Inserting** an item adds its sell value to the owner's EMC and teaches the owner that item, as it does online.
  The NBT filter and the "must have an EMC value" rules still apply. Items worth 0 EMC are now refused instead of being
  voided; that was a bug in the original.
- **Extracting** an item requires the owner to know it, and costs its EMC value. It never takes the balance below 0.
- Teammates who share EMC or knowledge through Team ProjectE and are online see the changes live.
- **Power Flowers:** Project Expansion still holds the flower's output while the owner is offline and delivers it when
  they log in. That income is added on top of whatever the cells did in the meantime.

## Installation

1. **Back up** the server's `world` folder.
2. Stop the server.
3. In `mods/`, **delete** `ProjectCell-1.20.1-1.0.1.jar` and add `ProjectCell-1.20.1-1.0.1-offline.2.jar`.
   Never keep both jars.
4. Start the server. The log should contain:
   `EMC Storage Cells stay available while their owner is offline (Team ProjectE data)`

Forge will add the new setting to `config/projectcell/server.toml` and log
`Configuration file ... is not correct. Correcting` once. This is expected.

Clients don't need the new jar, because the client-side code is unchanged. Swapping it there too is harmless.

### Setting
`config/projectcell/server.toml`:

```toml
# Keep EMC Storage Cells working while their owner is offline.
offlineOwnerAccess = true
```

Set it to `false` and restart to get the original behaviour back without changing jars.

### Migration
Nothing is migrated or converted. No new save data is written. The mod only uses Team ProjectE's
existing data, so existing cells work offline as soon as the server starts. A player who has never
joined since Team ProjectE was installed has no data yet. Their cells stay empty, and no data is created
for them, until they log in once.

### Rollback
Stop the server, put the original `ProjectCell-1.20.1-1.0.1.jar` back in place of this one, and start it.
No data needs converting, because none was changed. Alternatively set `offlineOwnerAccess = false`.

## Supported versions

Built and tested against the exact jars shipped in Project Infinity 0.1:

| Mod | Version |
|---|---|
| Minecraft / Forge | 1.20.1 / 47.4.20 |
| Applied Energistics 2 | 15.4.10 |
| ProjectE | 1.20.1-PE1.0.1 |
| Team ProjectE | 1.20.1-1.1.5 (needed for offline access, optional otherwise) |
| Project Expansion | 1.20.1-1.1.3 (Power Flower test) |

Without Team ProjectE the jar loads and behaves exactly like the original 1.0.1.

## Known limitations
- **Duplicate counts across cells of the same owner or team.** Two cells of the same owner (or of teammates sharing
  EMC) in one network each report the full balance, so AE2 shows the amount twice. Extraction still can't overspend
  (tested). This is unchanged from the original mod.
- **CPU cost.** A cell whose owner is offline now costs what an online owner's cell already did: AE2 lists the
  owner's learned items each time it refreshes its item list, which happens only while something watches the network,
  such as an open terminal. Since 1.0.1-offline.2 the per-item work (EMC values, item keys, NBT checks) is cached and
  rebuilt only when the owner's knowledge or the EMC values change. A listing of 1,533 items takes about 0.8 ms
  (1.7 ms before).
- **Crash rollback.** A server crash rolls Team ProjectE data and AE2 or chest contents back to the last world save
  together. This is the same as for every other storage and is unchanged from online use.
- Tested with simulated players on a real dedicated server (see `TESTING.md`), not with real clients.

## Building

Copy the jars listed in `libs/README.md` into `libs/`, then:

```bash
./gradlew :forge:build
```

The jar is written to `forge/build/libs/`. Building needs JDK 17.

## Credits
Original mod by gmr ([gongmouren214/projectcell](https://github.com/gongmouren214/projectcell)), MIT licence.
The zero-sell-value fix follows the BOLTMAGIC fork.
