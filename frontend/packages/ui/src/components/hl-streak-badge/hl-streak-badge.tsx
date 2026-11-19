import { Component, h, Prop } from '@stencil/core';

@Component({ tag: 'hl-streak-badge', styleUrl: 'hl-streak-badge.css', shadow: true })
export class HlStreakBadge {
  @Prop() days = 0;

  render() {
    return <span class="badge" aria-label={`${this.days} day streak`}>🔥 {this.days} day streak</span>;
  }
}
