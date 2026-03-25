"""
train_model.py — Train ALS model + export jobs CSV
Run once: python train_model.py
"""
import ast, json, os, warnings
import numpy as np
import pandas as pd
import scipy.sparse as sparse

warnings.filterwarnings('ignore')
np.random.seed(42)

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # CS_APP root

X_TRAIN = os.path.join(BASE, 'x_train_Meacfjr.csv')
Y_TRAIN = os.path.join(BASE, 'y_train_SwJNMSu.csv')
JOBS_META = os.path.join(BASE, 'job_listings.json')

MODEL_DIR = os.path.join(os.path.dirname(__file__), 'model')
os.makedirs(MODEL_DIR, exist_ok=True)

WEIGHT_MAP = {'apply': 5, 'view': 1}
ALS_FACTORS = 64
ALS_ITERATIONS = 20
ALS_REG = 0.05
ALS_ALPHA = 40

# ── Load sessions ──────────────────────────────────────────────────────────────
def load_sessions(path):
    df = pd.read_csv(path)
    df['job_ids'] = df['job_ids'].apply(ast.literal_eval)
    df['actions'] = df['actions'].apply(ast.literal_eval)
    return df

print('Loading data...')
df_train = load_sessions(X_TRAIN)
print(f'  {len(df_train):,} sessions, {len({j for js in df_train["job_ids"] for j in js}):,} unique jobs')

# ── Build interaction matrix ───────────────────────────────────────────────────
sessions_list = df_train['session_id'].tolist()
session2idx   = {s: i for i, s in enumerate(sessions_list)}
all_jobs      = sorted({j for js in df_train['job_ids'] for j in js})
job2idx       = {j: i for i, j in enumerate(all_jobs)}
idx2job       = {i: j for j, i in job2idx.items()}

rows, cols, data = [], [], []
for _, row in df_train.iterrows():
    s_i = session2idx[row['session_id']]
    for job, act in zip(row['job_ids'], row['actions']):
        j_i = job2idx.get(job)
        if j_i is not None:
            rows.append(s_i); cols.append(j_i)
            data.append(WEIGHT_MAP.get(act, 1))

R = sparse.csr_matrix((data, (rows, cols)),
    shape=(len(session2idx), len(job2idx)), dtype=np.float32)
print(f'  Matrix {R.shape}, {R.nnz:,} non-zeros')

# ── ALS training ───────────────────────────────────────────────────────────────
def als_fit(R, factors=64, iterations=20, reg=0.05, alpha=40):
    n_users, n_items = R.shape
    C = R.copy().astype(np.float32); C.data = 1.0 + alpha * C.data
    P = R.copy(); P.data[:] = 1.0
    U = (np.random.randn(n_users, factors) * 0.01).astype(np.float32)
    V = (np.random.randn(n_items, factors) * 0.01).astype(np.float32)
    reg_I = reg * np.eye(factors, dtype=np.float32)
    Rt, Ct, Pt = R.T.tocsr(), C.T.tocsr(), P.T.tocsr()

    for it in range(iterations):
        VtV = V.T @ V
        for u in range(n_users):
            s, e = R.indptr[u], R.indptr[u+1]
            if s == e: continue
            ii, cv, pv = R.indices[s:e], C.data[s:e], P.data[s:e]
            Vi = V[ii]
            A = VtV + Vi.T @ (np.diag(cv - 1) @ Vi) + reg_I
            U[u] = np.linalg.solve(A, Vi.T @ (cv * pv))
        UtU = U.T @ U
        for i in range(n_items):
            s, e = Rt.indptr[i], Rt.indptr[i+1]
            if s == e: continue
            uu, cv, pv = Rt.indices[s:e], Ct.data[s:e], Pt.data[s:e]
            Ui = U[uu]
            A = UtU + Ui.T @ (np.diag(cv - 1) @ Ui) + reg_I
            V[i] = np.linalg.solve(A, Ui.T @ (cv * pv))
        print(f'  ALS iter {it+1}/{iterations}')
    return U, V

print('Training ALS...')
U, V = als_fit(R, factors=ALS_FACTORS, iterations=ALS_ITERATIONS,
               reg=ALS_REG, alpha=ALS_ALPHA)

# ── Popularity fallback ────────────────────────────────────────────────────────
pop_scores = np.zeros(len(job2idx), dtype=np.float32)
for _, row in df_train.iterrows():
    for job, act in zip(row['job_ids'], row['actions']):
        j_i = job2idx.get(job)
        if j_i is not None:
            pop_scores[j_i] += WEIGHT_MAP.get(act, 1)

# ── Save model ─────────────────────────────────────────────────────────────────
np.savez(os.path.join(MODEL_DIR, 'als_model.npz'),
         V=V, pop_scores=pop_scores, job_ids=np.array(all_jobs))
pd.DataFrame({'job_id': all_jobs, 'idx': range(len(all_jobs))}).to_csv(
    os.path.join(MODEL_DIR, 'job_index.csv'), index=False)
print(f'✓ Model saved → {MODEL_DIR}/als_model.npz')

# ── Export jobs CSV from job_listings.json ─────────────────────────────────────
if os.path.exists(JOBS_META):
    print('Exporting jobs CSV...')
    with open(JOBS_META, 'r', encoding='utf-8') as f:
        raw_jobs = json.load(f)

    rows_out = []
    for job_id, content in raw_jobs.items():
        if isinstance(content, str):
            try: content = ast.literal_eval(content)
            except: content = [content]
        if isinstance(content, list):
            blocks = content
        else:
            blocks = [str(content)]

        title = description = ''
        skills, tasks = [], []
        for block in blocks:
            block = str(block).strip()
            if block.startswith('TITLE'):
                lines = block.split('\n')
                if len(lines) > 1: title = lines[1].strip()
            elif block.startswith('SKILLS'):
                for line in block.split('\n')[1:]:
                    line = line.strip().lstrip('- ')
                    if not line: continue
                    try:
                        s = ast.literal_eval(line)
                        if isinstance(s, dict): skills.append(s.get('name', ''))
                    except: skills.append(line)
            elif block.startswith('TASKS'):
                for line in block.split('\n')[1:]:
                    line = line.strip().lstrip('- ')
                    if not line: continue
                    try:
                        t = ast.literal_eval(line)
                        if isinstance(t, dict): tasks.append(t.get('name', ''))
                    except: tasks.append(line)
            elif block.startswith('SECTION:'):
                idx = block.find('DESCRIPTION:')
                if idx != -1:
                    snippet = block[idx + len('DESCRIPTION:'):].strip()
                    description = (description + ' ' + snippet).strip()

        # Deduplicate skills
        seen_s = set(); deduped = []
        for s in skills:
            k = s.lower().strip()
            if k not in seen_s: seen_s.add(k); deduped.append(s.strip())

        row = {
            'job_id': str(job_id),
            'title': title[:120] if title else f'Poste #{job_id}',
            'description_preview': description[:400].rstrip(),
            'skills': ' | '.join(deduped[:8]),
            'tasks': ' | '.join(tasks[:5]),
        }
        for k in range(1, 4):
            row[f'skill_{k}'] = deduped[k-1] if len(deduped) >= k else ''
        rows_out.append(row)

    df_jobs = pd.DataFrame(rows_out)
    jobs_csv = os.path.join(MODEL_DIR, 'jobs_export.csv')
    df_jobs.to_csv(jobs_csv, index=False, encoding='utf-8')
    print(f'✓ {len(df_jobs):,} jobs exported → {jobs_csv}')
else:
    print(f'[WARN] {JOBS_META} not found — jobs CSV skipped')

print('\nDone! Run the FastAPI server to serve recommendations.')
