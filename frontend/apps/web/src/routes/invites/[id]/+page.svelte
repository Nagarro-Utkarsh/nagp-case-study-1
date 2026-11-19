<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { getChallenge, listInvites, respondToInvite } from '@habitloop/api-client';
  import { errorMessage } from '$lib/forms/errors';
  import type { Invite } from '@habitloop/api-client';

  const inviteId = page.params.id;
  const challengeId = page.url.searchParams.get('challenge');
  let invite = $state<Invite | null>(null);
  let loading = $state(true);
  let busy = $state(false);
  let error = $state('');
  let notice = $state('');

  onMount(() => {
    void listInvites().then((items) => {
      invite = items.find((item) => item.id === inviteId) ?? null;
      if (!invite) error = 'This invite is not pending for your account. It may have already been accepted or declined.';
    }).catch((reason: unknown) => error = errorMessage(reason)).finally(() => loading = false);
  });

  async function accept(): Promise<void> {
    if (!invite) return;
    busy = true; error = ''; notice = '';
    try {
      await respondToInvite(invite.id, 'accept');
      if (!challengeId) {
        notice = 'You are now a member of this community.';
        return;
      }
      try {
        const detail = await getChallenge(challengeId);
        if (detail.challenge.communityId !== invite.communityId) {
          notice = 'You joined the invited community, but this link points to a different community challenge. Your community membership is already active.';
          return;
        }
        await goto(`/challenges/${encodeURIComponent(challengeId)}`);
      } catch {
        notice = 'You joined the community, but this challenge link is unavailable. Your community membership is already active; you can return to the challenge feed.';
      }
    } catch (reason) { error = errorMessage(reason); }
    finally { busy = false; }
  }

  async function decline(): Promise<void> {
    if (!invite) return;
    busy = true; error = '';
    try {
      await respondToInvite(invite.id, 'decline');
      notice = 'Invite declined.';
      invite = null;
    } catch (reason) { error = errorMessage(reason); }
    finally { busy = false; }
  }
</script>

<svelte:head><title>Community invitation — HabitLoop</title></svelte:head>
<section class="invite-landing">
  <div class="invite-illustration"><span>◎</span><i>✳</i><b>+</b></div>
  <span class="eyebrow">A PLACE IN THE CIRCLE</span>
  <h1>You're invited.</h1>
  <p>Join this HabitLoop community and build momentum with people who are showing up too.</p>
  {#if loading}<div class="loading-state">Checking your invitation…</div>
  {:else if error}<div class="alert error" role="alert">{error}</div>
  {:else if notice}<div class="alert success" role="status">{notice}</div>
  {:else if invite}
    <p class="muted">Community ID <span class="mono">{invite.communityId}</span></p>
    <div class="invite-actions"><button class="button secondary" disabled={busy} onclick={decline}>Decline</button><button class="button primary" disabled={busy} onclick={accept}>{busy ? 'Joining…' : 'Accept invite'} <span aria-hidden="true">→</span></button></div>
  {/if}
  <a class="back-link" href="/challenges">Back to challenges</a>
</section>
