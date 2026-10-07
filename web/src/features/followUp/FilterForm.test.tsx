import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { FilterForm } from './FilterForm';

const FACILITIES = [
  { id: 'FAC-0101', name: 'Mwansa Urban Clinic' },
  { id: 'FAC-0102', name: 'Mwansa East Clinic' },
];

function setup() {
  const onApply = vi.fn();
  render(<FilterForm facilities={FACILITIES} applied={{ facilityId: 'FAC-0101', status: '', overdueDays: 7 }} onApply={onApply} />);
  return { onApply, user: userEvent.setup() };
}

describe('FilterForm', () => {
  it('reaches every control by Tab, in visual order, and submits with Enter', async () => {
    const { onApply, user } = setup();

    await user.tab();
    expect(screen.getByLabelText('Facility')).toHaveFocus();
    await user.selectOptions(screen.getByLabelText('Facility'), 'FAC-0102');
    await user.tab();
    expect(screen.getByLabelText('Status')).toHaveFocus();
    await user.selectOptions(screen.getByLabelText('Status'), 'missed');
    await user.tab();
    expect(screen.getByLabelText('Missed after (days)')).toHaveFocus();
    await user.keyboard('{Control>}a{/Control}14{Enter}');

    expect(onApply).toHaveBeenCalledWith({ facilityId: 'FAC-0102', status: 'missed', overdueDays: 14 });
    await user.tab();
    expect(screen.getByRole('button', { name: 'Show list' })).toHaveFocus();
  });

  it('shows an error tied to the days field and moves focus to it', async () => {
    const { onApply, user } = setup();
    const days = screen.getByLabelText('Missed after (days)');

    await user.clear(days);
    await user.type(days, 'two weeks');
    await user.click(screen.getByRole('button', { name: 'Show list' }));

    expect(onApply).not.toHaveBeenCalled();
    expect(days).toHaveFocus();
    expect(days).toHaveAttribute('aria-invalid', 'true');
    expect(days).toHaveAccessibleDescription(/Enter a whole number of days from 0 to 365/);
  });

  it('clears the error once the value is valid', async () => {
    const { onApply, user } = setup();
    const days = screen.getByLabelText('Missed after (days)');

    await user.clear(days);
    await user.click(screen.getByRole('button', { name: 'Show list' }));
    expect(days).toHaveAttribute('aria-invalid', 'true');

    await user.type(days, '30');
    await user.click(screen.getByRole('button', { name: 'Show list' }));
    expect(days).not.toHaveAttribute('aria-invalid');
    expect(onApply).toHaveBeenCalledWith({ facilityId: 'FAC-0101', status: '', overdueDays: 30 });
  });
});
