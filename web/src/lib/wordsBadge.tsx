import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api } from "../api/reelang";
import { useSession } from "./session";

interface BadgeValue {
  dueCount: number;
  refresh: () => void;
}

const BadgeContext = createContext<BadgeValue>({ dueCount: 0, refresh: () => {} });

export function WordsBadgeProvider({ children }: { children: ReactNode }) {
  const { user } = useSession();
  const [dueCount, setDueCount] = useState(0);

  const refresh = useCallback(() => {
    if (!user) {
      setDueCount(0);
      return;
    }
    api
      .listWords(true)
      .then((words) => setDueCount(words.length))
      .catch(() => setDueCount(0));
  }, [user]);

  useEffect(refresh, [refresh]);

  const value = useMemo(() => ({ dueCount, refresh }), [dueCount, refresh]);

  return <BadgeContext.Provider value={value}>{children}</BadgeContext.Provider>;
}

export const useWordsBadge = () => useContext(BadgeContext);
