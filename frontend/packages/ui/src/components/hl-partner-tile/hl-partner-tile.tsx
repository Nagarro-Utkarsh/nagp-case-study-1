import { Component, Event, EventEmitter, h, Prop } from '@stencil/core';

@Component({ tag: 'hl-partner-tile', styleUrl: 'hl-partner-tile.css', shadow: true })
export class HlPartnerTile {
  @Prop() partner = '';
  @Prop() connected = false;
  @Prop() habit = '';
  @Event() connect: EventEmitter<void>;
  @Event() disconnect: EventEmitter<void>;

  render() {
    return (
      <article class="tile">
        <div class="head"><div class="icon">↗</div><span class={this.connected ? 'status connected' : 'status'}>{this.connected ? 'Connected' : 'Available'}</span></div>
        <h3>{this.partner.replace(/_/g, ' ')}</h3>
        <p>{this.habit || 'Connect a habit to get started.'}</p>
        <slot />
        {this.connected
          ? <button class="secondary" onClick={() => this.disconnect.emit()}>Disconnect</button>
          : <button onClick={() => this.connect.emit()}>Connect</button>}
      </article>
    );
  }
}
