from dotenv import load_dotenv
load_dotenv()  # Charge les variables de .env AVANT tout import

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from database import init_db
from routers import auth, users

app = FastAPI(
    title="Meet2Play API",
    description="Backend de l'application Meet2Play",
    version="1.0.0",
)

# ─── CORS ─────────────────────────────────────────────────────────────────────
# Autorise les appels depuis l'app Flutter web (localhost:*)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],       # En production, restreindre à votre domaine
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── Initialisation de la base de données au démarrage ───────────────────────
@app.on_event("startup")
async def startup():
    init_db()
    print("✅  Base de données initialisée")
    print("📖  Documentation API : http://localhost:8000/docs")

# ─── Routers ──────────────────────────────────────────────────────────────────
app.include_router(auth.router, prefix="/auth", tags=["Authentification"])
app.include_router(users.router, prefix="/users", tags=["Utilisateurs"])

# ─── Route de santé ───────────────────────────────────────────────────────────
@app.get("/", tags=["Santé"])
def health():
    return {"status": "ok", "app": "Meet2Play API"}
