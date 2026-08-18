import json
import urllib.request
import time
import os
import sys

EVAL_QUESTIONS = [
    {
        "id": "eval_01",
        "category": "In-Scope (Syllabus)",
        "subject": "Operating Systems",
        "question": "What is Priority Scheduling in Operating Systems and what problem can occur with low priority processes?",
        "expected_keywords": ["Priority", "preempt", "starvation", "aging", "deadline", "priority"],
        "expect_grounded": True
    },
    {
        "id": "eval_02",
        "category": "In-Scope (Syllabus)",
        "subject": "Operating Systems",
        "question": "What are the main states of a process and process scheduling queues in OS?",
        "expected_keywords": ["process", "state", "ready", "running", "waiting", "queue", "scheduling"],
        "expect_grounded": True
    },
    {
        "id": "eval_03",
        "category": "In-Scope (Syllabus)",
        "subject": "Calculus And Linear Algebra",
        "question": "State the Cayley-Hamilton Theorem and explain how it helps find matrix inverse or higher powers.",
        "expected_keywords": ["characteristic", "equation", "matrix", "Cayley", "Hamilton", "eigenvalue", "inverse"],
        "expect_grounded": True
    },
    {
        "id": "eval_04",
        "category": "In-Scope (Syllabus)",
        "subject": "Database Management Systems",
        "question": "What are the ACID properties in database transaction management?",
        "expected_keywords": ["Atomicity", "Consistency", "Isolation", "Durability", "transaction"],
        "expect_grounded": True
    },
    {
        "id": "eval_05",
        "category": "In-Scope (Syllabus)",
        "subject": "Discrete Mathematics",
        "question": "Explain Dijkstra algorithm for finding the shortest path in a graph.",
        "expected_keywords": ["shortest path", "graph", "weight", "distance", "Dijkstra", "vertex", "algorithm"],
        "expect_grounded": True
    },
    {
        "id": "eval_06",
        "category": "In-Scope (Syllabus)",
        "subject": "Chemistry",
        "question": "Explain the Clausius-Clapeyron equation and its significance in phase equilibria.",
        "expected_keywords": ["Clausius", "Clapeyron", "vapor", "pressure", "temperature", "enthalpy", "latent", "phase"],
        "expect_grounded": True
    },
    {
        "id": "eval_07",
        "category": "In-Scope (Syllabus)",
        "subject": "Database Management Systems",
        "question": "What are the limitations and issues with traditional File Processing Systems compared to DBMS?",
        "expected_keywords": ["file", "processing", "redundancy", "isolation", "integrity", "security", "atomicity", "data"],
        "expect_grounded": True
    },
    {
        "id": "eval_08",
        "category": "In-Scope (Syllabus)",
        "subject": "Operating Systems",
        "question": "What is the Dining Philosophers Problem and why is it important in synchronization?",
        "expected_keywords": ["philosopher", "chopstick", "fork", "deadlock", "starvation", "synchronization"],
        "expect_grounded": True
    },
    {
        "id": "eval_09",
        "category": "Out-of-Scope (Deliberate Non-Syllabus)",
        "subject": None,
        "question": "How do I make traditional Italian spaghetti carbonara with egg yolks and guanciale?",
        "expected_keywords": ["not contain information", "syllabus", "course materials do not contain"],
        "expect_grounded": False
    },
    {
        "id": "eval_10",
        "category": "Out-of-Scope (Deliberate Non-Syllabus)",
        "subject": None,
        "question": "What were the worldwide box office earnings of the movie Avengers Endgame in 2019?",
        "expected_keywords": ["not contain information", "syllabus", "course materials do not contain"],
        "expect_grounded": False
    }
]

def query_chat_api(question, subject=None, k=5):
    payload = {
        "message": question,
        "k": k
    }
    if subject:
        payload["subject"] = subject

    req = urllib.request.Request(
        "http://127.0.0.1:8080/api/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )

    sources = []
    tokens = []
    thread_id = None
    start_time = time.time()

    with urllib.request.urlopen(req) as resp:
        for raw_line in resp:
            line = raw_line.decode("utf-8").strip()
            if not line:
                continue
            if line.startswith("data:"):
                try:
                    data = json.loads(line[5:].strip())
                    t = data.get("type")
                    if t == "sources":
                        sources = data.get("sources", [])
                        thread_id = data.get("threadId")
                    elif t == "token":
                        tokens.append(data.get("token", ""))
                    elif t == "done":
                        thread_id = data.get("threadId")
                except Exception as e:
                    pass

    elapsed = round(time.time() - start_time, 2)
    full_answer = "".join(tokens).strip()

    return {
        "thread_id": thread_id,
        "elapsed_seconds": elapsed,
        "sources": sources,
        "sources_count": len(sources),
        "answer": full_answer
    }

def run_eval():
    print("=" * 70)
    print("THE HELPER — Academic Syllabus RAG Evaluation Suite")
    print("=" * 70)

    results = []
    passed_count = 0

    for idx, item in enumerate(EVAL_QUESTIONS, 1):
        q_id = item["id"]
        q_cat = item["category"]
        question = item["question"]
        subj = item.get("subject")
        expect_grounded = item["expect_grounded"]

        print(f"\n[{idx}/{len(EVAL_QUESTIONS)}] Running: {q_id} ({q_cat})")
        print(f"Query: \"{question}\"")
        if subj:
            print(f"Subject Scope: {subj}")

        try:
            resp = query_chat_api(question, subject=subj)
            answer = resp["answer"]
            sources = resp["sources"]
            elapsed = resp["elapsed_seconds"]

            passed = False
            reasons = []

            if expect_grounded:
                if len(sources) > 0:
                    reasons.append(f"Retrieved {len(sources)} source chunks")
                else:
                    reasons.append("FAIL: No source chunks retrieved")

                has_keywords = any(kw.lower() in answer.lower() for kw in item["expected_keywords"])
                if has_keywords:
                    reasons.append("Answer contains expected technical concepts")
                else:
                    reasons.append("WARN: Missing expected domain keywords")

                has_citation = "[" in answer or len(sources) > 0
                if has_citation:
                    reasons.append("Includes source citations or chunk bindings")

                passed = len(sources) > 0 and len(answer) > 50 and has_keywords
            else:
                refusal_phrases = ["not contain information", "syllabus", "course materials do not contain", "not covered", "does not contain"]
                is_refusal = any(rp in answer.lower() for rp in refusal_phrases)
                if is_refusal:
                    reasons.append("Correctly triggered out-of-scope syllabus refusal")
                    passed = True
                else:
                    reasons.append("FAIL: Did not clearly refuse out-of-scope question")
                    passed = False

            if passed:
                passed_count += 1
                status_str = "PASSED"
            else:
                status_str = "FAILED"

            print(f"Status: {status_str} ({elapsed}s)")
            print(f"Sources Count: {len(sources)}")
            print(f"Answer Preview: {answer[:160]}...")
            if sources:
                m = sources[0].get("metadata", {})
                print(f"Top Source: {m.get('subject')} | {m.get('file_name')} (Page {m.get('page_num')}) [Sim: {sources[0].get('similarity')}]")

            results.append({
                "id": q_id,
                "category": q_cat,
                "question": question,
                "subject": subj,
                "passed": passed,
                "elapsed_seconds": elapsed,
                "sources_count": len(sources),
                "top_source": sources[0] if sources else None,
                "answer": answer,
                "checks": reasons
            })

        except Exception as e:
            print(f"ERROR running {q_id}: {e}")
            results.append({
                "id": q_id,
                "category": q_cat,
                "question": question,
                "passed": False,
                "error": str(e)
            })

        # Pacing to respect Gemini free tier RPM
        if idx < len(EVAL_QUESTIONS):
            time.sleep(3.0)

    print("\n" + "=" * 70)
    print(f"EVALUATION SUMMARY: {passed_count}/{len(EVAL_QUESTIONS)} tests passed ({round(passed_count/len(EVAL_QUESTIONS)*100, 1)}%)")
    print("=" * 70)

    out_file = "/Users/rishii/the-helper-rag-app/eval/eval_results.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump({
            "total": len(EVAL_QUESTIONS),
            "passed": passed_count,
            "success_rate": round(passed_count / len(EVAL_QUESTIONS) * 100, 1),
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "results": results
        }, f, indent=2)
    print(f"Saved detailed results to {out_file}")

if __name__ == "__main__":
    run_eval()
