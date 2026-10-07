import { render } from '@testing-library/react';
import { it } from 'vitest';
import { FollowUpTable } from '../features/followUp/FollowUpTable';
import { generateItems } from './fixtures';

/**
 * npm run measure. Numbers that do not depend on the machine: DOM size and response payload size
 * for 10,000 rows against one page of 50. Times printed here come from jsdom, not a browser, so they
 * show the ratio only. Use /?bench in Chrome for real timings.
 */
it('measures the 10,000-row view against one page', () => {
  const all = generateItems(10_000);
  const encoder = new TextEncoder();

  for (const count of [10_000, 50]) {
    const items = all.slice(0, count);
    const payloadBytes = encoder.encode(
      JSON.stringify({ page: 1, page_size: count, total: 10_000, items }),
    ).length;
    const start = performance.now();
    const { container, unmount } = render(
      <FollowUpTable
        items={items}
        caption="Measurement"
        sort="days_overdue_desc"
        busy={false}
        canRecordContact
        pendingIds={new Set()}
        onSortChange={() => undefined}
        onRecordContact={() => undefined}
      />,
    );
    const ms = performance.now() - start;
    const elements = container.querySelectorAll('*').length;
    unmount();
    console.log(
      `${String(count).padStart(6)} rows | ${String(elements).padStart(7)} DOM elements | ` +
        `${(payloadBytes / 1024).toFixed(1).padStart(8)} KB JSON | ${ms.toFixed(0).padStart(6)} ms (jsdom, indicative only)`,
    );
  }
});
