def test_get_nonexistent_profile_creates_it(client):
    response = client.get("/api/v1/profiles/me", headers={"Authorization": "Bearer test-user-123"})
    assert response.status_code == 200
    data = response.json()
    assert data["user_id"] == "test-user-123"


def test_get_profile_returns_same_data(client):
    headers = {"Authorization": "Bearer test-user-456"}
    client.get("/api/v1/profiles/me", headers=headers)
    response = client.get("/api/v1/profiles/me", headers=headers)
    assert response.status_code == 200
    assert response.json()["user_id"] == "test-user-456"


def test_update_profile_username(client):
    headers = {"Authorization": "Bearer test-user-789"}
    client.get("/api/v1/profiles/me", headers=headers)
    response = client.put(
        "/api/v1/profiles/me",
        headers=headers,
        json={"username": "newusername", "bio": "test bio"},
    )
    assert response.status_code == 200
    assert response.json()["username"] == "newusername"


def test_search_profiles(client):
    client.get("/api/v1/profiles/me", headers={"Authorization": "Bearer search-test-user"})
    response = client.get("/api/v1/profiles/search?q=search")
    assert response.status_code == 200
    assert isinstance(response.json(), list)
