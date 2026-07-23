import { create } from 'zustand';

import type { Session } from '@/domain/types';
import { listSessions } from '@/storage/session-files';

type SessionHistoryState = {
  status: 'idle' | 'loading' | 'ready' | 'error';
  sessions: Session[];
  errors: string[];
  hydrate: () => Promise<void>;
};

export const useSessionHistoryStore = create<SessionHistoryState>((set) => ({
  status: 'idle',
  sessions: [],
  errors: [],
  hydrate: async () => {
    set({ status: 'loading' });
    const { sessions, errors } = await listSessions();
    set({ status: 'ready', sessions, errors });
  },
}));
