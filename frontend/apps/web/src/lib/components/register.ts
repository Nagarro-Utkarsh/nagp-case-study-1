import { defineCustomElements } from '@habitloop/ui/custom-elements';

let registered = false;

export function registerUi(): void {
  if (registered) return;
  defineCustomElements();
  registered = true;
}
