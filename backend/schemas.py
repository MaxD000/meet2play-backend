from pydantic import BaseModel, EmailStr, field_validator
from typing import Optional
import json


# ─── Auth ─────────────────────────────────────────────────────────────────────

class RegisterRequest(BaseModel):
    email: EmailStr
    password: str
    name: str


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


class MessageResponse(BaseModel):
    message: str


# ─── Profil ───────────────────────────────────────────────────────────────────

class UserProfile(BaseModel):
    model_config = {"from_attributes": True}

    id: int
    email: str
    name: str
    is_verified: bool
    sports: list[str] = []
    sport_level: str = ""
    job: str = ""
    company: str = ""
    company_city: str = ""
    age: str = ""
    studies: str = ""
    school: str = ""
    school_city: str = ""

    @field_validator("sports", mode="before")
    @classmethod
    def parse_sports(cls, v):
        """Désérialise le JSON stocké en base vers une liste Python."""
        if isinstance(v, str):
            try:
                return json.loads(v)
            except (ValueError, TypeError):
                return []
        return v or []


class UpdateProfileRequest(BaseModel):
    name: Optional[str] = None
    sports: Optional[list[str]] = None
    sport_level: Optional[str] = None
    job: Optional[str] = None
    company: Optional[str] = None
    company_city: Optional[str] = None
    age: Optional[str] = None
    studies: Optional[str] = None
    school: Optional[str] = None
    school_city: Optional[str] = None
