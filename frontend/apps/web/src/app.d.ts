declare global {
  namespace App {}
}

declare module 'svelte/elements' {
  interface SvelteHTMLElements {
    'hl-activity-summary': import('svelte/elements').HTMLAttributes<HTMLElement> & {
      'habit-name'?: string;
      'done-today'?: boolean;
      streak?: number;
      completion?: number;
      onlog?: (event: CustomEvent<void>) => void;
    };
    'hl-challenge-card': import('svelte/elements').HTMLAttributes<HTMLElement> & {
      'challenge-title'?: string;
      community?: string;
      dates?: string;
      onopen?: (event: CustomEvent<void>) => void;
    };
    'hl-partner-tile': import('svelte/elements').HTMLAttributes<HTMLElement> & {
      partner?: string;
      connected?: boolean;
      habit?: string;
      onconnect?: (event: CustomEvent<void>) => void;
      ondisconnect?: (event: CustomEvent<void>) => void;
    };
    'hl-trend-chart': import('svelte/elements').HTMLAttributes<HTMLElement> & {
      points?: Array<{ date: string; count: number }>;
    };
    'hl-streak-badge': import('svelte/elements').HTMLAttributes<HTMLElement> & {
      days?: number;
    };
  }
}

export {};
