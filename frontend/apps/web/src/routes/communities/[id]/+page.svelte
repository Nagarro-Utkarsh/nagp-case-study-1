<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/state';
  import { createChallenge, getCommunity, inviteByEmail } from '@habitloop/api-client';
  import type { CommunityDetail } from '@habitloop/api-client';
  import { auth } from '$lib/state/auth.svelte';
  import { errorMessage } from '$lib/forms/errors';

  const id = page.params.id ?? '';
  const today = new Date().toISOString().slice(0, 10);
  let detail = $state<CommunityDetail | null>(null);
  let loading = $state(true);
  let busy = $state(false);
  let error = $state('');
  let success = $state('');
  let inviteEmail = $state('');
  let inviteLink = $state('');
  let title = $state('');
  let challengeDescription = $state('');
  let habitCategory = $state('');
  let targetCount = $state(7);
  let startDate = $state(today);
  let endDate = $state(new Date(Date.now() + 7 * 86_400_000).toISOString().slice(0, 10));

  async function load(): Promise<void> {
    if (!id) { error = 'Community not found.'; loading = false; return; }
    try { detail = await getCommunity(id); }
    catch (reason) { error = errorMessage(reason); }
    finally { loading = false; }
  }
  onMount(() => void load());
  const isOwner = $derived(Boolean(detail && detail.community.createdBy === auth.user?.id));

  async function invite(event: SubmitEvent): Promise<void> {
    event.preventDefault(); busy = true; error = ''; success = ''; inviteLink = '';
    try {
      const created = await inviteByEmail(id, inviteEmail.trim());
      inviteLink = `${window.location.origin}/invites/${created.id}`;
      await navigator.clipboard.writeText(inviteLink);
      success = 'Invite created and link copied.';
    } catch (reason) { error = errorMessage(reason); }
    finally { busy = false; }
  }

  async function create(event: SubmitEvent): Promise<void> {
    event.preventDefault(); busy = true; error = ''; success = '';
    try {
      await createChallenge(id, {
        title: title.trim(), description: challengeDescription.trim(), habitCategory: habitCategory.trim(),
        targetCount: Number(targetCount), startDate, endDate
      });
      title = ''; challengeDescription = ''; habitCategory = '';
      await load();
      success = 'Challenge created.';
    } catch (reason) { error = errorMessage(reason); }
    finally { busy = false; }
  }

  async function copyLink(): Promise<void> {
    try { await navigator.clipboard.writeText(inviteLink); success = 'Invite link copied.'; }
    catch (reason) { error = errorMessage(reason); }
  }
</script>

<svelte:head><title>{detail?.community.name ?? 'Community'} — HabitLoop</title></svelte:head>
<section class="page-heading"><div><a class="back-link" href="/challenges">← Communities</a><span class="eyebrow">{detail?.community.visibility ?? 'COMMUNITY'}</span><h1>{loading ? 'Loading community…' : detail?.community.name ?? 'Community unavailable'}</h1><p>{detail?.community.description || 'A place to build good habits together.'}</p></div></section>
{#if error}<div class="alert error" role="alert">{error}</div>{/if}
{#if success}<div class="alert success" role="status">{success}</div>{/if}
{#if loading}<div class="card loading-state">Loading community…</div>
{:else if detail}
  <div class="community-detail-grid">
    <section class="card">
      <div class="section-head"><div><span class="eyebrow">YOUR CIRCLE</span><h2>Members</h2></div><span class="badge">{detail.members.length} members</span></div>
      {#if detail.members.length}
        <div class="member-list">{#each detail.members as member (member.userId)}
          <div class="member-row"><span class="avatar">{member.userId === auth.user?.id ? auth.user?.name.slice(0, 1).toUpperCase() : 'H'}</span><div><strong>{member.userId === auth.user?.id ? `${auth.user?.name} (you)` : 'HabitLoop member'}</strong><small>Joined {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(member.joinedAt))} UTC</small></div><span class="badge">{member.role}</span></div>
        {/each}</div>
      {:else}<p class="muted">No members to show yet.</p>{/if}
    </section>
    <section class="card">
      <div class="section-head"><div><span class="eyebrow">SHARED GOALS</span><h2>Challenges</h2></div></div>
      {#if detail.challenges.length}
        <div class="community-challenge-list">{#each detail.challenges as challenge (challenge.id)}
          <a class="community-challenge" href={`/challenges/${challenge.id}`}><span class="community-icon">✳</span><span><strong>{challenge.title}</strong><small>{challenge.habitCategory} · target {challenge.targetCount}</small></span><span aria-hidden="true">→</span></a>
        {/each}</div>
      {:else}<div class="empty-state compact"><p>Your community's first challenge is waiting to be created.</p></div>{/if}
    </section>
  </div>
  {#if isOwner}
    <div class="community-detail-grid owner-tools">
      <section class="card">
        <div class="section-head"><div><span class="eyebrow">BRING A FRIEND</span><h2>Invite a member</h2></div></div>
        <p class="muted">Invite an existing account in your workspace. They can accept from the link.</p>
        <form class="stack-form" onsubmit={invite}><label>Email address<input type="email" required bind:value={inviteEmail} placeholder="friend@example.com" /></label><button class="button primary" disabled={busy}>{busy ? 'Creating…' : 'Create invite link'}</button></form>
        {#if inviteLink}<div class="copy-link"><input readonly value={inviteLink} aria-label="Community invitation link" /><button class="button secondary" onclick={copyLink}>Copy</button></div>{/if}
      </section>
      <section class="card">
        <div class="section-head"><div><span class="eyebrow">SET A SHARED GOAL</span><h2>Create a challenge</h2></div></div>
        <form class="stack-form" onsubmit={create}>
          <label>Title<input required bind:value={title} placeholder="e.g. One week of movement" /></label>
          <label>Description<input bind:value={challengeDescription} placeholder="Tell the group what to aim for" /></label>
          <label>Habit category<input required bind:value={habitCategory} placeholder="Must match member habit categories" /></label>
          <label>Target check-ins<input type="number" min="1" required bind:value={targetCount} /></label>
          <div class="form-grid"><label>Start date<input type="date" required bind:value={startDate} /></label><label>End date<input type="date" required bind:value={endDate} /></label></div>
          <button class="button primary" disabled={busy}>{busy ? 'Creating…' : 'Create challenge'}</button>
        </form>
      </section>
    </div>
  {/if}
{/if}
