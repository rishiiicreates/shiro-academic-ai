const API_BASE = import.meta.env.VITE_API_URL || '/api';

export async function fetchMetadata() {
  const res = await fetch(`${API_BASE}/metadata`);
  if (!res.ok) throw new Error('Failed to fetch metadata');
  return res.json();
}

export async function fetchThreads() {
  const res = await fetch(`${API_BASE}/threads`);
  if (!res.ok) throw new Error('Failed to fetch threads');
  return res.json();
}

export async function fetchThread(id) {
  const res = await fetch(`${API_BASE}/threads/${id}`);
  if (!res.ok) throw new Error('Failed to fetch thread');
  return res.json();
}

export async function createThread(title, semester, subject) {
  const res = await fetch(`${API_BASE}/threads`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, semester, subject })
  });
  if (!res.ok) throw new Error('Failed to create thread');
  return res.json();
}

export async function deleteThread(id) {
  const res = await fetch(`${API_BASE}/threads/${id}`, {
    method: 'DELETE'
  });
  if (!res.ok) throw new Error('Failed to delete thread');
  return res.json();
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
