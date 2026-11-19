<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/state';
  import { dashboard } from '$lib/state/dashboard.svelte';
  import { getHabit, getHabitLogs, logHabit, saveHabit } from '@habitloop/api-client';
  import type { ActivityLog, Habit } from '@habitloop/api-client';
  import { errorMessage, fieldErrors } from '$lib/forms/errors';

  let habit = $state<Habit | null>(null);
  let logs = $state<ActivityLog[]>([]);
  let name = $state('');
  let category = $state('');
  let frequency = $state<'DAILY' | 'WEEKLY'>('DAILY');
  let daysOfWeek = $state<number[]>([]);
  let preferredTime = $state('');
  let loading = $state(true);
  let busy = $state(false);
  let error = $state('');
  let errors = $state<Record<string, string>>({});
  const id = page.params.id ?? '';
  const weekdays = [[1, 'Mon'], [2, 'Tue'], [3, 'Wed'], [4, 'Thu'], [5, 'Fri'], [6, 'Sat'], [7, 'Sun']] as const;

  onMount(() => {
    if (!id) { error = 'Habit not found.'; loading = false; return; }
    void Promise.all([getHabit(id), getHabitLogs(id)]).then(([found, history]) => {
      habit = found; logs = history;
      name = found.name; category = found.category; frequency = found.frequency;
      daysOfWeek = [...found.daysOfWeek]; preferredTime = found.preferredTime ?? '';
    }).catch((reason: unknown) => error = errorMessage(reason)).finally(() => loading = false);
  });

  function toggleDay(day: number): void {
    daysOfWeek = daysOfWeek.includes(day) ? daysOfWeek.filter((value) => value !== day) : [...daysOfWeek, day].sort();
  }

  async function save(event: SubmitEvent): Promise<void> {
    event.preventDefault(); busy = true; error = ''; errors = {};
    try {
      habit = await saveHabit({
        name: name.trim(), category: category.trim(), frequency,
        daysOfWeek: frequency === 'WEEKLY' ? daysOfWeek : [],
        preferredTime: preferredTime || null
      }, id);
      await dashboard.refresh();
    } catch (reason) { error = errorMessage(reason); errors = fieldErrors(reason); }
    finally { busy = false; }
  }

  async function log(): Promise<void> {
    error = '';
    try {
      await logHabit(id);
      logs = await getHabitLogs(id);
      await dashboard.refresh();
    } catch (reason) { error = errorMessage(reason); }
  }
</script>

<svelte:head><title>{habit?.name ?? 'Habit details'} — HabitLoop</title></svelte:head>
<section class="page-heading">
  <div><a class="back-link" href="/habits">← All habits</a><span class="eyebrow">YOUR ROUTINE</span><h1>{loading ? 'Loading habit…' : habit?.name ?? 'Habit unavailable'}</h1><p>Edit the schedule or look back at your check-ins.</p></div>
  {#if habit}<button class="button primary" onclick={log}>✓ Log today</button>{/if}
</section>
{#if error}<div class="alert error" role="alert">{error}</div>{/if}
{#if loading}<div class="card loading-state">Loading habit details…</div>
{:else if habit}
  <div class="habits-layout">
    <section class="card">
      <div class="section-head"><div><span class="eyebrow">MAKE IT YOURS</span><h2>Habit schedule</h2></div></div>
      <form class="stack-form" onsubmit={save}>
        <label>Habit name<input required bind:value={name} />{#if errors.name}<small class="field-error">{errors.name}</small>{/if}</label>
        <label>Category<input required bind:value={category} />{#if errors.category}<small class="field-error">{errors.category}</small>{/if}</label>
        <label>Frequency<select bind:value={frequency}><option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option></select></label>
        {#if frequency === 'WEEKLY'}
          <fieldset><legend>Days of the week</legend><div class="day-picker">{#each weekdays as [day, label] (day)}<button type="button" class:chosen={daysOfWeek.includes(day)} onclick={() => toggleDay(day)} aria-pressed={daysOfWeek.includes(day)}>{label}</button>{/each}</div>{#if errors.daysOfWeek}<small class="field-error">{errors.daysOfWeek}</small>{/if}</fieldset>
        {/if}
        <label>Preferred time <span class="label-note">UTC</span><input type="time" bind:value={preferredTime} /></label>
        <button class="button primary" disabled={busy}>{busy ? 'Saving…' : 'Save schedule'}</button>
      </form>
    </section>
    <section class="card">
      <div class="section-head"><div><span class="eyebrow">YOUR PROGRESS</span><h2>Log history</h2></div><span class="muted">{logs.length} check-ins</span></div>
      {#if logs.length}
        <div class="log-list">{#each logs as entry (entry.id)}
          <div class="log-row"><span class="log-dot"></span><strong>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(entry.loggedAt))} UTC</strong><span class="badge">{entry.source}</span></div>
        {/each}</div>
      {:else}<div class="empty-state compact"><p>Your check-ins will appear here. Ready for the first?</p><button class="button primary" onclick={log}>Log today</button></div>{/if}
      <p class="fine-print">Times are shown in UTC. Analytics can take a moment to catch up with a saved log.</p>
    </section>
  </div>
{/if}
