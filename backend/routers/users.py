import json

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

import models
import schemas
from database import get_db
from security import get_current_user

router = APIRouter()


def _user_to_profile(user: models.User) -> schemas.UserProfile:
    """Convertit un objet ORM User en schéma UserProfile."""
    return schemas.UserProfile(
        id=user.id,
        email=user.email,
        name=user.name,
        is_verified=user.is_verified,
        sports=json.loads(user.sports or "[]"),
        sport_level=user.sport_level or "",
        job=user.job or "",
        company=user.company or "",
        company_city=user.company_city or "",
        age=user.age or "",
        studies=user.studies or "",
        school=user.school or "",
        school_city=user.school_city or "",
    )


# ─── Récupérer son profil ─────────────────────────────────────────────────────

@router.get("/me", response_model=schemas.UserProfile)
def get_me(current_user: models.User = Depends(get_current_user)):
    return _user_to_profile(current_user)


# ─── Mettre à jour son profil ─────────────────────────────────────────────────

@router.put("/me", response_model=schemas.UserProfile)
def update_me(
    req: schemas.UpdateProfileRequest,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(get_current_user),
):
    data = req.model_dump(exclude_none=True)

    for field, value in data.items():
        if field == "sports":
            # Sérialiser la liste en JSON pour le stockage
            setattr(current_user, field, json.dumps(value))
        else:
            setattr(current_user, field, value)

    db.commit()
    db.refresh(current_user)
    return _user_to_profile(current_user)
