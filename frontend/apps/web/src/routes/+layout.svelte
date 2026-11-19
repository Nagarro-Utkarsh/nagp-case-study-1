<script lang="ts">
  import { onMount } from 'svelte';
  import type { Snippet } from 'svelte';
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { dashboard } from '$lib/state/dashboard.svelte';
  import { auth } from '$lib/state/auth.svelte';
  import { openDashboardStream } from '@habitloop/api-client';
  import { registerUi } from '$lib/components/register';
  import './app.css';

  let { children }: { children: Snippet } = $props();
  const nav = [
    { href: '/dashboard', label: 'Overview', icon: '◫' },
    { href: '/habits', label: 'My habits', icon: '✓' },
    { href: '/challenges', label: 'Challenges', icon: '◎' },
    { href: '/integrations', label: 'Integrations', icon: '↗' },
    { href: '/recommendations', label: 'Recommendations', icon: '✳' }
  ];
  let mobileMenu = $state(false);
  let eventSource: EventSource | null = null;
  let streamToken: string | null = null;

  onMount(() => {
    void auth.hydrate();
    void registerUi();
    const storage = (event: StorageEvent) => {
      if (event.key === 'habitloop.token') auth.storageChanged(event.newValue);
    };
    window.addEventListener('storage', storage);
    return () => {
      window.removeEventListener('storage', storage);
      eventSource?.close();
    };
  });

  $effect(() => {
    const ready = auth.ready;
    const token = auth.token;
    const path = page.url.pathname;
    if (!ready) return;
    const publicPage = path === '/login' || path === '/signup';
    const isAuthed = auth.isAuthed;

    if (!isAuthed) {
      eventSource?.close();
      eventSource = null;
      streamToken = null;
      dashboard.reset();
      if (!publicPage) {
        const returnTo = `${path}${page.url.search}`;
        void goto(`/login?returnTo=${encodeURIComponent(returnTo)}`, { replaceState: true });
      }
      return;
    }
    if (publicPage) {
      void goto('/dashboard', { replaceState: true });
      return;
    }
    if (!token) return;
    if (eventSource && streamToken !== token) {
      eventSource.close();
      eventSource = null;
      dashboard.reset();
    }
    if (!eventSource) {
      dashboard.setStreamState('connecting');
      eventSource = openDashboardStream(token);
      streamToken = token;
      eventSource.onopen = () => {
        dashboard.setStreamState('live');
        void dashboard.refresh();
      };
      eventSource.addEventListener('dashboard-updated', () => void dashboard.refresh());
      eventSource.onerror = () => dashboard.setStreamState('reconnecting');
    }
  });
</script>

<svelte:head>
  <title>HabitLoop — small steps, shared momentum</title>
  <meta name="description" content="Build consistent habits with your community." />
</svelte:head>

{#if !auth.ready}
  <div class="boot-screen"><span class="brand-mark">H</span><p>Getting your space ready…</p></div>
{:else if !auth.isAuthed}
  {@render children()}
{:else}
  <div class="app-shell">
    <aside class:open={mobileMenu} class="sidebar">
      <a class="brand" href="/dashboard"><span class="brand-mark">H</span><span>habitloop<small>BUILD YOUR RHYTHM</small></span></a>
      <div class="workspace-label">WORKSPACE</div>
      <nav aria-label="Main navigation">
        {#each nav as item (item.href)}
          <a class:active={page.url.pathname === item.href || page.url.pathname.startsWith(`${item.href}/`)} href={item.href} onclick={() => mobileMenu = false}>
            <span class="nav-icon">{item.icon}</span>{item.label}
          </a>
        {/each}
      </nav>
      <div class="sidebar-bottom">
        <div class="tenant-pill"><span></span>{page.url.pathname ? auth.user?.timezone ?? 'UTC' : 'UTC'} workspace</div>
        <button class="profile-button" onclick={() => auth.logout()}>
          <span class="avatar">{auth.user?.name?.slice(0, 1).toUpperCase() ?? 'U'}</span>
          <span class="profile-copy"><strong>{auth.user?.name}</strong><small>{auth.user?.email}</small></span>
          <span aria-hidden="true">↗</span>
        </button>
      </div>
    </aside>
    <main class="main">
      <header class="topbar">
        <button class="menu-toggle" aria-label="Toggle navigation" aria-expanded={mobileMenu} onclick={() => mobileMenu = !mobileMenu}>☰</button>
        <div class="breadcrumb">YOUR SPACE <span>/</span> <strong>{nav.find((item) => page.url.pathname.startsWith(item.href))?.label ?? page.url.pathname.split('/')[1] ?? 'Overview'}</strong></div>
        <div class="topbar-right">
          <span class="live-indicator"><i class:live={dashboard.streamState === 'live'}></i>{dashboard.streamState === 'live' ? 'Live updates on' : dashboard.streamState === 'reconnecting' ? 'Reconnecting…' : 'Connecting…'}</span>
          <span class="avatar small">{auth.user?.name?.slice(0, 1).toUpperCase() ?? 'U'}</span>
        </div>
      </header>
      <div class="page-content">{@render children()}</div>
    </main>
  </div>
  {#if mobileMenu}<button class="nav-backdrop" aria-label="Close navigation" onclick={() => mobileMenu = false}></button>{/if}
{/if}
