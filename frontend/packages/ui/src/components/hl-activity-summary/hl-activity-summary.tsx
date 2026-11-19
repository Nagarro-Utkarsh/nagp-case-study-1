import { Component, Event, EventEmitter, h, Prop } from '@stencil/core';

@Component({ tag: 'hl-activity-summary', styleUrl: 'hl-activity-summary.css', shadow: true })
export class HlActivitySummary {
  @Prop() habitName = '';
  @Prop() doneToday = false;
  @Prop() streak = 0;
  @Prop() completion = 0;
  @Event() log: EventEmitter<void>;

  render() {
    return (
      <article class="summary">
        <div class="top"><strong>{this.habitName}</strong><span>{this.completion}%</span></div>
        <p>{this.doneToday ? 'Completed today' : 'Not logged today'}</p>
        <div class="bottom"><hl-streak-badge days={this.streak}></hl-streak-badge><button disabled={this.doneToday} onClick={() => this.log.emit()}>{this.doneToday ? 'Completed' : 'Log today'}</button></div>
      </article>
    );
  }
}
