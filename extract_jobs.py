"""
extract_jobs.py — Job Listing → jobs_export.csv
================================================
Parses raw job listings (ENS Challenge #164 format) into a flat CSV
ready for Flutter (SQLite / Hive / in-memory Map).

Usage
-----
  # From a JSON dict  {job_id: raw_listing_string, ...}
  python extract_jobs.py --input job_postings_raw.json --output jobs_export.csv

  # From a CSV        columns: job_id, content
  python extract_jobs.py --input raw_listings.csv --id-col job_id --content-col content

Output columns
--------------
  job_id, title, description, description_preview (500 chars),
  skills_hard, skills_soft,           <- pipe-separated
  tasks,                               <- pipe-separated
  languages, certifications,           <- pipe-separated
  top_skill_1 .. top_skill_5,          <- individual columns for chip display
  n_skills, n_tasks
"""

import ast, json, argparse, sys
import pandas as pd


# ── Parser ─────────────────────────────────────────────────────────────────────

def parse_raw_listing(raw) -> dict:
    """
    Parse a single job listing from the challenge string/list format.

    Input:  string like "['TITLE\\nIngénieur...', 'SKILLS\\n- {...}', ...]"
            OR already a list of section strings
            OR a plain dict (pass-through)

    Returns: clean dict with keys:
        TITLE, DESCRIPTION, SKILLS, TASKS, LANGUAGES, CERTIFICATIONS, COURSES
    """
    if isinstance(raw, dict):
        return raw  # already parsed

    if isinstance(raw, str):
        try:
            raw = ast.literal_eval(raw)
        except Exception:
            raw = [raw]

    title, description = '', ''
    skills, tasks, languages, certifications, courses = [], [], [], [], []

    for block in raw:
        block = str(block).strip()

        # ── TITLE ──────────────────────────────────────────────────────────────
        if block.startswith('TITLE'):
            lines = block.split('\n')
            if len(lines) > 1:
                title = lines[1].strip()

        # ── SKILLS ────────────────────────────────────────────────────────────
        elif block.startswith('SKILLS'):
            for line in block.split('\n')[1:]:
                line = line.strip().lstrip('- ')
                if not line:
                    continue
                try:
                    skill = ast.literal_eval(line)
                    if isinstance(skill, dict):
                        skills.append(skill)
                    else:
                        skills.append({'name': str(skill), 'type': 'hard', 'value': None})
                except Exception:
                    skills.append({'name': line, 'type': 'hard', 'value': None})

        # ── TASKS ─────────────────────────────────────────────────────────────
        elif block.startswith('TASKS'):
            for line in block.split('\n')[1:]:
                line = line.strip().lstrip('- ')
                if not line:
                    continue
                try:
                    task = ast.literal_eval(line)
                    if isinstance(task, dict):
                        tasks.append(task)
                    else:
                        tasks.append({'name': str(task), 'value': None})
                except Exception:
                    tasks.append({'name': line, 'value': None})

        # ── LANGUAGES ─────────────────────────────────────────────────────────
        elif block.startswith('LANGUAGES'):
            languages = [l.strip().lstrip('- ')
                         for l in block.split('\n')[1:] if l.strip()]

        # ── CERTIFICATIONS ────────────────────────────────────────────────────
        elif block.startswith('CERTIFICATIONS'):
            certifications = [l.strip().lstrip('- ')
                               for l in block.split('\n')[1:] if l.strip()]

        # ── COURSES ───────────────────────────────────────────────────────────
        elif block.startswith('COURSES'):
            courses = [l.strip().lstrip('- ')
                       for l in block.split('\n')[1:] if l.strip()]

        # ── SECTION: DESCRIPTION ──────────────────────────────────────────────
        elif block.startswith('SECTION:'):
            idx = block.find('DESCRIPTION:')
            if idx != -1:
                snippet = block[idx + len('DESCRIPTION:'):].strip()
                description = (description + '\n' + snippet).strip()

    return {
        'TITLE':          title,
        'DESCRIPTION':    description,
        'SKILLS':         skills,
        'TASKS':          tasks,
        'LANGUAGES':      languages,
        'CERTIFICATIONS': certifications,
        'COURSES':        courses,
    }


# ── Flattener ──────────────────────────────────────────────────────────────────

def flatten_job(job_id: str, meta: dict) -> dict:
    """Convert a parsed job dict into a flat CSV row."""

    raw_skills = meta.get('SKILLS', [])
    hard_skills = [s['name'] for s in raw_skills
                   if isinstance(s, dict) and s.get('type') == 'hard']
    soft_skills = [s['name'] for s in raw_skills
                   if isinstance(s, dict) and s.get('type') == 'soft']

    # Deduplicate hard skills (preserve order, case-insensitive)
    seen = set()
    deduped = []
    for s in hard_skills:
        key = s.lower().strip()
        if key not in seen:
            seen.add(key)
            deduped.append(s.strip())

    raw_tasks = meta.get('TASKS', [])
    tasks = [t['name'] for t in raw_tasks if isinstance(t, dict) and 'name' in t]

    description = (meta.get('DESCRIPTION') or '').strip()

    row = {
        'job_id':               str(job_id),
        'title':                (meta.get('TITLE') or '').strip(),
        'description':          description,
        'description_preview':  description[:500].rstrip(),
        'skills_hard':          ' | '.join(deduped),
        'skills_soft':          ' | '.join(soft_skills),
        'tasks':                ' | '.join(tasks),
        'languages':            ' | '.join(meta.get('LANGUAGES') or []),
        'certifications':       ' | '.join(meta.get('CERTIFICATIONS') or []),
        'n_skills':             len(deduped),
        'n_tasks':              len(tasks),
    }
    for k in range(1, 6):
        row[f'top_skill_{k}'] = deduped[k - 1] if len(deduped) >= k else ''

    return row


# ── Main export ────────────────────────────────────────────────────────────────

def export_jobs(postings: dict, output_path: str = 'jobs_export.csv') -> pd.DataFrame:
    """Flatten all jobs and write to CSV."""
    rows = [flatten_job(jid, parse_raw_listing(meta))
            for jid, meta in postings.items()]
    df = pd.DataFrame(rows)

    # Reorder columns for readability
    col_order = [
        'job_id', 'title', 'description_preview',
        'top_skill_1', 'top_skill_2', 'top_skill_3', 'top_skill_4', 'top_skill_5',
        'skills_hard', 'skills_soft', 'tasks',
        'languages', 'certifications',
        'n_skills', 'n_tasks', 'description',
    ]
    df = df[[c for c in col_order if c in df.columns]]
    df.to_csv(output_path, index=False, encoding='utf-8')
    print(f'✓ {len(df):,} jobs exported → {output_path}')
    return df


# ── Demo ───────────────────────────────────────────────────────────────────────

SAMPLE_RAW = (
    "['TITLE\\nIngénieur Système\\n\\nSUMMARY\\nNous recherchons un Ingénieur Système.\\n\\n"
    "SECTION:\\nDESCRIPTION: Le candidat idéal devra posséder des compétences variées "
    "en virtualisation, Cloud et DevOps.\\n\\n"
    "SECTION:\\nDESCRIPTION: Diplôme Bac +3/+5 en Informatique.\\n"
    "Maîtrise des environnements Windows et Linux.\\n\\n"
    "SKILLS\\n- {\\'name\\': \\'Azure\\', \\'type\\': \\'hard\\', \\'value\\': None}\\n"
    "- {\\'name\\': \\'DevOps\\', \\'type\\': \\'hard\\', \\'value\\': None}\\n"
    "- {\\'name\\': \\'docker\\', \\'type\\': \\'hard\\', \\'value\\': None}\\n"
    "- {\\'name\\': \\'kubernetes\\', \\'type\\': \\'hard\\', \\'value\\': None}\\n"
    "- {\\'name\\': \\'linux\\', \\'type\\': \\'hard\\', \\'value\\': None}\\n\\n"
    "TASKS\\n- {\\'name\\': \\'administrer des serveurs middleware\\', \\'value\\': None}\\n"
    "- {\\'name\\': \\'gérer des bases de données\\', \\'value\\': None}\\n\\n"
    "LANGUAGES\\n\\nCERTIFICATIONS\\n']"
)


def demo():
    """Quick demo using the sample listing from the problem statement."""
    sample_postings = {'JOB_001': SAMPLE_RAW}
    df = export_jobs(sample_postings, 'jobs_export_demo.csv')
    print('\nSample row:')
    for col, val in df.iloc[0].items():
        if val:
            print(f'  {col:<22} {val}')


# ── CLI ────────────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Export job listings to CSV')
    parser.add_argument('--input',       default=None,      help='Input file (.json or .csv)')
    parser.add_argument('--output',      default='jobs_export.csv')
    parser.add_argument('--id-col',      default='job_id',  help='Job ID column (CSV mode)')
    parser.add_argument('--content-col', default='content', help='Content column (CSV mode)')
    parser.add_argument('--demo',        action='store_true')
    args = parser.parse_args()

    if args.demo or args.input is None:
        print('[Demo mode]')
        demo()
        sys.exit(0)

    ext = args.input.rsplit('.', 1)[-1].lower()

    if ext == 'json':
        with open(args.input, encoding='utf-8') as f:
            postings = json.load(f)
        # Values may already be dicts or raw strings — handled by parse_raw_listing
        export_jobs(postings, args.output)

    elif ext == 'csv':
        df_raw = pd.read_csv(args.input)
        postings = {
            str(row[args.id_col]): row[args.content_col]
            for _, row in df_raw.iterrows()
        }
        export_jobs(postings, args.output)

    else:
        print(f'Unsupported file type: {ext}. Use .json or .csv')
        sys.exit(1)
