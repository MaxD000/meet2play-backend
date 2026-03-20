import uuid
from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

import models
import schemas
from database import get_db
from email_utils import send_verification_email
from security import create_access_token, hash_password, verify_password

router = APIRouter()


# ─── Inscription ──────────────────────────────────────────────────────────────

@router.post("/register", response_model=schemas.MessageResponse, status_code=201)
def register(req: schemas.RegisterRequest, db: Session = Depends(get_db)):
    # Vérifier si l'email existe déjà
    if db.query(models.User).filter(models.User.email == req.email).first():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cette adresse email est déjà utilisée.",
        )

    # Valider le mot de passe (min 6 caractères)
    if len(req.password) < 6:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Le mot de passe doit contenir au moins 6 caractères.",
        )

    user = models.User(
        email=req.email,
        hashed_password=hash_password(req.password),
        name=req.name.strip(),
        is_verified=True,
    )
    db.add(user)
    db.commit()

    return {"message": f"Compte créé ! Vous pouvez maintenant vous connecter."}


# ─── Vérification email ───────────────────────────────────────────────────────

@router.get("/verify-email", response_model=schemas.MessageResponse)
def verify_email(token: str = Query(...), db: Session = Depends(get_db)):
    user = db.query(models.User).filter(
        models.User.verification_token == token
    ).first()

    if not user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Lien de vérification invalide.",
        )

    if user.verification_expires and user.verification_expires < datetime.utcnow():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Ce lien a expiré. Veuillez vous réinscrire.",
        )

    user.is_verified = True
    user.verification_token = None
    user.verification_expires = None
    db.commit()

    return {"message": "Email vérifié avec succès ! Vous pouvez maintenant vous connecter."}


# ─── Renvoi de l'email de vérification ───────────────────────────────────────

@router.post("/resend-verification", response_model=schemas.MessageResponse)
def resend_verification(email: str, db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.email == email).first()

    if not user:
        # On ne révèle pas si l'email existe ou non (sécurité)
        return {"message": "Si ce compte existe, un email a été envoyé."}

    if user.is_verified:
        return {"message": "Ce compte est déjà vérifié."}

    # Générer un nouveau token
    user.verification_token = str(uuid.uuid4())
    user.verification_expires = datetime.utcnow() + timedelta(hours=24)
    db.commit()

    send_verification_email(user.email, user.verification_token, user.name)
    return {"message": "Email de vérification renvoyé."}


# ─── Connexion ────────────────────────────────────────────────────────────────

@router.post("/login", response_model=schemas.TokenResponse)
def login(req: schemas.LoginRequest, db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.email == req.email).first()

    if not user or not verify_password(req.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Email ou mot de passe incorrect.",
        )

    access_token = create_access_token(user.email)
    return {"access_token": access_token, "token_type": "bearer"}
