import { Suspense, lazy } from "react";
import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { usePageViews } from "./lib/analytics";
import { useSession } from "./lib/session";
import { WordsBadgeProvider } from "./lib/wordsBadge";
import { AdminLayout } from "./components/AdminLayout";
import { BottomNav } from "./components/BottomNav";
import { LoadingBox, Spinner } from "./components/common";
import { AuthScreen } from "./screens/AuthScreen";
import { OnboardingScreen } from "./screens/OnboardingScreen";
import { FeedRoute, SearchFeedRoute, SingleReelRoute, UserReelsRoute } from "./screens/ReelsScreen";
import { SearchScreen } from "./screens/SearchScreen";
import { WordsScreen } from "./screens/WordsScreen";
import { WordDetailScreen } from "./screens/WordDetailScreen";
import { PracticeScreen } from "./screens/PracticeScreen";
import { ProfileScreen } from "./screens/ProfileScreen";
import { StatsScreen } from "./screens/StatsScreen";
import { SettingsScreen } from "./screens/SettingsScreen";
import { CreateReelScreen } from "./screens/CreateReelScreen";

const SchemaViewer = lazy(() =>
  import("./screens/SchemaViewer").then((m) => ({ default: m.SchemaViewer })),
);

const EventStatsView = lazy(() =>
  import("./screens/EventStatsView").then((m) => ({ default: m.EventStatsView })),
);

const LogsView = lazy(() =>
  import("./screens/LogsView").then((m) => ({ default: m.LogsView })),
);

const navRoutes = ["/feed", "/search", "/words", "/profile"];

function Shell({ dark = false }: { dark?: boolean }) {
  const { pathname } = useLocation();
  const showNav = navRoutes.includes(pathname);

  return (
    <div className={`shell${dark ? " shell--dark" : ""}`}>
      <Outlet />
      {showNav && <BottomNav />}
    </div>
  );
}

function RequireAuth() {
  const { user, loading } = useSession();
  const location = useLocation();

  if (loading) {
    return (
      <div className="shell">
        <div className="center-box">
          <Spinner />
        </div>
      </div>
    );
  }

  if (!user) return <Navigate to="/auth" replace state={{ from: location.pathname }} />;

  return (
    <WordsBadgeProvider>
      <Shell />
    </WordsBadgeProvider>
  );
}

function PublicOnly() {
  const { user, loading } = useSession();

  if (loading) {
    return (
      <div className="shell">
        <div className="center-box">
          <Spinner />
        </div>
      </div>
    );
  }

  if (user) return <Navigate to="/feed" replace />;

  return (
    <div className="shell">
      <Outlet />
    </div>
  );
}

export function App() {
  usePageViews();

  return (
    <Routes>
      <Route element={<PublicOnly />}>
        <Route path="/auth" element={<AuthScreen />} />
      </Route>

      <Route element={<RequireAuth />}>
        <Route path="/onboarding" element={<OnboardingScreen />} />
        <Route path="/feed" element={<FeedRoute />} />
        <Route path="/feed/from-search/:reelIds" element={<SearchFeedRoute />} />
        <Route path="/reel/:reelId" element={<SingleReelRoute />} />
        <Route path="/user-reels/:userId/:startReelId" element={<UserReelsRoute />} />
        <Route path="/search" element={<SearchScreen />} />
        <Route path="/words" element={<WordsScreen />} />
        <Route path="/words/:wordId" element={<WordDetailScreen />} />
        <Route path="/practice" element={<PracticeScreen />} />
        <Route path="/profile" element={<ProfileScreen />} />
        <Route path="/profile/:userId" element={<ProfileScreen />} />
        <Route path="/stats" element={<StatsScreen />} />
        <Route path="/settings" element={<SettingsScreen />} />
        <Route path="/create" element={<CreateReelScreen />} />
      </Route>

      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<Navigate to="/admin/schema" replace />} />
        <Route
          path="schema"
          element={
            <Suspense fallback={<LoadingBox />}>
              <SchemaViewer />
            </Suspense>
          }
        />
        <Route
          path="events"
          element={
            <Suspense fallback={<LoadingBox />}>
              <EventStatsView />
            </Suspense>
          }
        />
        <Route
          path="logs"
          element={
            <Suspense fallback={<LoadingBox />}>
              <LogsView />
            </Suspense>
          }
        />
      </Route>

      <Route path="*" element={<Navigate to="/feed" replace />} />
    </Routes>
  );
}
