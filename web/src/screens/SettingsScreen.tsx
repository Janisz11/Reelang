import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/reelang";
import { useSession } from "../lib/session";
import { useToast } from "../lib/toast";
import { EditIcon, LogoutIcon, PersonIcon } from "../components/Icons";
import { Modal, TopBar } from "../components/common";

function SettingsItem({
  icon,
  label,
  tint,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  tint?: string;
  onClick: () => void;
}) {
  return (
    <button
      className="card"
      onClick={onClick}
      style={{ display: "flex", alignItems: "center", gap: 12, padding: 16, width: "100%", textAlign: "left", color: tint }}
    >
      {icon}
      <span style={{ fontSize: 15, fontWeight: 500 }}>{label}</span>
    </button>
  );
}

export function SettingsScreen() {
  const navigate = useNavigate();
  const toast = useToast();
  const { user, signOut } = useSession();

  const [username, setUsername] = useState("");
  const [bio, setBio] = useState("");
  const [showEdit, setShowEdit] = useState(false);
  const [showLogout, setShowLogout] = useState(false);

  useEffect(() => {
    api
      .getMyProfile()
      .then((profile) => {
        setUsername(profile.username);
        setBio(profile.bio ?? "");
      })
      .catch(() => undefined);
  }, []);

  async function saveProfile() {
    try {
      await api.updateMyProfile({ username, bio });
      setShowEdit(false);
      toast("Profile updated");
    } catch (err) {
      toast((err as Error).message);
    }
  }

  async function confirmLogout() {
    await signOut();
    navigate("/auth", { replace: true });
  }

  return (
    <div className="screen">
      <TopBar title="Settings" />

      <div className="screen screen--scroll" style={{ padding: 16, gap: 8 }}>
        <p className="field__label" style={{ padding: "4px 4px 8px" }}>
          ACCOUNT
        </p>

        <div className="card" style={{ display: "flex", alignItems: "center", gap: 12, padding: 16 }}>
          <PersonIcon size={20} color="var(--text-secondary)" />
          <div>
            <div style={{ fontWeight: 600, fontSize: 15 }}>{username || user?.displayName || "User"}</div>
            <div className="muted" style={{ fontSize: 12 }}>
              {user?.email}
            </div>
          </div>
        </div>

        <div style={{ height: 8 }} />

        <SettingsItem
          icon={<EditIcon size={20} color="var(--text-secondary)" />}
          label="Edit Profile"
          onClick={() => setShowEdit(true)}
        />
        <div style={{ height: 8 }} />
        <SettingsItem
          icon={<LogoutIcon size={20} color="var(--red)" />}
          label="Sign out"
          tint="var(--red)"
          onClick={() => setShowLogout(true)}
        />
      </div>

      {showEdit && (
        <Modal title="Edit Profile" confirmLabel="Save" onConfirm={() => void saveProfile()} onDismiss={() => setShowEdit(false)}>
          <div className="field">
            <label className="field__label" htmlFor="username">
              Username
            </label>
            <input id="username" className="input" value={username} onChange={(event) => setUsername(event.target.value)} />
          </div>
          <div className="field">
            <label className="field__label" htmlFor="bio">
              Bio
            </label>
            <textarea id="bio" className="input" rows={3} value={bio} onChange={(event) => setBio(event.target.value)} />
          </div>
        </Modal>
      )}

      {showLogout && (
        <Modal
          title="Sign out"
          confirmLabel="Sign out"
          onConfirm={() => void confirmLogout()}
          onDismiss={() => setShowLogout(false)}
        >
          <p className="muted" style={{ margin: 0 }}>
            Are you sure you want to sign out?
          </p>
        </Modal>
      )}
    </div>
  );
}
