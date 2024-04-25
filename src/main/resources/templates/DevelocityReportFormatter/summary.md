{#if buildScans == null || buildScans.empty}
No build scan found for this CI run.
{#else}
| Status | Job/Workflow | Tags | Goals | Build Scan | Tests | Logs |
| :-: | -- | -- | -- | :-: | :-: | :-: |
{#for buildScan in buildScans}
|[{buildScan.status.emoji}]({buildScan.statusUri}){!
!}|{list:create(buildScan.jobOrWorkflow, buildScan.stage).spaceDelimited.backQuoted}{!
!}|{buildScan.tags.backQuoted.spaceDelimited}{!
!}|{buildScan.goals.spaceDelimited.backQuoted}{!
!}|[:mag:]({buildScan.buildScanUri}){!
!}|[{buildScan.testStatus.emoji}]({buildScan.testsUri}){!
!}|[:page_with_curl:]({buildScan.logsUri}){!
!}|
{/for}
{/if}