"""
Messagerie instantanée — WebSocket + REST
Routes :
  WS  /chat/ws/{conversation_id}?token=<jwt>
  GET /chat/conversations                      → liste des conversations
  POST /chat/conversations                     → créer/récupérer une conversation
  GET /chat/conversations/{id}/messages        → historique
  PATCH /chat/conversations/{id}/read          → marquer comme lu
"""

import json
from datetime import datetime
from typing import Dict, List, Set

from fastapi import APIRouter, Depends, HTTPException, WebSocket, WebSocketDisconnect, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from database import get_db
from models import Conversation, Message, User
from security import get_current_user, SECRET_KEY, ALGORITHM

router = APIRouter()


# ─── Connection Manager ──────────────────────────────────────────────────────

class ConnectionManager:
    def __init__(self):
        # conv_id → set of websockets
        self.rooms: Dict[int, Set[WebSocket]] = {}

    async def connect(self, ws: WebSocket, conv_id: int):
        await ws.accept()
        self.rooms.setdefault(conv_id, set()).add(ws)

    def disconnect(self, ws: WebSocket, conv_id: int):
        room = self.rooms.get(conv_id, set())
        room.discard(ws)
        if not room:
            self.rooms.pop(conv_id, None)

    async def broadcast(self, conv_id: int, data: dict, exclude: WebSocket = None):
        for ws in list(self.rooms.get(conv_id, [])):
            if ws is not exclude:
                try:
                    await ws.send_json(data)
                except Exception:
                    pass


manager = ConnectionManager()


# ─── Schémas Pydantic ────────────────────────────────────────────────────────

class ConversationCreate(BaseModel):
    other_user_id: int


class MessageOut(BaseModel):
    id: int
    conversation_id: int
    sender_id: int
    sender_name: str
    content: str
    created_at: datetime
    is_read: bool

    model_config = {"from_attributes": True}


class ConversationOut(BaseModel):
    id: int
    other_user_id: int
    other_user_name: str
    last_message: str | None
    last_message_at: datetime | None
    unread_count: int

    model_config = {"from_attributes": True}


# ─── Helpers ─────────────────────────────────────────────────────────────────

def _get_conv_or_404(conv_id: int, user_id: int, db: Session) -> Conversation:
    conv = db.query(Conversation).filter(Conversation.id == conv_id).first()
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation introuvable")
    if user_id not in (conv.user1_id, conv.user2_id):
        raise HTTPException(status_code=403, detail="Accès refusé")
    return conv


def _other_user(conv: Conversation, my_id: int) -> int:
    return conv.user2_id if conv.user1_id == my_id else conv.user1_id


# ─── WebSocket ───────────────────────────────────────────────────────────────

@router.websocket("/ws/{conv_id}")
async def websocket_endpoint(
    ws: WebSocket,
    conv_id: int,
    token: str = Query(...),
    db: Session = Depends(get_db),
):
    """
    Connexion WebSocket authentifiée par token JWT (query param).
    Messages envoyés : { "content": "texte" }
    Messages reçus   : { "id", "sender_id", "sender_name", "content", "created_at" }
    """
    # Authentification via token JWT passé en query param
    from jose import JWTError, jwt as jose_jwt
    try:
        payload = jose_jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        email: str = payload.get("sub")
        if not email:
            raise ValueError("no sub")
    except (JWTError, ValueError):
        await ws.close(code=1008)
        return

    user = db.query(User).filter(User.email == email).first()
    if not user:
        await ws.close(code=1008)
        return

    user_id = user.id

    # Vérification que l'utilisateur fait partie de la conversation
    conv = db.query(Conversation).filter(Conversation.id == conv_id).first()
    if not conv or user_id not in (conv.user1_id, conv.user2_id):
        await ws.close(code=1008)
        return

    await manager.connect(ws, conv_id)
    try:
        while True:
            data = await ws.receive_text()
            try:
                payload = json.loads(data)
            except json.JSONDecodeError:
                continue

            content = (payload.get("content") or "").strip()
            if not content:
                continue

            # Persister le message
            msg = Message(
                conversation_id=conv_id,
                sender_id=user_id,
                content=content,
            )
            db.add(msg)
            conv.last_message_at = datetime.utcnow()
            db.commit()
            db.refresh(msg)

            # Diffuser à tous les participants dans la room
            out = {
                "id": msg.id,
                "conversation_id": conv_id,
                "sender_id": user_id,
                "sender_name": user.name or user.email,
                "content": msg.content,
                "created_at": msg.created_at.isoformat(),
                "is_read": False,
            }
            await manager.broadcast(conv_id, out)

    except WebSocketDisconnect:
        manager.disconnect(ws, conv_id)


# ─── REST ────────────────────────────────────────────────────────────────────

@router.get("/conversations", response_model=List[ConversationOut])
def list_conversations(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    convs = (
        db.query(Conversation)
        .filter(
            (Conversation.user1_id == current_user.id)
            | (Conversation.user2_id == current_user.id)
        )
        .order_by(Conversation.last_message_at.desc().nullslast())
        .all()
    )

    result = []
    for conv in convs:
        other_id = _other_user(conv, current_user.id)
        other = db.query(User).filter(User.id == other_id).first()

        last_msg = (
            db.query(Message)
            .filter(Message.conversation_id == conv.id)
            .order_by(Message.created_at.desc())
            .first()
        )

        unread = (
            db.query(Message)
            .filter(
                Message.conversation_id == conv.id,
                Message.sender_id != current_user.id,
                Message.is_read == False,
            )
            .count()
        )

        result.append(
            ConversationOut(
                id=conv.id,
                other_user_id=other_id,
                other_user_name=other.name or other.email if other else "Utilisateur",
                last_message=last_msg.content if last_msg else None,
                last_message_at=conv.last_message_at,
                unread_count=unread,
            )
        )
    return result


@router.post("/conversations", response_model=ConversationOut)
def get_or_create_conversation(
    body: ConversationCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if body.other_user_id == current_user.id:
        raise HTTPException(status_code=400, detail="Impossible de discuter avec soi-même")

    other = db.query(User).filter(User.id == body.other_user_id).first()
    if not other:
        raise HTTPException(status_code=404, detail="Utilisateur introuvable")

    u1, u2 = sorted([current_user.id, body.other_user_id])
    conv = (
        db.query(Conversation)
        .filter(Conversation.user1_id == u1, Conversation.user2_id == u2)
        .first()
    )
    if not conv:
        conv = Conversation(user1_id=u1, user2_id=u2)
        db.add(conv)
        db.commit()
        db.refresh(conv)

    other_id = _other_user(conv, current_user.id)
    other_user = db.query(User).filter(User.id == other_id).first()
    last_msg = (
        db.query(Message)
        .filter(Message.conversation_id == conv.id)
        .order_by(Message.created_at.desc())
        .first()
    )
    unread = (
        db.query(Message)
        .filter(
            Message.conversation_id == conv.id,
            Message.sender_id != current_user.id,
            Message.is_read == False,
        )
        .count()
    )
    return ConversationOut(
        id=conv.id,
        other_user_id=other_id,
        other_user_name=other_user.name or other_user.email if other_user else "Utilisateur",
        last_message=last_msg.content if last_msg else None,
        last_message_at=conv.last_message_at,
        unread_count=unread,
    )


@router.get("/conversations/{conv_id}/messages", response_model=List[MessageOut])
def get_messages(
    conv_id: int,
    limit: int = 50,
    before_id: int = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    conv = _get_conv_or_404(conv_id, current_user.id, db)

    q = db.query(Message).filter(Message.conversation_id == conv.id)
    if before_id:
        q = q.filter(Message.id < before_id)
    messages = q.order_by(Message.created_at.desc()).limit(limit).all()
    messages.reverse()

    result = []
    for m in messages:
        sender = db.query(User).filter(User.id == m.sender_id).first()
        result.append(
            MessageOut(
                id=m.id,
                conversation_id=m.conversation_id,
                sender_id=m.sender_id,
                sender_name=sender.name or sender.email if sender else "?",
                content=m.content,
                created_at=m.created_at,
                is_read=m.is_read,
            )
        )
    return result


@router.patch("/conversations/{conv_id}/read")
def mark_as_read(
    conv_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    conv = _get_conv_or_404(conv_id, current_user.id, db)
    db.query(Message).filter(
        Message.conversation_id == conv.id,
        Message.sender_id != current_user.id,
        Message.is_read == False,
    ).update({"is_read": True})
    db.commit()
    return {"ok": True}
