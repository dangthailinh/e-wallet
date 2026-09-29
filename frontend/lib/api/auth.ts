import { ApiError, apiRequest } from './client';

export type AuthSession = {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
  roles: string[];
};

export type LoginInput = {
  username: string;
  password: string;
};

export type SignupInput = LoginInput & {
  firstName: string;
  lastName: string;
  email: string;
};

export const SESSION_ENDED_EVENT = 'storefront:session-ended';
const SESSION_ENDED_CHANNEL = 'storefront-session-events';
export const SESSION_ENDED_STORAGE_KEY = 'storefront.session-ended.signal';

export async function login(input: LoginInput): Promise<AuthSession> {
  // A public page can already have an in-flight /me -> /refresh attempt from
  // the session navigation. Do not let that pre-login null result be reused
  // after the successful login has navigated to the storefront.
  sessionRestoreGeneration += 1;
  pendingSessionRestore = undefined;
  try {
    return await apiRequest<AuthSession>('/auth/login', { method: 'POST', body: input });
  } finally {
    pendingSessionRestore = undefined;
  }
}

export function signup(input: SignupInput): Promise<{ id: number }> {
  return apiRequest<{ id: number }>('/auth/signup', { method: 'POST', body: input });
}

export function getCurrentUser(): Promise<AuthSession> {
  return apiRequest<AuthSession>('/auth/me');
}

export function refreshSession(): Promise<AuthSession> {
  return apiRequest<AuthSession>('/auth/refresh', { method: 'POST' });
}

export async function logout(): Promise<void> {
  try {
    await apiRequest<void>('/auth/logout', { method: 'POST' });
  } finally {
    announceSessionEnded();
  }
}

export function announceSessionEnded(): void {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new Event(SESSION_ENDED_EVENT));
  const channel = openSessionChannel();
  if (channel) {
    channel.postMessage(SESSION_ENDED_EVENT);
    channel.close();
    return;
  }
  try {
    window.localStorage.setItem(SESSION_ENDED_STORAGE_KEY, `${Date.now()}-${Math.random()}`);
    window.localStorage.removeItem(SESSION_ENDED_STORAGE_KEY);
  } catch {
    // The current-tab event above still clears sensitive UI when storage is unavailable.
  }
}

export function subscribeSessionEnded(listener: () => void): () => void {
  if (typeof window === 'undefined') return () => undefined;
  const onLocal = () => listener();
  window.addEventListener(SESSION_ENDED_EVENT, onLocal);

  const channel = openSessionChannel();
  if (channel) {
    const onMessage = (event: MessageEvent<unknown>) => {
      if (event.data === SESSION_ENDED_EVENT) listener();
    };
    channel.addEventListener('message', onMessage);
    return () => {
      window.removeEventListener(SESSION_ENDED_EVENT, onLocal);
      channel.removeEventListener('message', onMessage);
      channel.close();
    };
  }

  const onStorage = (event: StorageEvent) => {
    if (event.key === SESSION_ENDED_STORAGE_KEY && event.newValue) listener();
  };
  window.addEventListener('storage', onStorage);
  return () => {
    window.removeEventListener(SESSION_ENDED_EVENT, onLocal);
    window.removeEventListener('storage', onStorage);
  };
}

function openSessionChannel(): BroadcastChannel | undefined {
  try {
    return typeof window.BroadcastChannel === 'function'
      ? new window.BroadcastChannel(SESSION_ENDED_CHANNEL)
      : undefined;
  } catch {
    return undefined;
  }
}

let sessionRestoreGeneration = 0;
let pendingSessionRestore:
  | { generation: number; promise: Promise<AuthSession | null> }
  | undefined;

export function restoreSession(): Promise<AuthSession | null> {
  const generation = sessionRestoreGeneration;
  if (!pendingSessionRestore || pendingSessionRestore.generation !== generation) {
    const promise = restoreSessionOnce().finally(() => {
      // An older request must not clear a newer generation's in-flight
      // promise (for example, /me started on /login racing with a login).
      if (pendingSessionRestore?.generation === generation) {
        pendingSessionRestore = undefined;
      }
    });
    pendingSessionRestore = { generation, promise };
  }
  return pendingSessionRestore.promise;
}

async function restoreSessionOnce(): Promise<AuthSession | null> {
  try {
    return await getCurrentUser();
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
  }

  try {
    return await refreshSession();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}
