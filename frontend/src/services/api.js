const API_BASE = import.meta.env.VITE_API_URL || '/api';
const THREADS_KEY = 'shiro_user_threads_v2';

function getStoredThreads() {
  try {
    const raw = localStorage.getItem(THREADS_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    console.error('Failed to read threads from localStorage:', e);
    return [];
  }
}

function persistThreads(threads) {
  try {
    localStorage.setItem(THREADS_KEY, JSON.stringify(threads.slice(0, 30)));
  } catch (e) {
    console.error('Failed to save threads to localStorage:', e);
  }
}

export async function fetchMetadata() {
  const res = await fetch(`${API_BASE}/metadata`);
  if (!res.ok) throw new Error('Failed to fetch metadata');
  return res.json();
}

export async function fetchThreads() {
  return getStoredThreads();
}

export async function fetchThread(id) {
  const threads = getStoredThreads();
  const found = threads.find((t) => t.id === id);
  if (!found) throw new Error('Thread not found');
  return found;
}

export async function createThread(title, semester, subject) {
  const newThread = {
    id: 'thread-' + Date.now() + '-' + Math.random().toString(36).substring(2, 7),
    title: title || 'New Study Session',
    semester: semester || null,
    subject: subject || null,
    messages: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };
  const threads = getStoredThreads();
  persistThreads([newThread, ...threads]);
  return newThread;
}

export async function saveThreadMessages(threadId, messages, subject = null) {
  const threads = getStoredThreads();
  let found = false;
  const updated = threads.map((t) => {
    if (t.id === threadId) {
      found = true;
      let title = t.title;
      if (title === 'New Study Session' || title === 'Syllabus Chat' || !title) {
        const firstUser = messages.find((m) => m.role === 'user');
        if (firstUser && firstUser.content) {
          title = firstUser.content.length > 35 ? firstUser.content.substring(0, 35) + '...' : firstUser.content;
        }
      }
      return {
        ...t,
        title,
        subject: subject !== null && subject !== undefined ? subject : t.subject,
        messages,
        updatedAt: new Date().toISOString()
      };
    }
    return t;
  });

  if (!found) {
    let title = 'Study Session';
    const firstUser = messages.find((m) => m.role === 'user');
    if (firstUser && firstUser.content) {
      title = firstUser.content.length > 35 ? firstUser.content.substring(0, 35) + '...' : firstUser.content;
    }
    updated.unshift({
      id: threadId,
      title,
      subject,
      messages,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    });
  }

  persistThreads(updated);
}

export async function deleteThread(id) {
  const threads = getStoredThreads();
  const filtered = threads.filter((t) => t.id !== id);
  persistThreads(filtered);
  return { success: true, id };
}

export async function uploadFile(file) {
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`${API_BASE}/upload`, {
    method: 'POST',
    body: formData
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Upload failed (${res.status}): ${errText}`);
  }

  return res.json();
}

export async function streamChat({
  message,
  threadId,
  messages = [],
  semester,
  subject,
  category,
  studyMode,
  attachments = [],
  k = 5,
  signal,
  onSources,
  onToken,
  onDone,
  onError
}) {
  try {
    const res = await fetch(`${API_BASE}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({
        message,
        threadId,
        messages,
        semester: semester || undefined,
        subject: subject || undefined,
        category: category || undefined,
        studyMode: studyMode || undefined,
        attachments: attachments && attachments.length > 0 ? attachments : undefined,
        k
      }),
      signal
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Server returned ${res.status}: ${errText}`);
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      let currentEvent = 'message';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) {
          currentEvent = 'message';
          continue;
        }

        if (trimmed.startsWith('event:')) {
          currentEvent = trimmed.substring(6).trim();
        } else if (trimmed.startsWith('data:')) {
          const jsonStr = trimmed.substring(5).trim();
          if (!jsonStr) continue;

          try {
            const data = JSON.parse(jsonStr);

            if (data.type === 'sources' || currentEvent === 'sources') {
              if (onSources && data.sources) {
                onSources(data.sources, data.threadId);
              }
            } else if (data.type === 'token' || currentEvent === 'token') {
              if (onToken && data.token) {
                onToken(data.token, data.threadId);
              }
            } else if (data.type === 'done' || currentEvent === 'done') {
              if (onDone) {
                onDone(data.threadId);
              }
            } else if (data.type === 'error' || currentEvent === 'error') {
              if (onError) {
                onError(data.error || 'Unknown streaming error');
              }
            }
          } catch (e) {
            console.warn('Failed to parse SSE line:', trimmed, e);
          }
        }
      }
    }

    if (onDone) {
      onDone(threadId);
    }
  } catch (err) {
    if (err.name === 'AbortError') {
      if (onDone) onDone(threadId);
    } else {
      console.error('Chat stream error:', err);
      if (onError) onError(err.message);
    }
  }
}
