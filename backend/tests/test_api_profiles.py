def test_get_nonexistent_profile_creates_it(client):
    response = client.get("/api/v1/profiles/me?user_id=test-user-123")
    assert response.status_code == 200
    data = response.json()
    assert data["user_id"] == "test-user-123"


def test_get_profile_returns_same_data(client):
    client.get("/api/v1/profiles/me?user_id=test-user-456")
    response = client.get("/api/v1/profiles/me?user_id=test-user-456")
    assert response.status_code == 200
    assert response.json()["user_id"] == "test-user-456"


def test_update_profile_username(client):
    client.get("/api/v1/profiles/me?user_id=test-user-789")
    response = client.put(
        "/api/v1/profiles/me?user_id=test-user-789",
        json={"username": "newusername", "bio": "test bio"},
    )
    assert response.status_code == 200
    assert response.json()["username"] == "newusername"


def test_search_profiles(client):
    client.get("/api/v1/profiles/me?user_id=search-test-user")
    response = client.get("/api/v1/profiles/search?q=search")
    assert response.status_code == 200
    assert isinstance(response.json(), list)
