from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from app.settings import settings
from app.database import init_db
from app.routers import announcements, auth, config, devices, jellyfin, notifications, parental, profiles, provider_sync, subscriptions, users


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


app = FastAPI(title="QuadTV Admin", version="0.1.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[origin.strip() for origin in settings.cors_origins.split(",")],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

api_prefix = "/api/v1"
app.include_router(auth.router, prefix=api_prefix)
app.include_router(config.router, prefix=api_prefix)
app.include_router(devices.router, prefix=api_prefix)
app.include_router(profiles.router, prefix=api_prefix)
app.include_router(users.router, prefix=api_prefix)
app.include_router(subscriptions.router, prefix=api_prefix)
app.include_router(announcements.router, prefix=api_prefix)
app.include_router(notifications.router, prefix=api_prefix)
app.include_router(parental.router, prefix=api_prefix)
app.include_router(jellyfin.router, prefix=api_prefix)
app.include_router(provider_sync.router, prefix=api_prefix)

@app.get("/health")
def health():
    return {"status": "ok", "service": "QuadTV Admin"}

app.mount("/", StaticFiles(directory="web", html=True), name="web")
