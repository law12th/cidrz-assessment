import { describe, expect, it } from 'vitest';
import { daysLabel, formatDate } from './format';

describe('daysLabel', () => {
  it.each([
    [31, '31 days overdue'],
    [1, '1 day overdue'],
    [0, 'Due today'],
    [-1, 'Due in 1 day'],
    [-3, 'Due in 3 days'],
  ])('%i -> %s', (days, label) => {
    expect(daysLabel(days)).toBe(label);
  });
});

describe('formatDate', () => {
  it('formats a calendar date without shifting it across time zones', () => {
    // "Sep" or "Sept" depending on the browser's locale data
    expect(formatDate('2026-09-05')).toMatch(/^5 Sept? 2026$/);
  });
});
