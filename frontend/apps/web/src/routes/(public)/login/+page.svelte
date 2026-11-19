<script lang="ts">
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { auth } from '$lib/state/auth.svelte';
  import { errorMessage } from '$lib/forms/errors';

  let email = $state('');
  let password = $state('');
  let tenantSlug = $state('public');
  let busy = $state(false);
  let error = $state('');

  function safeReturnPath(value: string | null): string {
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/login') || value.startsWith('/signup')) {
      return '/dashboard';
    }
    return value;
  }

  async function submit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      await auth.login(email.trim(), password, tenantSlug);
      await goto(safeReturnPath(page.url.searchParams.get('returnTo')));
    } catch (reason) {
      error = errorMessage(reason);
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head><title>Sign in — HabitLoop</title></svelte:head>

<main class="auth-page">
  <section class="auth-brand-panel">
    <a class="brand" href="/login"><span class="brand-mark">H</span><span>habitloop<small>BUILD YOUR RHYTHM</small></span></a>
    <div class="brand-message">
      <span class="eyebrow">A LITTLE BETTER, TOGETHER</span>
      <h1>Small steps.<br />Shared momentum.</h1>
      <p>Make space for the habits that matter, and find your people along the way.</p>
      <div class="quote-card"><span class="quote-icon">✳</span><span>Consistency isn't about being perfect.<br /><strong>It's about showing up again.</strong></span></div>
    </div>
    <span class="brand-footnote">YOUR NEXT CHAPTER STARTS WITH ONE STEP</span>
  </section>
  <section class="auth-form-panel">
    <div class="auth-form-wrap">
      <span class="eyebrow">WELCOME BACK</span>
      <h2>Sign in to your space</h2>
      <p class="muted">Pick up where you left off.</p>
      {#if error || auth.error}<div class="alert error" role="alert">{error || auth.error}</div>{/if}
      <form onsubmit={submit}>
        <label>Email address<input type="email" name="email" autocomplete="email" required bind:value={email} placeholder="you@example.com" /></label>
        <label>Password<input type="password" name="password" autocomplete="current-password" required bind:value={password} placeholder="At least 8 characters" /></label>
        <label>Workspace
          <select bind:value={tenantSlug}>
            <option value="public">Public</option>
            <option value="acme">Acme</option>
          </select>
        </label>
        <button class="button primary wide" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'} <span aria-hidden="true">→</span></button>
      </form>
      <p class="auth-switch">New to HabitLoop? <a href="/signup">Create an account</a></p>
    </div>
    <footer>By continuing, you agree to keep showing up for yourself.</footer>
  </section>
</main>
