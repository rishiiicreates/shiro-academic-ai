import React, { useState, useEffect, useRef } from 'react';
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import { fetchMetadata, fetchThreads, fetchThread, deleteThread, saveThreadMessages, streamChat } from './services/api';
import './App.css';

export default function App() {
  const [theme, setTheme] = useState(() => localStorage.getItem('the_helper_theme') || 'light');
  const [isSidebarOpen, setIsSidebarOpen] = useState(() => typeof window !== 'undefined' && window.innerWidth >= 768);
  const [metadata, setMetadata] = useState(null);
  
  const [threads, setThreads] = useState([]);
  const [activeThreadId, setActiveThreadId] = useState(null);
  const [activeThread, setActiveThread] = useState(null);
  const [messages, setMessages] = useState([]);
  
  const [attachments, setAttachments] = useState([]);
  const [isStreaming, setIsStreaming] = useState(false);
  
  // Scope filters & study mode
  const [subject, setSubject] = useState('');
  const [studyMode, setStudyMode] = useState('all'); // "all" | "notes" | "pyqs" | "learn_basics"
  
  const abortControllerRef = useRef(null);
  const isStreamingRef = useRef(false);

  // Apply theme
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('the_helper_theme', theme);
  }, [theme]);

  const handleToggleTheme = () => {
    setTheme((prev) => (prev === 'light' ? 'dark' : 'light'));
  };

  // Load initial metadata and threads
  useEffect(() => {
    fetchMetadata()
      .then(setMetadata)
      .catch((err) => console.error('Failed to load metadata:', err));

    loadThreads();
  }, []);

  const loadThreads = async () => {
    try {
      const data = await fetchThreads();
      setThreads(data || []);
    } catch (err) {
      console.error('Failed to load threads:', err);
    }
  };

  // Load active thread messages when activeThreadId changes (only if NOT currently streaming this thread)
  useEffect(() => {
    if (!activeThreadId) {
      setActiveThread(null);
      setMessages([]);
      return;
    }

    if (isStreamingRef.current) {
      return;
    }

    fetchThread(activeThreadId)
      .then((t) => {
        setActiveThread(t);
        setMessages(t.messages || []);
        if (t.subject) setSubject(t.subject);
      })
      .catch((err) => {
        console.error('Failed to fetch thread:', err);
      });
  }, [activeThreadId]);

  const handleNewChat = () => {
    if (isStreaming && abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    isStreamingRef.current = false;
    setIsStreaming(false);
    setActiveThreadId(null);
    setActiveThread(null);
    setMessages([]);
    setAttachments([]);
    if (typeof window !== 'undefined' && window.innerWidth < 768) {
      setIsSidebarOpen(false);
    }
  };

  const handleSelectThread = (threadId) => {
    if (isStreaming && abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    isStreamingRef.current = false;
    setIsStreaming(false);
    setActiveThreadId(threadId);
    setAttachments([]);
    if (typeof window !== 'undefined' && window.innerWidth < 768) {
      setIsSidebarOpen(false);
    }
  };

  const handleDeleteThread = async (threadId) => {
    try {
      await deleteThread(threadId);
      setThreads((prev) => prev.filter((t) => t.id !== threadId));
      if (activeThreadId === threadId) {
        handleNewChat();
      }
    } catch (err) {
      console.error('Failed to delete thread:', err);
    }
  };

  const handleStop = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    isStreamingRef.current = false;
    setIsStreaming(false);
  };

  const handleSend = async (customMessage = null, customSubject = null) => {
    const textToSend = customMessage !== null ? customMessage : '';
    const currentAttachments = [...attachments];

    if ((!textToSend && currentAttachments.length === 0) || isStreaming) return;

    const targetSubject = customSubject !== null && customSubject !== undefined ? customSubject : subject;

    setAttachments([]);

    // If no active thread yet, generate an ID
    let currentThreadId = activeThreadId || ('shiro-' + Date.now());
    if (!activeThreadId) {
      setActiveThreadId(currentThreadId);
    }

    const userMessage = {
      id: 'usr-' + Date.now(),
      role: 'user',
      content: textToSend,
      attachments: currentAttachments,
      timestamp: new Date().toISOString()
    };

    const assistantPlaceholder = {
      id: 'asst-' + Date.now(),
      role: 'assistant',
      content: '',
      sources: [],
      timestamp: new Date().toISOString()
    };

    const priorHistory = messages.map((m) => ({
      role: m.role === 'user' ? 'user' : 'assistant',
      content: m.content || ''
    }));

    setMessages((prev) => [...prev, userMessage, assistantPlaceholder]);
    setIsStreaming(true);
    isStreamingRef.current = true;

    abortControllerRef.current = new AbortController();

    await streamChat({
      message: textToSend,
      threadId: currentThreadId,
      messages: priorHistory,
      semester: undefined,
      subject: targetSubject,
      category: studyMode === 'pyqs' ? 'PYQs' : studyMode === 'notes' ? 'Notes' : undefined,
      studyMode,
      attachments: currentAttachments,
      signal: abortControllerRef.current.signal,
      onSources: (sources, returnedThreadId) => {
        setMessages((prev) => {
          const updated = [...prev];
          const lastIdx = updated.length - 1;
          if (lastIdx >= 0 && updated[lastIdx].role === 'assistant') {
            updated[lastIdx] = {
              ...updated[lastIdx],
              sources
            };
          }
          return updated;
        });
      },
      onToken: (token, returnedThreadId) => {
        setMessages((prev) => {
          const updated = [...prev];
          const lastIdx = updated.length - 1;
          if (lastIdx >= 0 && updated[lastIdx].role === 'assistant') {
            updated[lastIdx] = {
              ...updated[lastIdx],
              content: updated[lastIdx].content + token
            };
          }
          return updated;
        });
      },
      onDone: (returnedThreadId) => {
        setIsStreaming(false);
        isStreamingRef.current = false;
        abortControllerRef.current = null;
        setMessages((finalMsgs) => {
          saveThreadMessages(currentThreadId, finalMsgs, targetSubject);
          loadThreads();
          return finalMsgs;
        });
      },
      onError: (errMsg) => {
        setIsStreaming(false);
        isStreamingRef.current = false;
        abortControllerRef.current = null;
        setMessages((prev) => {
          const updated = [...prev];
          const lastIdx = updated.length - 1;
          if (lastIdx >= 0 && updated[lastIdx].role === 'assistant') {
            updated[lastIdx] = {
              ...updated[lastIdx],
              content: (updated[lastIdx].content ? updated[lastIdx].content + '\n\n' : '') + `⚠️ **Error:** ${errMsg}`
            };
          }
          saveThreadMessages(currentThreadId, updated, targetSubject);
          loadThreads();
          return updated;
        });
      }
    });
  };

  const handleSelectPrompt = (promptText, subjectHint) => {
    if (subjectHint) {
      setSubject(subjectHint);
    }
    handleSend(promptText, subjectHint);
  };

  return (
    <div className="app-container">
      {isSidebarOpen && (
        <div
          className="sidebar-backdrop"
          onClick={() => setIsSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      <Sidebar
        isOpen={isSidebarOpen}
        threads={threads}
        activeThreadId={activeThreadId}
        onSelectThread={handleSelectThread}
        onNewChat={handleNewChat}
        onDeleteThread={handleDeleteThread}
        theme={theme}
        onToggleTheme={handleToggleTheme}
        metadata={metadata}
      />

      <ChatArea
        thread={activeThread}
        messages={messages}
        isStreaming={isStreaming}
        attachments={attachments}
        setAttachments={setAttachments}
        onSend={(text) => handleSend(text)}
        onStop={handleStop}
        onSelectPrompt={handleSelectPrompt}
        subject={subject}
        setSubject={setSubject}
        studyMode={studyMode}
        setStudyMode={setStudyMode}
        metadata={metadata}
        isSidebarOpen={isSidebarOpen}
        onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
      />
    </div>
  );
}
