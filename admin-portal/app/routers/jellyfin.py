from fastapi import APIRouter

router = APIRouter(prefix="/jellyfin", tags=["Jellyfin"])

@router.get("")
def list_jellyfin():
    return {"items": [], "status": "scaffold"}
