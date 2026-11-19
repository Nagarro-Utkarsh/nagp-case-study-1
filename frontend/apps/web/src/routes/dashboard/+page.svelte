<script lang="ts">
  import { dashboard } from '$lib/state/dashboard.svelte';
  import { logHabit, listHabits } from '@habitloop/api-client';
  import { onMount } from 'svelte';
  import type { Habit } from '@habitloop/api-client';

  let habits = $state<Habit[]>([]);
  let loadingHabits = $state(true);
  let actionError = $state('');
  let chartElement = $state<HTMLElement & { points?: Array<{ date: string; count: number }> }>();
  let points = $derived(dashboard.trend?.days ?? []);
  let dateText = $derived(new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric', timeZone: 'UTC' }).format(new Date()));

  onMount(() => {
    void listHabits().then((result) => habits = result).catch((error: unknown) => {
      actionError = error instanceof Error ? error.message : 'Habits could not be loaded.';
    }).finally(() => loadingHabits = false);
  });

  $effect(() => {
    const element = chartElement;
    const currentPoints = points;
    if (element) element.points = currentPoints;
  });

  async function log(id: string): Promise<void> {
    actionError = '';
    try {
      await logHabit(id);
      await dashboard.refresh();
      habits = await listHabits();
    } catch (error) {
      actionError = error instanceof Error ? error.message : 'Your activity could not be saved.';
    }
  }
</script>

<svelte:head><title>Overview — HabitLoop</title></svelte:head>
<section class="page-heading dashboard-heading">
  <div><span class="eyebrow">{dateText.toUpperCase()} · UTC</span><h1>A little progress adds up.</h1><p>Make today count, one small win at a time.</p></div>
  <a class="button primary" href="/habits">+ Add a habit</a>
</section>
{#if actionError}<div class="alert error" role="alert">{actionError}</div>{/if}
{#if dashboard.error}<div class="alert error" role="alert">{dashboard.error} <button class="text-button" onclick={() => dashboard.refresh()}>Try again</button></div>{/if}
<div class="stats-row">
  <article class="card stat-card"><span class="stat-icon mint">✓</span><div><span class="stat-label">Habits in motion</span><strong>{loadingHabits ? '—' : habits.length}</strong><small>small steps, every day</small></div></article>
  <article class="card stat-card"><span class="stat-icon peach">✳</span><div><span class="stat-label">Today's check-ins</span><strong>{dashboard.summaries.filter((summary) => summary.doneToday).length}<span class="stat-denom"> / {dashboard.summaries.length}</span></strong><small>your daily momentum</small></div></article>
  <article class="card stat-card"><span class="stat-icon lilac">↗</span><div><span class="stat-label">Habits on track</span><strong>{dashboard.summaries.filter((summary) => summary.completionPercentage >= 70).length}</strong><small>70% or better this week</small></div></article>
</div>
<div class="dashboard-grid">
  <section class="card habits-overview">
    <div class="section-head"><div><span class="eyebrow">YOUR ROUTINE</span><h2>Today's habits</h2></div><a href="/habits">See all <span aria-hidden="true">→</span></a></div>
    {#if dashboard.loading && !dashboard.summaries.length}
      <div class="loading-state">Loading your habits…</div>
    {:else if !habits.length && !loadingHabits}
      <div class="empty-state"><span class="empty-icon">✳</span><h3>Your rhythm starts here</h3><p>Create your first habit and come back to check in.</p><a class="button primary" href="/habits">Create a habit</a></div>
    {:else if !dashboard.summaries.length && !dashboard.loading}
      <div class="empty-state compact"><p>Your habits are ready. Log the first one to see your progress.</p><a href="/habits">Open my habits →</a></div>
    {:else}
      <div class="summary-list">
        {#each dashboard.summaries as summary (summary.habitId)}
          <div class="summary-row">
            <hl-activity-summary habit-name={summary.name} done-today={summary.doneToday} streak={summary.currentStreak} completion={summary.completionPercentage} onlog={() => log(summary.habitId)}></hl-activity-summary>
            <small class="summary-category">{summary.category}</small>
          </div>
        {/each}
      </div>
    {/if}
    <p class="fine-print">Saved logs may take a little time to appear in your analytics.</p>
  </section>
  <section class="card trend-card">
    <div class="section-head"><div><span class="eyebrow">THE BIG PICTURE</span><h2>Activity trend</h2></div>
      {#if dashboard.summaries.length > 1}
        <label class="sr-only" for="trend-habit">Choose a habit trend</label><select id="trend-habit" value={dashboard.trendHabitId ?? ''} onchange={(event) => dashboard.selectTrend(event.currentTarget.value)}>{#each dashboard.summaries as item (item.habitId)}<option value={item.habitId}>{item.name}</option>{/each}</select>
      {/if}
    </div>
    {#if dashboard.summaries.length}
      <hl-trend-chart bind:this={chartElement}></hl-trend-chart>
      <div class="chart-caption"><span>Last 30 days</span><span>Counts shown in UTC</span></div>
    {:else}<div class="empty-state compact"><p>Your activity trend will show up after your first habit log.</p></div>{/if}
  </section>
</div>
<section class="card cohort-card">
  <div class="section-head"><div><span class="eyebrow">COMMUNITY MOMENTUM</span><h2>Growing together</h2></div><span class="muted">Workspace snapshot</span></div>
  {#if dashboard.cohorts.length}
    <div class="cohort-list">{#each dashboard.cohorts.slice(-4).reverse() as cohort (cohort.signupWeek)}
      <div class="cohort-row"><span>{cohort.signupWeek}</span><div class="cohort-track"><i style={`width:${Math.min(100, Math.max(0, cohort.activePercentage))}%`}></i></div><strong>{cohort.activePercentage}%</strong><small>{cohort.activeUsers} of {cohort.totalUsers} active</small></div>
    {/each}</div>
  {:else}<p class="muted">Community insights will appear as cohort activity is recorded.</p>{/if}
</section>
