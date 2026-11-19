import { Component, h, Prop } from '@stencil/core';

@Component({ tag: 'hl-trend-chart', styleUrl: 'hl-trend-chart.css', shadow: true })
export class HlTrendChart {
  @Prop() points: Array<{ date: string; count: number }> = [];

  render() {
    const values = this.points ?? [];
    const max = Math.max(1, ...values.map((point) => point.count));
    const width = Math.max(240, values.length * 22);
    return (
      <div class="chart" role="img" aria-label="Activity trend chart">
        {values.length === 0
          ? <p>No trend data yet.</p>
          : <svg viewBox={`0 0 ${width} 130`} preserveAspectRatio="none">
              <line x1="0" y1="110" x2={width} y2="110" />
              {values.map((point, index) => {
                const barHeight = Math.max(3, (point.count / max) * 90);
                const x = index * 22 + 5;
                return <g>
                  <rect x={x} y={110 - barHeight} width="12" height={barHeight} rx="4" />
                  <title>{point.date}: {point.count}</title>
                </g>;
              })}
            </svg>}
      </div>
    );
  }
}
