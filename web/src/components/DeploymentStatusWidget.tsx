import { useCallback, useEffect, useState } from "react";
import {
  ADMIN_TOKEN_CHANGED_EVENT,
  fetchDeployments,
  readStoredAdminToken,
  type DeploymentPlatform,
  type DeploymentStatus,
} from "../api/admin";

const REFRESH_INTERVAL_MS = 60_000;

const PLATFORM_LABELS: Record<DeploymentPlatform, string> = {
  railway: "Railway",
  vercel: "Vercel",
};

const PLATFORM_ORDER: DeploymentPlatform[] = ["railway", "vercel"];

function relativeTime(isoTimestamp: string | null): string {
  if (!isoTimestamp) return "brak danych";

  const deployedAt = new Date(isoTimestamp).getTime();
  if (Number.isNaN(deployedAt)) return "brak danych";

  const seconds = Math.round((Date.now() - deployedAt) / 1000);
  if (seconds < 0) return "przed chwilą";
  if (seconds < 60) return "przed chwilą";

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min temu`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} godz. temu`;

  const days = Math.floor(hours / 24);
  return days === 1 ? "1 dzień temu" : `${days} dni temu`;
}

function DeploymentCard({ deployment }: { deployment: DeploymentStatus }) {
  const { platform, status, raw_status, deployed_at, commit_sha, error } = deployment;
  const detail = error ?? `${raw_status ?? "—"} · ${relativeTime(deployed_at)}`;

  return (
    <div className="deploy-card" title={error ?? raw_status ?? undefined}>
      <span className={`deploy-card__dot deploy-card__dot--${status}`} />
      <span className="deploy-card__platform">{PLATFORM_LABELS[platform]}</span>
      <span className="deploy-card__detail">{detail}</span>
      {commit_sha ? (
        <code className="deploy-card__commit">{commit_sha.slice(0, 7)}</code>
      ) : null}
    </div>
  );
}

export function DeploymentStatusWidget() {
  const [deployments, setDeployments] = useState<DeploymentStatus[] | null>(null);
  const [token, setToken] = useState(readStoredAdminToken);

  useEffect(() => {
    const syncToken = () => setToken(readStoredAdminToken());
    window.addEventListener(ADMIN_TOKEN_CHANGED_EVENT, syncToken);
    return () => window.removeEventListener(ADMIN_TOKEN_CHANGED_EVENT, syncToken);
  }, []);

  const load = useCallback(async (activeToken: string, isCancelled: () => boolean) => {
    try {
      const response = await fetchDeployments(activeToken);
      if (!isCancelled()) setDeployments(response.deployments);
    } catch {
      if (!isCancelled()) setDeployments(null);
    }
  }, []);

  useEffect(() => {
    if (!token) {
      setDeployments(null);
      return;
    }

    let cancelled = false;
    const isCancelled = () => cancelled;

    void load(token, isCancelled);
    const timer = window.setInterval(() => void load(token, isCancelled), REFRESH_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [token, load]);

  if (!deployments) return null;

  const ordered = PLATFORM_ORDER.map((platform) =>
    deployments.find((deployment) => deployment.platform === platform),
  ).filter((deployment): deployment is DeploymentStatus => deployment !== undefined);

  if (ordered.length === 0) return null;

  return (
    <div className="deploy-bar">
      {ordered.map((deployment) => (
        <DeploymentCard key={deployment.platform} deployment={deployment} />
      ))}
    </div>
  );
}
