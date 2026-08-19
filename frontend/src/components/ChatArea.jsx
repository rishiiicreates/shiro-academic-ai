import React, { useRef, useEffect, useState } from 'react';
import { PanelLeft, Plus, Sun, Moon } from 'lucide-react';
import MessageItem from './MessageItem';
import InputBox from './InputBox';
import SubjectSelectorModal from './SubjectSelectorModal';
import ImageModal from './ImageModal';

const SAMPLE_PROMPTS = [
  {
    tag: 'Operating Systems (21CSC202J)',
    subject: 'Operating Systems',
    prompt: 'Explain CPU scheduling algorithms and Priority-based preemption with an intuitive analogy, code example, and mindmap.'
  },
  {
    tag: 'Calculus & Linear Algebra',
    subject: 'Calculus And Linear Algebra',
    prompt: 'Walk me through Cayley-Hamilton theorem and how to calculate the inverse of a 3x3 matrix step-by-step.'
  },
  {
    tag: 'DBMS (21CSC205P)',
    subject: 'Database Management Systems',
    prompt: 'What are the ACID properties in database transaction management and how are concurrency conflicts avoided?'
  },
  {
    tag: 'Design & Analysis of Algorithms',
    subject: 'Design And Analysis Of Algorithms',
    prompt: 'Compare Dijkstra vs Bellman-Ford algorithm with time complexity and real SRM exam question examples.'
  }
];

export default function ChatArea({
  thread,
  messages,
  isStreaming,
  attachments,
  setAttachments,
  onSend,
  onStop,
  onSelectPrompt,
  onNewChat,
  theme,
  onToggleTheme,
  subject,
  setSubject,
  studyMode,
  setStudyMode,
  metadata,
  isSidebarOpen,
  onToggleSidebar
}) {
  const [showFilters, setShowFilters] = useState(false);
  const [activeImage, setActiveImage] = useState(null);
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isStreaming]);

  return (
    <main className="main-chat">
      {/* Top Navigation */}
      <header className="top-nav">
        <div className="nav-left">
          <button
            className="toggle-sidebar-btn desktop-only"
            onClick={onToggleSidebar}
            title={isSidebarOpen ? "Hide sidebar" : "Show sidebar"}
          >
            <PanelLeft size={16} />
          </button>

          <div className="mobile-brand-wrapper mobile-only" onClick={onNewChat} title="Start New Chat">
            <img src="/assets/smiling-dog.svg" alt="Shiro" className="mobile-brand-avatar" />
            <span className="mobile-brand-title">Shiro</span>
          </div>

          <div className="thread-header-info desktop-only">
            <div className="current-thread-title">
              {thread?.title || 'Shiro'}
            </div>
            {subject && (
              <div className="active-filter-indicator">
                Focus Subject: {subject}
              </div>
            )}
          </div>
        </div>

        <div className="nav-right">
          {/* New Chat Button on Mobile */}
          <button
            className="nav-action-btn mobile-only"
            onClick={onNewChat}
            title="Start new study session"
          >
            <Plus size={15} color="var(--accent-warm)" />
            <span>New</span>
          </button>

          {/* Theme Switcher on Mobile */}
          <button
            className="nav-theme-btn mobile-only"
            onClick={onToggleTheme}
            title={theme === 'dark' ? "Switch to light mode" : "Switch to dark mode"}
          >
            {theme === 'dark' ? <Sun size={15} color="#f59e0b" /> : <Moon size={15} color="#64748b" />}
          </button>
        </div>
      </header>

      {/* Messages List / Empty State */}
      <div className="messages-container">
        <div className="messages-inner">
          {messages.length === 0 ? (
            <div className="empty-state">
              <div className="empty-logo-glow">
                <img src="/assets/astronaut-dog.svg" alt="Shiro Astronaut Dog" className="empty-hero-dog-img" />
              </div>
              <h1 className="empty-title">Shiro</h1>
              <p className="empty-subtitle">
                Learn any SRM topic from scratch, solve past exam papers, or query official lecture notes with unfiltered late-night wit and clarity.
              </p>

              <div className="prompt-suggestions-grid">
                {SAMPLE_PROMPTS.map((p, idx) => (
                  <div
                    key={idx}
                    className="prompt-card"
                    onClick={() => onSelectPrompt(p.prompt, p.subject)}
                  >
                    <div className="prompt-card-tag">{p.tag}</div>
                    <div className="prompt-card-text">{p.prompt}</div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            messages.map((msg, index) => (
              <MessageItem
                key={msg.id || index}
                message={msg}
                isStreaming={isStreaming && index === messages.length - 1 && msg.role === 'assistant'}
                onOpenImageModal={(img) => setActiveImage(img)}
              />
            ))
          )}
          {messages.length > 0 && <div className="messages-bottom-spacer" />}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Slide Image Modal */}
      {activeImage && (
        <ImageModal
          image={activeImage}
          onClose={() => setActiveImage(null)}
        />
      )}

      {/* Subject Search & Picker Modal */}
      <SubjectSelectorModal
        isOpen={showFilters}
        onClose={() => setShowFilters(false)}
        subject={subject}
        setSubject={setSubject}
        metadata={metadata}
      />

      {/* Bottom Input with Study Mode Toggle Bar */}
      {/* Bottom Input with Study Mode Toggle Bar (Isolated State for 0ms Latency) */}
      <InputBox
        attachments={attachments}
        setAttachments={setAttachments}
        onSend={onSend}
        isStreaming={isStreaming}
        onStop={onStop}
        subject={subject}
        onOpenFilters={() => setShowFilters(true)}
        studyMode={studyMode}
        setStudyMode={setStudyMode}
      />
    </main>
  );
}
