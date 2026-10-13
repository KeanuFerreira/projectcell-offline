# Test results

All tests ran on 2026-10-13 on a real Forge 1.20.1-47.4.20 dedicated server, with the pack's exact jars:
AE2 15.4.10, GuideME 20.1.15, ProjectE PE1.0.1, Team ProjectE 1.1.5 and Project Expansion 1.1.3.

## How it works

`testharness/` is a small test-only mod. It is not part of the release.
- **Players:** it logs simulated players in and out through the server's real login and logout path (`PlayerList.placeNewPlayer` / `remove`), so ProjectE, Team ProjectE and Project Expansion see real joins and leaves.
- **AE2:** it uses real AE2 drives, each powered by a creative energy cell. Inserts, extracts and listings go through the grid's storage service, which is what terminals and buses use. "cached" is AE2's network-wide item list that terminals display.
- **Power Flower:** it places a real MK16 (`final_power_flower`) Power Flower and lets it run.

`testharness/run_tests.py` drives the server through its console, checks every result, and restarts the server twice.

```bash
python3 testharness/run_tests.py <server_dir> forge/build/libs/projectcell-1.0.1-offline.1.jar
```

## 1. Bug reproduced with the original jar (`--baseline`)

| Jar | Owner online | Owner offline | Offline insert of 5 cobblestone |
|---|---|---|---|
| Original 1.0.1 | 123 diamonds | **0** | **0 accepted** |
| This build | 123 diamonds | **123** | **5 accepted** |
| This build, `offlineOwnerAccess = false` | 123 diamonds | 0 | 0 accepted |

## 2. Full matrix: 40 of 40 checks passed

Three server sessions:
1. **Session A:** online basics, then the owner goes offline while another player stays online, then several cells and players.
2. **Restart, session B:** everything while the owner is still offline, then a Power Flower login and a respawn.
3. **Restart, session C:** a Power Flower login with no cell activity.

```
PASS unknown item not listed
PASS unknown item not extractable
PASS online insert accepted
PASS online insert adds sell value
PASS online insert teaches item
PASS online count = emc / value
PASS online extract
PASS online extract costs value
PASS NBT filter refuses enchanted item
PASS NBT filter refuses damaged item
PASS refused items cost nothing
PASS offline: contents still listed  [fresh=121 emc=991808]
PASS offline: AE2 cached view matches
PASS offline: iron not known yet
PASS offline: insert accepted
PASS offline: insert adds sell value
PASS offline: insert teaches item
PASS offline: extract
PASS offline: extract costs value
PASS offline: NBT filter still refuses
PASS two cells same owner: no duplication  [affordable=117 got=117 emcAfter=3136]
PASS drained: nothing more extractable
PASS isolation: Bob's network can't extract Alice-only item
PASS isolation: Alice's EMC untouched
PASS two offline owners: Bob's cell lists cobblestone
PASS never-seen owner: no team created
PASS restart: Alice's offline balance persisted
PASS restart: Bob's offline balance persisted
PASS restart: offline-learned knowledge persisted
PASS restart: cell available before owner ever logs in
PASS restart: offline insert
PASS restart: offline extract
PASS flower accumulated backlog while owner offline  [flower info stored=239794342133700 perFire=239794342133700 delay=100 owner=Alice]
PASS flower backlog + offline cell changes merge exactly once  [PASS flower-merge expected=479588685291976 actual=479588685291976 flowerStoredAfter=0]
PASS after login: offline-learned item still known
PASS after login: count matches live EMC
PASS after respawn: extract works
PASS full knowledge offline: all items listed  [keys=1533 scan=11246us]
PASS restart 2: balance persisted
PASS flower merge without cell activity  [PASS flower-merge expected=1918354738085984 actual=1918354738085984 flowerStoredAfter=0]

40 checks, 0 failed
```

**Power Flower check.** The test waits until the flower has a backlog with Alice offline. Offline cell inserts and extracts have already happened by then. It logs Alice in 30 ticks before the flower fires, then waits for the delivery. The final EMC must equal the balance at login, plus the flower backlog, plus that one firing. The two numbers matched exactly, and the flower's stored EMC was 0 afterwards.

## 3. Full Project Infinity server mod set

The baseline scenario was also run on the server's real `mods` (351 jars), `config`, `defaultconfigs` and `kubejs`, using a fresh world with the original jar replaced:
- the server started normally and logged the offline-access line;
- owner online: 123 diamonds; owner offline: 123; offline insert: 5 of 5 accepted;
- no errors or warnings from ProjectCell. The one exception is Forge adding the new config key to the existing `server.toml`, which is expected.

## Mapping to the requested matrix

| Requested | Covered by |
|---|---|
| Basic 1–4 (insert, extract, unknown items, NBT filter) | session A, "online" and "NBT filter" checks |
| Offline 1–6 | session A "offline:" checks, session B "after login" checks |
| Power Flower 1–7 | session B merge (with offline cell activity), session C merge (without) |
| Restart while offline / after an offline transaction | session B "restart:" checks |
| Restart around login | not needed: with a single data store there is no login step to interrupt |
| Two cells of the same owner | "two cells same owner: no duplication" |
| Two players' cells | "isolation" and "two offline owners" checks |
| Death, respawn, reconnect | "after respawn: extract works" |
| Cell removal and reinsertion | `putcell` / `takecell` between checks |
| Large knowledge set | "full knowledge offline": 1,533 items, about 3 ms per scan once warm |

Not covered: real game clients and the terminal screen itself. AE2's cached item list, which terminals display, was checked instead.
