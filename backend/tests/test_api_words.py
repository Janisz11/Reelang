USER_HEADER = {"x-user-id": "test-user"}


def test_get_words_empty(client):
    response = client.get("/api/v1/words", headers=USER_HEADER)
    assert response.status_code == 200
    assert response.json() == []


def test_create_word(client):
    response = client.post("/api/v1/words", headers=USER_HEADER, json={
        "term": "hola",
        "language": "es",
        "reel_id": None,
        "segment_id": None,
    })
    assert response.status_code == 201
    data = response.json()
    assert data["term"] == "hola"
    assert data["language"] == "es"


def test_create_duplicate_word_returns_existing(client):
    client.post("/api/v1/words", headers=USER_HEADER, json={
        "term": "gato",
        "language": "es",
        "reel_id": None,
        "segment_id": None,
    })
    response = client.post("/api/v1/words", headers=USER_HEADER, json={
        "term": "gato",
        "language": "es",
        "reel_id": None,
        "segment_id": None,
    })
    assert response.status_code == 201
    assert response.json()["term"] == "gato"


def test_get_words_after_create(client):
    client.post("/api/v1/words", headers=USER_HEADER, json={
        "term": "casa",
        "language": "es",
        "reel_id": None,
        "segment_id": None,
    })
    response = client.get("/api/v1/words", headers=USER_HEADER)
    assert response.status_code == 200
    words = response.json()
    assert len(words) == 1
    assert words[0]["term"] == "casa"


def test_get_word_by_id(client):
    create_response = client.post("/api/v1/words", headers=USER_HEADER, json={
        "term": "perro",
        "language": "es",
        "reel_id": None,
        "segment_id": None,
    })
    word_id = create_response.json()["id"]
    response = client.get(f"/api/v1/words/{word_id}", headers=USER_HEADER)
    assert response.status_code == 200
    assert response.json()["term"] == "perro"


def test_get_nonexistent_word_returns_404(client):
    response = client.get("/api/v1/words/nonexistent-id", headers=USER_HEADER)
    assert response.status_code == 404


def test_delete_word(client):
    create_response = client.post("/api/v1/words", headers=USER_HEADER, json={
        "term": "mesa",
        "language": "es",
        "reel_id": None,
        "segment_id": None,
    })
    word_id = create_response.json()["id"]
    delete_response = client.delete(f"/api/v1/words/{word_id}", headers=USER_HEADER)
    assert delete_response.status_code == 200
    get_response = client.get(f"/api/v1/words/{word_id}", headers=USER_HEADER)
    assert get_response.status_code == 404
