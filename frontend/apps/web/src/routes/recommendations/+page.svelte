<script lang="ts">
  import { onMount } from 'svelte';
  import { dashboard } from '$lib/state/dashboard.svelte';
  import { applyRecommendation, getRecommendations, listHabits } from '@habitloop/api-client';
  import type { Habit, Recommendation } from '@habitloop/api-client';
  import { errorMessage } from '$lib/forms/errors';

  let recommendations = $state<Recommendation[]>([]);
  let habits = $state<Habit[]>([]);
  let selectedDays = $state<Record<string, number[]>>({});
  let loading = $state(true);
  let busyId = $state('');
  let error = $state('');
  let notice = $state('');
  const weekdays = [[1, 'Mon'], [2, 'Tue'], [3, 'Wed'], [4, 'Thu'], [5, 'Fri'], [6, 'Sat'], [7, 'Sun']] as const;

  async function load(): Promise<void> {
    error = '';
    try { [recommendations, habits] = await Promise.all([getRecommendations(), listHabits()]); }
    catch (reason) { error = errorMessage(reason); }
    finally { loading = false; }
  }
  onMount(() => void load());

  function daysFor(habitId: string): number[] {
    if (selectedDays[habitId] !== undefined) return selectedDays[habitId];
    const habit = habits.find((value) => value.id === habitId);
    return habit?.frequency === 'WEEKLY' ? habit.daysOfWeek : [];
  }

  function toggleDay(id: string, day: number): void {
    const current = daysFor(id);
    selectedDays[id] = current.includes(day) ? current.filter((value) => value !== day) : [...current, day].sort();
  }

  async function apply(item: Recommendation): Promise<void> {
    busyId = item.habitId; error = ''; notice = '';
    try {
      if (item.type === 'TIMING') {
        await applyRecommendation({ habitId: item.habitId, preferredTime: item.suggested });
      } else {
        const frequency = item.suggested.toUpperCase();
        const daysOfWeek = frequency === 'WEEKLY' ? daysFor(item.habitId) : [];
        if (frequency === 'WEEKLY' && !daysOfWeek.length) {
          error = 'Choose at least one weekday before applying a weekly schedule.';
          return;
        }
        await applyRecommendation({ habitId: item.habitId, frequency, daysOfWeek });
      }
      notice = 'Recommendation applied to your habit schedule.';
      await Promise.all([dashboard.refresh(), load()]);
    } catch (reason) { error = errorMessage(reason); }
    finally { busyId = ''; }
  }
</script>

<svelte:head><title>Recommendations — HabitLoop</title></svelte:head>
<section class="page-heading"><div><span class="eyebrow">FIND YOUR FLOW</span><h1>A rhythm that works for you.</h1><p>Small schedule shifts can make a good habit easier to keep.</p></div></section>
{#if error}<div class="alert error" role="alert">{error}</div>{/if}
{#if notice}<div class="alert success" role="status">{notice}</div>{/if}
{#if loading}<div class="card loading-state">Looking for patterns in your activity…</div>
{:else if recommendations.length}
  <div class="recommendation-grid">
    {#each recommendations as item (`${item.habitId}-${item.type}`)}
      {@const habit = habits.find((value) => value.id === item.habitId)}
      <article class="card recommendation-card">
        <div class="recommendation-top"><span class="recommendation-icon">{item.type === 'TIMING' ? '◷' : '↗'}</span><span class="badge">{item.type === 'TIMING' ? 'Timing idea' : 'Frequency idea'}</span></div>
        <h2>{habit?.name ?? 'Your habit'}</h2><p class="muted">{habit?.category ?? ''}</p>
        <div class="recommendation-change"><div><small>CURRENT</small><strong>{item.type === 'TIMING' ? `${item.current} UTC` : item.current}</strong></div><span aria-hidden="true">→</span><div class="suggested"><small>SUGGESTED</small><strong>{item.type === 'TIMING' ? `${item.suggested} UTC` : item.suggested}</strong></div></div>
        <p>{item.reason}</p>
        {#if item.type === 'FREQUENCY' && item.suggested.toUpperCase() === 'WEEKLY'}
          <fieldset><legend>Choose weekly days before applying</legend><div class="day-picker">{#each weekdays as [day, label] (day)}<button type="button" class:chosen={daysFor(item.habitId).includes(day)} onclick={() => toggleDay(item.habitId, day)} aria-pressed={daysFor(item.habitId).includes(day)}>{label}</button>{/each}</div></fieldset>
        {/if}
        <button class="button primary wide" disabled={busyId === item.habitId} onclick={() => apply(item)}>{busyId === item.habitId ? 'Applying…' : 'Apply suggestion'} <span aria-hidden="true">→</span></button>
      </article>
    {/each}
  </div>
{:else}
  <section class="card recommendations-empty">
    <span class="empty-icon">✳</span><h2>Keep showing up. Your patterns will follow.</h2>
    <p>There aren't any schedule suggestions yet. Keep logging your habits; timing ideas need at least five distinct completions in the last 28 days, and all analytics use UTC.</p>
    <a class="button primary" href="/habits">Log a habit</a>
  </section>
{/if}
