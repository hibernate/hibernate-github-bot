{#if buildScans == null || buildScans.empty}
No build scan found for this CI run.
{#else}
| Status | Job/Workflow {!
!}{#for tagColumn in tagColumns}{!
!}| {#if tagColumn.name}{tagColumn.name}{#else}{#if tagColumns.size == 1}Tags{#else}Other tags{/if}{/if} {!
!}{/for}{!
!}| Goals | Build Scan | Tests | Logs |
| :-: | -- {!
!}{#for tagColumn in tagColumns}| -- {/for}{!
!}| -- | :-: | :-: | :-: |
{#for buildScan in buildScans}
|[{buildScan.status.emoji}]({buildScan.statusUri}){!
!}|{list:create(buildScan.jobOrWorkflow, buildScan.stage).spaceDelimited.backQuoted}{!
!}{#for tagColumn in tagColumns}{!
!}|{tagColumn.content.get(buildScan).or(list:create()).backQuoted.spaceDelimited}{!
!}{/for}{!
!}|{buildScan.goals.spaceDelimited.backQuoted}{!
!}|[:mag:]({buildScan.buildScanUri}){!
!}|[{buildScan.testStatus.emoji}]({buildScan.testsUri}){!
!}|[:page_with_curl:]({buildScan.logsUri}){!
!}|
{/for}
{/if}