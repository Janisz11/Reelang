import { initializeApp } from "firebase/app";
import { getAuth, GoogleAuthProvider, setPersistence, browserLocalPersistence } from "firebase/auth";

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

/** Auth only needs apiKey + authDomain; appId matters for Analytics, which this app does not use. */
export const firebaseConfigured = Boolean(config.apiKey && config.authDomain);

export const app = initializeApp(config);
export const auth = getAuth(app);
export const googleProvider = new GoogleAuthProvider();

void setPersistence(auth, browserLocalPersistence);

export const demoCredentials = {
  email: import.meta.env.VITE_DEMO_EMAIL ?? "",
  password: import.meta.env.VITE_DEMO_PASSWORD ?? "",
};

export const demoAvailable = Boolean(demoCredentials.email && demoCredentials.password);
