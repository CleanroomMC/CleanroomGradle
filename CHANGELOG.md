# Changelog

## 0.17.4 - 2026-09-24

## Bug Fix

- Update installer to fix local malformed builds *[commit by [@Rongmario](https://github.com/Rongmario) in [a405b5e](https://github.com/CleanroomMC/CleanroomGradle/commit/a405b5e6ac9ccc946fcce46d647de5e3d7d7de94)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.17.3...0.17.4

## 0.17.3 - 2026-09-24

## Bug Fix

- Register the minecraft patch dev sources with their producing task *[commit by [@Rongmario](https://github.com/Rongmario) in [b8812f5](https://github.com/CleanroomMC/CleanroomGradle/commit/b8812f5d578265f113c09a58b063393333174f23)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.17.2...0.17.3

## 0.17.2 - 2026-09-22

## Feature

- Embed the universal jar in the mmc pack unless the build uploads it *[commit by [@Rongmario](https://github.com/Rongmario) in [76357d4](https://github.com/CleanroomMC/CleanroomGradle/commit/76357d465ad0edd61311258b2e52b98979f938df)]*
- Carry project, included build and file dependencies in distributions *[commit by [@Rongmario](https://github.com/Rongmario) in [a922f67](https://github.com/CleanroomMC/CleanroomGradle/commit/a922f67579e1f610e0a4b29e634a1fd0e6642467)]*

## Bug Fix

- Local dependencies still need url member in mmc patches *[commit by [@Rongmario](https://github.com/Rongmario) in [c330f8d](https://github.com/CleanroomMC/CleanroomGradle/commit/c330f8da949b98d6ba520f4097baa66b4bfa2396)]*

## Testing

- Keep the jdk 28 toolchain test from resolving a compiler *[commit by [@Rongmario](https://github.com/Rongmario) in [df5243e](https://github.com/CleanroomMC/CleanroomGradle/commit/df5243ed094441de80f29e03d7c7e9cc8b04cad3)]*
- Expect url member on local mmc libraries *[commit by [@Rongmario](https://github.com/Rongmario) in [b59321f](https://github.com/CleanroomMC/CleanroomGradle/commit/b59321f3314f29dd7ff66667bb4bad0216f58d99)]*
- Consolidate and trim redundant tests *[commit by [@Rongmario](https://github.com/Rongmario) in [7b61a65](https://github.com/CleanroomMC/CleanroomGradle/commit/7b61a65eca159fafffc7d278bf40c3836e85e5c7)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.17.1...0.17.2

## 0.17.1 - 2026-09-21

## Bug Fix

- Pass lwjgl natives to runtime test classpath *[commit by [@Rongmario](https://github.com/Rongmario) in [39be2f8](https://github.com/CleanroomMC/CleanroomGradle/commit/39be2f87fff8c95b109dd43ce057c8785f946080)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.17.0...0.17.1

## 0.17.0 - 2026-09-21

## Feature

- Split local and Maven artifact names *[commit by [@Rongmario](https://github.com/Rongmario) in [84ca12d](https://github.com/CleanroomMC/CleanroomGradle/commit/84ca12dc262d970c3897fd73ce1254925f04cd2e)]*

## Bug Fix

- Keep loader test output out of the project directory *[commit by [@Rongmario](https://github.com/Rongmario) in [09b6388](https://github.com/CleanroomMC/CleanroomGradle/commit/09b638867181ccc70a557732a2480d808c4aaed7)]*
- Deobfuscate classified dependencies *[commit by [@Rongmario](https://github.com/Rongmario) in [8213663](https://github.com/CleanroomMC/CleanroomGradle/commit/8213663cb8eb4060e131402903bfdcac1ed72342)]*
- Point the deobf() mappings error at the userdev dependency *[commit by [@Rongmario](https://github.com/Rongmario) in [8b4ac33](https://github.com/CleanroomMC/CleanroomGradle/commit/8b4ac338260f7c0b0bb351d8136e540fda8ec06c)]*
- Stop passing client-only arguments to the dedicated server *[commit by [@Rongmario](https://github.com/Rongmario) in [6e9541d](https://github.com/CleanroomMC/CleanroomGradle/commit/6e9541dce71e671bed14d92d99e77530a1bcdfed)]*
- Give the reobfuscated jar a publishable srg classifier *[commit by [@Rongmario](https://github.com/Rongmario) in [6ee23d2](https://github.com/CleanroomMC/CleanroomGradle/commit/6ee23d2e21bfbc9b1aebb4d6f8401211e0875547)]*
- Buffer tool output and replay it only on failure *[commit by [@Rongmario](https://github.com/Rongmario) in [9c8714e](https://github.com/CleanroomMC/CleanroomGradle/commit/9c8714e8b6c84830caf0c93f4a9b45da2e76d524)]*

## Documentation

- Document the mod artifacts and how to publish both jars *[commit by [@Rongmario](https://github.com/Rongmario) in [00d072a](https://github.com/CleanroomMC/CleanroomGradle/commit/00d072a1db9c5f1b1f59577b82a5d54c22b7902a)]*

## Build and Dependencies

- Update conventions plugin & workflow to 1.1.5 *[commit by [@Rongmario](https://github.com/Rongmario) in [778bd56](https://github.com/CleanroomMC/CleanroomGradle/commit/778bd562c6564fad1e2f89907eef1a6970e4526e)]*

## First-time Contributors

- **[@github-actions[bot]](https://github.com/github-actions[bot]) made their first contribution!**

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.16.0...0.17.0

## 0.16.0 - 2026-09-12

## Feature

- Implement fluent minecraft runs registrations/configurations *[commit by [@Rongmario](https://github.com/Rongmario) in [c3ef5a8](https://github.com/CleanroomMC/CleanroomGradle/commit/c3ef5a8775579a282045d467a34ec43196e0d3b5)]*

## Bug Fix

- Set loader runs in the "cleanroom runs" task group *[commit by [@Rongmario](https://github.com/Rongmario) in [2abfd83](https://github.com/CleanroomMC/CleanroomGradle/commit/2abfd83fb10338195156db2af30380bf3cd43428)]*

## Testing

- Raise testkit daemon metaspace *[commit by [@Rongmario](https://github.com/Rongmario) in [fca479a](https://github.com/CleanroomMC/CleanroomGradle/commit/fca479ac4a537649ae545d2023167491330edf06)]*

## Build and Dependencies

- Update conventions plugin & workflow to 1.1.1 *[commit by [@Rongmario](https://github.com/Rongmario) in [fca96a0](https://github.com/CleanroomMC/CleanroomGradle/commit/fca96a0710b175c259487ccdffd85ed4bc589391)]*
- Migrate to Conventions plugin *[commit by [@Rongmario](https://github.com/Rongmario) in [37689db](https://github.com/CleanroomMC/CleanroomGradle/commit/37689dbcbb9871b8860c17be9fba996ee9ccd487)]*

## CI

- Use Conventions reusable workflows *[commit by [@Rongmario](https://github.com/Rongmario) in [fd29d82](https://github.com/CleanroomMC/CleanroomGradle/commit/fd29d82beedb44fc8b098aede7a0880efdcab206)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.15.1...0.16.0

## 0.15.1 - 2026-09-03

## Bug Fix

- Stripping, again *[commit by [@Rongmario](https://github.com/Rongmario) in [a32deb4](https://github.com/CleanroomMC/CleanroomGradle/commit/a32deb443c743fb0cc15c84d2fe76d0d06ec3c6d)]*
- Ironed out internal configuration names *[commit by [@Rongmario](https://github.com/Rongmario) in [ca5fde6](https://github.com/CleanroomMC/CleanroomGradle/commit/ca5fde6998f9d76bc08540b65d41b0a011ef2ccc)]*
- Userdev to use existing tool configurations *[commit by [@Rongmario](https://github.com/Rongmario) in [ba4523a](https://github.com/CleanroomMC/CleanroomGradle/commit/ba4523ad5714151a48e5a9bd38f0a3e83c894bd8)]*

## Testing

- Refactored once again *[commit by [@Rongmario](https://github.com/Rongmario) in [88f3dbc](https://github.com/CleanroomMC/CleanroomGradle/commit/88f3dbcd5918dff730f6500996b7676520aacfb3)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.15.0...0.15.1

## 0.15.0 - 2026-09-02

> [!WARNING]
> This version has breaking changes! More details below.

## Breaking Changes

- Activate userdev through a cleanroom.userdev dependency *[commit by [@Rongmario](https://github.com/Rongmario) in [c0fb7db](https://github.com/CleanroomMC/CleanroomGradle/commit/c0fb7dbef27f5de9af6a35d4583c0b291b944abf)]*

## Feature

- Gate stage runs behind cleanroom.loader.intermediateRuns *[commit by [@Rongmario](https://github.com/Rongmario) in [132d443](https://github.com/CleanroomMC/CleanroomGradle/commit/132d443d72ea1528f2e04f2c6c5e58d4772e9266)]*
- Publish separate cleanroom and cleanroom-userdev modules *[commit by [@Rongmario](https://github.com/Rongmario) in [e4c1582](https://github.com/CleanroomMC/CleanroomGradle/commit/e4c15827732422bab2b1c27ed130031d3e3fd691)]*
- Generate client and server binpatches into one prefixed archive *[commit by [@Rongmario](https://github.com/Rongmario) in [88f7bdc](https://github.com/CleanroomMC/CleanroomGradle/commit/88f7bdc480036b60627d79169eeda6e080be7cac)]*

## Bug Fix

- Remove foojay by default and unpin vendor from java toolchains *[commit by [@Rongmario](https://github.com/Rongmario) in [f6820ce](https://github.com/CleanroomMC/CleanroomGradle/commit/f6820cee831a7f559dac0f7a45011161eec638f1)]*
- Move to cleanroom maven, even for forge artifacts *[commit by [@Rongmario](https://github.com/Rongmario) in [439d307](https://github.com/CleanroomMC/CleanroomGradle/commit/439d307005ebbaebf0728413f087be511bc34a53)]*
- Treat Windows Ctrl+C as a clean RunMinecraft stop *[commit by [@Rongmario](https://github.com/Rongmario) in [f90b4f1](https://github.com/CleanroomMC/CleanroomGradle/commit/f90b4f171dd3c904ecb54cf03c11a1f7a26f3b10)]*

## Refactor

- Rework IntermediateProcessor into per-consumer discard edges *[commit by [@Rongmario](https://github.com/Rongmario) in [b64035f](https://github.com/CleanroomMC/CleanroomGradle/commit/b64035f2eb15f8ba5b17e5750526894be5ed75b0)]*

## Documentation

- Document environments, publishing and stage runs for 0.15.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [275b6d7](https://github.com/CleanroomMC/CleanroomGradle/commit/275b6d75924ec9a90ce596fe9aa3c2499f1edf1c)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.14.0...0.15.0

## 0.14.0 - 2026-08-28

## Other

- Fix deobf ext test *[commit by [@Rongmario](https://github.com/Rongmario) in [b0d6f96](https://github.com/CleanroomMC/CleanroomGradle/commit/b0d6f96c3bd51fe31d9606a906948de7ddca0a25)]*
- Deobfuscation on dependencies - 0.14.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [b0b30da](https://github.com/CleanroomMC/CleanroomGradle/commit/b0b30daff17838b6ba2edf6def60dcb8f7baa93d)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.7...0.14.0

## 0.13.7 - 2026-08-28

## Other

- Fixed tests *[commit by [@Rongmario](https://github.com/Rongmario) in [d5646bd](https://github.com/CleanroomMC/CleanroomGradle/commit/d5646bd1f904f6d37062a867bedf155e1bf53bbf)]*
- Bump to 0.13.7 *[commit by [@Rongmario](https://github.com/Rongmario) in [fa29fe5](https://github.com/CleanroomMC/CleanroomGradle/commit/fa29fe5a3e53ffd6c32b67f3521e785271a6ba21)]*
- Cleanup usages of gradle properties, and append any left with `cg.` *[commit by [@Rongmario](https://github.com/Rongmario) in [4a85edb](https://github.com/CleanroomMC/CleanroomGradle/commit/4a85edb809cae27c46e55ae137213b6a99a873fe)]*
- `cg.repos.enableLocal` property to allow `mavenLocal()` to work with default exclusiveContent repos *[commit by [@Rongmario](https://github.com/Rongmario) in [0b00cbb](https://github.com/CleanroomMC/CleanroomGradle/commit/0b00cbbaea6fd278cc9ec55e98ca939620cab126)]*
- Support packaging local dependencies in installer/mmczips, good for local testing *[commit by [@Rongmario](https://github.com/Rongmario) in [f400aaf](https://github.com/CleanroomMC/CleanroomGradle/commit/f400aaff94258f5dd06ba973e97421196e8d834d)]*
- Include version for final Cleanroom jar for userdevs to see in their dependencies list *[commit by [@Rongmario](https://github.com/Rongmario) in [c6b4b80](https://github.com/CleanroomMC/CleanroomGradle/commit/c6b4b80744cdf8dba7e161377374d4580f2480a9)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.6...0.13.7

## 0.13.6 - 2026-08-27

## Other

- Userdev stripping sides, and remove client-extras from server classpath - 0.13.6 *[commit by [@Rongmario](https://github.com/Rongmario) in [ba1f52e](https://github.com/CleanroomMC/CleanroomGradle/commit/ba1f52eb1814eea5f1ffa9e08fb279bb668b42f0)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.5...0.13.6

## 0.13.5 - 2026-08-27

## Other

- Use obf-remapped jar for mergetool - 0.13.5 *[commit by [@Rongmario](https://github.com/Rongmario) in [8c7e802](https://github.com/CleanroomMC/CleanroomGradle/commit/8c7e8028fa1ffadc6d4abd70cbb4fb4e71994d6f)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.4...0.13.5

## 0.13.4 - 2026-08-27

## Other

- Bump to 0.13.4, userdev please work this time *[commit by [@Rongmario](https://github.com/Rongmario) in [3c5e831](https://github.com/CleanroomMC/CleanroomGradle/commit/3c5e831c64f98999cddb335658a1994017a04074)]*
- Allocate directory for userdev files inside jar, pass the correct classpath to mergetool *[commit by [@Rongmario](https://github.com/Rongmario) in [477efde](https://github.com/CleanroomMC/CleanroomGradle/commit/477efde8639a700909b8d28dc7b925c5e6bdb01c)]*
- Installer 0.1.2 *[commit by [@Rongmario](https://github.com/Rongmario) in [98dd851](https://github.com/CleanroomMC/CleanroomGradle/commit/98dd851b33eebbca6413c1936f364533570740bd)]*
- Add native platform rules properly for installer jars *[commit by [@Rongmario](https://github.com/Rongmario) in [dc01453](https://github.com/CleanroomMC/CleanroomGradle/commit/dc01453d2e2ceee0f082bd59f1d9c01c951e57bb)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.3...0.13.4

## 0.13.3 - 2026-08-27

## Other

- Bump to 0.13.3 *[commit by [@Rongmario](https://github.com/Rongmario) in [ec85323](https://github.com/CleanroomMC/CleanroomGradle/commit/ec85323315c9c5232a46a734f15ffc55466fa0d9)]*
- Fixed parallel race-condition happening with compileJava & test task - 0.13.3 *[commit by [@Rongmario](https://github.com/Rongmario) in [931a476](https://github.com/CleanroomMC/CleanroomGradle/commit/931a476b7267dfec96e619d2452b0106042960ea)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.2...0.13.3

## 0.13.2 - 2026-08-25

## Other

- Set `zone.rong` as exclusive content for Cleanroom's repository - 0.13.2 *[commit by [@Rongmario](https://github.com/Rongmario) in [7f51065](https://github.com/CleanroomMC/CleanroomGradle/commit/7f51065ae379623c08c392d4d7f5c2b12f637528)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.1...0.13.2

## 0.13.1 - 2026-08-24

## Other

- Produce thin mmc zip for installer to pack, updates installer default version - 0.13.1 *[commit by [@Rongmario](https://github.com/Rongmario) in [2f88e8e](https://github.com/CleanroomMC/CleanroomGradle/commit/2f88e8e463629f7c6a119ee7887c29b8cfda1ede)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.0...0.13.1

## 0.13.0 - 2026-08-24

## Other

- SSoT-ize. For things such as Java, ASM versions & dependencies - 0.13.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [3535d5a](https://github.com/CleanroomMC/CleanroomGradle/commit/3535d5aa2e4d76088e99153c17761a2f63f66eb7)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.12.0...0.13.0

## 0.12.0 - 2026-08-24

## Other

- Bump to 0.12.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [41a52cb](https://github.com/CleanroomMC/CleanroomGradle/commit/41a52cbc311d80d277bab7d5c32cd0de07479a9c)]*
- Couple configs of multiple distribution methods together *[commit by [@Rongmario](https://github.com/Rongmario) in [1aba66c](https://github.com/CleanroomMC/CleanroomGradle/commit/1aba66c8ac2513d3788f251ab1fb9b8f1a6e1492)]*
- Add default repositories to projects in a more compatible way *[commit by [@Rongmario](https://github.com/Rongmario) in [8000b38](https://github.com/CleanroomMC/CleanroomGradle/commit/8000b38cb3b66b93c16a3b8154476fbc8279266b)]*
- Moved ASM forced resolution strategy into ToolConfigs class *[commit by [@Rongmario](https://github.com/Rongmario) in [0c48bb0](https://github.com/CleanroomMC/CleanroomGradle/commit/0c48bb02dfd09e4fc3c2b17effcf3a18fa10ac37)]*
- Force ASM 9.10.1 onto Forge tooling *[commit by [@Rongmario](https://github.com/Rongmario) in [48a137c](https://github.com/CleanroomMC/CleanroomGradle/commit/48a137c4df2fb70b1b14458f3c4f46ff817abc59)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.11.0...0.12.0

## 0.11.0 - 2026-08-23

## Other

- Update README *[commit by [@Rongmario](https://github.com/Rongmario) in [db9cba3](https://github.com/CleanroomMC/CleanroomGradle/commit/db9cba3847d398b9521adc9b8f42a9f96696a80b)]*
- IO cleanup - Bump to 0.11.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [eb4ccd2](https://github.com/CleanroomMC/CleanroomGradle/commit/eb4ccd2e5be369237c2f47e3dfa032c87fd57e1f)]*
- Implement metadata injection without relying on MCInjector *[commit by [@Rongmario](https://github.com/Rongmario) in [e6bfe48](https://github.com/CleanroomMC/CleanroomGradle/commit/e6bfe48e0cc0a5e040954903c4cde3ebbb2a7b64)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.4...0.11.0

## 0.10.4 - 2026-08-22

## Other

- Fixed bin patches not being in notch names - 0.10.4 *[commit by [@Rongmario](https://github.com/Rongmario) in [7ec1c8b](https://github.com/CleanroomMC/CleanroomGradle/commit/7ec1c8b2a678b4cfea10d8450732a5eeb0c73582)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.3...0.10.4

## 0.10.3 - 2026-08-22

## Other

- I hate handling Mojang natives - 0.10.3 *[commit by [@Rongmario](https://github.com/Rongmario) in [85b101f](https://github.com/CleanroomMC/CleanroomGradle/commit/85b101ffb73a28163fe4e7e189326ef316cfb12b)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.2...0.10.3

## 0.10.2 - 2026-08-21

## Other

- Ensure no duplicates and outdated artifacts gets in the installer profile - 0.10.2 *[commit by [@Rongmario](https://github.com/Rongmario) in [c35db55](https://github.com/CleanroomMC/CleanroomGradle/commit/c35db55623785f34909c83e28ee09fadbcfda682)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.1...0.10.2

## 0.10.1 - 2026-08-21

## Other

- Bump to 0.10.1 *[commit by [@Rongmario](https://github.com/Rongmario) in [8cc40b0](https://github.com/CleanroomMC/CleanroomGradle/commit/8cc40b06b34e810c889e0a687ab944c1dd7573e3)]*
- Add specific Mojang case for `java3d` and `lzma` artifacts *[commit by [@Rongmario](https://github.com/Rongmario) in [6f3ce61](https://github.com/CleanroomMC/CleanroomGradle/commit/6f3ce6178e482bf94eea05c668550a98c1245007)]*
- Make `WriteInstallProfile#getVersionMeta` internal and untrack it for gradle *[commit by [@Rongmario](https://github.com/Rongmario) in [d46860e](https://github.com/CleanroomMC/CleanroomGradle/commit/d46860e1bf43cd9ff73e14825b4735545cd2eb7b)]*
- Fix local jars not having the right file name for installers *[commit by [@Rongmario](https://github.com/Rongmario) in [a7a220d](https://github.com/CleanroomMC/CleanroomGradle/commit/a7a220d061a78bf7a39084cc25efbca96576eacf)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.0...0.10.1

## 0.10.0 - 2026-08-21

## Other

- Allow string values for enum gradle properties - Bump to 0.10.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [c026053](https://github.com/CleanroomMC/CleanroomGradle/commit/c026053c970b74e16ec83fd4d6d3c2f9447b82d0)]*
- Split up tests *[commit by [@Rongmario](https://github.com/Rongmario) in [f57a67c](https://github.com/CleanroomMC/CleanroomGradle/commit/f57a67c8e354d08125f7d8916eea45f721e4290f)]*
- Cleanup and organize tasks + extensions + deduplicating properties *[commit by [@Rongmario](https://github.com/Rongmario) in [93a52bb](https://github.com/CleanroomMC/CleanroomGradle/commit/93a52bb35027e9e4ca116c7b4c7222e821ee8813)]*
- Javadoc cleanup + `defaultLogFile` helper for `MavenJarExec` tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [964d3e9](https://github.com/CleanroomMC/CleanroomGradle/commit/964d3e92d24dc829f14dc12a99385b34a86f1a9c)]*
- Remove "1.12.2-" prepend in installation profile's id *[commit by [@Rongmario](https://github.com/Rongmario) in [1ea2da8](https://github.com/CleanroomMC/CleanroomGradle/commit/1ea2da8c213a69469f25efca5de4f2493a9427a3)]*
- "setup" task for loader + userdev with MaintenanceTasks *[commit by [@Rongmario](https://github.com/Rongmario) in [451811a](https://github.com/CleanroomMC/CleanroomGradle/commit/451811ad27d4d507c9904b58b75fed700195a10d)]*
- Allow "dirty" files to be warned and moved when patches are applied *[commit by [@Rongmario](https://github.com/Rongmario) in [0f88d5d](https://github.com/CleanroomMC/CleanroomGradle/commit/0f88d5d1ad354336b14ba9c7bb98fd3b68d09765)]*
- Further development on mmc pack/installer publishing *[commit by [@Rongmario](https://github.com/Rongmario) in [d5feb89](https://github.com/CleanroomMC/CleanroomGradle/commit/d5feb89cfcc70563d457898da653dac691ff8028)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.9.1...0.10.0

## 0.9.1 - 2026-08-19

## Other

- Fixed orphaned nested members staying after stripping parents - 0.9.1 beta *[commit by [@Rongmario](https://github.com/Rongmario) in [51c182c](https://github.com/CleanroomMC/CleanroomGradle/commit/51c182cbfbcb48cdc68f457bc81821c9e9463c73)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.9.0...0.9.1

## 0.9.0 - 2026-08-19

## Other

- Fix failing test *[commit by [@Rongmario](https://github.com/Rongmario) in [2bee11e](https://github.com/CleanroomMC/CleanroomGradle/commit/2bee11e7b43f2a80219e92bf3f6507853d17cc50)]*
- Work on publishing mmc packs - bump to 0.9.0 alpha *[commit by [@Rongmario](https://github.com/Rongmario) in [cd8c560](https://github.com/CleanroomMC/CleanroomGradle/commit/cd8c5609d29033deb8666c4d03e40f62c22f5acd)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.8.1...0.9.0

## 0.8.1 - 2026-08-19

## Other

- Re-push 0.8.1 with v2 Versioning gradle plugin *[commit by [@Rongmario](https://github.com/Rongmario) in [fef2d5c](https://github.com/CleanroomMC/CleanroomGradle/commit/fef2d5c05ceb8b5417ad1422ec513a5817b228df)]*
- Update README *[commit by [@Rongmario](https://github.com/Rongmario) in [8b2463d](https://github.com/CleanroomMC/CleanroomGradle/commit/8b2463db958da0b0d075313e68556a07d40fb449)]*
- Update ASM, added experimental `RunMinecraft` `ignoreExitValue = true` - 0.8.1 *[commit by [@Rongmario](https://github.com/Rongmario) in [66cbd57](https://github.com/CleanroomMC/CleanroomGradle/commit/66cbd575c36e6ed5a49d182393d606da2efc14f0)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.8.0...0.8.1

## 0.8.0 - 2026-08-18

## Other

- Re-did tests *[commit by [@Rongmario](https://github.com/Rongmario) in [a1b4edb](https://github.com/CleanroomMC/CleanroomGradle/commit/a1b4edb9a366c626d62badad1da44c11ba5755e8)]*
- Bump to 0.8.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [1faec9e](https://github.com/CleanroomMC/CleanroomGradle/commit/1faec9eb2512f88e97129cf2f6b0dc0178e7deaf)]*
- Do not track RunMinecraft tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [bb65e16](https://github.com/CleanroomMC/CleanroomGradle/commit/bb65e16df1639686ceffdca9884bc9eef01380d6)]*
- Everything everywhere all at once *[commit by [@Rongmario](https://github.com/Rongmario) in [4368310](https://github.com/CleanroomMC/CleanroomGradle/commit/436831040bf10403a00ab542ae03c52dc9ed5236)]*
- DownloadsAssets cleanup + tests *[commit by [@Rongmario](https://github.com/Rongmario) in [e472c6f](https://github.com/CleanroomMC/CleanroomGradle/commit/e472c6f077c7286103f4f8d0547a249fac4840b7)]*
- Cleanups on apply/generate binpatch tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [47917a3](https://github.com/CleanroomMC/CleanroomGradle/commit/47917a3fe100db818073cfb97e6ff959ca687c6a)]*
- Allow ATs to cache via gradle *[commit by [@Rongmario](https://github.com/Rongmario) in [dfd8a62](https://github.com/CleanroomMC/CleanroomGradle/commit/dfd8a62dde17f115cdd05d9ef648c02bdfe24916)]*
- Zip normalization streams one entry at one time *[commit by [@Rongmario](https://github.com/Rongmario) in [fba6fe8](https://github.com/CleanroomMC/CleanroomGradle/commit/fba6fe8a9c27885858fa43f833cc8bb7f2f81d6b)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.7.0...0.8.0

## 0.7.0 - 2026-08-17

## Other

- Bump to 0.7.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [2c0faef](https://github.com/CleanroomMC/CleanroomGradle/commit/2c0faef7d899ea00dadbd52b65dc2c31aa28c375)]*
- Allow distributed jars to be built after `assemble` *[commit by [@Rongmario](https://github.com/Rongmario) in [7e94184](https://github.com/CleanroomMC/CleanroomGradle/commit/7e941842f88b12489b16f03741a578740096e82e)]*
- Fixed Download tasks never being marked as up-to-date *[commit by [@Rongmario](https://github.com/Rongmario) in [9d239f6](https://github.com/CleanroomMC/CleanroomGradle/commit/9d239f6d531cd5e063ccbe70c424b66620d318ab)]*
- Allows removal of intermediary artifacts *[commit by [@Rongmario](https://github.com/Rongmario) in [108ec28](https://github.com/CleanroomMC/CleanroomGradle/commit/108ec280ff7398facd43506ebc19a526e97fa4eb)]*
- Allow `RunMinecraft` to use `joinLibraryPath` *[commit by [@Rongmario](https://github.com/Rongmario) in [5ce1653](https://github.com/CleanroomMC/CleanroomGradle/commit/5ce1653c74f83f58bec68febe40dd2ac45fb8a65)]*
- JoinLibraryPath *[commit by [@Rongmario](https://github.com/Rongmario) in [58f0f2e](https://github.com/CleanroomMC/CleanroomGradle/commit/58f0f2ecb88856ed85e7f42b9a3246ef0e816f3d)]*
- Allow arguments to be added to NsightExec tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [cddfe72](https://github.com/CleanroomMC/CleanroomGradle/commit/cddfe72c85f43208016ce0b93d70627ec3f19a19)]*
- Select correct files to be excluded from the universal jar *[commit by [@Rongmario](https://github.com/Rongmario) in [4f213af](https://github.com/CleanroomMC/CleanroomGradle/commit/4f213af95e74f1b9cc1678268b00e4ab2dfca988)]*
- Conform AT task to new tooling conventions *[commit by [@Rongmario](https://github.com/Rongmario) in [c88302c](https://github.com/CleanroomMC/CleanroomGradle/commit/c88302c8119b0753c8e208d23e7c8cb720d5aa9a)]*
- README *[commit by [@Rongmario](https://github.com/Rongmario) in [4fc8c8b](https://github.com/CleanroomMC/CleanroomGradle/commit/4fc8c8b2ea83e1dcaded341cc93ee2598b75832a)]*
- Cleanups in buildscript *[commit by [@Rongmario](https://github.com/Rongmario) in [85ff88f](https://github.com/CleanroomMC/CleanroomGradle/commit/85ff88ff392531bff0174e21b8362de037922305)]*
- Remove `FORGE` from available environments *[commit by [@Rongmario](https://github.com/Rongmario) in [e76d8e5](https://github.com/CleanroomMC/CleanroomGradle/commit/e76d8e51709db279255e2341fbbc381f2b2fdba8)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.6.0...0.7.0

## 0.6.0 - 2026-08-13

## Other

- Hook up ApplyDiffs - 0.6.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [d5dd0d1](https://github.com/CleanroomMC/CleanroomGradle/commit/d5dd0d1e53f6628ec6b16a68560f4fe854a9116b)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.5.0...0.6.0

## 0.5.0 - 2026-08-13

## Other

- Allow the clean task to clean CG global/local caches - Bump to 0.5.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [e1faab1](https://github.com/CleanroomMC/CleanroomGradle/commit/e1faab1ff3a7edd70a84296fdc5e298125e44e66)]*
- Run two passes for SRG/MCP AT names, resolves #2 *[commit by [@Rongmario](https://github.com/Rongmario) in [649cc60](https://github.com/CleanroomMC/CleanroomGradle/commit/649cc600a86e4f219eba1f343cf6385fcd336719), issue by [@Ecdcaeb](https://github.com/Ecdcaeb)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.8...0.5.0

## 0.4.8 - 2026-08-12

## Other

- Inject `MethodsReturnNonnullByDefault` file into sources - 0.4.8 *[commit by [@Rongmario](https://github.com/Rongmario) in [09770a4](https://github.com/CleanroomMC/CleanroomGradle/commit/09770a4517f9c13bb7e5526f5893688a55f1ac27)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.7...0.4.8

## 0.4.7 - 2026-08-12

## Other

- Bump to 0.4.7 *[commit by [@Rongmario](https://github.com/Rongmario) in [271dd13](https://github.com/CleanroomMC/CleanroomGradle/commit/271dd13837cc1addd52c00fcbe9beee58cb50aec)]*
- Fixes unzip issues *[commit by [@Rongmario](https://github.com/Rongmario) in [67f1f78](https://github.com/CleanroomMC/CleanroomGradle/commit/67f1f780a5c5edc4fc47e5bc9bd0b509e3fa78c9)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.6...0.4.7

## 0.4.6 - 2026-08-12

## Other

- Bump to 0.4.6 *[commit by [@Rongmario](https://github.com/Rongmario) in [99b59d1](https://github.com/CleanroomMC/CleanroomGradle/commit/99b59d1668b088f02aa0a8b1d80110f85dc0aecf)]*
- Cleanups and tidying up on tasks, configs registrations *[commit by [@Rongmario](https://github.com/Rongmario) in [eec38bc](https://github.com/CleanroomMC/CleanroomGradle/commit/eec38bc2a41646e9c5efcfd689cc839361d81631)]*
- Fix error that can be caused by InjectMetadata under certain circumstances *[commit by [@Rongmario](https://github.com/Rongmario) in [9b17647](https://github.com/CleanroomMC/CleanroomGradle/commit/9b17647003b7579f361a806328431cbadd1f8fbe)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.5...0.4.6

## 0.4.5 - 2026-08-12

## Other

- Bump to 0.4.5 *[commit by [@Rongmario](https://github.com/Rongmario) in [0cdfb13](https://github.com/CleanroomMC/CleanroomGradle/commit/0cdfb13ec51f442db3189a581bb78ba9916f28b0)]*
- Don't inject markers when merging jars (see: MinecraftForge/MergeTool#12) *[commit by [@Rongmario](https://github.com/Rongmario) in [6ba9a1a](https://github.com/CleanroomMC/CleanroomGradle/commit/6ba9a1a75012476beb66073feda46fc114e53fe8)]*
- Create directories for patch dev envs automatically *[commit by [@Rongmario](https://github.com/Rongmario) in [55c14f9](https://github.com/CleanroomMC/CleanroomGradle/commit/55c14f9f90c268ae4ce3128c92979c47c6ad7d34)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.4...0.4.5

## 0.4.4 - 2026-08-12

## Other

- Fixed test *[commit by [@Rongmario](https://github.com/Rongmario) in [7c29a78](https://github.com/CleanroomMC/CleanroomGradle/commit/7c29a7889a9fd79d3f8309be0100ecb5a42320cf)]*
- Include patch devenv by default for loader envs *[commit by [@Rongmario](https://github.com/Rongmario) in [844321e](https://github.com/CleanroomMC/CleanroomGradle/commit/844321e1965fe2f595617c9c291fe9cae3af735e)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.3...0.4.4

## 0.4.3 - 2026-08-11

## Other

- Bump to 0.4.3 *[commit by [@Rongmario](https://github.com/Rongmario) in [f9072fb](https://github.com/CleanroomMC/CleanroomGradle/commit/f9072fbe6de41d4f7c7d37bd1e3956971034d618)]*
- Lwjgl 3, jinput natives related fixes *[commit by [@Rongmario](https://github.com/Rongmario) in [eb3f466](https://github.com/CleanroomMC/CleanroomGradle/commit/eb3f4666afdf84af8960d0eccfd971f0992b07af)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.2...0.4.3

## 0.4.2 - 2026-08-04

## Other

- Bump to 0.4.2 *[commit by [@Rongmario](https://github.com/Rongmario) in [0eecbbd](https://github.com/CleanroomMC/CleanroomGradle/commit/0eecbbdc52b4c0e3917ccd7a198f560c43987dec)]*
- Bump initial-patches version to 1.2.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [35e1edb](https://github.com/CleanroomMC/CleanroomGradle/commit/35e1edb7ff9a342fe829aa699d0a726c0c150d23)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.1...0.4.2

## 0.4.1 - 2026-07-30

## Other

- Bump to 0.4.1 *[commit by [@Rongmario](https://github.com/Rongmario) in [9877d23](https://github.com/CleanroomMC/CleanroomGradle/commit/9877d2357c844dd17687cd4e69f9014b691c026c)]*
- Input/Patches/Output properties for patch dev envs *[commit by [@Rongmario](https://github.com/Rongmario) in [9a58e9f](https://github.com/CleanroomMC/CleanroomGradle/commit/9a58e9f0c98a086c3346473f8f84cb9ab2ec772b)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.0...0.4.1

## 0.4.0 - 2026-07-29

## Other

- Bump 0.4.0 for real this time oops *[commit by [@Rongmario](https://github.com/Rongmario) in [c7e01d5](https://github.com/CleanroomMC/CleanroomGradle/commit/c7e01d5c8fc046f023af94a792d127d97fd12113)]*
- Userdev + more experimental shit straight into production - 0.4.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [73253b6](https://github.com/CleanroomMC/CleanroomGradle/commit/73253b613f565d3500998ca3133da6bc590e6409)]*
- More proper newlines *[commit by [@Rongmario](https://github.com/Rongmario) in [1ce32d9](https://github.com/CleanroomMC/CleanroomGradle/commit/1ce32d9afb23db984f5fc947edb4492e38e406cd)]*
- Set newlines properly *[commit by [@Rongmario](https://github.com/Rongmario) in [18c8b5d](https://github.com/CleanroomMC/CleanroomGradle/commit/18c8b5da4126ea9fbd59d052c3a7bb9fed17212f)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.3.0...0.4.0

## 0.3.0 - 2026-07-19

## Other

- Re-did ids, republished previous versions *[commit by [@Rongmario](https://github.com/Rongmario) in [33aeae1](https://github.com/CleanroomMC/CleanroomGradle/commit/33aeae12132bd0b43034819f31ab322baf9e138e)]*
- Fix test *[commit by [@Rongmario](https://github.com/Rongmario) in [dbe9edb](https://github.com/CleanroomMC/CleanroomGradle/commit/dbe9edb8780352b84c13a2df95a72cc8539ab2e8)]*
- 0.3.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [2e65ec8](https://github.com/CleanroomMC/CleanroomGradle/commit/2e65ec8269a2a5d3e090b2c9447746c617dd5ef4)]*
- DistributionTasks *[commit by [@Rongmario](https://github.com/Rongmario) in [3be5e00](https://github.com/CleanroomMC/CleanroomGradle/commit/3be5e00156adbb88292ee516ea9b88f07eaeffe5)]*
- Strip, SAS, inheritance. *[commit by [@Rongmario](https://github.com/Rongmario) in [e569e99](https://github.com/CleanroomMC/CleanroomGradle/commit/e569e99e87bce7f5ece1e5bbc3d79c02f4555ddb)]*
- Stub `UserDevTasks`, for setting up mod dev envs *[commit by [@Rongmario](https://github.com/Rongmario) in [912ce03](https://github.com/CleanroomMC/CleanroomGradle/commit/912ce03afb53723cdbf2a6b318fc374d18a7570b)]*
- :afterExec`, normalize zip after ATing *[commit by [@Rongmario](https://github.com/Rongmario) in [d0847e2](https://github.com/CleanroomMC/CleanroomGradle/commit/d0847e265bcd713c007d7ebcb770ff1120109cab)]*
- :normalizeZip` *[commit by [@Rongmario](https://github.com/Rongmario) in [f0af78d](https://github.com/CleanroomMC/CleanroomGradle/commit/f0af78d18de6df85d190b1b849960816edbbffd9)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.2.0...0.3.0

## 0.2.0 - 2026-07-18

## Other

- Bump to 0.2.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [a6774b7](https://github.com/CleanroomMC/CleanroomGradle/commit/a6774b7e1ab1b67501443e91c500b92944c574d1)]*
- Versioning *[commit by [@Rongmario](https://github.com/Rongmario) in [f222f47](https://github.com/CleanroomMC/CleanroomGradle/commit/f222f47c76e3f5a0b94f37755882e991d7357030)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.1.0...0.2.0

## 0.1.0 - 2026-07-17

## Other

- Bump to 0.1.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [64a8906](https://github.com/CleanroomMC/CleanroomGradle/commit/64a89067649f5cf565eb151dba82133d386425fa)]*
- Cleanroom's run tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [26da603](https://github.com/CleanroomMC/CleanroomGradle/commit/26da60302c42a4db50a7229f558509d999828e2c)]*
- Apply settings plugin in test *[commit by [@Rongmario](https://github.com/Rongmario) in [b6a483c](https://github.com/CleanroomMC/CleanroomGradle/commit/b6a483c3d392fcbba13c1668ef337cb64118ba20)]*
- Cacheable LzmaCompress task *[commit by [@Rongmario](https://github.com/Rongmario) in [31e190d](https://github.com/CleanroomMC/CleanroomGradle/commit/31e190d2169e785a9658b60fed0d9199fe3318ad)]*
- Cleanup *[commit by [@Rongmario](https://github.com/Rongmario) in [7e2f54a](https://github.com/CleanroomMC/CleanroomGradle/commit/7e2f54aaad8632b9143b929bb151fa1e770836b6)]*
- NsightExec *[commit by [@Rongmario](https://github.com/Rongmario) in [922336b](https://github.com/CleanroomMC/CleanroomGradle/commit/922336bd836927624e374fbd60b84d5034fb13c2)]*
- Wire new things into the MCP tasks pipeline *[commit by [@Rongmario](https://github.com/Rongmario) in [0198509](https://github.com/CleanroomMC/CleanroomGradle/commit/0198509f57468158640d29450b4c0599d6bcb430)]*
- Generate launch args from VersionMeta for vanilla runs *[commit by [@Rongmario](https://github.com/Rongmario) in [0efa8ee](https://github.com/CleanroomMC/CleanroomGradle/commit/0efa8ee4fec9322f85c6fbbacf384e8c7b8a8481)]*
- Support -Pmc=<version> for vanilla tasks and per-version toolchain *[commit by [@Rongmario](https://github.com/Rongmario) in [9a33845](https://github.com/CleanroomMC/CleanroomGradle/commit/9a338458d459713d632ef251167d8b7134330d47)]*
- Add Tiny2 names source as alternative to MCP CSVs *[commit by [@Rongmario](https://github.com/Rongmario) in [7ddef61](https://github.com/CleanroomMC/CleanroomGradle/commit/7ddef61001aa2479d1da57ce639c32870a5c0d3c)]*
- Stamp and validate mapping identity in patch sets *[commit by [@Rongmario](https://github.com/Rongmario) in [220be73](https://github.com/CleanroomMC/CleanroomGradle/commit/220be736739355e7d570570604ccdb898618af81)]*
- Names and names and more names *[commit by [@Rongmario](https://github.com/Rongmario) in [bd364b4](https://github.com/CleanroomMC/CleanroomGradle/commit/bd364b44910b4a7c6ae63dbad4219f29fbd56ffa)]*
- New ext properties *[commit by [@Rongmario](https://github.com/Rongmario) in [8bd6d12](https://github.com/CleanroomMC/CleanroomGradle/commit/8bd6d12bce44396b260880a5905f58771507cdcc)]*
- `LzmaCompress` w/ IO helpers *[commit by [@Rongmario](https://github.com/Rongmario) in [0107de6](https://github.com/CleanroomMC/CleanroomGradle/commit/0107de66eea6e4206be26c07ce042dd0b88354f3)]*
- LaunchArguments utility *[commit by [@Rongmario](https://github.com/Rongmario) in [61e63a5](https://github.com/CleanroomMC/CleanroomGradle/commit/61e63a52c3080e05b5d757e281a6d4cf2909ee2e)]*
- Apply foojay-resolver w/ the new settings plugin *[commit by [@Rongmario](https://github.com/Rongmario) in [250f6f7](https://github.com/CleanroomMC/CleanroomGradle/commit/250f6f760f6135173577de8ae52d2f6d11b405c0)]*
- Update VersionMeta to include newer things *[commit by [@Rongmario](https://github.com/Rongmario) in [4ad81f0](https://github.com/CleanroomMC/CleanroomGradle/commit/4ad81f0b82b38d17492fe1a9ed52f51f730cf1aa)]*
- LzmaCompress *[commit by [@Rongmario](https://github.com/Rongmario) in [6c93b27](https://github.com/CleanroomMC/CleanroomGradle/commit/6c93b274c236ff494388b514ec9087de972c278f)]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.0.1...0.1.0

## 0.0.1 - 2026-07-15

## Other

- (For now) *[commit by [@Rongmario](https://github.com/Rongmario) in [a3b5b52](https://github.com/CleanroomMC/CleanroomGradle/commit/a3b5b52e8cd067b817f3bcba29e559da7ba91330)]*
- Publish *[commit by [@Rongmario](https://github.com/Rongmario) in [53fff19](https://github.com/CleanroomMC/CleanroomGradle/commit/53fff1919b33ebddd01f97af223b3c5059c81912)]*
- Testing *[commit by [@Rongmario](https://github.com/Rongmario) in [c3cce61](https://github.com/CleanroomMC/CleanroomGradle/commit/c3cce6168f981dea87e83ffb9ad2a6d22f51885d)]*
- Bundle 1.12.2 meta *[commit by [@Rongmario](https://github.com/Rongmario) in [3f4feb7](https://github.com/CleanroomMC/CleanroomGradle/commit/3f4feb77f11c2d998e7764c0cc6142873a9f7483)]*
- Make tasks instance-based, push for cache eligibility, update gradle *[commit by [@Rongmario](https://github.com/Rongmario) in [617e9d0](https://github.com/CleanroomMC/CleanroomGradle/commit/617e9d00fdc4ac55041066fa1346d576141857a7)]*
- New overload for `Objects::config` *[commit by [@Rongmario](https://github.com/Rongmario) in [8fb143e](https://github.com/CleanroomMC/CleanroomGradle/commit/8fb143e6e2abb994efdc85164fb3fc7d53655ebc)]*
- RunMinecraft task cleanup *[commit by [@Rongmario](https://github.com/Rongmario) in [36e5729](https://github.com/CleanroomMC/CleanroomGradle/commit/36e5729b9ace3440b5f9787822b0e21be9c10f75)]*
- Further cleanup in RemapSrg2Mcp *[commit by [@Rongmario](https://github.com/Rongmario) in [842dce2](https://github.com/CleanroomMC/CleanroomGradle/commit/842dce28bfae218d8434c4506b3b6cfd2bc03b82)]*
- Reproducibility in zip tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [d704f5b](https://github.com/CleanroomMC/CleanroomGradle/commit/d704f5bc472971471810d67e4ec5e18c9dfe44ef)]*
- Zeroed zip entry time when SplitJar *[commit by [@Rongmario](https://github.com/Rongmario) in [57b253c](https://github.com/CleanroomMC/CleanroomGradle/commit/57b253ccf68c3539dee1a73f0e47c3bd4314ea59)]*
- `GenerateBinPatches` *[commit by [@Rongmario](https://github.com/Rongmario) in [7911836](https://github.com/CleanroomMC/CleanroomGradle/commit/79118364303202a0277982bd7c19842e352d1572)]*
- Cleanup + allow exec type tasks to not disturb config cache *[commit by [@Rongmario](https://github.com/Rongmario) in [720fa27](https://github.com/CleanroomMC/CleanroomGradle/commit/720fa27b3d0734cdbf667ed665f108d3b60389e7)]*
- Value sources *[commit by [@Rongmario](https://github.com/Rongmario) in [5781c28](https://github.com/CleanroomMC/CleanroomGradle/commit/5781c2817eef4b263e45efaed93b6e0f6da5163f)]*
- Also disable caching by default on JavaExecs *[commit by [@Rongmario](https://github.com/Rongmario) in [685065c](https://github.com/CleanroomMC/CleanroomGradle/commit/685065c01626f7ba32dfaddf078f9e14d8a41f82)]*
- Update foojay resolver convention to 1.0.0 *[commit by [@Rongmario](https://github.com/Rongmario) in [b3b0ff0](https://github.com/CleanroomMC/CleanroomGradle/commit/b3b0ff0ae3396285bac210a1660f2582865a29e8)]*
- MavenJarExec rewrite *[commit by [@Rongmario](https://github.com/Rongmario) in [c02db8b](https://github.com/CleanroomMC/CleanroomGradle/commit/c02db8ba42c1d6d1fc9a1fff7cce1240b295ab3f)]*
- DownloadWithETag + readJson in IO class + cached HttpClient *[commit by [@Rongmario](https://github.com/Rongmario) in [254e9f7](https://github.com/CleanroomMC/CleanroomGradle/commit/254e9f76fa7c39b71e6e28e29b7d03929e2fb541)]*
- Use Cleanflower in Decompile task + rich javadocs for getOptions *[commit by [@Rongmario](https://github.com/Rongmario) in [6c2c7d0](https://github.com/CleanroomMC/CleanroomGradle/commit/6c2c7d01d6c690647f3c1ff4f06a0b4608b08764)]*
- Don't use build dir for patch outputs *[commit by [@Rongmario](https://github.com/Rongmario) in [ba11950](https://github.com/CleanroomMC/CleanroomGradle/commit/ba119509287328990de3a8222f6a14530af57503)]*
- Clean up diffs before generating, if needed *[commit by [@Rongmario](https://github.com/Rongmario) in [886ce3d](https://github.com/CleanroomMC/CleanroomGradle/commit/886ce3d796f63642731dc532d48129cdecec30c0)]*
- Allow patch dev tasks to be configurable to have a dependsOn task *[commit by [@Rongmario](https://github.com/Rongmario) in [aea2aa1](https://github.com/CleanroomMC/CleanroomGradle/commit/aea2aa13c16e718398574b6054c65306402ea07a)]*
- Move copying to its own task, preparing deals with whatever is before it *[commit by [@Rongmario](https://github.com/Rongmario) in [b66a09f](https://github.com/CleanroomMC/CleanroomGradle/commit/b66a09f3b367e1ef0fd91a239f42bae99c540cd5)]*
- Use eager detached configs for toolchain tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [32e4b84](https://github.com/CleanroomMC/CleanroomGradle/commit/32e4b84b75904a569a42dce4fcefe4b8c5ba42ea)]*
- `developCleanroom` config in extension *[commit by [@Rongmario](https://github.com/Rongmario) in [6b6af4b](https://github.com/CleanroomMC/CleanroomGradle/commit/6b6af4ba27ce66b303bc84f3d303dab2f99dcd58)]*
- Work on patch dev extension *[commit by [@Rongmario](https://github.com/Rongmario) in [4cc8e2d](https://github.com/CleanroomMC/CleanroomGradle/commit/4cc8e2dae0b106c4f64cb2d063a0d060c53518b4)]*
- Update vineflower *[commit by [@Rongmario](https://github.com/Rongmario) in [679058e](https://github.com/CleanroomMC/CleanroomGradle/commit/679058ec7c164d03bf4df4fcfdb3edce3dc04917)]*
- Update java-diff-utils *[commit by [@Rongmario](https://github.com/Rongmario) in [799cf9d](https://github.com/CleanroomMC/CleanroomGradle/commit/799cf9d9d9a6242c29c48a53015e9639dabe9618)]*
- Mkdirs when getting setting rundir *[commit by [@Rongmario](https://github.com/Rongmario) in [65a912a](https://github.com/CleanroomMC/CleanroomGradle/commit/65a912ace5a0fef7160dfb415dc6bb46c144169b)]*
- Remove Internal annotation from private member *[commit by [@Rongmario](https://github.com/Rongmario) in [9923091](https://github.com/CleanroomMC/CleanroomGradle/commit/99230917f2d7fb2bff5709364b0718b1c6a4106d)]*
- Default log files for more tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [fa2f88c](https://github.com/CleanroomMC/CleanroomGradle/commit/fa2f88cbd6b272a11f90d3d2eedbcf67356a908b)]*
- AccessTransform implementation *[commit by [@Rongmario](https://github.com/Rongmario) in [973b07c](https://github.com/CleanroomMC/CleanroomGradle/commit/973b07c18f391db249828f9ff494a8ec7df28e8c)]*
- Configure jar tasks for srg/mcp source sets *[commit by [@Rongmario](https://github.com/Rongmario) in [e9e1826](https://github.com/CleanroomMC/CleanroomGradle/commit/e9e1826a8b6dbb3cd2c13c126929a924641966ea)]*
- Moving packages *[commit by [@Rongmario](https://github.com/Rongmario) in [055a2c1](https://github.com/CleanroomMC/CleanroomGradle/commit/055a2c1cd22be45999a20c7211f31300a8534f0c)]*
- More gradle shenanigans *[commit by [@Rongmario](https://github.com/Rongmario) in [511764b](https://github.com/CleanroomMC/CleanroomGradle/commit/511764b9dd4c2f6fb940300df4aaf490b9430291)]*
- Fix decompile log location + set max thread count for decompilation *[commit by [@Rongmario](https://github.com/Rongmario) in [e4a8163](https://github.com/CleanroomMC/CleanroomGradle/commit/e4a8163b5a39d4fc6798ed51be14a2aa41c980bd)]*
- Use project managed providers *[commit by [@Rongmario](https://github.com/Rongmario) in [8239c23](https://github.com/CleanroomMC/CleanroomGradle/commit/8239c23ee56bc7df44cb76a82f375c72a0b1f5fd)]*
- Conform to config cache standards of not using project during task exec *[commit by [@Rongmario](https://github.com/Rongmario) in [5228137](https://github.com/CleanroomMC/CleanroomGradle/commit/5228137368e6b995372baf2be9b43ce5ce23873b)]*
- Simplify RunMinecraft task configurations *[commit by [@Rongmario](https://github.com/Rongmario) in [1e846df](https://github.com/CleanroomMC/CleanroomGradle/commit/1e846df11263323d915c6cb08fbb94c1a759d2ef)]*
- Simplified lazy toString objects *[commit by [@Rongmario](https://github.com/Rongmario) in [a7a7b1d](https://github.com/CleanroomMC/CleanroomGradle/commit/a7a7b1d791429f0d3d8148d4249c77975966e3b4)]*
- Better interface name + use helper in extension *[commit by [@Rongmario](https://github.com/Rongmario) in [8afc057](https://github.com/CleanroomMC/CleanroomGradle/commit/8afc0570cfd0b2d2f75242679c7bf6348a701b04)]*
- MCP sourceset and run tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [8ad51bc](https://github.com/CleanroomMC/CleanroomGradle/commit/8ad51bcebea4d924175d545a285fccc244136784)]*
- Added reobf srg run tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [e166b1f](https://github.com/CleanroomMC/CleanroomGradle/commit/e166b1fac82b9b411bffa024692ffa9acd488ca8)]*
- Add more SourceSet helpers *[commit by [@Rongmario](https://github.com/Rongmario) in [af798bc](https://github.com/CleanroomMC/CleanroomGradle/commit/af798bc96d8af26c4c9ae6ad26bd918f921c8019)]*
- Fixed rundir location *[commit by [@Rongmario](https://github.com/Rongmario) in [d175b8a](https://github.com/CleanroomMC/CleanroomGradle/commit/d175b8a2f49cffbc047b6a7c16ec8a3a665fe7af)]*
- Fix non-inplace variant of ApplyDiffs task *[commit by [@Rongmario](https://github.com/Rongmario) in [f4ff947](https://github.com/CleanroomMC/CleanroomGradle/commit/f4ff947372277546249bff705582a495b8b6e97e)]*
- Remove resource extraction in favour of splitting jars + srg sourceset *[commit by [@Rongmario](https://github.com/Rongmario) in [bf772b2](https://github.com/CleanroomMC/CleanroomGradle/commit/bf772b2f7c12449c298864968236127ff55db0d1)]*
- Move SourceSets helpers to its own class *[commit by [@Rongmario](https://github.com/Rongmario) in [9c05ec5](https://github.com/CleanroomMC/CleanroomGradle/commit/9c05ec5b461931340fcbecc5f3284c62450c1cdf)]*
- Rewrite and finished remapping process *[commit by [@Rongmario](https://github.com/Rongmario) in [a4ded30](https://github.com/CleanroomMC/CleanroomGradle/commit/a4ded303d22680551cffaf4b243487d5d102f0b7)]*
- Lazy configs pt.2 *[commit by [@Rongmario](https://github.com/Rongmario) in [8fed728](https://github.com/CleanroomMC/CleanroomGradle/commit/8fed728a8c2b7b91d33937c9c1d92b00a133d6b8)]*
- Resource folder helpers for SourceSets *[commit by [@Rongmario](https://github.com/Rongmario) in [6dd1f20](https://github.com/CleanroomMC/CleanroomGradle/commit/6dd1f205833e33cf78402033a79cbc5d18743d41)]*
- Remove build listener, works erroneously *[commit by [@Rongmario](https://github.com/Rongmario) in [51cf17c](https://github.com/CleanroomMC/CleanroomGradle/commit/51cf17cc0c58519277c345db5642b81ec507daa4)]*
- Make configurations lazy and not instantly resolved again *[commit by [@Rongmario](https://github.com/Rongmario) in [84b4070](https://github.com/CleanroomMC/CleanroomGradle/commit/84b40705b7ca087f2b2f2fb54d8712f30252f647)]*
- Update download plugin *[commit by [@Rongmario](https://github.com/Rongmario) in [26677c1](https://github.com/CleanroomMC/CleanroomGradle/commit/26677c1f76b1f80e242caf84a6d6d26a028a51db)]*
- Move default decompile args to task provider *[commit by [@Rongmario](https://github.com/Rongmario) in [c238f20](https://github.com/CleanroomMC/CleanroomGradle/commit/c238f202cd20b3218563c2c92cd7abcf27e032c3)]*
- Rename vanilla sourceset to not have the version appending *[commit by [@Rongmario](https://github.com/Rongmario) in [a10eee2](https://github.com/CleanroomMC/CleanroomGradle/commit/a10eee20af76327344771be92d3eef0ed305c1f4)]*
- Update gradle + cleanup env + fixed assets dl + fixed generated folder *[commit by [@Rongmario](https://github.com/Rongmario) in [192951a](https://github.com/CleanroomMC/CleanroomGradle/commit/192951a7b65cc6015ae0864570410ca9463a251c)]*
- Remove patches references from LoaderDevExtension, ready for decoupling *[commit by [@Rongmario](https://github.com/Rongmario) in [2dcc208](https://github.com/CleanroomMC/CleanroomGradle/commit/2dcc208f6d29d7c836fee487d97597761e17b40b)]*
- Remove BUKKIT from being a valid Side *[commit by [@Rongmario](https://github.com/Rongmario) in [352a758](https://github.com/CleanroomMC/CleanroomGradle/commit/352a758a7f4454b32b269f6d9aa032577984872d)]*
- Fix versioning + update to Gradle 8.6 *[commit by [@Rongmario](https://github.com/Rongmario) in [dc46d2e](https://github.com/CleanroomMC/CleanroomGradle/commit/dc46d2ec501cdfd3b17d5def2d47e4c307413b02)]*
- Use InputFiles instead of InputDirectory for non-existent directories *[commit by [@Rongmario](https://github.com/Rongmario) in [f7ea673](https://github.com/CleanroomMC/CleanroomGradle/commit/f7ea67334d43ecc123bf675ca6a365315ad1e92d)]*
- Fix various locations + turn download tasks into simple actions *[commit by [@Rongmario](https://github.com/Rongmario) in [b870886](https://github.com/CleanroomMC/CleanroomGradle/commit/b8708864384eea8059c73f0d84764ba1d82004d1)]*
- Maven-publish plugin for the time being to publish to mavenLocal *[commit by [@Rongmario](https://github.com/Rongmario) in [1cee714](https://github.com/CleanroomMC/CleanroomGradle/commit/1cee7145e2c48596a7952775bf76b56a3416e0a4)]*
- Add this to please gradle's java toolchains api *[commit by [@Rongmario](https://github.com/Rongmario) in [c0356c4](https://github.com/CleanroomMC/CleanroomGradle/commit/c0356c4d5986a31d45c2bef8b145ae6b0c56eb89)]*
- Processing libraries depends on downloadVersionMeta *[commit by [@Rongmario](https://github.com/Rongmario) in [6ca65ce](https://github.com/CleanroomMC/CleanroomGradle/commit/6ca65ce3707a8909dce1b76d0f2221e99c9aadcc)]*
- Thanks git *[commit by [@Rongmario](https://github.com/Rongmario) in [912a4ab](https://github.com/CleanroomMC/CleanroomGradle/commit/912a4ab3a6ecf60793651eb9fd186b9524c5c89a)]*
- RunSrgClient/Server *[commit by [@Rongmario](https://github.com/Rongmario) in [40c57ed](https://github.com/CleanroomMC/CleanroomGradle/commit/40c57eded09e8469602573e0c7398a02147c2f6a)]*
- Use vanilla config for classpath *[commit by [@Rongmario](https://github.com/Rongmario) in [f0d5bf7](https://github.com/CleanroomMC/CleanroomGradle/commit/f0d5bf770aba62f463e5a0166ba2003dba8b6bd3)]*
- RunMcpClient/Server + made minecraft sourceset's name version dependent *[commit by [@Rongmario](https://github.com/Rongmario) in [a07c6a6](https://github.com/CleanroomMC/CleanroomGradle/commit/a07c6a63c32a5d667a37ccb4558789ed606aa9cf)]*
- Tasks named/configure *[commit by [@Rongmario](https://github.com/Rongmario) in [2603f1d](https://github.com/CleanroomMC/CleanroomGradle/commit/2603f1de56b1167779317ada58458ea19aa12995)]*
- UTF-8 encoding when running minecraft *[commit by [@Rongmario](https://github.com/Rongmario) in [06ed832](https://github.com/CleanroomMC/CleanroomGradle/commit/06ed8325321316c7de52e7abfc9bdd8822801b5b)]*
- Locations needs to be documented and decided altogether *[commit by [@Rongmario](https://github.com/Rongmario) in [4da323a](https://github.com/CleanroomMC/CleanroomGradle/commit/4da323a4e1c6d2e386437780240709eb09d909ad)]*
- Make non-native vanilla configuration transitive *[commit by [@Rongmario](https://github.com/Rongmario) in [937a041](https://github.com/CleanroomMC/CleanroomGradle/commit/937a0413fd7fbe7b9bfd5834e940ffb223d32208)]*
- Work on patched minecraft sourceset + few more helpers *[commit by [@Rongmario](https://github.com/Rongmario) in [dad4c3b](https://github.com/CleanroomMC/CleanroomGradle/commit/dad4c3bc85e22897ef64b523f62c0de2825f4616)]*
- Better run folder getter *[commit by [@Rongmario](https://github.com/Rongmario) in [e778f5a](https://github.com/CleanroomMC/CleanroomGradle/commit/e778f5a6e8f522c6fdeb0c4bc992211024922a98)]*
- RunVanillaServer *[commit by [@Rongmario](https://github.com/Rongmario) in [79af2b0](https://github.com/CleanroomMC/CleanroomGradle/commit/79af2b05f362ef7543ace4326b6c1b5a88e91cb0)]*
- RunVanillaClient + assetIndexId getter *[commit by [@Rongmario](https://github.com/Rongmario) in [eec34f9](https://github.com/CleanroomMC/CleanroomGradle/commit/eec34f915296b8c45b05a701b7384756bc20fc74)]*
- Fixes log4j-api not being upgraded as well + configs being transitive *[commit by [@Rongmario](https://github.com/Rongmario) in [7167430](https://github.com/CleanroomMC/CleanroomGradle/commit/716743066396af92878e2036d3707065a90f1e3a)]*
- Common & vanilla & mcp config tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [4c806da](https://github.com/CleanroomMC/CleanroomGradle/commit/4c806daa689eb3a1352bb651a303035e1c232bf1)]*
- Moved to Java 17 + actually buildable and runnable now (vanilla) *[commit by [@Rongmario](https://github.com/Rongmario) in [3cb73b9](https://github.com/CleanroomMC/CleanroomGradle/commit/3cb73b9e216da18a3d309014b882aafe504f8643)]*
- Latest + ASM class generation for run task + more tests *[commit by [@Rongmario](https://github.com/Rongmario) in [b6ce323](https://github.com/CleanroomMC/CleanroomGradle/commit/b6ce323c61f7b63d8b53e8864aaa0e5e978a6b75)]*
- JarExec *[commit by [@Rongmario](https://github.com/Rongmario) in [e726ef7](https://github.com/CleanroomMC/CleanroomGradle/commit/e726ef71bf71bbed17d82d0846578bd2d301e172)]*
- Amend some download tasks and constants *[commit by [@Rongmario](https://github.com/Rongmario) in [1c66c19](https://github.com/CleanroomMC/CleanroomGradle/commit/1c66c1975c1f86e95ec811a3cb52545d9299145b)]*
- Redo version JSON deserialization *[commit by [@Rongmario](https://github.com/Rongmario) in [2157528](https://github.com/CleanroomMC/CleanroomGradle/commit/2157528b61248b80c113611020e85e745db0caef)]*
- Added DiffPatch dep *[commit by [@Rongmario](https://github.com/Rongmario) in [b4e58f4](https://github.com/CleanroomMC/CleanroomGradle/commit/b4e58f44d54f4ec74c8bff0589549d2602ff0fd7)]*
- Download client/server jar tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [f1e884a](https://github.com/CleanroomMC/CleanroomGradle/commit/f1e884a30057d7f5f745dbca5f47c34eadd10451)]*
- Less schizo test rigging *[commit by [@Rongmario](https://github.com/Rongmario) in [b50d25a](https://github.com/CleanroomMC/CleanroomGradle/commit/b50d25a355455e62a269abaa42ff9f2e95cf690d)]*
- Schizo mode unit-testing gradle tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [5b82d8f](https://github.com/CleanroomMC/CleanroomGradle/commit/5b82d8f2e70a551d23239dcc1f825c4f139095b6)]*
- Deleted some remnant code + overhauled downloading of versions *[commit by [@Rongmario](https://github.com/Rongmario) in [fd3e75b](https://github.com/CleanroomMC/CleanroomGradle/commit/fd3e75b9779630b309615125c6efcc37b83d5e97)]*
- WIP Rewrite, make implementation much more gradle-esque *[commit by [@Rongmario](https://github.com/Rongmario) in [a9819a5](https://github.com/CleanroomMC/CleanroomGradle/commit/a9819a5ca17c106e78af93e9b334947c8b41bb16)]*
- Catering imaginary versions + multithreaded assets dl *[commit by [@Rongmario](https://github.com/Rongmario) in [a078a4c](https://github.com/CleanroomMC/CleanroomGradle/commit/a078a4c34edf6bce7141737eef133756a996b3eb)]*
- Implement a better downloader/file integrity checker *[commit by [@Rongmario](https://github.com/Rongmario) in [4ef1e34](https://github.com/CleanroomMC/CleanroomGradle/commit/4ef1e34ab66a6bc8cf1ba34a9e19417ee8a3c1da)]*
- Define plugin via build.gradle *[commit by [@Rongmario](https://github.com/Rongmario) in [6affc5d](https://github.com/CleanroomMC/CleanroomGradle/commit/6affc5dd2cb5fccf72dde36b371a726657af784e)]*
- Remove 1.12.2 json resource *[commit by [@Rongmario](https://github.com/Rongmario) in [ee3e126](https://github.com/CleanroomMC/CleanroomGradle/commit/ee3e126e1d271014a469c39a5317859ad3c87ade)]*
- Fixed GrabAssetsTask behaviour + fixed constants jumbled up *[commit by [@Rongmario](https://github.com/Rongmario) in [a6b39fc](https://github.com/CleanroomMC/CleanroomGradle/commit/a6b39fc1c6f0c8f669f6556ceafbe1f7744bd902)]*
- Work on mappings, successfully extract them + once again improve tests *[commit by [@Rongmario](https://github.com/Rongmario) in [aea470f](https://github.com/CleanroomMC/CleanroomGradle/commit/aea470f8dfd0c90fca8b618a07fda15acbf8fdd8)]*
- Rise and shine, its API and cleanup time *[commit by [@Rongmario](https://github.com/Rongmario) in [8a9d636](https://github.com/CleanroomMC/CleanroomGradle/commit/8a9d6368df2e7892d9d6a2cdc767fe0874175bbe)]*
- Universal jars working, the task is horrible and needs rewriting however *[commit by [@Rongmario](https://github.com/Rongmario) in [dba9740](https://github.com/CleanroomMC/CleanroomGradle/commit/dba974088cd454f0884aa186ea5e5ed19792c7af)]*
- Add our beloved ASM library to the buildscript *[commit by [@Rongmario](https://github.com/Rongmario) in [461e79d](https://github.com/CleanroomMC/CleanroomGradle/commit/461e79daaa334d0f49024bcd339a67d84b7faff1)]*
- Totally didn't realise .gitignore captured ProjectTest as well *[commit by [@Rongmario](https://github.com/Rongmario) in [09e91d5](https://github.com/CleanroomMC/CleanroomGradle/commit/09e91d5eaabca7e3a8f000fac64273eac072b970)]*
- Splitting the jar to pure and dep jars works, but not for newer jars *[commit by [@Rongmario](https://github.com/Rongmario) in [af4f009](https://github.com/CleanroomMC/CleanroomGradle/commit/af4f009a06da65e3cfa9a72a4ce0022781d7a2f9)]*
- Successfully download/get assets into/from appdata cache *[commit by [@Rongmario](https://github.com/Rongmario) in [75086d9](https://github.com/CleanroomMC/CleanroomGradle/commit/75086d9f2fe96cde286dfeff451ede4abd48296d)]*
- Check against hash before downloading client/server jars again *[commit by [@Rongmario](https://github.com/Rongmario) in [8cf3742](https://github.com/CleanroomMC/CleanroomGradle/commit/8cf37420d4fa24cbc38c4c9c9baa6b00975ba8c9)]*
- For some reason git broke and tracked .idea files *[commit by [@Rongmario](https://github.com/Rongmario) in [c3a98fe](https://github.com/CleanroomMC/CleanroomGradle/commit/c3a98fe197600b45d457ef3cd32f7de7168577e4)]*
- Successfully download minecraft client and server jar + cleaned up tests *[commit by [@Rongmario](https://github.com/Rongmario) in [5248dca](https://github.com/CleanroomMC/CleanroomGradle/commit/5248dcac29e2479679ecdbce550aa8f124ea8ee1)]*
- Differentiate different download tasks + added more constants *[commit by [@Rongmario](https://github.com/Rongmario) in [16244b4](https://github.com/CleanroomMC/CleanroomGradle/commit/16244b44d4e4dbb8832a8b042dd6c1184b405e4c)]*
- Successfully parse version, assetIndex jsons + tests *[commit by [@Rongmario](https://github.com/Rongmario) in [c81487c](https://github.com/CleanroomMC/CleanroomGradle/commit/c81487c5729ce33cb03ca727222041ca4cf848c0)]*
- Downloading manifest now working, tests have dedicated folders now *[commit by [@Rongmario](https://github.com/Rongmario) in [10d398f](https://github.com/CleanroomMC/CleanroomGradle/commit/10d398f44ab8a3a968cc58afc543398e69b4c453)]*
- Creating download tasks + add helpers *[commit by [@Rongmario](https://github.com/Rongmario) in [f39b921](https://github.com/CleanroomMC/CleanroomGradle/commit/f39b9212afd60534875d0c781dd15815b8627fa0)]*
- Test to see if the tasks are created *[commit by [@Rongmario](https://github.com/Rongmario) in [f997345](https://github.com/CleanroomMC/CleanroomGradle/commit/f9973455410d50ca4f924c5338962c21805ecfe6)]*
- RunClient + runServer tasks *[commit by [@Rongmario](https://github.com/Rongmario) in [ec1e53e](https://github.com/CleanroomMC/CleanroomGradle/commit/ec1e53e8dd187d8416b3e15a1321cc5d37a910c3)]*
- Setup commit *[commit by [@Rongmario](https://github.com/Rongmario) in [586e773](https://github.com/CleanroomMC/CleanroomGradle/commit/586e773479ba8893a87fc6d6f5f609e6df388c08)]*
- Initial commit *[commit by [@Rongmario](https://github.com/Rongmario) in [ef1bc28](https://github.com/CleanroomMC/CleanroomGradle/commit/ef1bc28ea91d9b10a1378ad6ea043f3e0f47d7c9)]*


