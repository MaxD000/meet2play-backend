"""
routers/jobs.py — Job listings + ALS recommendation endpoint
"""
from __future__ import annotations
import os
from typing import List
import numpy as np
import pandas as pd
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from security import get_current_user

router = APIRouter()

# ── Load model + jobs at startup ───────────────────────────────────────────────
MODEL_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'model')

def _load():
    model_path = os.path.join(MODEL_DIR, 'als_model.npz')
    index_path = os.path.join(MODEL_DIR, 'job_index.csv')
    jobs_path  = os.path.join(MODEL_DIR, 'jobs_export.csv')

    data       = np.load(model_path)
    V          = data['V'].astype(np.float32)
    pop_scores = data['pop_scores'].astype(np.float32)
    df_idx     = pd.read_csv(index_path)
    job2idx    = dict(zip(df_idx['job_id'].astype(str), df_idx['idx'].astype(int)))
    idx2job    = dict(zip(df_idx['idx'].astype(int), df_idx['job_id'].astype(str)))

    df_jobs    = pd.read_csv(jobs_path, dtype=str).fillna('')
    jobs_map   = {row['job_id']: row.to_dict() for _, row in df_jobs.iterrows()}

    return V, pop_scores, job2idx, idx2job, jobs_map

try:
    _V, _pop, _job2idx, _idx2job, _jobs_map = _load()
    print(f'[jobs] Model loaded — {len(_job2idx)} jobs, {_V.shape[1]} factors')
except Exception as e:
    print(f'[jobs] WARNING: model not loaded — {e}')
    _V = _pop = _job2idx = _idx2job = _jobs_map = None


# ── Schemas ────────────────────────────────────────────────────────────────────
class RecommendRequest(BaseModel):
    job_ids: List[str] = []
    actions: List[str] = []
    top_k: int = 15

class JobOut(BaseModel):
    job_id: str
    title: str
    description_preview: str
    skills: str
    skill_1: str
    skill_2: str
    skill_3: str
    tasks: str


# ── Helpers ────────────────────────────────────────────────────────────────────
WEIGHTS = {'apply': 5, 'view': 1}
ALPHA, REG = 40.0, 0.05

def _als_recommend(job_ids: list, actions: list, top_k: int) -> list:
    if _V is None:
        raise HTTPException(503, "Model not loaded")
    n_jobs = len(_job2idx)
    seen   = {_job2idx[j] for j in job_ids if j in _job2idx}

    if not seen:
        scores = _pop.copy()
    else:
        r = np.zeros(n_jobs, dtype=np.float32)
        for j, a in zip(job_ids, actions):
            if j in _job2idx:
                r[_job2idx[j]] += WEIGHTS.get(a, 1)
        c = 1.0 + ALPHA * r
        p = (r > 0).astype(np.float32)
        A = _V.T @ (c[:, None] * _V) + REG * np.eye(_V.shape[1], dtype=np.float32)
        u_star = np.linalg.solve(A, _V.T @ (c * p))
        scores = _V @ u_star

    scores[list(seen)] = -np.inf
    top_idx = np.argsort(scores)[::-1][:top_k]
    return [str(_idx2job[i]) for i in top_idx if i in _idx2job]

def _job_to_out(job_id: str) -> dict | None:
    if _jobs_map is None:
        return None
    m = _jobs_map.get(str(job_id))
    if m is None:
        return None
    return {
        'job_id':              m.get('job_id', job_id),
        'title':               m.get('title', f'Poste #{job_id}'),
        'description_preview': m.get('description_preview', ''),
        'skills':              m.get('skills', ''),
        'skill_1':             m.get('skill_1', ''),
        'skill_2':             m.get('skill_2', ''),
        'skill_3':             m.get('skill_3', ''),
        'tasks':               m.get('tasks', ''),
    }


# ── Endpoints ──────────────────────────────────────────────────────────────────

@router.post('/recommend')
def recommend_jobs(req: RecommendRequest, _=Depends(get_current_user)):
    """Return top-K recommended job objects based on session history."""
    ids = _als_recommend(req.job_ids, req.actions, req.top_k)
    jobs = [_job_to_out(i) for i in ids]
    return {'jobs': [j for j in jobs if j]}


@router.get('/popular')
def popular_jobs(limit: int = 20, _=Depends(get_current_user)):
    """Return most popular jobs (cold-start / first load)."""
    if _pop is None:
        raise HTTPException(503, "Model not loaded")
    top_idx = np.argsort(_pop)[::-1][:limit]
    ids  = [str(_idx2job[i]) for i in top_idx if i in _idx2job]
    jobs = [_job_to_out(i) for i in ids]
    return {'jobs': [j for j in jobs if j]}


@router.get('/search')
def search_jobs(q: str = '', limit: int = 30, _=Depends(get_current_user)):
    """Basic keyword search on title + skills."""
    if _jobs_map is None:
        raise HTTPException(503, "Jobs data not loaded")
    q_lower = q.lower()
    results = []
    for m in _jobs_map.values():
        if q_lower in m.get('title', '').lower() or q_lower in m.get('skills', '').lower():
            results.append(_job_to_out(m['job_id']))
        if len(results) >= limit:
            break
    return {'jobs': [j for j in results if j]}
