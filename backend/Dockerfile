FROM python:3.12-slim

WORKDIR /app

# Instalacja zależności systemowych + Node.js w pewniejszy sposób
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    && curl -sL https://deb.nodesource.com/setup_18.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Sprawdzenie czy node działa (opcjonalne, do logów budowania)
RUN node -v

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN mkdir -p /tmp/audio

EXPOSE 8000

# Ważne: upewnij się, że ścieżka do aplikacji jest poprawna (app.main vs main)
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]