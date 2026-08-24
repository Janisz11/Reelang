import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/client";
import {
  ADMIN_TOKEN_CHANGED_EVENT,
  readStoredAdminToken,
  storeAdminToken,
} from "../api/admin";

export interface AdminTokenState {
  token: string;
  submitToken: (next: string) => void;
  clearToken: () => void;
}

export function useAdminToken(): AdminTokenState {
  const [token, setToken] = useState(readStoredAdminToken);

  useEffect(() => {
    const sync = () => setToken(readStoredAdminToken());
    window.addEventListener(ADMIN_TOKEN_CHANGED_EVENT, sync);
    return () => window.removeEventListener(ADMIN_TOKEN_CHANGED_EVENT, sync);
  }, []);

  const submitToken = useCallback((next: string) => {
    storeAdminToken(next);
    setToken(next);
  }, []);

  const clearToken = useCallback(() => {
    storeAdminToken("");
    setToken("");
  }, []);

  return { token, submitToken, clearToken };
}

export function describeAdminError(err: unknown): string {
  if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
    return "Token odrzucony przez serwer (401/403). Sprawdź ADMIN_TOKEN.";
  }
  if (err instanceof ApiError) {
    return `Błąd serwera (${err.status}): ${err.message}`;
  }
  return "Nie udało się połączyć z API.";
}
