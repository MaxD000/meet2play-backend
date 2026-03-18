from sqlalchemy import Column, Integer, String, Boolean, DateTime, Text
from sqlalchemy.sql import func
from database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=False)
    name = Column(String, default="")

    # Vérification email
    is_verified = Column(Boolean, default=False)
    verification_token = Column(String, nullable=True)
    verification_expires = Column(DateTime, nullable=True)

    # Profil sportif & pro
    sports = Column(Text, default="[]")       # JSON array sérialisé
    sport_level = Column(String, default="")
    job = Column(String, default="")
    company = Column(String, default="")
    company_city = Column(String, default="")
    age = Column(String, default="")
    studies = Column(String, default="")
    school = Column(String, default="")
    school_city = Column(String, default="")

    # Métadonnées
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())
