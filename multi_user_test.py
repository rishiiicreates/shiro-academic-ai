import asyncio
import time
import os
from playwright.async_api import async_playwright

FRONTEND_URL = os.environ.get("TARGET_URL", "https://chiroshiro.vercel.app")

USER_PERSONAS = [
    {
        "name": "User 1 (Math PYQ Hunter)",
        "viewport": {"width": 1440, "height": 900},
        "subject_search": "Linear Algebra",
        "study_mode": "Exam PYQs",
        "question": "What is Cayley-Hamilton theorem and how to find inverse of a matrix using it?"
    },
    {
        "name": "User 2 (OS Concept Learner)",
        "viewport": {"width": 1280, "height": 800},
        "subject_search": "Operating Systems",
        "study_mode": "Lecture Notes",
        "question": "Explain Peterson algorithm and semaphores for mutual exclusion."
    },
    {
        "name": "User 3 (Mobile Quick Revisor)",
        "viewport": {"width": 375, "height": 812},
        "is_mobile": True,
        "subject_search": "Data Structures",
        "study_mode": "Learn from Basics",
        "question": "Explain QuickSort average and worst case time complexity."
    }
]

async def simulate_user(playwright, persona, results):
    name = persona["name"]
    print(f"[{name}] 🚀 Starting user session...")
    browser = await playwright.chromium.launch(headless=True)
    context = await browser.new_context(
        viewport=persona["viewport"],
        is_mobile=persona.get("is_mobile", False),
        user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )
    page = await context.new_page()

    console_logs = []
    network_errors = []
    sse_chunks = []

    page.on("console", lambda msg: console_logs.append(f"[{msg.type}] {msg.text}"))
    page.on("pageerror", lambda err: console_logs.append(f"[pageerror] {err}"))

    def on_response(response):
        if response.status >= 400:
            network_errors.append(f"{response.request.method} {response.url} -> {response.status}")

    page.on("response", on_response)

    start_time = time.time()
    try:
        # Step 1: Navigate to homepage
        print(f"[{name}] Navigating to {FRONTEND_URL}...")
        await page.goto(FRONTEND_URL, wait_until="networkidle", timeout=30000)
        page_title = await page.title()
        print(f"[{name}] Page loaded: '{page_title}' ({time.time() - start_time:.2f}s)")

        # Step 2: Check for subject selector modal or input
        subject_trigger = page.locator("button:has-text('Focus Subject'), button:has-text('Select Subject'), [data-testid='select-subject']").first
        if await subject_trigger.is_visible():
            await subject_trigger.click()
            await page.wait_for_timeout(500)
            search_input = page.locator("input[placeholder*='Search'], input[type='search']").first
            if await search_input.is_visible():
                await search_input.fill(persona["subject_search"])
                await page.wait_for_timeout(600)
                first_subj = page.locator("button:has-text('" + persona["subject_search"] + "'), div:has-text('" + persona["subject_search"] + "')").first
                if await first_subj.is_visible():
                    await first_subj.click()
                    print(f"[{name}] Selected subject '{persona['subject_search']}'")

        # Step 3: Toggle Study Mode if available
        mode_pill = page.locator(f"button:has-text('{persona['study_mode']}'), [role='tab']:has-text('{persona['study_mode']}')").first
        if await mode_pill.is_visible():
            await mode_pill.click()
            print(f"[{name}] Toggled study mode: '{persona['study_mode']}'")

        # Step 4: Type and submit chat prompt
        textarea = page.locator("textarea, input[placeholder*='Ask'], input[placeholder*='question']").first
        await textarea.wait_for(state="visible", timeout=10000)
        await textarea.fill(persona["question"])
        print(f"[{name}] Typed prompt: '{persona['question'][:40]}...'")

        submit_btn = page.locator("button[type='submit'], button[aria-label*='Send'], button:has-text('Send')").first
        if await submit_btn.is_visible():
            await submit_btn.click()
        else:
            await textarea.press("Enter")
        print(f"[{name}] Submitted prompt. Monitoring response...")

        # Step 5: Wait and capture response / stream
        await page.wait_for_timeout(8000)

        # Check latest assistant message
        messages = await page.locator(".prose, [data-message-role='assistant'], div:has-text('⚠️ Error')").all_text_contents()
        screenshot_path = f"/Users/rishii/Projects/ChrioShiro/user_{persona['name'].split()[1]}_screenshot.png"
        await page.screenshot(path=screenshot_path, full_page=True)

        results.append({
            "name": name,
            "success": True,
            "duration": time.time() - start_time,
            "network_errors": network_errors,
            "screenshot": screenshot_path,
            "messages_count": len(messages)
        })
        print(f"[{name}] ✅ Completed journey in {time.time() - start_time:.2f}s (Captured screenshot)")

    except Exception as e:
        print(f"[{name}] ❌ Failed: {e}")
        results.append({
            "name": name,
            "success": False,
            "duration": time.time() - start_time,
            "error": str(e),
            "network_errors": network_errors
        })
    finally:
        await context.close()
        await browser.close()

async def main():
    print(f"=== LAUNCHING CONCURRENT MULTI-USER TEST ON {FRONTEND_URL} ===")
    results = []
    async with async_playwright() as playwright:
        tasks = [simulate_user(playwright, p, results) for p in USER_PERSONAS]
        await asyncio.gather(*tasks)

    print("\n=== MULTI-USER SIMULATION SUMMARY ===")
    for r in results:
        status = "PASSED" if r.get("success") else "FAILED"
        print(f"* {r['name']}: {status} in {r.get('duration', 0):.2f}s | Errors: {len(r.get('network_errors', []))}")
        if r.get("network_errors"):
            print(f"  Network errors: {r['network_errors'][:3]}")

if __name__ == "__main__":
    asyncio.run(main())
