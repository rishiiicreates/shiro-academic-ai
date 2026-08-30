import React, { useRef, useEffect, useState } from 'react';
import { PanelLeft, Plus, Sun, Moon } from 'lucide-react';
import MessageItem from './MessageItem';
import InputBox from './InputBox';
import SubjectSelectorModal from './SubjectSelectorModal';
import ImageModal from './ImageModal';

export default function ChatArea({
  thread,
  messages,
  isStreaming,
  attachments,
  setAttachments,
  onSend,
  onStop,
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
  const messagesContainerRef = useRef(null);
  const messagesEndRef = useRef(null);
  const userScrolledUpRef = useRef(false);

  const handleContainerScroll = () => {
    if (!messagesContainerRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = messagesContainerRef.current;
    const distanceFromBottom = scrollHeight - (scrollTop + clientHeight);
    userScrolledUpRef.current = distanceFromBottom > 100;
  };

  const scrollToBottom = () => {
    if (userScrolledUpRef.current) return;
    requestAnimationFrame(() => {
      if (messagesContainerRef.current) {
        messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight;
      }
    });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isStreaming]);

  return (
    <main className="main-chat">
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

          <div className="thread-header-info">
            <div className="current-thread-title desktop-only">
              {thread?.title || 'Shiro'}
            </div>
            {subject && (
              <button 
                type="button"
                className="active-filter-indicator"
                onClick={() => setShowFilters(true)}
                title="Click to change or clear focus subject"
              >
                Focus: {subject}
              </button>
            )}
          </div>
        </div>

        <div className="nav-right">
          <button
            className="nav-action-btn mobile-only"
            onClick={onNewChat}
            title="Start new study session"
          >
            <Plus size={15} color="var(--accent-warm)" />
            <span>New</span>
          </button>

          <button
            className="nav-theme-btn mobile-only"
            onClick={onToggleTheme}
            title={theme === 'dark' ? "Switch to light mode" : "Switch to dark mode"}
          >
            {theme === 'dark' ? <Sun size={15} color="#f59e0b" /> : <Moon size={15} color="#64748b" />}
          </button>
        </div>
      </header>

      <div className="messages-container" ref={messagesContainerRef} onScroll={handleContainerScroll}>
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

      {activeImage && (
        <ImageModal
          image={activeImage}
          onClose={() => setActiveImage(null)}
        />
      )}

      <SubjectSelectorModal
        isOpen={showFilters}
        onClose={() => setShowFilters(false)}
        subject={subject}
        setSubject={setSubject}
        metadata={metadata}
      />

      <InputBox
        attachments={attachments}
        setAttachments={setAttachments}
        onSend={onSend}
        isStreaming={isStreaming}
        onStop={onStop}
        subject={subject}
        setSubject={setSubject}
        onOpenFilters={() => setShowFilters(true)}
        onClearSubject={() => setSubject('')}
        studyMode={studyMode}
        setStudyMode={setStudyMode}
      />
    </main>
  );
}
