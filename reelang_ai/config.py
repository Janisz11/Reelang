import os
from dotenv import load_dotenv

load_dotenv()

YOUTUBE_API_KEY = os.getenv("YOUTUBE_API_KEY")
BACKEND_URL = os.getenv("BACKEND_URL", "http://api:8000")
INTERNAL_API_TOKEN = os.getenv("INTERNAL_API_TOKEN")
DATABASE_URL = os.getenv("DATABASE_URL")
SCHEDULER_INTERVAL_MINUTES = int(os.getenv("SCHEDULER_INTERVAL_MINUTES", "30"))
RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
EVENTS_HEALTH_PORT = int(os.getenv("EVENTS_HEALTH_PORT", "8002"))
EVENTS_PREFETCH_COUNT = int(os.getenv("EVENTS_PREFETCH_COUNT", "20"))
QUEUE_MIN_SIZE = int(os.getenv("QUEUE_MIN_SIZE", "5"))
QUEUE_MAX_SIZE = int(os.getenv("QUEUE_MAX_SIZE", "20"))
REELS_PER_RUN = int(os.getenv("REELS_PER_RUN", "5"))
RETENTION_DAYS = int(os.getenv("RETENTION_DAYS", "30"))
