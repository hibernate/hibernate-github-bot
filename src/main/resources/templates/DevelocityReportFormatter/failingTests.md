{#if rows != null && !rows.empty}
## Failing Tests

| Test | Where {#if showHistory}| Recent history {/if}|
| -- | -- {#if showHistory}| -- {/if}|
{#for row in rows}
|`{row.test.shortName}`|{row.where}{#if showHistory}|{row.test.historyLabel} [:mag:]({row.test.testHistoryUri} "{row.test.name}"){/if}|
{/for}

{/if}