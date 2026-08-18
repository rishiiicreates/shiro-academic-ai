import React from 'react';
import { Plus, Trash2, Sun, Moon, Database } from 'lucide-react';

export default function Sidebar({
  isOpen,
  threads,
  activeThreadId,
  onSelectThread,
  onNewChat,
  onDeleteThread,
  theme,
  onToggleTheme,
  metadata
}) {
  const totalChunks = metadata?.total_chunks || 95672;

  return (
    <aside className={`sidebar ${isOpen ? 'open' : 'collapsed'}`}>
      <div className="sidebar-header">
        <div className="brand-badge">
          <div className="brand-icon">
            <img src="/assets/smiling-dog.svg" alt="Shiro Dog" className="brand-dog-avatar" />
          </div>
          <div>
            <div className="brand-title">Shiro</div>
            <div className="brand-sub">
              Built by: <a href="https://rishiicreates.vercel.app/" target="_blank" rel="noopener noreferrer" className="brand-author-link">rishiicreates</a>
            </div>
          </div>
        </div>
      </div>

      <button className="new-chat-btn" onClick={onNewChat}>
        <Plus size={16} color="var(--accent-warm)" />
        <span>New Study Session</span>
      </button>

      <div className="sidebar-threads-section">
        <div className="threads-group-title">Recent Study Sessions ({threads.length}/20)</div>
        {threads.length === 0 ? (
          <div style={{ padding: '16px 8px', fontSize: '13px', color: 'var(--text-muted)', textAlign: 'center', fontFamily: 'var(--font-body)' }}>
            No sessions yet. Ask Shiro anything to begin!
          </div>
        ) : (
          <div className="thread-list">
            {threads.map((t) => (
              <div
                key={t.id}
                className={`thread-item ${t.id === activeThreadId ? 'active' : ''}`}
                onClick={() => onSelectThread(t.id)}
              >
                <span className="thread-item-title">{t.title || 'Untitled Session'}</span>
                <button
                  className="thread-delete-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDeleteThread(t.id);
                  }}
                  title="Delete session"
                >
                  <Trash2 size={13} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="sidebar-footer">
        <div className="corpus-stat-badge" title="SRM University Indexed Corpus">
          <Database size={12} color="var(--accent-warm)" />
          <span>{totalChunks.toLocaleString()} SRM Chunks</span>
        </div>

        <button className="theme-toggle-btn" onClick={onToggleTheme} title="Toggle theme">
          {theme === 'dark' ? <Sun size={13} /> : <Moon size={13} />}
          <span>{theme === 'dark' ? 'Light' : 'Dark'}</span>
        </button>
      </div>
    </aside>
  );
}
