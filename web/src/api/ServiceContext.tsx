import { createContext, useContext, type ReactNode } from 'react';
import type { FollowUpService } from './followUpService';

const FollowUpServiceContext = createContext<FollowUpService | null>(null);

export function FollowUpServiceProvider({ service, children }: { service: FollowUpService; children: ReactNode }) {
  return <FollowUpServiceContext.Provider value={service}>{children}</FollowUpServiceContext.Provider>;
}

export function useFollowUpService(): FollowUpService {
  const service = useContext(FollowUpServiceContext);
  if (!service) throw new Error('useFollowUpService must be used inside FollowUpServiceProvider');
  return service;
}
