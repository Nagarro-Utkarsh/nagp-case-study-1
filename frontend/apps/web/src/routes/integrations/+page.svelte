<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import { dashboard } from '$lib/state/dashboard.svelte';
  import { connectPartner, disconnectPartner, getEvents, listConnections, listPartners, listHabits, simulateEvent } from '@habitloop/api-client';
  import type { Connection, CreatedConnection, Habit, Partner, PartnerEvent } from '@habitloop/api-client';
  import { errorMessage } from '$lib/forms/errors';

  let partners = $state<Partner[]>([]);
  let connections = $state<Connection[]>([]);
  let habits = $state<Habit[]>([]);
  let events = $state<Record<string, PartnerEvent[]>>({});
  let selectedHabit = $state('');
  let loading = $state(true);
  let busyId = $state('');
  let error = $state('');
  let notice = $state('');
  let apiKeyNotice = $state<CreatedConnection | null>(null);
  let recentReceipt = $state<Record<string, { eventId: string; duplicate: boolean }>>({});
  let pollTimer: ReturnType<typeof setInterval> | undefined;
  let keyTimer: ReturnType<typeof setTimeout> | undefined;

  async function load(): Promise<void> {
    try {
      [partners, connections, habits] = await Promise.all([listPartners(), listConnections(), listHabits()]);
      if (!selectedHabit && habits[0]) selectedHabit = habits[0].id;
      await Promise.all(connections.map(async (connection) => {
        events[connection.id] = await getEvents(connection.id);
      }));
    } catch (reason) { error = errorMessage(reason); }
    finally { loading = false; }
  }
  onMount(() => {
    void load();
    pollTimer = setInterval(() => {
      for (const connection of connections) {
        if (events[connection.id]?.some((event) => event.status === 'RECEIVED')) void refreshEvents(connection.id);
      }
    }, 2000);
  });
  onDestroy(() => {
    if (pollTimer) clearInterval(pollTimer);
    if (keyTimer) clearTimeout(keyTimer);
  });

  async function refreshEvents(connectionId: string): Promise<void> {
    try {
      events[connectionId] = await getEvents(connectionId);
      const currentReceipt = recentReceipt[connectionId];
      if (currentReceipt && events[connectionId].some((item) => item.eventId === currentReceipt.eventId && item.status !== 'RECEIVED')) {
        void dashboard.refresh();
      }
    } catch (reason) { error = errorMessage(reason); }
  }

  async function connect(partner: Partner): Promise<void> {
    if (!selectedHabit) { error = 'Create a habit before connecting a partner.'; return; }
    busyId = partner.partner; error = ''; notice = '';
    try {
      const created = await connectPartner(partner.partner, selectedHabit);
      apiKeyNotice = created;
      notice = `${partner.partner.replaceAll('_', ' ')} connected. Save this API key now; it is only shown once.`;
      if (keyTimer) clearTimeout(keyTimer);
      keyTimer = setTimeout(() => apiKeyNotice = null, 45_000);
      connections = await listConnections();
      await refreshEvents(created.id);
    } catch (reason) { error = errorMessage(reason); }
    finally { busyId = ''; }
  }

  async function disconnect(connection: Connection): Promise<void> {
    if (!confirm('Disconnect this wearable? Existing activity will be kept.')) return;
    busyId = connection.id; error = ''; notice = '';
    try {
      await disconnectPartner(connection.id);
      connections = await listConnections();
      notice = 'Wearable disconnected. Existing activity was kept.';
    } catch (reason) { error = errorMessage(reason); }
    finally { busyId = ''; }
  }

  async function sendEvent(connection: Connection, kind: 'valid' | 'invalid' | 'resend'): Promise<void> {
    busyId = connection.id; error = ''; notice = '';
    const previous = recentReceipt[connection.id];
    if (kind === 'resend' && !previous) {
      error = 'Send an event first so there is an event to resend.';
      busyId = '';
      return;
    }
    try {
      const receipt = await simulateEvent(connection.id, {
        metric: kind === 'invalid' ? 'unknown_metric' : 'steps',
        value: kind === 'invalid' ? 0 : 2400,
        occurredAt: new Date().toISOString(),
        ...(kind === 'resend' && previous ? { eventId: previous.eventId } : {})
      });
      recentReceipt[connection.id] = { eventId: receipt.eventId, duplicate: receipt.duplicate };
      notice = receipt.duplicate
        ? `Duplicate event acknowledged (${receipt.eventId}); no second activity was created.`
        : kind === 'invalid'
          ? `Invalid event sent (${receipt.eventId}). Its rejection will appear in the event history.`
          : `Event received (${receipt.eventId}); waiting for its processing status.`;
      await refreshEvents(connection.id);
      if (kind !== 'invalid') void dashboard.refresh();
    } catch (reason) { error = errorMessage(reason); }
    finally { busyId = ''; }
  }

  function statusClass(status: string): string {
    return status === 'PROCESSED' ? 'status-good' : status === 'REJECTED' ? 'status-bad' : 'status-waiting';
  }
</script>

<svelte:head><title>Integrations — HabitLoop</title></svelte:head>
<section class="page-heading"><div><span class="eyebrow">YOUR DEVICES</span><h1>Bring your activity along.</h1><p>Connect a partner to keep your progress in sync. The mock wearable is ready for a demo.</p></div></section>
{#if error}<div class="alert error" role="alert">{error}</div>{/if}
{#if notice}<div class="alert success" role="status">{notice}</div>{/if}
{#if apiKeyNotice}<section class="api-key-notice"><div><strong>Wearable API key — shown once</strong><p>Copy it now and keep it private. This key is not stored in your browser.</p></div><code>{apiKeyNotice.apiKey}</code><button class="button secondary" onclick={async () => { try { await navigator.clipboard.writeText(apiKeyNotice!.apiKey); notice = 'API key copied. Keep it private.'; } catch (reason) { error = errorMessage(reason); } }}>Copy key</button><button class="close-button" aria-label="Dismiss API key" onclick={() => apiKeyNotice = null}>×</button></section>{/if}

<section class="card partner-header">
  <div><span class="eyebrow">AVAILABLE PARTNERS</span><h2>Choose what you connect</h2><p class="muted">Choose which habit should receive partner activity.</p></div>
  {#if habits.length}<label>Linked habit<select bind:value={selectedHabit}>{#each habits as habit (habit.id)}<option value={habit.id}>{habit.name} · {habit.category}</option>{/each}</select></label>{:else}<a class="button secondary" href="/habits">Create a habit first</a>{/if}
</section>
{#if loading}<div class="card loading-state">Checking your integrations…</div>
{:else}
  <div class="partner-grid">
    {#each partners as partner (partner.partner)}
      {@const active = connections.find((item) => item.partner === partner.partner && item.status === 'ACTIVE')}
      {#if partner.partner === 'MOCK_WEARABLE'}
        <hl-partner-tile partner={partner.partner} connected={Boolean(active)} habit={habits.find((habit) => habit.id === active?.habitId)?.name ?? ''} onconnect={() => connect(partner)} ondisconnect={() => active && disconnect(active)}></hl-partner-tile>
      {:else}
        <article class="card unavailable-partner"><div class="partner-icon">↗</div><span class="badge">Coming soon</span><h3>{partner.partner.replaceAll('_', ' ')}</h3><p>This partner isn't available in the current demo.</p><button class="button secondary" disabled>Not available yet</button></article>
      {/if}
    {/each}
  </div>
{/if}

{#each connections.filter((item) => item.status === 'ACTIVE') as connection (connection.id)}
  <section class="card wearable-panel">
    <div class="section-head"><div><span class="eyebrow">MOCK WEARABLE · DEMO PANEL</span><h2>Send a test event</h2></div><span class="badge">{habits.find((habit) => habit.id === connection.habitId)?.name ?? 'Linked habit'}</span></div>
    <p class="muted">Simulate a wearable event, replay it to confirm duplicate handling, or send an invalid event to see validation in action.</p>
    <div class="demo-actions">
      <button class="button primary" disabled={busyId === connection.id} onclick={() => sendEvent(connection, 'valid')}>{busyId === connection.id ? 'Sending…' : 'Send valid event'}</button>
      <button class="button secondary" disabled={busyId === connection.id || !recentReceipt[connection.id]} onclick={() => sendEvent(connection, 'resend')}>Resend last event</button>
      <button class="button danger-button" disabled={busyId === connection.id} onclick={() => sendEvent(connection, 'invalid')}>Send invalid event</button>
    </div>
    {#if recentReceipt[connection.id]}<div class="receipt-note"><span class="eyebrow">LATEST RECEIPT</span><code>{recentReceipt[connection.id].eventId}</code><span>{recentReceipt[connection.id].duplicate ? 'Duplicate — no second log' : 'Accepted by Integration service'}</span></div>{/if}
    <div class="event-history">
      <div class="section-head"><h3>Recent event history</h3><button class="text-button" onclick={() => refreshEvents(connection.id)}>Refresh</button></div>
      {#if events[connection.id]?.length}
        <div class="event-table">
          <div class="event-table-head"><span>EVENT</span><span>STATUS</span><span>RECEIVED · UTC</span><span>ATTEMPTS</span></div>
          {#each events[connection.id] as event (event.eventId)}
            <div class="event-row"><span><code>{event.eventId}</code>{#if event.rejectReason}<small class="event-reason">{event.rejectReason}</small>{/if}</span><span class={`status-pill ${statusClass(event.status)}`}>{event.status}</span><span>{new Intl.DateTimeFormat(undefined, { dateStyle: 'short', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(event.receivedAt))}</span><span>{event.publishAttempts}</span></div>
          {/each}
        </div>
      {:else}<div class="empty-state compact"><p>Your simulated events will appear here.</p></div>{/if}
    </div>
  </section>
{/each}
<p class="fine-print integration-footnote">Event processing and analytics are asynchronous; dashboard summaries may take a short time to catch up.</p>
