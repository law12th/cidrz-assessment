import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { ApiError } from '../../api/apiError';
import type { FollowUpService } from '../../api/followUpService';
import { FollowUpServiceProvider } from '../../api/ServiceContext';
import type { FollowUpItem, FollowUpPage, FollowUpQuery } from '../../api/types';
import { FollowUpScreen } from './FollowUpScreen';

const FACILITIES = [{ id: 'FAC-0101', name: 'Mwansa Urban Clinic' }];

function item(overrides: Partial<FollowUpItem>): FollowUpItem {
  return {
    id: 'p_8841', patient_number: '0101-000471', first_name: 'Thandiwe', last_name: 'Banda',
    facility_id: 'FAC-0101', facility_name: 'Mwansa Urban Clinic', missed_appointment_date: '2026-09-05',
    days_overdue: 31, follow_up_status: 'overdue', phone_number: '+260970000471', last_contact_attempt: null,
    ...overrides,
  };
}

const PAGE: FollowUpPage = {
  page: 1, page_size: 50, total: 120,
  items: [
    item({}),
    item({ id: 'p_6611', first_name: 'Chanda', last_name: 'Mwale', patient_number: '0101-000088',
      days_overdue: 8, follow_up_status: 'missed', missed_appointment_date: '2026-09-28' }),
  ],
};

function fakeService(overrides: Partial<FollowUpService> = {}): FollowUpService {
  return {
    list: vi.fn<FollowUpService['list']>().mockResolvedValue(PAGE),
    recordContact: vi.fn<FollowUpService['recordContact']>().mockResolvedValue(undefined),
    ...overrides,
  };
}

function renderScreen(service: FollowUpService, canRecordContact = true) {
  const user = userEvent.setup();
  const view = render(
    <FollowUpServiceProvider service={service}>
      <FollowUpScreen facilities={FACILITIES} canRecordContact={canRecordContact} />
    </FollowUpServiceProvider>,
  );
  return { user, ...view };
}

const lastQuery = (service: FollowUpService): FollowUpQuery => {
  const calls = vi.mocked(service.list).mock.calls;
  return calls[calls.length - 1]![0];
};

describe('FollowUpScreen', () => {
  it('shows a loading state, then the list', async () => {
    renderScreen(fakeService());

    expect(screen.getByText(/Loading the follow-up list/)).toHaveAttribute('role', 'status');
    const table = await screen.findByRole('table');
    expect(within(table).getByRole('rowheader', { name: /Thandiwe Banda/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('120 patients at Mwansa Urban Clinic');
  });

  it('shows status as words, not colour alone', async () => {
    renderScreen(fakeService());
    const table = await screen.findByRole('table');
    expect(within(table).getByText('Overdue')).toBeInTheDocument();
    expect(within(table).getByText('Recently missed')).toBeInTheDocument();
    expect(within(table).getByText('31 days overdue')).toBeInTheDocument();
  });

  it('when the server is down, explains what happened and recovers on retry', async () => {
    const list = vi.fn<FollowUpService['list']>()
      .mockRejectedValueOnce(new ApiError('network', 'The server could not be reached', null, null, 'corr-1234'))
      .mockResolvedValueOnce(PAGE);
    const { user } = renderScreen(fakeService({ list }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/cannot be reached/);
    expect(alert).toHaveTextContent('corr-1234');

    await user.click(within(alert).getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(list).toHaveBeenCalledTimes(2);
  });

  it('shows an empty state with a next step', async () => {
    renderScreen(fakeService({ list: vi.fn<FollowUpService['list']>().mockResolvedValue({ ...PAGE, total: 0, items: [] }) }));
    expect(await screen.findByText(/No patients match these filters/)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('sorts on the server when the column header is activated, and reports the order', async () => {
    const service = fakeService();
    const { user } = renderScreen(service);
    const sortButton = await screen.findByRole('button', { name: /Days overdue/ });
    expect(sortButton.closest('th')).toHaveAttribute('aria-sort', 'descending');

    await user.click(sortButton);

    await waitFor(() => expect(lastQuery(service)).toMatchObject({ sort: 'days_overdue_asc', page: 1 }));
  });

  it('moves focus to the results heading after changing page', async () => {
    const service = fakeService();
    const { user } = renderScreen(service);

    await user.click(await screen.findByRole('button', { name: 'Next page' }));

    await waitFor(() => expect(lastQuery(service).page).toBe(2));
    await waitFor(() => expect(screen.getByRole('heading', { level: 2 })).toHaveFocus());
  });

  it('records a contact, updates the row and announces the result', async () => {
    const service = fakeService();
    const { user } = renderScreen(service);

    await user.click(await screen.findByRole('button', { name: 'Record contact with Thandiwe Banda' }));

    expect(service.recordContact).toHaveBeenCalledWith('p_8841', expect.objectContaining({ contactedAt: expect.any(String) }), expect.any(String));
    expect(await screen.findByText('Contact recorded for Thandiwe Banda.')).toBeInTheDocument();
    const row = screen.getByRole('rowheader', { name: /Thandiwe Banda/ }).closest('tr')!;
    expect(within(row).queryByText('Not contacted yet')).not.toBeInTheDocument();
  });

  it('gives a district officer no way to record a contact', async () => {
    renderScreen(fakeService(), false);
    await screen.findByRole('table');
    expect(screen.queryByRole('button', { name: /Record contact/ })).not.toBeInTheDocument();
  });

  it('has no automatically detectable accessibility violations', async () => {
    const { container } = renderScreen(fakeService());
    await screen.findByRole('table');
    // jsdom cannot render, so contrast is checked separately (see README); every other axe rule runs.
    expect(await axe(container, { rules: { 'color-contrast': { enabled: false } } })).toHaveNoViolations();
  });
});
