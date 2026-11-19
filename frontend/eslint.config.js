import globals from 'globals';
import svelte from 'eslint-plugin-svelte';
import tsParser from '@typescript-eslint/parser';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['**/.svelte-kit/**', '**/build/**', '**/dist/**', '**/node_modules/**'] },
  ...tseslint.configs.recommended,
  ...svelte.configs['flat/recommended'],
  {
    files: ['**/*.{js,ts,tsx,svelte}'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node }
    }
  },
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: tsParser
    }
  },
  {
    files: ['**/*.svelte'],
    languageOptions: {
      parserOptions: {
        parser: tsParser
      }
    }
  },
  {
    files: ['apps/web/src/**/*.{svelte,ts}'],
    rules: {
      'svelte/no-navigation-without-resolve': 'off'
    }
  },
  {
    files: ['packages/ui/src/**/*.tsx'],
    rules: {
      '@typescript-eslint/no-unused-vars': ['error', { varsIgnorePattern: '^h$' }]
    }
  }
);
