# Blender IK provenance and public artifact verification

The inherited Java solver was source-revalidated on 2026-10-15 against official
Blender revision `67807e1800cc48cc7bff3c793525e1179a4d64ca`.
Its original translation revision was not recorded and remains unknown.
The exact upstream paths and retained copyright are listed in
`third_party/blender_ports.json` and each derived Java file.

The stiffness correction uses `IK_STRETCH_STIFF_EPS = 0.01f` from
[`IK_solver.h`](https://github.com/blender/blender/blob/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/extern/IK_solver.h),
as applied by `IK_SetStiffness` in
[`IK_Solver.cpp`](https://github.com/blender/blender/blob/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_Solver.cpp).
The translation minimum `0.001f` is a separate constant.

## Public Preview 2, checked 2026-10-17

- Source: `80a1d8924e8aa798cffe7c64c2cfbf9020ed4644`.
- Actual anonymous product download:
  `05ae129423604889fd75459da1fb3dd45b451c092a25ab930c99b4d862d3606e`.
- Complete source archive:
  `1a0ca32d48a029573aba7d63b5cdddb95fab8bef63a1e1bcbf2adfa9e97faebd`.
- Standalone official Blender solver reference:
  `292418248bf1dc660e49aca6a7f9e46d51baaa2b436104d250efbd187c72b348`.
- Reference Eigen revision: `3147391d946bb4b6c68edd901f2add6ac1f31f8c`.

All 22 numerical comparison cases pass on the actual downloaded product, with
the product JAR first on the classpath and no class overlay. The same geometry
and public C-API operations are passed to official `intern/iksolver` and the
Java port. Inputs follow the C-API's float precision and matrix conventions.
The checks require finite values, matching segment counts and convergence
results. The largest observed difference is `2.90000001e-8`, below the unchanged
`1e-6` comparison threshold. The JAR digest is unchanged after the comparison.
The test JVM is Java 25 and its JOML dependency is 1.10.9.

Coverage includes hinge, elbow, swing and spherical joints, stretch stiffness,
joint limits, translation-only chains, orientation goals, pole constraints,
weighted branch goals and three captured rig-chain inputs. This is a numerical
solver comparison, not a complete Blender/BBS rig, editor or recording test.
The former private installation record is not used as a public artifact claim.

The derived source retains `GPL-2.0-or-later`, original NaN Holding BV notices,
the pinned upstream paths and BBS modification dates. Both bundled GPL copies
match the complete unmodified text from
[GNU GCC](https://github.com/gcc-mirror/gcc/blob/b3a3ecc460237948d924bca6cb8fe15622e5025b/COPYING),
SHA `231f7edcc7352d7734a96eef0b8030f77982678c516876fcb81e25b32d68564c`.
