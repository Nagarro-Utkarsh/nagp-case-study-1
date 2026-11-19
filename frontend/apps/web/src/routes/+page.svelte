<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  onMount(() => goto(resolve('/dashboard'), { replaceState: true }));
</script>

<div class="boot-screen"><span class="brand-mark">H</span><p>Opening your space…</p></div>
