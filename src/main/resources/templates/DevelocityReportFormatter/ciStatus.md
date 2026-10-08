{#if !runningChecks.empty}
:hourglass: **{runningChecks.size} workflow{#if runningChecks.size > 1}s{/if} still running:** {#for job in runningChecks}{job.link}{#if job_hasNext}, {/if}{/for}

{/if}
{#if !failedChecksWithoutScan.empty}
:x: **{failedChecksWithoutScan.size} other CI check{#if failedChecksWithoutScan.size > 1}s{/if} failed:** {#for job in failedChecksWithoutScan}{job.link}{#if job_hasNext}, {/if}{/for}

{/if}
{#if !checksWithoutScan.empty}
:information_source: **{checksWithoutScan.size} CI check{#if checksWithoutScan.size > 1}s{/if} without a build scan:** {#for job in checksWithoutScan}{job.link}{#if job_hasNext}, {/if}{/for}

{/if}
