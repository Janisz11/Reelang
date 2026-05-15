FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
# Copy package contents into /app/reelang_ai/ so "python -m reelang_ai.main" resolves correctly
COPY . ./reelang_ai/
CMD ["python", "-m", "reelang_ai.main"]
