{#if buildScans == null || buildScans.empty}
No build scan found for this CI run.
{#else}
| Job/Workflow {!
!}{#for tagColumn in tagColumns}{!
!}| {#if tagColumn.name}{tagColumn.name}{#else}{#if tagColumns.size == 1}Tags{#else}Other tags{/if}{/if} {!
!}{/for}{!
!}| Goals | Results |
| -- {!
!}{#for tagColumn in tagColumns}| -- {/for}{!
!}| -- | :-: |
{#for buildScan in buildScans}
|[{buildScan.jobOrWorkflow}]({buildScan.jobOrWorkflowUri} "Build"){!
!}{#for tagColumn in tagColumns}{!
!}|{tagColumn.content.get(buildScan).or(list:create()).backQuoted.spaceDelimited}{!
!}{/for}{!
!}|{buildScan.goals.spaceDelimited.backQuoted}{!
!}|[{buildScan.status.circleEmoji}]({buildScan.statusUri} "Build Scan"){!
!} [{buildScan.testStatus.checkEmoji}]({buildScan.testsUri} "Tests"){!
!} [:page_with_curl:]({buildScan.logsUri} "Logs"){!
!}|
{/for}
{/if}