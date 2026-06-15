def test_get_reels_empty(client):
    response = client.get("/api/v1/reels")
    assert response.status_code == 200
    assert response.json() == []


def test_get_reels_with_language_filter(client):
    response = client.get("/api/v1/reels?language=es")
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_get_nonexistent_reel_returns_404(client):
    response = client.get("/api/v1/reels/nonexistent-id")
    assert response.status_code == 404


def test_get_feed_empty_returns_fallback(client):
    response = client.get("/api/v1/feed", headers={"Authorization": "Bearer test-user"})
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_get_user_reels_empty(client):
    response = client.get("/api/v1/reels/user/nonexistent-user")
    assert response.status_code == 200
    assert response.json() == []


def test_get_captions_empty_for_new_reel(client, db):
    from app.models import Reel
    import uuid
    from datetime import datetime

    reel = Reel(
        id=str(uuid.uuid4()),
        title="Test Reel",
        language="es",
        youtube_id="test123",
        created_at=datetime.utcnow(),
    )
    db.add(reel)
    db.commit()

    response = client.get(f"/api/v1/reels/{reel.id}/captions")
    assert response.status_code == 200
    assert response.json() == []


def test_get_captions_returns_segments(client, db):
    from app.models import Reel, CaptionSegment
    import uuid
    from datetime import datetime

    reel = Reel(
        id=str(uuid.uuid4()),
        title="Test Reel",
        language="es",
        youtube_id="test456",
        created_at=datetime.utcnow(),
    )
    db.add(reel)

    caption = CaptionSegment(
        id=str(uuid.uuid4()),
        reel_id=reel.id,
        start_ms=0,
        end_ms=3000,
        original_text="Hola mundo",
        translated_text="Hello world",
    )
    db.add(caption)
    db.commit()

    response = client.get(f"/api/v1/reels/{reel.id}/captions")
    assert response.status_code == 200
    captions = response.json()
    assert len(captions) == 1
    assert captions[0]["original_text"] == "Hola mundo"
    assert captions[0]["translated_text"] == "Hello world"


def test_upload_reel_returns_owner_user_id(client, tmp_path):
    import io

    fake_video = io.BytesIO(b"fake video content")
    response = client.post(
        "/api/v1/reels/upload",
        data={
            "title": "Test Upload",
            "language": "es",
            "tags": "test",
            "owner_user_id": "test-owner-123",
        },
        files={"file": ("test.mp4", fake_video, "video/mp4")},
    )
    assert response.status_code == 201
    data = response.json()
    assert "id" in data
    assert data["title"] == "Test Upload"


def test_uploaded_reel_has_owner_in_user_reels(client, db, tmp_path):
    import io

    fake_video = io.BytesIO(b"fake video content")
    upload_response = client.post(
        "/api/v1/reels/upload",
        headers={"Authorization": "Bearer owner-456"},
        data={
            "title": "Owner Test",
            "language": "es",
            "tags": "",
        },
        files={"file": ("test.mp4", fake_video, "video/mp4")},
    )
    assert upload_response.status_code == 201

    reels_response = client.get("/api/v1/reels/user/owner-456")
    assert reels_response.status_code == 200
    reels = reels_response.json()
    assert len(reels) == 1
    assert reels[0]["title"] == "Owner Test"
    assert reels[0]["owner_user_id"] == "owner-456"


def test_reel_like_increases_count(client, db):
    from app.models import Reel
    import uuid
    from datetime import datetime

    reel = Reel(
        id=str(uuid.uuid4()),
        title="Like Test",
        language="es",
        youtube_id="liketest",
        likes_count=0,
        created_at=datetime.utcnow(),
    )
    db.add(reel)
    db.commit()

    response = client.post(f"/api/v1/reels/{reel.id}/like", headers={"Authorization": "Bearer test-user"})
    assert response.status_code == 200
    data = response.json()
    assert data["liked"] is True
    assert data["likes_count"] == 1


def test_reel_like_toggle_decreases_count(client, db):
    from app.models import Reel
    import uuid
    from datetime import datetime

    reel = Reel(
        id=str(uuid.uuid4()),
        title="Toggle Test",
        language="es",
        youtube_id="toggletest",
        likes_count=0,
        created_at=datetime.utcnow(),
    )
    db.add(reel)
    db.commit()

    headers = {"Authorization": "Bearer test-user"}
    client.post(f"/api/v1/reels/{reel.id}/like", headers=headers)
    response = client.post(f"/api/v1/reels/{reel.id}/like", headers=headers)

    assert response.status_code == 200
    data = response.json()
    assert data["liked"] is False
    assert data["likes_count"] == 0


def _create_reel(db, youtube_id="test"):
    from app.models import Reel
    import uuid
    from datetime import datetime
    reel = Reel(
        id=str(uuid.uuid4()),
        title="Test",
        language="es",
        youtube_id=youtube_id,
        likes_count=0,
        created_at=datetime.utcnow(),
    )
    db.add(reel)
    db.commit()
    return reel


def _auth(user_id: str) -> dict:
    return {"Authorization": f"Bearer {user_id}"}


def test_is_liked_true_in_list_after_liking(client, db):
    reel = _create_reel(db, "liked-reel")
    client.post(f"/api/v1/reels/{reel.id}/like", headers=_auth("user-a"))
    response = client.get("/api/v1/reels", headers=_auth("user-a"))
    assert response.status_code == 200
    reel_data = next(r for r in response.json() if r["id"] == reel.id)
    assert reel_data["is_liked"] is True


def test_is_liked_false_in_list_after_unliking(client, db):
    reel = _create_reel(db, "unliked-reel")
    client.post(f"/api/v1/reels/{reel.id}/like", headers=_auth("user-a"))
    client.post(f"/api/v1/reels/{reel.id}/like", headers=_auth("user-a"))
    response = client.get("/api/v1/reels", headers=_auth("user-a"))
    assert response.status_code == 200
    reel_data = next(r for r in response.json() if r["id"] == reel.id)
    assert reel_data["is_liked"] is False


def test_is_liked_is_user_specific(client, db):
    reel = _create_reel(db, "user-specific-reel")
    client.post(f"/api/v1/reels/{reel.id}/like", headers=_auth("user-a"))
    response = client.get("/api/v1/reels", headers=_auth("user-b"))
    assert response.status_code == 200
    reel_data = next(r for r in response.json() if r["id"] == reel.id)
    assert reel_data["is_liked"] is False


def test_save_toggle_returns_saved_true(client, db):
    reel = _create_reel(db, "save-reel")
    response = client.post(f"/api/v1/reels/{reel.id}/save", headers=_auth("user-a"))
    assert response.status_code == 200
    assert response.json()["saved"] is True


def test_save_toggle_returns_saved_false_on_second_call(client, db):
    reel = _create_reel(db, "unsave-reel")
    client.post(f"/api/v1/reels/{reel.id}/save", headers=_auth("user-a"))
    response = client.post(f"/api/v1/reels/{reel.id}/save", headers=_auth("user-a"))
    assert response.status_code == 200
    assert response.json()["saved"] is False


def test_is_saved_true_in_list_after_saving(client, db):
    reel = _create_reel(db, "saved-list-reel")
    client.post(f"/api/v1/reels/{reel.id}/save", headers=_auth("user-a"))
    response = client.get("/api/v1/reels", headers=_auth("user-a"))
    assert response.status_code == 200
    reel_data = next(r for r in response.json() if r["id"] == reel.id)
    assert reel_data["is_saved"] is True


def test_is_saved_false_in_list_after_unsaving(client, db):
    reel = _create_reel(db, "unsaved-list-reel")
    client.post(f"/api/v1/reels/{reel.id}/save", headers=_auth("user-a"))
    client.post(f"/api/v1/reels/{reel.id}/save", headers=_auth("user-a"))
    response = client.get("/api/v1/reels", headers=_auth("user-a"))
    assert response.status_code == 200
    reel_data = next(r for r in response.json() if r["id"] == reel.id)
    assert reel_data["is_saved"] is False
