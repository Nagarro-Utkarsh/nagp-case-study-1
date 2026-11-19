<script lang="ts">
  import { goto } from '$app/navigation';
  import { auth } from '$lib/state/auth.svelte';
  import { errorMessage } from '$lib/forms/errors';

  let name = $state('');
  let email = $state('');
  let password = $state('');
  let tenantSlug = $state('public');
  let busy = $state(false);
  let error = $state('');

  async function submit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      await auth.signup(name.trim(), email.trim(), password, tenantSlug);
      await goto('/onboarding');
    } catch (reason) {
      error = errorMessage(reason);
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head><title>Create account — HabitLoop</title></svelte:head>

<main class="auth-page">
  <section class="auth-brand-panel">
    <a class="brand" href="/login"><span class="brand-mark">H</span><span>habitloop<small>BUILD YOUR RHYTHM</small></span></a>
    <div class="brand-message">
      <span class="eyebrow">A FRESH START</span>
      <h1>Your goals,<br />with good company.</h1>
      <p>Start with one small habit. Build a rhythm that fits your life, and share the journey with a community.</p>
      <div class="quote-card"><span class="quote-icon">✳</span><span>Make today count.<br /><strong>Tomorrow will take care of itself.</strong></span></div>
    </div>
    <span class="brand-footnote">ONE STEP IS ENOUGH TO BEGIN</span>
  </section>
  <section class="auth-form-panel">
    <div class="auth-form-wrap">
      <span class="eyebrow">GET STARTED</span>
      <h2>Create your account</h2>
      <p class="muted">A better routine starts here.</p>
      {#if error}<div class="alert error" role="alert">{error}</div>{/if}
      <form onsubmit={submit}>
        <label>Your name<input name="name" autocomplete="name" required maxlength="100" bind:value={name} placeholder="Alex Morgan" /></label>
        <label>Email address<input type="email" name="email" autocomplete="email" required bind:value={email} placeholder="you@example.com" /></label>
        <label>Password<input type="password" name="new-password" autocomplete="new-password" required minlength="8" maxlength="72" bind:value={password} placeholder="At least 8 characters" /></label>
        <label>Workspace
          <select bind:value={tenantSlug}>
            <option value="public">Public</option>
            <option value="acme">Acme</option>
          </select>
        </label>
        <button class="button primary wide" disabled={busy}>{busy ? 'Creating account…' : 'Create account'} <span aria-hidden="true">→</span></button>
      </form>
      <p class="auth-switch">Already have an account? <a href="/login">Sign in</a></p>
    </div>
    <footer>Your account is scoped to the selected workspace.</footer>
  </section>
</main>
