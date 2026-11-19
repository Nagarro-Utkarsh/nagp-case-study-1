<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/state';
  import { getChallenge, inviteByEmail, joinChallenge, listCommunities, listHabits } from '@habitloop/api-client';
  import type { ChallengeDetail, Community, Habit } from '@habitloop/api-client';
  import { auth } from '$lib/state/auth.svelte';
  import { errorMessage } from '$lib/forms/errors';
  import { dashboard } from '$lib/state/dashboard.svelte';

  const id = page.params.id ?? '';
  let detail = $state<ChallengeDetail | null>(null);
  let communities = $state<Community[]>([]);
  let habits = $state<Habit[]>([]);
  let selectedHabitId = $state('');
  let inviteEmail = $state('');
  let inviteLink = $state('');
  let loading = $state(true);
  let busy = $state(false);
  let error = $state('');
  let success = $state('');

  async function load(): Promise<void> {
    if (!id) { error = 'Challenge not found.'; loading = false; return; }
    try {
      [detail, communities, habits] = await Promise.all([getChallenge(id), listCommunities(), listHabits()]);
      selectedHabitId = habits.find((habit) => habit.category.trim().toLowerCase() === detail?.challenge.habitCategory.trim().toLowerCase())?.id ?? '';
    } catch (reason) { error = errorMessage(reason); }
    finally { loading = false; }
  }
  onMount(() => void load());
  const community = $derived(communities.find((item) => item.id === detail?.challenge.communityId));
  const matchingHabits = $derived(habits.filter((habit) => habit.category.trim().toLowerCase() === detail?.challenge.habitCategory.trim().toLowerCase()));

  async function join(): Promise<void> {
    if (!selectedHabitId) return;
    busy = true; error = ''; success = '';
    try {
      await joinChallenge(id, selectedHabitId);
      detail = await getChallenge(id);
      success = 'You joined the challenge. Your habit is linked to your progress.';
      await dashboard.refresh();
    } catch (reason) { error = errorMessage(reason); }
    finally { busy = false; }
  }

  async function invite(event: SubmitEvent): Promise<void> {
    event.preventDefault(); busy = true; error = ''; success = ''; inviteLink = '';
    try {
      const created = await inviteByEmail(detail!.challenge.communityId, inviteEmail.trim());
      const link = `${window.location.origin}/invites/${created.id}?challenge=${encodeURIComponent(id)}`;
      inviteLink = link;
      await navigator.clipboard.writeText(link);
      success = 'Invite created and link copied. The invite grants community membership; joining this challenge is a separate step.';
    } catch (reason) {
      error = errorMessage(reason);
    } finally { busy = false; }
  }

  async function copyLink(): Promise<void> {
    try { await navigator.clipboard.writeText(inviteLink); success = 'Invite link copied.'; }
    catch (reason) { error = errorMessage(reason); }
  }
</script>

<svelte:head><title>{detail?.challenge.title ?? 'Challenge'} — HabitLoop</title></svelte:head>
<section class="page-heading">
  <div><a class="back-link" href="/challenges">← Challenge board</a><span class="eyebrow">SHOW UP TOGETHER</span><h1>{loading ? 'Loading challenge…' : detail?.challenge.title ?? 'Challenge unavailable'}</h1><p>{detail?.challenge.description || 'A shared goal, one day at a time.'}</p></div>
  {#if community}<a class="button secondary" href={`/communities/${community.id}`}>{community.name} ↗</a>{/if}
</section>
{#if error}<div class="alert error" role="alert">{error}</div>{/if}
{#if success}<div class="alert success" role="status">{success}</div>{/if}
{#if loading}<div class="card loading-state">Loading challenge details…</div>
{:else if detail}
  <div class="challenge-detail-grid">
    <section class="card challenge-overview">
      <span class="eyebrow">THE CHALLENGE</span><h2>{detail.challenge.habitCategory}</h2>
      <div class="challenge-meta">
        <div><small>Target</small><strong>{detail.challenge.targetCount} check-ins</strong></div>
        <div><small>Dates</small><strong>{detail.challenge.startDate} – {detail.challenge.endDate}</strong></div>
      </div>
      <div class="join-challenge">
        <h3>Ready to join?</h3>
        {#if matchingHabits.length}
          <label>Choose a matching habit<select bind:value={selectedHabitId}>{#each matchingHabits as habit (habit.id)}<option value={habit.id}>{habit.name} · {habit.category}</option>{/each}</select></label>
          <button class="button primary" disabled={busy || !selectedHabitId} onclick={join}>{busy ? 'Joining…' : 'Join this challenge'} <span aria-hidden="true">→</span></button>
        {:else}<p class="muted">You need a habit in the <strong>{detail.challenge.habitCategory}</strong> category to join. Create one in <a href="/habits">My habits</a> first.</p>{/if}
      </div>
    </section>
    <section class="card leaderboard">
      <div class="section-head"><div><span class="eyebrow">MAKING PROGRESS</span><h2>Leaderboard</h2></div><span class="badge">{detail.leaderboard.length} people</span></div>
      {#if detail.leaderboard.length}
        <div class="leader-list">{#each detail.leaderboard as person, index (person.userId)}
          <div class="leader-row"><span class="leader-rank">{String(index + 1).padStart(2, '0')}</span><span class="avatar">{person.name.slice(0, 1).toUpperCase()}</span><strong>{person.name}</strong><span class="leader-progress">{person.count === null ? 'unavailable' : `${person.count} / ${person.targetCount}`}</span></div>
        {/each}</div>
      {:else}<div class="empty-state compact"><p>Be the first to join and set the pace.</p></div>{/if}
      <p class="fine-print">Progress may be unavailable while analytics catches up.</p>
    </section>
  </div>
  {#if community?.createdBy === auth.user?.id}
    <section class="card invite-owner-card"><div><span class="eyebrow">GROW THE CIRCLE</span><h2>Invite someone to this challenge</h2><p class="muted">The invite adds them to the community; they can then choose a matching habit to join.</p></div>
      <form class="invite-form" onsubmit={invite}><label>Email address<input type="email" required bind:value={inviteEmail} placeholder="friend@example.com" /></label><button class="button primary" disabled={busy}>{busy ? 'Creating…' : 'Create invite link'}</button></form>
      {#if inviteLink}<div class="copy-link"><input readonly value={inviteLink} aria-label="Challenge invitation link" /><button class="button secondary" onclick={copyLink}>Copy link</button></div>{/if}
    </section>
  {/if}
{/if}
