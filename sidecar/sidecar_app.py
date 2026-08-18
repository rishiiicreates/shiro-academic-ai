import os
import sys
import json
import re
import sqlite3
from typing import List, Optional, Dict, Any
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
import chromadb
from fastembed import TextEmbedding

EMBEDDINGS_DIR = os.getenv('EMBEDDINGS_DIR', '/Users/rishii/the-helper-vector-embeddings')
CHROMA_DIR = os.path.join(EMBEDDINGS_DIR, 'chroma_db')
DB_PATH = os.path.join(EMBEDDINGS_DIR, 'the_helper_rag.db')
MANIFEST_PATH = os.path.join(EMBEDDINGS_DIR, 'manifest.json')
DATA_DIR = os.getenv('DATA_DIR', os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'data'))
IMAGES_DIR = os.getenv('IMAGES_DIR', os.path.join(DATA_DIR, 'images'))
IMAGES_MANIFEST_PATH = os.getenv('IMAGES_MANIFEST_PATH', os.path.join(DATA_DIR, 'images_manifest.json'))

MIN_SIMILARITY_THRESHOLD = 0.50

os.makedirs(IMAGES_DIR, exist_ok=True)

app = FastAPI(
    title="Shiro RAG Retrieval Sidecar",
    description="Fastembed ONNX vector + SQLite Topic-Wise PYQ & FTS5 hybrid retrieval sidecar for SRM syllabus",
    version="2.3.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/images", StaticFiles(directory=IMAGES_DIR), name="images")

# Global singletons
_embedding_model: Optional[TextEmbedding] = None
_chroma_client: Optional[chromadb.PersistentClient] = None
_collection = None
_sqlite_conn: Optional[sqlite3.Connection] = None
_manifest_data: Optional[Dict[str, Any]] = None

SUBJECT_MAP = {
    'dsa': 'Data Structures And Algorithm',
    'data structures': 'Data Structures And Algorithm',
    'data structure': 'Data Structures And Algorithm',
    'data structures and algorithm': 'Data Structures And Algorithm',
    'data structures and algorithms': 'Data Structures And Algorithm',
    'os': 'Operating Systems',
    'operating system': 'Operating Systems',
    'operating systems': 'Operating Systems',
    'dbms': 'Database Management Systems',
    'database': 'Database Management Systems',
    'database management': 'Database Management Systems',
    'cn': 'Computer Networks',
    'computer network': 'Computer Networks',
    'computer networks': 'Computer Networks',
    'coa': 'Computer Organization And Architecture',
    'cao': 'Computer Organization And Architecture',
    'computer organization': 'Computer Organization And Architecture',
    'computer architecture': 'Computer Organization And Architecture',
    'daa': 'Design And Analysis Of Algorithms',
    'ada': 'Design And Analysis Of Algorithms',
    'algorithms': 'Design And Analysis Of Algorithms',
    'algorithm': 'Design And Analysis Of Algorithms',
    'pps': 'Programming For Problem Solving',
    'c programming': 'Programming For Problem Solving',
    'programming for problem solving': 'Programming For Problem Solving',
    'cla': 'Calculus And Linear Algebra',
    'linear algebra': 'Calculus And Linear Algebra',
    'calculus': 'Calculus And Linear Algebra',
    'maths 1': 'Calculus And Linear Algebra',
    'm1': 'Calculus And Linear Algebra',
    'acca': 'Advanced Calculus And Complex Analysis',
    'maths 2': 'Advanced Calculus And Complex Analysis',
    'm2': 'Advanced Calculus And Complex Analysis',
    'tpde': 'Transforms And Boundary Value Problems',
    'maths 3': 'Transforms And Boundary Value Problems',
    'm3': 'Transforms And Boundary Value Problems',
    'transforms': 'Transforms And Boundary Value Problems',
    'pqt': 'Probability And Queueing Theory',
    'probability': 'Probability And Queueing Theory',
    'nm': 'Numerical Methods & Analysis',
    'nma': 'Numerical Methods & Analysis',
    'numerical methods': 'Numerical Methods & Analysis',
    'ai': 'Artificial Intelligence',
    'artificial intelligence': 'Artificial Intelligence',
    'ml': 'Machine Learning',
    'machine learning': 'Machine Learning',
    'sepm': 'Software Engineering & Project Management (SEPM)',
    'se': 'Software Engineering & Project Management (SEPM)',
    'software engineering': 'Software Engineering & Project Management (SEPM)',
    'dld': 'Digital Logic Design',
    'dip': 'Digital Image Processing',
    'cd': 'Compiler Design',
    'compiler design': 'Compiler Design',
    'compiler': 'Compiler Design',
    'fswd': 'Full Stack Web Development',
    'full stack': 'Full Stack Web Development',
    'web dev': 'Full Stack Web Development',
    'oodp': 'Object Oriented Design And Programming',
    'oops': 'Object Oriented Design And Programming',
    'oop': 'Object Oriented Design And Programming',
    'java': 'Object Oriented Design And Programming',
    'foe': 'Fundamental Of Economics (FOE)',
    'economics': 'Fundamental Of Economics (FOE)',
    'cga': 'CGA',
    'comp bio': 'Introduction To Computational Biology',
    'computational biology': 'Introduction To Computational Biology',
    'chem': 'Chemistry',
    'chemistry': 'Chemistry',
    'physics': 'Semiconductor Physics And Computational Methods',
    'semiconductor physics': 'Semiconductor Physics And Computational Methods',
    'eee': 'Electrical And Electronics Engineering',
    'electrical': 'Electrical And Electronics Engineering',
    'cell bio': 'Cell Biology',
    'cell biology': 'Cell Biology',
    'biology': 'Biology'
}

def detect_subject_from_query(text: str) -> Optional[str]:
    if not text:
        return None
    t_lower = text.lower()
    for alias, canonical in sorted(SUBJECT_MAP.items(), key=lambda x: -len(x[0])):
        pattern = r'\b' + re.escape(alias) + r'\b'
        if re.search(pattern, t_lower):
            return canonical
    return None

def get_model() -> TextEmbedding:
    global _embedding_model
    if _embedding_model is None:
        print("[Sidecar] Initializing BAAI/bge-small-en-v1.5 fastembed ONNX model...")
        _embedding_model = TextEmbedding(model_name="BAAI/bge-small-en-v1.5")
    return _embedding_model

def get_collection():
    global _chroma_client, _collection
    if _collection is None:
        print(f"[Sidecar] Connecting to ChromaDB at {CHROMA_DIR}...")
        _chroma_client = chromadb.PersistentClient(path=CHROMA_DIR)
        _collection = _chroma_client.get_collection("the_helper_docs")
        print(f"[Sidecar] Loaded collection 'the_helper_docs' with {_collection.count()} chunks.")
    return _collection

def get_db() -> sqlite3.Connection:
    global _sqlite_conn
    if _sqlite_conn is None:
        _sqlite_conn = sqlite3.connect(DB_PATH, check_same_thread=False)
        _sqlite_conn.row_factory = sqlite3.Row
        _sqlite_conn.execute("PRAGMA journal_mode = WAL;")
    return _sqlite_conn

def get_manifest() -> Dict[str, Any]:
    global _manifest_data
    if _manifest_data is None:
        if os.path.exists(MANIFEST_PATH):
            with open(MANIFEST_PATH, 'r', encoding='utf-8') as f:
                _manifest_data = json.load(f)
        else:
            _manifest_data = {"subjects": [], "categories": []}

        try:
            db = get_db()
            cursor = db.cursor()
            cursor.execute("SELECT DISTINCT semester, subject FROM chunks WHERE semester != '' AND subject != '' ORDER BY semester, subject")
            rows = cursor.fetchall()
            sem_map = {}
            for row in rows:
                sem = row["semester"]
                sub = row["subject"]
                if sem not in sem_map:
                    sem_map[sem] = []
                if sub not in sem_map[sem]:
                    sem_map[sem].append(sub)
            _manifest_data["semester_subjects"] = sem_map
        except Exception as e:
            print(f"[Sidecar] Error building semester_subjects: {e}")

    return _manifest_data

class RetrieveRequest(BaseModel):
    question: str = Field(..., description="The query to retrieve chunks for")
    k: int = Field(5, description="Number of top chunks to return", ge=1, le=25)
    semester: Optional[str] = Field(None, description="Optional semester filter")
    subject: Optional[str] = Field(None, description="Optional subject filter")
    category: Optional[str] = Field(None, description="Optional category filter (e.g. 'PYQs', 'Notes', 'Syllabus')")
    study_mode: Optional[str] = Field(None, description="Optional study mode (e.g. 'pyqs', 'notes', 'learn_basics')")

class ChunkMetadata(BaseModel):
    file_name: Optional[str] = ""
    page_num: Optional[Any] = "1"
    subject: Optional[str] = ""
    semester: Optional[str] = ""
    category: Optional[str] = ""
    unit: Optional[str] = ""
    rel_path: Optional[str] = ""

class RetrievedChunk(BaseModel):
    id: Optional[str] = None
    text: str
    similarity: float
    distance: Optional[float] = None
    metadata: ChunkMetadata
    images: List[str] = Field(default_factory=list)

class RetrieveResponse(BaseModel):
    query: str
    count: int
    chunks: List[RetrievedChunk]

def format_exam_session_title(file_name: str, exam_name: str) -> str:
    name = (file_name or exam_name or "").replace(".pdf", "").replace(".docx", "").replace(".doc", "").strip()
    name_lower = name.lower()

    # Detect 4-digit years (e.g. 2024, 2023, 2022, 2019, 2018)
    year_match = re.search(r'(20\d\d)', name)
    year = year_match.group(1) if year_match else ""

    # Detect month/session keywords
    month_match = re.search(r'\b(nov|dec|november|december|may|june|jun|july|jul|jan|january|oct|october|apr|april)\b', name_lower)
    month = month_match.group(1).capitalize() if month_match else ""

    is_end_sem = "pyq" in name_lower or "end sem" in name_lower or "university" in name_lower or "endsem" in name_lower
    is_ct = "ct" in name_lower or "cycle" in name_lower

    if is_end_sem:
        session = f"{month} {year}".strip() if (month or year) else "Previous Years"
        return f"SRM University End-Semester Examination ({session})"
    elif is_ct:
        ct_num = re.search(r'ct\s*[-_]?\s*([123])', name_lower)
        num = ct_num.group(1) if ct_num else "1"
        session = f"{year}".strip() if year else "Session"
        return f"SRM Cycle Test (CT-{num} {session}) Examination"
    elif "important" in name_lower:
        return "SRM Core High-Weightage University PYQ Compilation"
    elif "model" in name_lower:
        return "SRM University Model Exam Paper"
    else:
        session = f"{month} {year}".strip() if (month or year) else ""
        return f"SRM Official Exam Paper {session}".strip()

def retrieve_topic_pyqs_sql(query_text: str, subject: Optional[str], limit: int = 5) -> List[RetrievedChunk]:
    """
    Direct SQL topic search on pyq_questions table with strict subject isolation and BM25 relevance ranking.
    Pulls authentic past year questions without vector guessing or cross-subject pollution.
    """
    db = get_db()
    cursor = db.cursor()

    # Detect subject from query if not explicitly passed
    effective_subject = subject.strip() if (subject and subject.strip()) else detect_subject_from_query(query_text)

    # Clean query tokens
    clean_q = re.sub(r'[^a-zA-Z0-9\s]', ' ', query_text)
    stopwords = {
        'what', 'give', 'tell', 'show', 'exam', 'questions', 'question', 'solve', 
        'about', 'some', 'previous', 'year', 'with', 'from', 'pyqs', 'pyq', 'past', 
        'help', 'need', 'please', 'can', 'you', 'the', 'and', 'for', 'are', 'how', 
        'why', 'who', 'when', 'where', 'which', 'all', 'any', 'me', 'authentic', 
        'real', 'them', 'paper', 'papers', 'years', 'test', 'semester', 'topic', 'topics',
        'particular', 'specific', 'given', 'following', 'based', 'using', 'detail',
        'explain', 'write', 'define', 'state', 'find', 'calculate', 'derive', 'discuss',
        'describe', 'understand', 'hey', 'shiro', 'conditions', 'condition', 'this', 'that',
        'dsa', 'os', 'dbms', 'cn', 'coa', 'daa', 'pps', 'cla', 'acca', 'tpde', 'pqt', 'ai', 'ml'
    }
    tokens = [t for t in clean_q.split() if len(t) > 2 and t.lower() not in stopwords]

    chunks: List[RetrievedChunk] = []

    try:
        if effective_subject:
            # STRICT SUBJECT ISOLATION: Never match chunks from other subjects!
            if tokens:
                fts_query = ' OR '.join(tokens[:8])
                sql = """
                SELECT q.id, q.question_text, q.subject, q.semester, q.exam_name, q.part, q.question_num, q.file_name, q.page_num, q.rel_path, rank
                FROM pyq_questions q
                JOIN pyq_questions_fts f ON q.rowid = f.rowid
                WHERE pyq_questions_fts MATCH ? AND q.subject = ?
                ORDER BY rank
                LIMIT ?
                """
                cursor.execute(sql, (fts_query, effective_subject, limit))
                rows = cursor.fetchall()
            else:
                rows = []

            # If no keyword matches, fetch authentic sample questions from that subject's exam papers
            if not rows:
                sql = """
                SELECT q.id, q.question_text, q.subject, q.semester, q.exam_name, q.part, q.question_num, q.file_name, q.page_num, q.rel_path, 0 as rank
                FROM pyq_questions q
                WHERE q.subject = ?
                ORDER BY length(q.question_text) DESC
                LIMIT ?
                """
                cursor.execute(sql, (effective_subject, limit))
                rows = cursor.fetchall()

        else:
            # No subject detected or specified - perform broad multi-subject BM25 search
            if not tokens:
                tokens = [t for t in clean_q.split() if len(t) > 2]

            if not tokens:
                return []

            fts_query = ' OR '.join(tokens[:8])
            sql = """
            SELECT q.id, q.question_text, q.subject, q.semester, q.exam_name, q.part, q.question_num, q.file_name, q.page_num, q.rel_path, rank
            FROM pyq_questions q
            JOIN pyq_questions_fts f ON q.rowid = f.rowid
            WHERE pyq_questions_fts MATCH ?
            ORDER BY rank
            LIMIT ?
            """
            cursor.execute(sql, (fts_query, limit))
            rows = cursor.fetchall()

        for r in rows:
            q_id = r["id"]
            sec = f"{r['part']} Q{r['question_num']}".strip()
            exam_title = format_exam_session_title(r["file_name"], r["exam_name"])
            text_body = (
                f"[AUTHENTIC SRM PREVIOUS YEAR EXAM QUESTION]\n"
                f"• Subject: {r['subject']}\n"
                f"• Exam Session & Year: {exam_title}\n"
                f"• Source Paper: {r['file_name']} (Page {r['page_num']})\n"
                f"• Section / Question: {sec}\n\n"
                f"Authentic Exam Question:\n{r['question_text']}"
            )

            chunks.append(RetrievedChunk(
                id=str(q_id),
                text=text_body,
                similarity=0.99,
                distance=0.01,
                metadata=ChunkMetadata(
                    file_name=r["file_name"],
                    page_num=str(r["page_num"]),
                    subject=r["subject"],
                    semester=r["semester"],
                    category="PYQs",
                    unit=exam_title,
                    rel_path=r["rel_path"]
                ),
                images=[]
            ))
    except Exception as e:
        print(f"[Sidecar] PYQ FTS search error: {e}")

    return chunks

@app.on_event("startup")
def startup_event():
    get_model()
    get_collection()
    get_db()
    get_manifest()
    print("[Sidecar] Startup complete.")

@app.get("/health")
def health_check():
    coll = get_collection()
    return {
        "status": "ok",
        "chroma_chunks": coll.count() if coll else 0,
        "database": "sqlite + fastembed",
        "images_count": len(os.listdir(IMAGES_DIR))
    }

@app.get("/metadata")
def metadata():
    return get_manifest()

def detect_year_from_query(text: str) -> Optional[str]:
    if not text:
        return None
    match = re.search(r'\b(20[12]\d)\b', text)
    return match.group(1) if match else None

def is_full_paper_query(text: str) -> bool:
    if not text:
        return False
    t_lower = text.lower()
    return any(p in t_lower for p in [
        'full question paper', 'full paper', 'complete question paper',
        'entire question paper', 'whole question paper', 'question paper with solution',
        'paper with solution', 'full exam paper', 'question paper of year',
        'question paper of 20', 'question paper 20', 'pyq of year', 'pyqs of year',
        'past paper of year', 'past paper 20', 'exam paper of 20', 'exam paper 20',
        'give me full', 'give me question paper'
    ])

def retrieve_full_exam_paper_sql(query_text: str, subject: Optional[str], limit: int = 1) -> List[RetrievedChunk]:
    db = get_db()
    cursor = db.cursor()

    effective_subject = subject.strip() if (subject and subject.strip()) else detect_subject_from_query(query_text)
    detected_year = detect_year_from_query(query_text)

    chunks: List[RetrievedChunk] = []

    try:
        if effective_subject and detected_year:
            # Match exact subject and year
            sql = """
            SELECT id, subject, semester, year, session, exam_type, file_name, rel_path, page_count, full_text
            FROM exam_papers
            WHERE subject LIKE ? AND (year = ? OR session LIKE ?)
            ORDER BY length(full_text) DESC
            LIMIT ?
            """
            cursor.execute(sql, (f"%{effective_subject}%", detected_year, f"%{detected_year}%", limit))
            rows = cursor.fetchall()

            # If no paper for that exact year, get the closest latest year paper
            if not rows:
                sql = """
                SELECT id, subject, semester, year, session, exam_type, file_name, rel_path, page_count, full_text
                FROM exam_papers
                WHERE subject LIKE ?
                ORDER BY CASE WHEN year != 'All Years' THEN year ELSE '2000' END DESC, length(full_text) DESC
                LIMIT ?
                """
                cursor.execute(sql, (f"%{effective_subject}%", limit))
                rows = cursor.fetchall()

        elif effective_subject:
            sql = """
            SELECT id, subject, semester, year, session, exam_type, file_name, rel_path, page_count, full_text
            FROM exam_papers
            WHERE subject LIKE ?
            ORDER BY CASE WHEN year != 'All Years' THEN year ELSE '2000' END DESC, length(full_text) DESC
            LIMIT ?
            """
            cursor.execute(sql, (f"%{effective_subject}%", limit))
            rows = cursor.fetchall()

        elif detected_year:
            sql = """
            SELECT id, subject, semester, year, session, exam_type, file_name, rel_path, page_count, full_text
            FROM exam_papers
            WHERE year = ? OR session LIKE ?
            ORDER BY length(full_text) DESC
            LIMIT ?
            """
            cursor.execute(sql, (detected_year, f"%{detected_year}%", limit))
            rows = cursor.fetchall()

        else:
            rows = []

        for r in rows:
            text_body = (
                f"[OFFICIAL COMPLETE SRM QUESTION PAPER — {r['subject'].upper()} ({r['session']})]\n"
                f"• Subject: {r['subject']}\n"
                f"• Academic Year / Session: {r['session']} (Year {r['year']})\n"
                f"• Exam Type: {r['exam_type']}\n"
                f"• Original Source File: {r['file_name']}\n\n"
                f"=== FULL AUTHENTIC QUESTION PAPER CONTENTS ===\n"
                f"{r['full_text']}"
            )

            chunks.append(RetrievedChunk(
                id=str(r["id"]),
                text=text_body,
                similarity=1.0,
                distance=0.0,
                metadata=ChunkMetadata(
                    file_name=r["file_name"],
                    page_num="1",
                    subject=r["subject"],
                    semester=r["semester"],
                    category="PYQs",
                    unit=f"{r['exam_type']} ({r['session']})",
                    rel_path=r["rel_path"]
                ),
                images=[]
            ))
    except Exception as e:
        print(f"[Sidecar] Full exam paper retrieval error: {e}")

    return chunks

@app.post("/retrieve", response_model=RetrieveResponse)
def retrieve(req: RetrieveRequest):
    query_text = req.question.strip()
    if not query_text:
        raise HTTPException(status_code=400, detail="Query text cannot be empty.")

    detected_subject = req.subject or detect_subject_from_query(query_text)
    is_pyq_mode = (req.category or "").upper() == "PYQS" or (req.study_mode or "").lower() == "pyqs" or is_full_paper_query(query_text)

    # 1. FULL QUESTION PAPER RETRIEVAL: If student asks for complete paper of a subject/year
    if is_full_paper_query(query_text):
        full_paper_chunks = retrieve_full_exam_paper_sql(query_text, detected_subject, limit=1)
        if full_paper_chunks and len(full_paper_chunks) > 0:
            print(f"[Sidecar] Full Question Paper matched: {len(full_paper_chunks)} paper for '{query_text}' (Subject: {detected_subject})")
            return RetrieveResponse(
                query=query_text,
                count=len(full_paper_chunks),
                chunks=full_paper_chunks
            )

    # 2. DIRECT DATABASE PYQ SEARCH: Topic-wise and year-wise past questions
    if is_pyq_mode:
        sql_pyqs = retrieve_topic_pyqs_sql(query_text, detected_subject, req.k)
        if sql_pyqs and len(sql_pyqs) > 0:
            print(f"[Sidecar] Direct SQLite PYQ match found: {len(sql_pyqs)} authentic questions for '{query_text}' (Subject: {detected_subject})")
            return RetrieveResponse(
                query=query_text,
                count=len(sql_pyqs),
                chunks=sql_pyqs
            )

    # Standard Vector Search Pipeline
    model = get_model()
    collection = get_collection()

    query_vector = list(model.embed([query_text]))[0].tolist()

    where_clauses = []
    if req.semester and req.semester.strip():
        where_clauses.append({"semester": req.semester.strip()})
    if detected_subject and detected_subject.strip():
        where_clauses.append({"subject": detected_subject.strip()})
    if req.category and req.category.strip():
        where_clauses.append({"category": req.category.strip()})

    where = None
    if len(where_clauses) == 1:
        where = where_clauses[0]
    elif len(where_clauses) > 1:
        where = {"$and": where_clauses}

    kwargs = {
        "query_embeddings": [query_vector],
        "n_results": min(req.k * 2, 20),
        "include": ["documents", "metadatas", "distances"]
    }
    if where:
        kwargs["where"] = where

    try:
        results = collection.query(**kwargs)
    except Exception as e:
        print(f"[Sidecar] Query error with filter {where}: {e}")
        kwargs.pop("where", None)
        results = collection.query(**kwargs)

    retrieved_chunks: List[RetrievedChunk] = []
    docs = results.get("documents", [[]])[0]
    metas = results.get("metadatas", [[]])[0]
    distances = results.get("distances", [[]])[0]
    ids = results.get("ids", [[]])[0]

    for i in range(len(docs)):
        dist = distances[i] if i < len(distances) and distances[i] is not None else None
        similarity = round(1.0 - dist, 4) if dist is not None else 0.0

        if similarity < MIN_SIMILARITY_THRESHOLD:
            continue

        meta = metas[i] if i < len(metas) and metas[i] else {}
        chunk_id = ids[i] if i < len(ids) else None

        file_name = str(meta.get("file_name", "Unknown"))
        page_num = str(meta.get("page_num", "1"))

        retrieved_chunks.append(RetrievedChunk(
            id=str(chunk_id) if chunk_id else None,
            text=docs[i],
            similarity=similarity,
            distance=round(dist, 4) if dist is not None else None,
            metadata=ChunkMetadata(
                file_name=file_name,
                page_num=page_num,
                subject=str(meta.get("subject", "General")),
                semester=str(meta.get("semester", "General")),
                category=str(meta.get("category", "Notes")),
                unit=str(meta.get("unit", "")),
                rel_path=str(meta.get("rel_path", ""))
            ),
            images=[]
        ))

        if len(retrieved_chunks) >= req.k:
            break

    return RetrieveResponse(
        query=query_text,
        count=len(retrieved_chunks),
        chunks=retrieved_chunks
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8001)
