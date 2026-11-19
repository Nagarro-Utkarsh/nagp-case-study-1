import { getCohorts, getDashboard, getTrend } from '@habitloop/api-client';
import type { Cohort, DashboardSummary, Trend } from '@habitloop/api-client';

interface DashboardState {
  summaries: DashboardSummary[];
  cohorts: Cohort[];
  trend: Trend | null;
  trendHabitId: string | null;
  loading: boolean;
  error: string | null;
  lastUpdated: number | null;
  streamState: 'connecting' | 'live' | 'reconnecting' | 'closed';
}

const state = $state<DashboardState>({
  summaries: [],
  cohorts: [],
  trend: null,
  trendHabitId: null,
  loading: false,
  error: null,
  lastUpdated: null,
  streamState: 'closed'
});

let refreshSequence = 0;

export const dashboard = {
  get summaries() { return state.summaries; },
  get cohorts() { return state.cohorts; },
  get trend() { return state.trend; },
  get trendHabitId() { return state.trendHabitId; },
  get loading() { return state.loading; },
  get error() { return state.error; },
  get lastUpdated() { return state.lastUpdated; },
  get streamState() { return state.streamState; },
  setStreamState(value: DashboardState['streamState']): void {
    state.streamState = value;
  },
  async selectTrend(habitId: string): Promise<void> {
    state.trendHabitId = habitId;
    state.error = null;
    try {
      state.trend = await getTrend(habitId, 30);
    } catch (error) {
      state.error = error instanceof Error ? error.message : 'The activity trend could not be loaded.';
    }
  },
  async refresh(): Promise<void> {
    const sequence = ++refreshSequence;
    state.loading = true;
    state.error = null;
    try {
      const [summaries, cohorts] = await Promise.all([getDashboard(), getCohorts()]);
      if (sequence !== refreshSequence) return;
      state.summaries = summaries;
      state.cohorts = cohorts;
      const trendHabitId = state.trendHabitId ?? summaries[0]?.habitId ?? null;
      if (trendHabitId && summaries.some((item) => item.habitId === trendHabitId)) {
        state.trendHabitId = trendHabitId;
        state.trend = await getTrend(trendHabitId, 30);
      } else {
        state.trendHabitId = null;
        state.trend = null;
      }
      if (sequence === refreshSequence) state.lastUpdated = Date.now();
    } catch (error) {
      if (sequence === refreshSequence) {
        state.error = error instanceof Error ? error.message : 'Dashboard could not be loaded.';
      }
    } finally {
      if (sequence === refreshSequence) state.loading = false;
    }
  },
  reset(): void {
    refreshSequence++;
    state.summaries = [];
    state.cohorts = [];
    state.trend = null;
    state.trendHabitId = null;
    state.loading = false;
    state.error = null;
    state.lastUpdated = null;
    state.streamState = 'closed';
  }
};
