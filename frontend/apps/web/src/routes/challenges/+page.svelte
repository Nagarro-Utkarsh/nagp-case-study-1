<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { createCommunity, joinCommunity, listChallenges, listCommunities, listInvites, respondToInvite } from '@habitloop/api-client';
  import type { Challenge, Community, Invite } from '@habitloop/api-client';
  import { errorMessage } from '$lib/forms/errors';

  let challenges = $state<Challenge[]>([]);
  let communities = $state<Community[]>([]);
  let invites = $state<Invite[]>([]);
  let loading = $state(true);
  let busy = $state(false);
  let error = $state('');
  let communityName = $state('');
  let description = $state('');
  let visibility = $state<'PUBLIC' | 'PRIVATE'>('PUBLIC');

  async function load(): Promise<void> {
    error = '';
    try {
      [challenges, communities, invites] = await Promise.all([listChallenges(), listCommunities(), listInvites()]);
    } catch (reason) { error = errorMessage(reason); }
    finally { loading = false; }
  }
  onMount(() => void load());

  async function create(event: SubmitEvent): Promise<void> {
    event.preventDefault(); busy = true; error = '';
    try {
      await createCommunity({ name: communityName.trim(), description: description.trim(), visibility });
      communityName = ''; description = '';
      await load();
    } catch (reason) { error = errorMessage(reason); }
    finally { busy = false; }
  }

  async function handleInvite(invite: Invite, action: 'accept' | 'decline'): Promise<void> {
    busy = true; error = '';
    try {
      await respondToInvite(invite.id, action);
      if (action === 'accept') {
        await load();
        await goto(`/communities/${invite.communityId}`);
      } else {
        invites = invites.filter((item) => item.id !== invite.id);
      }
    } catch (reason) { error = errorMessage(reason); }
    finally { busy = false; }
  }

  async function join(id: string): Promise<void> {
    busy = true; error = '';
    try { await joinCommunity(id); await load(); }
    catch (reason) { error = errorMessage(reason); }
    finally { busy = false; }
  }
</script>

<svelte:head><title>Challenges & communities — HabitLoop</title></svelte:head>
<section class="page-heading"><div><span class="eyebrow">BETTER TOGETHER</span><h1>Find your people.</h1><p>Build momentum with a community, and make a little progress together.</p></div><a class="button primary" href="#new-community">+ New community</a></section>
{#if error}<div class="alert error" role="alert">{error}</div>{/if}

{#if invites.length}
  <section class="card invite-section">
    <div class="section-head"><div><span class="eyebrow">YOU'RE INVITED</span><h2>Community invites</h2></div><span class="badge">{invites.length} pending</span></div>
    <p class="muted">Accepting an invite adds you to the community. Challenge participation is a separate step.</p>
    {#each invites as invite (invite.id)}
      <div class="invite-row"><div class="invite-icon">✉</div><div class="invite-copy"><strong>Community invitation</strong><small>Community ID {invite.communityId}</small></div>
        <button class="button secondary" disabled={busy} onclick={() => handleInvite(invite, 'decline')}>Decline</button>
        <button class="button primary" disabled={busy} onclick={() => handleInvite(invite, 'accept')}>Accept</button>
      </div>
    {/each}
  </section>
{/if}

<section class="section-block">
  <div class="section-head section-heading-row"><div><span class="eyebrow">YOUR COMMUNITIES</span><h2>A place to show up together</h2></div></div>
  {#if loading}<div class="card loading-state">Finding your communities…</div>
  {:else if communities.length}
    <div class="community-grid">{#each communities as community (community.id)}
      <article class="card community-card">
        <div class="community-card-top"><span class="community-icon">◎</span><span class="badge">{community.visibility}</span></div>
        <h3>{community.name}</h3><p>{community.description || 'A community for building good habits together.'}</p>
        <div class="community-card-bottom"><span>{challenges.filter((challenge) => challenge.communityId === community.id).length} challenges</span><div class="button-group"><a class="button secondary" href={`/communities/${community.id}`}>Open</a>{#if community.visibility === 'PUBLIC'}<button class="button primary" disabled={busy} onclick={() => join(community.id)}>Join</button>{/if}</div></div>
      </article>
    {/each}</div>
  {:else}<div class="card empty-state"><span class="empty-icon">◎</span><h3>Your community is out there</h3><p>Join a public community or start one of your own.</p></div>{/if}
</section>

<section class="section-block">
  <div class="section-head"><div><span class="eyebrow">CHALLENGE BOARD</span><h2>Challenges in your circles</h2></div></div>
  {#if loading}<div class="card loading-state">Loading challenges…</div>
  {:else if challenges.length}
    <div class="challenge-grid">{#each challenges as challenge (challenge.id)}
      {@const community = communities.find((item) => item.id === challenge.communityId)}
      <hl-challenge-card challenge-title={challenge.title} community={community?.name ?? 'Your community'} dates={`${challenge.startDate} – ${challenge.endDate} · ${challenge.habitCategory}`} onopen={() => goto(`/challenges/${challenge.id}`)}></hl-challenge-card>
    {/each}</div>
  {:else}<div class="card empty-state compact"><p>No challenges just yet. Join a community, then check back for shared goals.</p></div>{/if}
</section>

<section id="new-community" class="card community-create">
  <div><span class="eyebrow">BRING PEOPLE TOGETHER</span><h2>Start a community</h2><p class="muted">Create a public group or invite people to a private one.</p></div>
  <form class="inline-create-form" onsubmit={create}>
    <label>Community name<input required maxlength="100" bind:value={communityName} placeholder="e.g. Morning movers" /></label>
    <label>Description<input bind:value={description} placeholder="What brings your group together?" /></label>
    <label>Visibility<select bind:value={visibility}><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option></select></label>
    <button class="button primary" disabled={busy}>{busy ? 'Creating…' : 'Create community'}</button>
  </form>
</section>
