from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    env: str = "dev"
    secret_key: str = "change-me"
    admin_username: str = "admin"
    admin_password: str = "change-me"
    database_url: str = "sqlite:///./quadtv_admin.db"
    cors_origins: str = "*"
    firebase_credentials_path: str | None = None

    model_config = SettingsConfigDict(env_prefix="QUADTV_", env_file=".env")

settings = Settings()
