import { useId, useMemo, useState } from 'react';
import { createFollowUpService } from './api/followUpService';
import { createHttpClient } from './api/httpClient';
import { FollowUpServiceProvider } from './api/ServiceContext';
import { DEV_IDENTITIES } from './config/identities';
import { FollowUpScreen } from './features/followUp/FollowUpScreen';

export function App() {
  const selectId = useId();
  const [identityIndex, setIdentityIndex] = useState(0);
  const identity = DEV_IDENTITIES[identityIndex] ?? DEV_IDENTITIES[0]!;

  const service = useMemo(
    () => createFollowUpService(createHttpClient({ getToken: () => identity.token })),
    [identity.token],
  );

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to the follow-up list
      </a>
      <header className="app-header">
        <p className="app-header__name">CareLink</p>
        {/* Development only: stands in for signing in (see README). */}
        <div className="dev-identity">
          <label htmlFor={selectId}>Signed in as (development)</label>
          <select id={selectId} value={identityIndex} onChange={(e) => setIdentityIndex(Number(e.target.value))}>
            {DEV_IDENTITIES.map((option, index) => (
              <option key={option.token} value={index}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </header>
      <FollowUpServiceProvider service={service}>
        {/* key: switching identity resets the screen's filters for the new user. */}
        <FollowUpScreen
          key={identity.token}
          facilities={identity.facilities}
          canRecordContact={identity.canRecordContact}
        />
      </FollowUpServiceProvider>
    </>
  );
}
