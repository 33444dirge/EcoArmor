# EcoArmor PDC performance verification

Version 2026.27-3 additionally skips typed marker lookup on an empty PDC, builds
the per-slot set list only after finding a registered set, counts directly into an
ordered map, appends slot holders directly, and allocates player cache state only
when it changes. Empty equipment still reaches the existing unequip/event logic.
The November 16 probe adds explicit AIR, empty-PDC and unrelated-PDC fixtures.
Its main benchmark now has 12 scan fixtures. `/ecoarmorprobe focused` supplements
the original reflective benchmark with direct installed-plugin calls: all six
fixtures receive 500,000 warmup calls before measurement; 24 rounds rotate fixture
order with 20,000 calls each. Keep both datasets when assessing timing differences.
The 2026.27-3 audit uses the original 2026.27-2 JAR as oracle and baseline on59603df.
See the delivered November 16 report for measurements and remaining limitations.

Version 2026.27-1 introduced read-only item PDC marker reads. Version 2026.27-2
reuses five immutable marker keys owned by the plugin instance, shares the existing
ordered equipment count between full and partial sets, and skips display Meta reads
when set/crystal/shard markers are all absent. Presence-only prefiltering retains
the old handling for malformed typed markers. Item values and definitions remain live.
The scan uses a list view of the core's freshly returned equipment array, avoiding
another array copy. Both entity and player paths were checked on96ca96b; their
existing asBukkitCopy/asCraftMirror conversion behavior is preserved.
There is no cross-call item cache, polling change, holder/hash shortcut, or scheduler change.

The original Folia source requires `SchedulerHelper`, which is absent from the public
libreforge Maven dependency. Extract just this compile-time helper from the patched
server dependency's **unshaded** `core/common/build/libs/common-2026.27.jar`
(Python standard library only):

```text
python verification/prepare_scheduler_api.py <unshaded-common.jar> <scheduler-api.jar>
gradlew.bat clean check shadowJar -Plibreforge-api-jar=<absolute-scheduler-api.jar>
```

Do not use a shaded standalone libreforge JAR here: its relocated Kotlin metadata
cannot supply the original Kotlin API, even when only the helper is extracted.
The extracted helper is `compileOnly`; the server still supplies libreforge at runtime.

`EcoArmorProbe.java` is a separate diagnostic plugin, never part of EcoArmor's JAR.
Compile it against the exact isolated core, libraries, eco, libreforge and candidate
EcoArmor. Start that local server with
`-Decoarmor.baseline=<absolute-original-EcoArmor.jar>`. Its child classloader loads only
baseline ArmorUtils/ArmorDisplay classes and delegates other types to the running candidate.
This oracle is for correctness comparisons. Its ArmorSet calls resolve to the
running plugin, so **do not use the oracle for full-path performance claims**.

The regression command is `/ecoarmorprobe regression` from a real test player.
Fixture sets `probe_a`/`probe_b` must cover 4/2-piece full sets, stacked/highest partial
holders and full-set partial suppression on/off. Automatic EcoArmor providers are
unregistered only in this isolated diagnostic process, avoiding duplicate events.
The probe compares marker results/exceptions, read-only item bytes, ordered holders,
live players and armor stands, definition replacement, tier attributes, and deferred
equip/unequip sequences including a same-tick change reverted before delivery.
The supplied `enderite` definition also exercises native full/partial/advanced holders.
The old/new attribute rounds force effects to refresh, assert +4 max health
for the regular full set, and assert the baseline is restored after unequipping.
The -2 probe additionally checks 1296 complete holder combinations over two set
requirements, and 528 display-byte/exception pairs (33 fixtures, eight configuration
combinations, with/without a viewer). It issues the actual reload command and checks
that a retained item resolves the new definition. Native inventory packet smoke sends
all 30 original-success non-AIR display fixtures, including crystals/shards/combinations.
Two original-exception malformed fixtures remain direct comparisons only. Packet
receipt is not a visual-client screenshot or complete packet-equivalence assertion.
`/ecoarmorprobe gates` is RED on -1 and GREEN on -2 for repeated marker-key construction
and unmarked display Meta reads. The key factory is replaced only in the diagnostic
process and restored immediately; no instrumentation is bundled in the product.

`/ecoarmorprobe bench` measures complete `getActiveHolders` calls on a platform thread
using detached real CraftItemStacks and test equipment/entity proxies. It never reads
a live player/inventory from that thread. Install the actual original/candidate JAR
in four separate complete processes in old/new/new/old order, with identical dependencies
and fixtures. Set `-Decoarmor.variant=old` or `new` on each process. Only the running
plugin's implementation is benchmarked; the child-classloader oracle is never timed.
Each scan fixture has 50,000 warmup calls and 16 samples of 5,000 calls per process.
Display measurements include cloning the detached input before each call, with
10,000 warmup calls and 12 samples of 2,000 calls per fixture/process. Only the
running plugin implementation is timed, with no live player passed to display.
ThreadMXBean allocation counters must be supported and nonnegative.
`analyze-20261110.py <evidence-directory>` validates all four finished logs and computes
per-fixture medians, retaining per-process medians to reveal timing variation.
The original `analyze.py` and RESULTS-20261023.md refer to the historical -1 benchmark.

These measurements are per-call allocation and wall-clock microbenchmarks, not
whole-server CPU, resident heap, production MSPT, or a production saving guarantee.
The original production JAR is the oracle; recompiling the baseline is not a substitute.

### 2026.27-6 build

Use the included compile-only SchedulerHelper API and the expanded mizuki 26.2 server
JAR from commit f44c27e. The latter is the file under `versions/26.2`, with SHA-256
`de89a7a8bd0611b2e3fd1c14f0905748c8765a1e1e1b2623d7ab43a5e9493981`.

```powershell
$scheduler = (Resolve-Path -LiteralPath 'verification/build-api/scheduler-api.jar').Path
$core = (Resolve-Path -LiteralPath '<isolated-server>/versions/26.2/shiroha-26.2.jar').Path
.\gradlew.bat clean check shadowJar "-Plibreforge-api-jar=$scheduler" "-Pmizuki-core-jar=$core"
```

Use the root `build/libs/EcoArmor-2026.27-6-all.jar`. The Java target is 21, the
Paper API is 1.21.8, and the isolated mizuki runtime uses Java 25. The core is a
compile-only dependency. At runtime the empty-armor optimization checks the
server build and equipment implementation before using native slots. The Bukkit
snapshot remains the fallback.

For reload retention, compare the published -4 JAR and the candidate on the same
isolated core and fixtures. Add and remove four distinct set/tier IDs via
`/ecoarmor reload`. At each removal, compare eco's registered keys and weak
references after GC. Prime the lookup cache with regular armor, advanced armor,
shards and crystals before removing each ID. Check the live heap and exercise
restoring a retired ID, removing a slot from a still-active set, and retaining
a replacement registration owned by another caller.

For the alias case, register an alias for the original advanced item and replace
its original key with a different object. Retire the definition and compare the
exact objects under both keys. EcoArmor verifies ownership and removes the
original entry in one operation against eco's concurrent item registry. The
compatible registry layout is verified on eco 2026.28. If unavailable, EcoArmor
logs a warning and leaves the external registry entries in place.
