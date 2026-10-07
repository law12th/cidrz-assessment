import type { ApiError } from '../../api/apiError';

/** What to tell a clinician for each failure: what happened and what to do, never a stack trace. */
export function describeError(error: ApiError): string {
  switch (error.kind) {
    case 'network':
      return 'The CareLink server cannot be reached. Check that this computer is connected to the clinic network, then try again.';
    case 'timeout':
      return 'The CareLink server took too long to answer. Try again in a moment.';
    case 'invalid_response':
      return 'The server sent an answer this screen does not understand. Try again, and report it if it keeps happening.';
    case 'http':
      if (error.status === 401) return 'Your session has ended. Sign in again to see the list.';
      if (error.status === 403) return 'Your account does not have access to this facility’s list.';
      if (error.status === 404) return 'This facility was not found.';
      if (error.status !== null && error.status >= 500) {
        return 'The CareLink server had a problem loading the list. Try again, and report it if it keeps happening.';
      }
      return error.message;
  }
}

export function LoadingMessage() {
  return (
    <p role="status" className="message">
      Loading the follow-up list…
    </p>
  );
}

export function EmptyMessage() {
  return (
    <p role="status" className="message">
      No patients match these filters. Try another status, or a smaller number of days.
    </p>
  );
}

export function ErrorMessage({ error, onRetry }: { error: ApiError; onRetry: () => void }) {
  return (
    <div role="alert" className="message message--error">
      <p>
        <span aria-hidden="true">✕ </span>
        {describeError(error)}
      </p>
      {error.correlationId && <p className="message__reference">Reference for support: {error.correlationId}</p>}
      <button type="button" className="button button--primary" onClick={onRetry}>
        Try again
      </button>
    </div>
  );
}
