# Native Windows failure and engine control

The unchanged0e25 BBS product in run39022117580 again exits with0xC0000005
before gametests. The postmortem control succeeds and restores the registry;
only its own deliberate-fault dumps exist. No JVM dump is captured. Separate
unchanged0e25 run39022119119 passes all three jobs. Neither outcome fixes or
establishes the cause of the earlier native game failures.

## Minecraft/Fabric without BBS

Source3f5dd2a3d adds an explicit engine-only production mode through Loom's mods
collection. Its control test JAR contains only the control class, fabric.mod.json
and a manifest. It asserts production mode, no bbs/bbsdev mod and no BBSMod class
resource. It creates a real world and requests three resource reloads.
No companion, BBS initializer, BBS mixin or BBS resource pack is selected.

Run39022532927 passes on Linux: Minecraft26.3, Java25.0.4.1+1-LTS,
Mesa25.2.8/llvmpipe LLVM20.1.2, all three reloads, BBS absent. On Windows,
the loader lists the control/Fabric mods and no bbs. The initial world image
exists and was inspected; the test's BBS-absence assertions precede that image.
The JVM then exits with0xC0000005 during the first resource reload, before the
final control marker. The observed Windows renderer is llvmpipe LLVM22.1.1.
This demonstrates the same exit code without BBS. It does not establish that
every earlier BBS failure has the same cause, or identify a faulty driver/JVM.
Raw receipt: vanilla-control-39022532927.json in the public audit evidence directory.

## Early exception capture

Run39023294185 attaches signed ProcDump to the marked engine-only JVM, PID3536.
Eight real first-chance access-violation dumps are decoded successfully by CDB.
They show low-address null reads in dynamically generated code; the process
continues afterward. The eight-dump limit is reached at10:03:38, thirteen seconds
before the actual0xC0000005 process exit. These are not the terminal-fault stack.

Source7afa454f2 uses smaller triage dumps, a128-dump limit, an explicit failure
if that limit is exhausted, and analysis of the newest sixteen captured dumps.
All raw captures remain in RUNNER_TEMP; metadata lists the full capture set.
Run39023834565 tests the extended capture. The original native cause is still open.
Handled/first-chance exceptions must never be relabelled as terminal game faults.

The source-build Windows job of39023834565 now retains capture through normal
game exit0. PID2548 produces65 triage captures within the128 limit; the latest
sixteen decode successfully. The actual log records normal termination and the
limit not being reached. Minecraft/Fabric completes all three reloads without
BBS, using Mesa26.0.3/git1897b18965, llvmpipe LLVM22.1.1 and Java25.0.4.1+1-LTS.
No terminal fault occurs in this monitored case; the original cause remains open.
Raw memory is absent from the downloaded artifact. This is successful capture
coverage for this run, not a game-crash fix. The frozen-control job is still pending.
Receipt: firstchance-extended-39023834565.json in the public audit evidence directory.
