<script lang="ts">
  import { onMount } from 'svelte';
  import { deleteHabit, listHabits, saveHabit } from '@habitloop/api-client';
  import type { Habit } from '@habitloop/api-client';
  import { errorMessage, fieldErrors } from '$lib/forms/errors';
  import { dashboard } from '$lib/state/dashboard.svelte';

  let habits = $state<Habit[]>([]);
  let loading = $state(true);
  let busy = $state(false);
  let error = $state('');
  let errors = $state<Record<string, string>>({});
  let name = $state('');
  let category = $state('');
  let frequency = $state<'DAILY' | 'WEEKLY'>('DAILY');
  let daysOfWeek = $state<number[]>([1, 2, 3, 4, 5]);
  let preferredTime = $state('08:00');
  const weekdays = [[1, 'M'], [2, 'T'], [3, 'W'], [4, 'T'], [5, 'F'], [6, 'S'], [7, 'S']] as const;

  async function load(): Promise<void> {
    error = '';
    try { habits = await listHabits(); }
    catch (reason) { error = errorMessage(reason); }
    finally { loading = false; }
  }
  onMount(() => void load());

  function toggleDay(day: number): void {
    daysOfWeek = daysOfWeek.includes(day) ? daysOfWeek.filter((value) => value !== day) : [...daysOfWeek, day].sort();
  }

  async function create(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    busy = true;
    error = '';
    errors = {};
    try {
      await saveHabit({
        name: name.trim(), category: category.trim(), frequency,
        daysOfWeek: frequency === 'WEEKLY' ? daysOfWeek : [],
        preferredTime: preferredTime || null
      });
      name = '';
      category = '';
      await load();
      await dashboard.refresh();
    } catch (reason) {
      error = errorMessage(reason);
      errors = fieldErrors(reason);
    } finally { busy = false; }
  }

  async function remove(habit: Habit): Promise<void> {
    if (!confirm(`Delete "${habit.name}" and its history?`)) return;
    error = '';
    try {
      await deleteHabit(habit.id);
      await load();
      await dashboard.refresh();
    } catch (reason) { error = errorMessage(reason); }
  }
</script>

<svelte:head><title>My habits — HabitLoop</title></svelte:head>
<section class="page-heading"><div><span class="eyebrow">YOUR ROUTINE</span><h1>Habits that make you, you.</h1><p>Keep the promises you make to yourself, one check-in at a time.</p></div></section>
{#if error}<div class="alert error" role="alert">{error}</div>{/if}
<div class="habits-layout">
  <section class="card">
    <div class="section-head"><div><span class="eyebrow">IN YOUR ROUTINE</span><h2>{habits.length} {habits.length === 1 ? 'habit' : 'habits'}</h2></div></div>
    {#if loading}<div class="loading-state">Loading your habits…</div>
    {:else if habits.length === 0}<div class="empty-state"><span class="empty-icon">✳</span><h3>Nothing on your list yet</h3><p>Create a habit to start building a routine that's yours.</p></div>
    {:else}<div class="habit-list">{#each habits as habit (habit.id)}
      <article class="habit-item">
        <span class="habit-icon">✳</span>
        <div class="habit-main"><a href={`/habits/${habit.id}`}><strong>{habit.name}</strong></a><span>{habit.category} · {habit.frequency === 'DAILY' ? 'Every day' : `Weekly · ${habit.daysOfWeek.join(', ')}`} · {habit.preferredTime ?? 'No preferred time'} UTC</span></div>
        <a class="icon-link" href={`/habits/${habit.id}`} aria-label={`Edit ${habit.name}`}>↗</a>
        <button class="text-button danger-text" onclick={() => remove(habit)}>Delete</button>
      </article>
    {/each}</div>{/if}
  </section>
  <section class="card create-habit-card">
    <div class="section-head"><div><span class="eyebrow">START SOMETHING</span><h2>Create a habit</h2></div></div>
    <form class="stack-form" onsubmit={create}>
      <label>Habit name<input required maxlength="100" bind:value={name} placeholder="e.g. Read for 20 minutes" />{#if errors.name}<small class="field-error">{errors.name}</small>{/if}</label>
      <label>Category<input required bind:value={category} placeholder="e.g. wellness, movement" />{#if errors.category}<small class="field-error">{errors.category}</small>{/if}</label>
      <label>Frequency<select bind:value={frequency}><option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option></select></label>
      {#if frequency === 'WEEKLY'}
        <fieldset><legend>Schedule days <span class="label-note">Monday = 1</span></legend><div class="day-picker">{#each weekdays as [day, label] (day)}<button type="button" class:chosen={daysOfWeek.includes(day)} onclick={() => toggleDay(day)} aria-pressed={daysOfWeek.includes(day)}>{label}</button>{/each}</div>{#if errors.daysOfWeek}<small class="field-error">{errors.daysOfWeek}</small>{/if}</fieldset>
      {/if}
      <label>Preferred time <span class="label-note">UTC</span><input type="time" bind:value={preferredTime} /></label>
      <button class="button primary wide" disabled={busy}>{busy ? 'Saving…' : 'Create habit'} <span aria-hidden="true">→</span></button>
    </form>
  </section>
</div>
