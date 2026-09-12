# Changelog

## 0.16.0 - 2026-09-12

## Feature

- Implement fluent minecraft runs registrations/configurations *[commit by @Rongmario in c3ef5a8]*

## Bug Fix

- Set loader runs in the "cleanroom runs" task group *[commit by @Rongmario in 2abfd83]*

## Testing

- Raise testkit daemon metaspace *[commit by @Rongmario in fca479a]*

## Build and Dependencies

- Update conventions plugin & workflow to 1.1.1 *[commit by @Rongmario in fca96a0]*
- Migrate to Conventions plugin *[commit by @Rongmario in 37689db]*

## CI

- Use Conventions reusable workflows *[commit by @Rongmario in fd29d82]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.15.1...0.16.0

## 0.15.1 - 2026-09-03

## Bug Fix

- Stripping, again *[commit by @Rongmario in a32deb4]*
- Ironed out internal configuration names *[commit by @Rongmario in ca5fde6]*
- Userdev to use existing tool configurations *[commit by @Rongmario in ba4523a]*

## Testing

- Refactored once again *[commit by @Rongmario in 88f3dbc]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.15.0...0.15.1

## 0.15.0 - 2026-09-02

> [!WARNING]
> This version has breaking changes! More details below.

## Breaking Changes

- Activate userdev through a cleanroom.userdev dependency *[commit by @Rongmario in c0fb7db]*

## Feature

- Gate stage runs behind cleanroom.loader.intermediateRuns *[commit by @Rongmario in 132d443]*
- Publish separate cleanroom and cleanroom-userdev modules *[commit by @Rongmario in e4c1582]*
- Generate client and server binpatches into one prefixed archive *[commit by @Rongmario in 88f7bdc]*

## Bug Fix

- Remove foojay by default and unpin vendor from java toolchains *[commit by @Rongmario in f6820ce]*
- Move to cleanroom maven, even for forge artifacts *[commit by @Rongmario in 439d307]*
- Treat Windows Ctrl+C as a clean RunMinecraft stop *[commit by @Rongmario in f90b4f1]*

## Refactor

- Rework IntermediateProcessor into per-consumer discard edges *[commit by @Rongmario in b64035f]*

## Documentation

- Document environments, publishing and stage runs for 0.15.0 *[commit by @Rongmario in 275b6d7]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.14.0...0.15.0

## 0.14.0 - 2026-08-28

## Other

- Fix deobf ext test *[commit by @Rongmario in b0d6f96]*
- Deobfuscation on dependencies - 0.14.0 *[commit by @Rongmario in b0b30da]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.7...0.14.0

## 0.13.7 - 2026-08-28

## Other

- Fixed tests *[commit by @Rongmario in d5646bd]*
- Bump to 0.13.7 *[commit by @Rongmario in fa29fe5]*
- Cleanup usages of gradle properties, and append any left with `cg.` *[commit by @Rongmario in 4a85edb]*
- `cg.repos.enableLocal` property to allow `mavenLocal()` to work with default exclusiveContent repos *[commit by @Rongmario in 0b00cbb]*
- Support packaging local dependencies in installer/mmczips, good for local testing *[commit by @Rongmario in f400aaf]*
- Include version for final Cleanroom jar for userdevs to see in their dependencies list *[commit by @Rongmario in c6b4b80]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.6...0.13.7

## 0.13.6 - 2026-08-27

## Other

- Userdev stripping sides, and remove client-extras from server classpath - 0.13.6 *[commit by @Rongmario in ba1f52e]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.5...0.13.6

## 0.13.5 - 2026-08-27

## Other

- Use obf-remapped jar for mergetool - 0.13.5 *[commit by @Rongmario in 8c7e802]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.4...0.13.5

## 0.13.4 - 2026-08-27

## Other

- Bump to 0.13.4, userdev please work this time *[commit by @Rongmario in 3c5e831]*
- Allocate directory for userdev files inside jar, pass the correct classpath to mergetool *[commit by @Rongmario in 477efde]*
- Installer 0.1.2 *[commit by @Rongmario in 98dd851]*
- Add native platform rules properly for installer jars *[commit by @Rongmario in dc01453]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.3...0.13.4

## 0.13.3 - 2026-08-27

## Other

- Bump to 0.13.3 *[commit by @Rongmario in ec85323]*
- Fixed parallel race-condition happening with compileJava & test task - 0.13.3 *[commit by @Rongmario in 931a476]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.2...0.13.3

## 0.13.2 - 2026-08-25

## Other

- Set `zone.rong` as exclusive content for Cleanroom's repository - 0.13.2 *[commit by @Rongmario in 7f51065]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.1...0.13.2

## 0.13.1 - 2026-08-24

## Other

- Produce thin mmc zip for installer to pack, updates installer default version - 0.13.1 *[commit by @Rongmario in 2f88e8e]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.13.0...0.13.1

## 0.13.0 - 2026-08-24

## Other

- SSoT-ize. For things such as Java, ASM versions & dependencies - 0.13.0 *[commit by @Rongmario in 3535d5a]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.12.0...0.13.0

## 0.12.0 - 2026-08-24

## Other

- Bump to 0.12.0 *[commit by @Rongmario in 41a52cb]*
- Couple configs of multiple distribution methods together *[commit by @Rongmario in 1aba66c]*
- Add default repositories to projects in a more compatible way *[commit by @Rongmario in 8000b38]*
- Moved ASM forced resolution strategy into ToolConfigs class *[commit by @Rongmario in 0c48bb0]*
- Force ASM 9.10.1 onto Forge tooling *[commit by @Rongmario in 48a137c]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.11.0...0.12.0

## 0.11.0 - 2026-08-23

## Other

- Update README *[commit by @Rongmario in db9cba3]*
- IO cleanup - Bump to 0.11.0 *[commit by @Rongmario in eb4ccd2]*
- Implement metadata injection without relying on MCInjector *[commit by @Rongmario in e6bfe48]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.4...0.11.0

## 0.10.4 - 2026-08-22

## Other

- Fixed bin patches not being in notch names - 0.10.4 *[commit by @Rongmario in 7ec1c8b]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.3...0.10.4

## 0.10.3 - 2026-08-22

## Other

- I hate handling Mojang natives - 0.10.3 *[commit by @Rongmario in 85b101f]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.2...0.10.3

## 0.10.2 - 2026-08-21

## Other

- Ensure no duplicates and outdated artifacts gets in the installer profile - 0.10.2 *[commit by @Rongmario in c35db55]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.1...0.10.2

## 0.10.1 - 2026-08-21

## Other

- Bump to 0.10.1 *[commit by @Rongmario in 8cc40b0]*
- Add specific Mojang case for `java3d` and `lzma` artifacts *[commit by @Rongmario in 6f3ce61]*
- Make `WriteInstallProfile#getVersionMeta` internal and untrack it for gradle *[commit by @Rongmario in d46860e]*
- Fix local jars not having the right file name for installers *[commit by @Rongmario in a7a220d]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.10.0...0.10.1

## 0.10.0 - 2026-08-21

## Other

- Allow string values for enum gradle properties - Bump to 0.10.0 *[commit by @Rongmario in c026053]*
- Split up tests *[commit by @Rongmario in f57a67c]*
- Cleanup and organize tasks + extensions + deduplicating properties *[commit by @Rongmario in 93a52bb]*
- Javadoc cleanup + `defaultLogFile` helper for `MavenJarExec` tasks *[commit by @Rongmario in 964d3e9]*
- Remove "1.12.2-" prepend in installation profile's id *[commit by @Rongmario in 1ea2da8]*
- "setup" task for loader + userdev with MaintenanceTasks *[commit by @Rongmario in 451811a]*
- Allow "dirty" files to be warned and moved when patches are applied *[commit by @Rongmario in 0f88d5d]*
- Further development on mmc pack/installer publishing *[commit by @Rongmario in d5feb89]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.9.1...0.10.0

## 0.9.1 - 2026-08-19

## Other

- Fixed orphaned nested members staying after stripping parents - 0.9.1 beta *[commit by @Rongmario in 51c182c]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.9.0...0.9.1

## 0.9.0 - 2026-08-19

## Other

- Fix failing test *[commit by @Rongmario in 2bee11e]*
- Work on publishing mmc packs - bump to 0.9.0 alpha *[commit by @Rongmario in cd8c560]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.8.1...0.9.0

## 0.8.1 - 2026-08-19

## Other

- Re-push 0.8.1 with v2 Versioning gradle plugin *[commit by @Rongmario in fef2d5c]*
- Update README *[commit by @Rongmario in 8b2463d]*
- Update ASM, added experimental `RunMinecraft` `ignoreExitValue = true` - 0.8.1 *[commit by @Rongmario in 66cbd57]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.8.0...0.8.1

## 0.8.0 - 2026-08-18

## Other

- Re-did tests *[commit by @Rongmario in a1b4edb]*
- Bump to 0.8.0 *[commit by @Rongmario in 1faec9e]*
- Do not track RunMinecraft tasks *[commit by @Rongmario in bb65e16]*
- Everything everywhere all at once *[commit by @Rongmario in 4368310]*
- DownloadsAssets cleanup + tests *[commit by @Rongmario in e472c6f]*
- Cleanups on apply/generate binpatch tasks *[commit by @Rongmario in 47917a3]*
- Allow ATs to cache via gradle *[commit by @Rongmario in dfd8a62]*
- Zip normalization streams one entry at one time *[commit by @Rongmario in fba6fe8]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.7.0...0.8.0

## 0.7.0 - 2026-08-17

## Other

- Bump to 0.7.0 *[commit by @Rongmario in 2c0faef]*
- Allow distributed jars to be built after `assemble` *[commit by @Rongmario in 7e94184]*
- Fixed Download tasks never being marked as up-to-date *[commit by @Rongmario in 9d239f6]*
- Allows removal of intermediary artifacts *[commit by @Rongmario in 108ec28]*
- Allow `RunMinecraft` to use `joinLibraryPath` *[commit by @Rongmario in 5ce1653]*
- JoinLibraryPath *[commit by @Rongmario in 58f0f2e]*
- Allow arguments to be added to NsightExec tasks *[commit by @Rongmario in cddfe72]*
- Select correct files to be excluded from the universal jar *[commit by @Rongmario in 4f213af]*
- Conform AT task to new tooling conventions *[commit by @Rongmario in c88302c]*
- README *[commit by @Rongmario in 4fc8c8b]*
- Cleanups in buildscript *[commit by @Rongmario in 85ff88f]*
- Remove `FORGE` from available environments *[commit by @Rongmario in e76d8e5]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.6.0...0.7.0

## 0.6.0 - 2026-08-13

## Other

- Hook up ApplyDiffs - 0.6.0 *[commit by @Rongmario in d5dd0d1]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.5.0...0.6.0

## 0.5.0 - 2026-08-13

## Other

- Allow the clean task to clean CG global/local caches - Bump to 0.5.0 *[commit by @Rongmario in e1faab1]*
- Run two passes for SRG/MCP AT names, resolves #2 *[commit by @Rongmario in 649cc60]*
    *[Fixes issue #2 by @Ecdcaeb]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.8...0.5.0

## 0.4.8 - 2026-08-12

## Other

- Inject `MethodsReturnNonnullByDefault` file into sources - 0.4.8 *[commit by @Rongmario in 09770a4]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.7...0.4.8

## 0.4.7 - 2026-08-12

## Other

- Bump to 0.4.7 *[commit by @Rongmario in 271dd13]*
- Fixes unzip issues *[commit by @Rongmario in 67f1f78]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.6...0.4.7

## 0.4.6 - 2026-08-12

## Other

- Bump to 0.4.6 *[commit by @Rongmario in 99b59d1]*
- Cleanups and tidying up on tasks, configs registrations *[commit by @Rongmario in eec38bc]*
- Fix error that can be caused by InjectMetadata under certain circumstances *[commit by @Rongmario in 9b17647]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.5...0.4.6

## 0.4.5 - 2026-08-12

## Other

- Bump to 0.4.5 *[commit by @Rongmario in 0cdfb13]*
- Don't inject markers when merging jars (see: MinecraftForge/MergeTool#12) *[commit by @Rongmario in 6ba9a1a]*
- Create directories for patch dev envs automatically *[commit by @Rongmario in 55c14f9]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.4...0.4.5

## 0.4.4 - 2026-08-12

## Other

- Fixed test *[commit by @Rongmario in 7c29a78]*
- Include patch devenv by default for loader envs *[commit by @Rongmario in 844321e]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.3...0.4.4

## 0.4.3 - 2026-08-11

## Other

- Bump to 0.4.3 *[commit by @Rongmario in f9072fb]*
- Lwjgl 3, jinput natives related fixes *[commit by @Rongmario in eb3f466]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.2...0.4.3

## 0.4.2 - 2026-08-04

## Other

- Bump to 0.4.2 *[commit by @Rongmario in 0eecbbd]*
- Bump initial-patches version to 1.2.0 *[commit by @Rongmario in 35e1edb]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.1...0.4.2

## 0.4.1 - 2026-07-30

## Other

- Bump to 0.4.1 *[commit by @Rongmario in 9877d23]*
- Input/Patches/Output properties for patch dev envs *[commit by @Rongmario in 9a58e9f]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.4.0...0.4.1

## 0.4.0 - 2026-07-29

## Other

- Bump 0.4.0 for real this time oops *[commit by @Rongmario in c7e01d5]*
- Userdev + more experimental shit straight into production - 0.4.0 *[commit by @Rongmario in 73253b6]*
- More proper newlines *[commit by @Rongmario in 1ce32d9]*
- Set newlines properly *[commit by @Rongmario in 18c8b5d]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.3.0...0.4.0

## 0.3.0 - 2026-07-19

## Other

- Re-did ids, republished previous versions *[commit by @Rongmario in 33aeae1]*
- Fix test *[commit by @Rongmario in dbe9edb]*
- 0.3.0 *[commit by @Rongmario in 2e65ec8]*
- DistributionTasks *[commit by @Rongmario in 3be5e00]*
- Strip, SAS, inheritance. *[commit by @Rongmario in e569e99]*
- Stub `UserDevTasks`, for setting up mod dev envs *[commit by @Rongmario in 912ce03]*
- :afterExec`, normalize zip after ATing *[commit by @Rongmario in d0847e2]*
- :normalizeZip` *[commit by @Rongmario in f0af78d]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.2.0...0.3.0

## 0.2.0 - 2026-07-18

## Other

- Bump to 0.2.0 *[commit by @Rongmario in a6774b7]*
- Versioning *[commit by @Rongmario in f222f47]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.1.0...0.2.0

## 0.1.0 - 2026-07-17

## Other

- Bump to 0.1.0 *[commit by @Rongmario in 64a8906]*
- Cleanroom's run tasks *[commit by @Rongmario in 26da603]*
- Apply settings plugin in test *[commit by @Rongmario in b6a483c]*
- Cacheable LzmaCompress task *[commit by @Rongmario in 31e190d]*
- Cleanup *[commit by @Rongmario in 7e2f54a]*
- NsightExec *[commit by @Rongmario in 922336b]*
- Wire new things into the MCP tasks pipeline *[commit by @Rongmario in 0198509]*
- Generate launch args from VersionMeta for vanilla runs *[commit by @Rongmario in 0efa8ee]*
- Support -Pmc=<version> for vanilla tasks and per-version toolchain *[commit by @Rongmario in 9a33845]*
- Add Tiny2 names source as alternative to MCP CSVs *[commit by @Rongmario in 7ddef61]*
- Stamp and validate mapping identity in patch sets *[commit by @Rongmario in 220be73]*
- Names and names and more names *[commit by @Rongmario in bd364b4]*
- New ext properties *[commit by @Rongmario in 8bd6d12]*
- `LzmaCompress` w/ IO helpers *[commit by @Rongmario in 0107de6]*
- LaunchArguments utility *[commit by @Rongmario in 61e63a5]*
- Apply foojay-resolver w/ the new settings plugin *[commit by @Rongmario in 250f6f7]*
- Update VersionMeta to include newer things *[commit by @Rongmario in 4ad81f0]*
- LzmaCompress *[commit by @Rongmario in 6c93b27]*

**Full Changelog**: https://github.com/CleanroomMC/CleanroomGradle/compare/0.0.1...0.1.0

## 0.0.1 - 2026-07-15

## Other

- (For now) *[commit by @Rongmario in a3b5b52]*
- Publish *[commit by @Rongmario in 53fff19]*
- Testing *[commit by @Rongmario in c3cce61]*
- Bundle 1.12.2 meta *[commit by @Rongmario in 3f4feb7]*
- Make tasks instance-based, push for cache eligibility, update gradle *[commit by @Rongmario in 617e9d0]*
- New overload for `Objects::config` *[commit by @Rongmario in 8fb143e]*
- RunMinecraft task cleanup *[commit by @Rongmario in 36e5729]*
- Further cleanup in RemapSrg2Mcp *[commit by @Rongmario in 842dce2]*
- Reproducibility in zip tasks *[commit by @Rongmario in d704f5b]*
- Zeroed zip entry time when SplitJar *[commit by @Rongmario in 57b253c]*
- `GenerateBinPatches` *[commit by @Rongmario in 7911836]*
- Cleanup + allow exec type tasks to not disturb config cache *[commit by @Rongmario in 720fa27]*
- Value sources *[commit by @Rongmario in 5781c28]*
- Also disable caching by default on JavaExecs *[commit by @Rongmario in 685065c]*
- Update foojay resolver convention to 1.0.0 *[commit by @Rongmario in b3b0ff0]*
- MavenJarExec rewrite *[commit by @Rongmario in c02db8b]*
- DownloadWithETag + readJson in IO class + cached HttpClient *[commit by @Rongmario in 254e9f7]*
- Use Cleanflower in Decompile task + rich javadocs for getOptions *[commit by @Rongmario in 6c2c7d0]*
- Don't use build dir for patch outputs *[commit by @Rongmario in ba11950]*
- Clean up diffs before generating, if needed *[commit by @Rongmario in 886ce3d]*
- Allow patch dev tasks to be configurable to have a dependsOn task *[commit by @Rongmario in aea2aa1]*
- Move copying to its own task, preparing deals with whatever is before it *[commit by @Rongmario in b66a09f]*
- Use eager detached configs for toolchain tasks *[commit by @Rongmario in 32e4b84]*
- `developCleanroom` config in extension *[commit by @Rongmario in 6b6af4b]*
- Work on patch dev extension *[commit by @Rongmario in 4cc8e2d]*
- Update vineflower *[commit by @Rongmario in 679058e]*
- Update java-diff-utils *[commit by @Rongmario in 799cf9d]*
- Mkdirs when getting setting rundir *[commit by @Rongmario in 65a912a]*
- Remove Internal annotation from private member *[commit by @Rongmario in 9923091]*
- Default log files for more tasks *[commit by @Rongmario in fa2f88c]*
- AccessTransform implementation *[commit by @Rongmario in 973b07c]*
- Configure jar tasks for srg/mcp source sets *[commit by @Rongmario in e9e1826]*
- Moving packages *[commit by @Rongmario in 055a2c1]*
- More gradle shenanigans *[commit by @Rongmario in 511764b]*
- Fix decompile log location + set max thread count for decompilation *[commit by @Rongmario in e4a8163]*
- Use project managed providers *[commit by @Rongmario in 8239c23]*
- Conform to config cache standards of not using project during task exec *[commit by @Rongmario in 5228137]*
- Simplify RunMinecraft task configurations *[commit by @Rongmario in 1e846df]*
- Simplified lazy toString objects *[commit by @Rongmario in a7a7b1d]*
- Better interface name + use helper in extension *[commit by @Rongmario in 8afc057]*
- MCP sourceset and run tasks *[commit by @Rongmario in 8ad51bc]*
- Added reobf srg run tasks *[commit by @Rongmario in e166b1f]*
- Add more SourceSet helpers *[commit by @Rongmario in af798bc]*
- Fixed rundir location *[commit by @Rongmario in d175b8a]*
- Fix non-inplace variant of ApplyDiffs task *[commit by @Rongmario in f4ff947]*
- Remove resource extraction in favour of splitting jars + srg sourceset *[commit by @Rongmario in bf772b2]*
- Move SourceSets helpers to its own class *[commit by @Rongmario in 9c05ec5]*
- Rewrite and finished remapping process *[commit by @Rongmario in a4ded30]*
- Lazy configs pt.2 *[commit by @Rongmario in 8fed728]*
- Resource folder helpers for SourceSets *[commit by @Rongmario in 6dd1f20]*
- Remove build listener, works erroneously *[commit by @Rongmario in 51cf17c]*
- Make configurations lazy and not instantly resolved again *[commit by @Rongmario in 84b4070]*
- Update download plugin *[commit by @Rongmario in 26677c1]*
- Move default decompile args to task provider *[commit by @Rongmario in c238f20]*
- Rename vanilla sourceset to not have the version appending *[commit by @Rongmario in a10eee2]*
- Update gradle + cleanup env + fixed assets dl + fixed generated folder *[commit by @Rongmario in 192951a]*
- Remove patches references from LoaderDevExtension, ready for decoupling *[commit by @Rongmario in 2dcc208]*
- Remove BUKKIT from being a valid Side *[commit by @Rongmario in 352a758]*
- Fix versioning + update to Gradle 8.6 *[commit by @Rongmario in dc46d2e]*
- Use InputFiles instead of InputDirectory for non-existent directories *[commit by @Rongmario in f7ea673]*
- Fix various locations + turn download tasks into simple actions *[commit by @Rongmario in b870886]*
- Maven-publish plugin for the time being to publish to mavenLocal *[commit by @Rongmario in 1cee714]*
- Add this to please gradle's java toolchains api *[commit by @Rongmario in c0356c4]*
- Processing libraries depends on downloadVersionMeta *[commit by @Rongmario in 6ca65ce]*
- Thanks git *[commit by @Rongmario in 912a4ab]*
- RunSrgClient/Server *[commit by @Rongmario in 40c57ed]*
- Use vanilla config for classpath *[commit by @Rongmario in f0d5bf7]*
- RunMcpClient/Server + made minecraft sourceset's name version dependent *[commit by @Rongmario in a07c6a6]*
- Tasks named/configure *[commit by @Rongmario in 2603f1d]*
- UTF-8 encoding when running minecraft *[commit by @Rongmario in 06ed832]*
- Locations needs to be documented and decided altogether *[commit by @Rongmario in 4da323a]*
- Make non-native vanilla configuration transitive *[commit by @Rongmario in 937a041]*
- Work on patched minecraft sourceset + few more helpers *[commit by @Rongmario in dad4c3b]*
- Better run folder getter *[commit by @Rongmario in e778f5a]*
- RunVanillaServer *[commit by @Rongmario in 79af2b0]*
- RunVanillaClient + assetIndexId getter *[commit by @Rongmario in eec34f9]*
- Fixes log4j-api not being upgraded as well + configs being transitive *[commit by @Rongmario in 7167430]*
- Common & vanilla & mcp config tasks *[commit by @Rongmario in 4c806da]*
- Moved to Java 17 + actually buildable and runnable now (vanilla) *[commit by @Rongmario in 3cb73b9]*
- Latest + ASM class generation for run task + more tests *[commit by @Rongmario in b6ce323]*
- JarExec *[commit by @Rongmario in e726ef7]*
- Amend some download tasks and constants *[commit by @Rongmario in 1c66c19]*
- Redo version JSON deserialization *[commit by @Rongmario in 2157528]*
- Added DiffPatch dep *[commit by @Rongmario in b4e58f4]*
- Download client/server jar tasks *[commit by @Rongmario in f1e884a]*
- Less schizo test rigging *[commit by @Rongmario in b50d25a]*
- Schizo mode unit-testing gradle tasks *[commit by @Rongmario in 5b82d8f]*
- Deleted some remnant code + overhauled downloading of versions *[commit by @Rongmario in fd3e75b]*
- WIP Rewrite, make implementation much more gradle-esque *[commit by @Rongmario in a9819a5]*
- Catering imaginary versions + multithreaded assets dl *[commit by @Rongmario in a078a4c]*
- Implement a better downloader/file integrity checker *[commit by @Rongmario in 4ef1e34]*
- Define plugin via build.gradle *[commit by @Rongmario in 6affc5d]*
- Remove 1.12.2 json resource *[commit by @Rongmario in ee3e126]*
- Fixed GrabAssetsTask behaviour + fixed constants jumbled up *[commit by @Rongmario in a6b39fc]*
- Work on mappings, successfully extract them + once again improve tests *[commit by @Rongmario in aea470f]*
- Rise and shine, its API and cleanup time *[commit by @Rongmario in 8a9d636]*
- Universal jars working, the task is horrible and needs rewriting however *[commit by @Rongmario in dba9740]*
- Add our beloved ASM library to the buildscript *[commit by @Rongmario in 461e79d]*
- Totally didn't realise .gitignore captured ProjectTest as well *[commit by @Rongmario in 09e91d5]*
- Splitting the jar to pure and dep jars works, but not for newer jars *[commit by @Rongmario in af4f009]*
- Successfully download/get assets into/from appdata cache *[commit by @Rongmario in 75086d9]*
- Check against hash before downloading client/server jars again *[commit by @Rongmario in 8cf3742]*
- For some reason git broke and tracked .idea files *[commit by @Rongmario in c3a98fe]*
- Successfully download minecraft client and server jar + cleaned up tests *[commit by @Rongmario in 5248dca]*
- Differentiate different download tasks + added more constants *[commit by @Rongmario in 16244b4]*
- Successfully parse version, assetIndex jsons + tests *[commit by @Rongmario in c81487c]*
- Downloading manifest now working, tests have dedicated folders now *[commit by @Rongmario in 10d398f]*
- Creating download tasks + add helpers *[commit by @Rongmario in f39b921]*
- Test to see if the tasks are created *[commit by @Rongmario in f997345]*
- RunClient + runServer tasks *[commit by @Rongmario in ec1e53e]*
- Setup commit *[commit by @Rongmario in 586e773]*
- Initial commit *[commit by @Rongmario in ef1bc28]*


