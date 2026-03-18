from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase

DATABASE_URL = "sqlite:///./meet2play.db"

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False},  # nécessaire pour SQLite
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db():
    """Dépendance FastAPI : fournit une session DB et la ferme après usage."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db():
    """Crée toutes les tables si elles n'existent pas encore."""
    import models  # noqa: F401 — nécessaire pour que SQLAlchemy détecte les modèles
    Base.metadata.create_all(bind=engine)
