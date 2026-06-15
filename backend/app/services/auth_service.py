import json
import os
import firebase_admin
from firebase_admin import credentials, auth
from fastapi import HTTPException


def initialize_firebase() -> None:
    if firebase_admin._apps:
        return
    cred_json = os.getenv("FIREBASE_CREDENTIALS_JSON")
    if cred_json:
        cred = credentials.Certificate(json.loads(cred_json))
    else:
        cred_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "./firebase-credentials.json")
        cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred)


def verify_token(token: str) -> str:
    """Verify a Firebase JWT and return the user's uid."""
    try:
        decoded = auth.verify_id_token(token)
        return decoded["uid"]
    except auth.ExpiredIdTokenError:
        raise HTTPException(status_code=401, detail="Token expired")
    except auth.InvalidIdTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")
