import { useEffect } from "react";
import { BASE_URL } from "../api/client";
import { auth } from "../firebase";
import { startEventTracking, stopEventTracking } from "./eventTracking";
import { useSession } from "./session";

/** Runs the in-memory event queue for as long as somebody is signed in. */
export function useEventTracking(): void {
  const { userId } = useSession();

  useEffect(() => {
    if (!userId) {
      stopEventTracking();
      return;
    }

    return startEventTracking({
      baseUrl: BASE_URL,
      getUserId: () => userId,
      getAuthToken: async () => {
        try {
          return (await auth.currentUser?.getIdToken()) ?? null;
        } catch {
          return null;
        }
      },
    });
  }, [userId]);
}
