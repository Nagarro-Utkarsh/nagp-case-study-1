<script lang="ts">
  import { goto } from '$app/navigation';
  import { saveHabit, updateProfile } from '@habitloop/api-client';
  import { auth } from '$lib/state/auth.svelte';
  import { errorMessage, fieldErrors } from '$lib/forms/errors';

  let name = $state(auth.user?.name ?? '');
  let habitName = $state('Daily steps');
  let category = $state('movement');
  let preferredTime = $state('08:00');
  let frequency = $state<'DAILY' | 'WEEKLY'>('DAILY');
  let daysOfWeek = $state<number[]>([1, 2, 3, 4, 5]);
  let busy = $state(false);
  let error = $state('');
  let errors = $state<Record<string, string>>({});
  const weekdays = [[1, 'Mon'], [2, 'Tue'], [3, 'Wed'], [4, 'Thu'], [5, 'Fri'], [6, 'Sat'], [7, 'Sun']] as const;

  function toggleDay(day: number): void {
    daysOfWeek = daysOfWeek.includes(day) ? daysOfWeek.filter((value) => value !== day) : [...daysOfWeek, day].sort();
  }

  async function submit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    busy = true;
    error = '';
    errors = {};
    try {
      if (name.trim() !== auth.user?.name) await updateProfile(name.trim(), auth.user?.timezone ?? 'UTC');
      await saveHabit({
        name: habitName.trim(),
        category: category.trim(),
        frequency,
        daysOfWeek: frequency === 'WEEKLY' ? daysOfWeek : [],
        preferredTime: preferredTime || null
      });
      await auth.refreshUser();
      await goto('/dashboard');
    } catch (reason) {
      error = errorMessage(reason);
      errors = fieldErrors(reason);
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head><title>Set up your first habit — HabitLoop</title></svelte:head>

<section class="page-heading">
  <div><span class="eyebrow">YOUR FIRST STEP</span><h1>Make this space yours</h1><p>Set up your profile and one habit to get your rhythm started.</p></div>
</section>
{#if error}<div class="alert error" role="alert">{error}</div>{/if}
<form class="card form-card onboarding-form" onsubmit={submit}>
  <div class="form-section-title"><span class="step-number">01</span><div><h2>Your profile</h2><p>Let's make it personal.</p></div></div>
  <label>What should we call you?<input bind:value={name} maxlength="100" required autocomplete="name" />{#if errors.name}<small class="field-error">{errors.name}</small>{/if}</label>
  <div class="form-section-title"><span class="step-number">02</span><div><h2>Your first habit</h2><p>Start small; consistency grows from there.</p></div></div>
  <div class="form-grid">
    <label>Habit name<input bind:value={habitName} required placeholder="e.g. Morning walk" />{#if errors.name}<small class="field-error">{errors.name}</small>{/if}</label>
    <label>Category<input bind:value={category} required placeholder="e.g. movement" />{#if errors.category}<small class="field-error">{errors.category}</small>{/if}</label>
    <label>Frequency<select bind:value={frequency}><option value="DAILY">Every day</option><option value="WEEKLY">Choose days</option></select></label>
    <label>Preferred time <span class="label-note">UTC</span><input type="time" bind:value={preferredTime} /></label>
  </div>
  {#if frequency === 'WEEKLY'}
    <fieldset><legend>Days of the week</legend><div class="day-picker">{#each weekdays as [day, label] (day)}<button type="button" class:chosen={daysOfWeek.includes(day)} onclick={() => toggleDay(day)} aria-pressed={daysOfWeek.includes(day)}>{label}</button>{/each}</div></fieldset>
  {/if}
  <div class="onboarding-actions"><p>You can adjust your schedule any time.</p><button class="button primary" disabled={busy}>{busy ? 'Saving your space…' : 'Finish setup'} <span aria-hidden="true">→</span></button></div>
</form>
