import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut as fbSignOut,
  type User,
} from "firebase/auth";
import { auth, demoCredentials, googleProvider } from "../firebase";
import { initialsFrom } from "./format";

interface SessionValue {
  user: User | null;
  loading: boolean;
  userId: string;
  displayName: string;
  initials: string;
  signIn: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  signInWithGoogle: () => Promise<void>;
  signInAsDemo: () => Promise<void>;
  signOut: () => Promise<void>;
}

const SessionContext = createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    return onAuthStateChanged(auth, (next) => {
      setUser(next);
      setLoading(false);
    });
  }, []);

  const value = useMemo<SessionValue>(() => {
    const displayName = user?.displayName ?? user?.email?.split("@")[0] ?? "User";
    return {
      user,
      loading,
      userId: user?.uid ?? "",
      displayName,
      initials: initialsFrom(displayName),
      signIn: async (email, password) => {
        await signInWithEmailAndPassword(auth, email, password);
      },
      register: async (email, password) => {
        await createUserWithEmailAndPassword(auth, email, password);
      },
      signInWithGoogle: async () => {
        await signInWithPopup(auth, googleProvider);
      },
      signInAsDemo: async () => {
        await signInWithEmailAndPassword(auth, demoCredentials.email, demoCredentials.password);
      },
      signOut: async () => {
        await fbSignOut(auth);
      },
    };
  }, [user, loading]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error("useSession must be used inside SessionProvider");
  return ctx;
}
