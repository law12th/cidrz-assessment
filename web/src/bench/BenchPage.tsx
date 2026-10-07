import { useRef, useState } from 'react';
import type { FollowUpItem } from '../api/types';
import { FollowUpTable } from '../features/followUp/FollowUpTable';
import { generateItems } from './fixtures';

/**
 * Measures the real FollowUpTable component with 10,000 rows ("before": everything rendered at once,
 * as the C2 component does) against one page of 50 ("after").
 * Open /?bench in Chrome. For numbers closer to an old clinic PC, set DevTools > Performance >
 * CPU to "4x slowdown" first. See the README for the procedure.
 */
const TOTAL = 10_000;
const PAGE = 50;
const RUNS = 5;

interface Result {
  mode: string;
  rows: number;
  medianMs: number;
  domElements: number;
  heapMb: number | null;
}

const nextPaint = () =>
  new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length / 2)] ?? 0;
}

function usedHeapMb(): number | null {
  // performance.memory exists only in Chromium and is not in the standard DOM types.
  const memory = (performance as Performance & { memory?: { usedJSHeapSize: number } }).memory;
  return memory ? Math.round(memory.usedJSHeapSize / 1_048_576) : null;
}

export function BenchPage() {
  const all = useRef<FollowUpItem[]>(generateItems(TOTAL));
  const container = useRef<HTMLDivElement>(null);
  const [rows, setRows] = useState<FollowUpItem[]>([]);
  const [results, setResults] = useState<Result[]>([]);
  const [running, setRunning] = useState(false);

  async function measure(mode: string, count: number): Promise<Result> {
    const timings: number[] = [];
    let domElements = 0;
    for (let run = 0; run < RUNS; run++) {
      setRows([]);
      await nextPaint();
      const start = performance.now();
      setRows(all.current.slice(0, count));
      await nextPaint(); // render, layout and paint have finished
      timings.push(performance.now() - start);
      domElements = container.current?.querySelectorAll('*').length ?? 0;
    }
    return { mode, rows: count, medianMs: Math.round(median(timings)), domElements, heapMb: usedHeapMb() };
  }

  async function runAll() {
    setRunning(true);
    const before = await measure('Before: all rows at once', TOTAL);
    const after = await measure('After: one page', PAGE);
    setRows([]);
    setResults([before, after]);
    setRunning(false);
  }

  return (
    <main className="bench">
      <h1>Follow-up table: rendering measurement</h1>
      <p>
        Renders {TOTAL.toLocaleString('en-GB')} rows and then {PAGE} rows, {RUNS} times each, and reports the median
        time from setting the rows to the next paint.
      </p>
      <button type="button" className="button button--primary" onClick={runAll} disabled={running}>
        {running ? 'Measuring…' : 'Run measurement'}
      </button>
      {results.length > 0 && (
        <table className="results">
          <caption>Results (median of {RUNS} runs)</caption>
          <thead>
            <tr>
              <th scope="col">Mode</th>
              <th scope="col">Rows</th>
              <th scope="col">Render to paint (ms)</th>
              <th scope="col">DOM elements</th>
              <th scope="col">JS heap after (MB)</th>
            </tr>
          </thead>
          <tbody>
            {results.map((r) => (
              <tr key={r.mode}>
                <th scope="row">{r.mode}</th>
                <td>{r.rows.toLocaleString('en-GB')}</td>
                <td>{r.medianMs}</td>
                <td>{r.domElements.toLocaleString('en-GB')}</td>
                <td>{r.heapMb ?? 'n/a (Chrome only)'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <div ref={container} aria-hidden="true">
        {rows.length > 0 && (
          <FollowUpTable
            items={rows}
            caption="Measurement table"
            sort="days_overdue_desc"
            busy={false}
            canRecordContact
            pendingIds={new Set()}
            onSortChange={() => undefined}
            onRecordContact={() => undefined}
          />
        )}
      </div>
    </main>
  );
}
