from fastapi import APIRouter

router = APIRouter(prefix="/parental", tags=["Parental"])

@router.get("")
def list_parental():
    return {"items": [], "status": "scaffold"}
