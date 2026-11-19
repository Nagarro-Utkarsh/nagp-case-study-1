import { Component, Event, EventEmitter, h, Prop } from '@stencil/core';

@Component({ tag: 'hl-challenge-card', styleUrl: 'hl-challenge-card.css', shadow: true })
export class HlChallengeCard {
  @Prop() challengeTitle = '';
  @Prop() community = '';
  @Prop() dates = '';
  @Event() open: EventEmitter<void>;

  render() {
    return (
      <article class="card">
        <span class="eyebrow">{this.community}</span>
        <h3>{this.challengeTitle}</h3>
        <p>{this.dates}</p>
        <button onClick={() => this.open.emit()}>View challenge</button>
      </article>
    );
  }
}
