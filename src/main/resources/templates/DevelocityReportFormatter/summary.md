{#if buildScans == null || buildScans.empty}
No build scan found for this CI run.
{#else}
| Job/Workflow {!
!}{#for tagColumn in tagColumns}{!
!}| {#if tagColumn.name}{tagColumn.name}{#else}{#if tagColumns.size == 1}Tags{#else}Other tags{/if}{/if} {!
!}{/for}{!
!}| Goals | Tests |
| -- {!
!}{#for tagColumn in tagColumns}| -- {/for}{!
!}| -- | :-: |
{#for buildScan in buildScans}
|{buildScan.status.emoji}{!
!} [:mag:]({buildScan.statusUri} "Build Scan"){!
!} [:link:]({buildScan.jobOrWorkflowUri} "Build"){!
!} [:page_with_curl:]({buildScan.logsUri} "Logs"){!
!} {buildScan.jobOrWorkflow}{!
!}{#for tagColumn in tagColumns}{!
!}|{tagColumn.content.get(buildScan).or(list:create()).backQuoted.spaceDelimited}{!
!}{/for}{!
!}|{buildScan.goals.spaceDelimited.backQuoted}{!
!}|{buildScan.testStatus.emoji}{!
!} [:mag:]({buildScan.testsUri} "Tests"){!
!}|
{/for}
{/if}