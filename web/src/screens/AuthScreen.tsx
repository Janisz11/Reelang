import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useSession } from "../lib/session";
import { demoAvailable, firebaseConfigured } from "../firebase";
import { EyeIcon } from "../components/Icons";
import { Spinner } from "../components/common";

const tabs = ["Sign In", "Register"];

function friendlyError(error: unknown): string {
  const code = (error as { code?: string }).code ?? "";
  switch (code) {
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
      return "Wrong email or password.";
    case "auth/email-already-in-use":
      return "That email is already registered.";
    case "auth/weak-password":
      return "Password must be at least 6 characters.";
    case "auth/invalid-email":
      return "That email address looks invalid.";
    case "auth/popup-closed-by-user":
      return "Google sign-in was cancelled.";
    case "auth/network-request-failed":
      return "Network error — check your connection.";
    default:
      return (error as Error).message ?? "Something went wrong.";
  }
}

export function AuthScreen() {
  const navigate = useNavigate();
  const { signIn, register, signInWithGoogle, signInAsDemo } = useSession();

  const [tab, setTab] = useState(0);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(action: () => Promise<void>, destination: string) {
    setLoading(true);
    setError(null);
    try {
      await action();
      navigate(destination, { replace: true });
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (tab === 0) void run(() => signIn(email, password), "/feed");
    else void run(() => register(email, password), "/onboarding");
  }

  return (
    <div
      className="screen screen--scroll"
      style={{ background: "linear-gradient(to bottom, #1A0A0A, #3D1515)", padding: "0 24px" }}
    >
      <div style={{ textAlign: "center", paddingTop: 64 }}>
        <div style={{ fontSize: 56, lineHeight: 1 }}>🌎</div>
        <h1 style={{ margin: "12px 0 2px", fontSize: 32, fontWeight: 800, color: "#fff" }}>ReeLang</h1>
        <p style={{ margin: 0, fontSize: 14, color: "rgba(255,255,255,0.6)" }}>
          Learn languages by watching reels
        </p>
      </div>

      {!firebaseConfigured && (
        <div
          style={{
            margin: "24px 0 0",
            padding: 14,
            borderRadius: 12,
            background: "rgba(255,255,255,0.08)",
            color: "#ffd7d7",
            fontSize: 13,
            lineHeight: 1.5,
          }}
        >
          Firebase is not configured. Copy <code>.env.example</code> to <code>.env.local</code> and fill in
          <code> VITE_FIREBASE_API_KEY</code> and <code>VITE_FIREBASE_APP_ID</code>.
        </div>
      )}

      <div style={{ marginTop: 32, background: "var(--surface)", borderRadius: 20, overflow: "hidden" }}>
        <div className="tabs">
          {tabs.map((title, index) => (
            <button
              key={title}
              className={`tabs__tab${index === tab ? " tabs__tab--active" : ""}`}
              onClick={() => {
                setTab(index);
                setError(null);
              }}
            >
              {title}
            </button>
          ))}
        </div>

        <form onSubmit={onSubmit} style={{ padding: "20px 20px 24px", display: "grid", gap: 14 }}>
          <div className="field">
            <label className="field__label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              className="input"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>

          <div className="field">
            <label className="field__label" htmlFor="password">
              Password
            </label>
            <div className="input-wrap">
              <input
                id="password"
                className="input"
                type={showPassword ? "text" : "password"}
                autoComplete={tab === 0 ? "current-password" : "new-password"}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                minLength={6}
              />
              <button
                type="button"
                className="input-wrap__action"
                onClick={() => setShowPassword((v) => !v)}
                aria-label="Toggle password visibility"
              >
                <EyeIcon size={20} filled={showPassword} color="var(--text-secondary)" />
              </button>
            </div>
          </div>

          {error && <p className="error-text" style={{ margin: 0 }}>{error}</p>}

          <button className="btn btn--primary btn--full" type="submit" disabled={loading || !firebaseConfigured}>
            {loading ? <Spinner light small /> : tab === 0 ? "Sign In" : "Create Account"}
          </button>

          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <hr className="divider" style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "var(--text-secondary)" }}>or</span>
            <hr className="divider" style={{ flex: 1 }} />
          </div>

          <button
            type="button"
            className="btn btn--full"
            style={{ background: "var(--cream)", border: "1px solid var(--border)" }}
            onClick={() => void run(signInWithGoogle, "/feed")}
            disabled={loading || !firebaseConfigured}
          >
            <span style={{ color: "#4285F4", fontWeight: 800, fontSize: 17 }}>G</span>
            Continue with Google
          </button>

          {demoAvailable && (
            <button
              type="button"
              className="btn btn--outline btn--full"
              onClick={() => void run(signInAsDemo, "/feed")}
              disabled={loading}
            >
              Try the demo — no signup
            </button>
          )}
        </form>
      </div>

      <div style={{ textAlign: "center", padding: "16px 0 32px" }}>
        <button
          onClick={() => setTab(tab === 0 ? 1 : 0)}
          style={{ color: "rgba(255,255,255,0.75)", fontSize: 13 }}
        >
          {tab === 0 ? "Don't have an account?  Register" : "Already have an account?  Sign In"}
        </button>
        <div style={{ marginTop: 12 }}>
          {/* Plain anchor: /about is a static page served outside the SPA. */}
          <a href="/about" style={{ color: "rgba(255,255,255,0.5)", fontSize: 12 }}>
            What is ReeLang?
          </a>
        </div>
      </div>
    </div>
  );
}
