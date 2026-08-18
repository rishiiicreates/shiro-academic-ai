import React, { useRef, useEffect, useState } from 'react';
import { 
  ArrowUp, 
  Square, 
  BookOpen,
  FileText, 
  Target, 
  Sparkles, 
  Paperclip, 
  X, 
  Loader2, 
  GraduationCap 
} from 'lucide-react';
import { uploadFile } from '../services/api';

const STUDY_MODES = [
  {
    id: 'all',
    label: 'All Materials',
    icon: Sparkles,
    dogIcon: '/assets/happy-unicorn-dog.svg',
    tooltip: 'Standard academic assistant across all course notes and materials'
  },
  {
    id: 'notes',
    label: 'Lecture Notes',
    icon: FileText,
    dogIcon: '/assets/happy-dog.svg',
    tooltip: 'Focus strictly on official SRM lecture notes, slides, and syllabus definitions'
  },
  {
    id: 'pyqs',
    label: 'Exam PYQs',
    icon: Target,
    dogIcon: '/assets/smiling-dog.svg',
    tooltip: 'Topic-wise authentic past year questions from SRM examination database'
  },
  {
    id: 'learn_basics',
    label: 'Learn from Basics',
    icon: GraduationCap,
    dogIcon: '/assets/astronaut-dog.svg',
    tooltip: 'Interactive AI tutor: Step-by-step from absolute ground zero with intuition & check-ins'
  }
];

export default function InputBox({
  onSend,
  isStreaming,
  onStop,
  subject,
  onOpenFilters,
  studyMode,
  setStudyMode,
  attachments,
  setAttachments
}) {
  const [text, setText] = useState('');
  const textareaRef = useRef(null);
  const fileInputRef = useRef(null);
  const [isUploading, setIsUploading] = useState(false);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 180)}px`;
    }
  }, [text]);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!isStreaming && !isUploading && (text.trim() || attachments.length > 0)) {
        const msg = text.trim();
        setText('');
        onSend(msg);
      }
    }
  };

  const handleSendClick = () => {
    if (!isStreaming && !isUploading && (text.trim() || attachments.length > 0)) {
      const msg = text.trim();
      setText('');
      onSend(msg);
    }
  };

  const handleFileSelect = async (e) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    setIsUploading(true);
    for (const file of files) {
      const localUrl = file.type.startsWith('image/') ? URL.createObjectURL(file) : null;
      try {
        const uploaded = await uploadFile(file);
        setAttachments((prev) => [
          ...prev,
          {
            fileUri: uploaded.fileUri,
            fileId: uploaded.fileId,
            mimeType: uploaded.mimeType,
            displayName: uploaded.displayName || file.name,
            sizeBytes: uploaded.sizeBytes || file.size,
            localUrl
          }
        ]);
      } catch (err) {
        console.error('File upload failed:', err);
        alert(`Failed to upload ${file.name}: ${err.message}`);
      }
    }
    setIsUploading(false);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleRemoveAttachment = (index) => {
    setAttachments((prev) => prev.filter((_, idx) => idx !== index));
  };

  const canSend = !isStreaming && !isUploading && (text.trim() || attachments.length > 0);

  return (
    <div className="input-section">
      {/* Study Mode Switcher Toggle Bar */}
      <div className="study-mode-toggle-bar">
        <div className="study-modes-group">
          {STUDY_MODES.map((mode) => {
            const Icon = mode.icon;
            const isActive = (studyMode || 'all') === mode.id;

            return (
              <button
                key={mode.id}
                type="button"
                className={`study-mode-btn ${isActive ? 'active' : ''} ${mode.id}`}
                onClick={() => setStudyMode(mode.id)}
                title={mode.tooltip}
              >
                <img src={mode.dogIcon} alt={mode.label} className="study-mode-dog-icon" />
                <span>{mode.label}</span>
              </button>
            );
          })}
        </div>

        {/* Subject Quick Pill */}
        <button
          type="button"
          className={`subject-quick-pill ${subject ? 'has-subject' : ''}`}
          onClick={onOpenFilters}
          title="Select or change focus subject"
        >
          <BookOpen size={12} />
          <span>{subject ? subject : 'All Subjects'}</span>
        </button>
      </div>

      {/* Main Input Box & Solid Bottom Backdrop */}
      <div className="input-box-wrapper">
        <div className="input-container">
          {/* Attached Files Preview Bar */}
          {attachments.length > 0 && (
            <div className="attachment-previews-list">
              {attachments.map((att, idx) => {
                const isImage = att.mimeType?.startsWith('image/') || att.localUrl;
                const sizeKb = Math.round((att.sizeBytes || 0) / 1024);

                return (
                  <div key={idx} className="attachment-chip">
                    {isImage && att.localUrl ? (
                      <img src={att.localUrl} alt={att.displayName} className="attachment-chip-thumb" />
                    ) : (
                      <FileText size={14} color="var(--accent-warm)" />
                    )}
                    <span className="attachment-chip-name" title={att.displayName}>
                      {att.displayName} {sizeKb > 0 ? `(${sizeKb} KB)` : ''}
                    </span>
                    <button
                      className="attachment-remove-btn"
                      onClick={() => handleRemoveAttachment(idx)}
                      title="Remove file"
                    >
                      <X size={12} />
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          <textarea
            ref={textareaRef}
            className="input-textarea"
            rows={1}
            placeholder={
              studyMode === 'learn_basics'
                ? 'Tell Shiro what concept or chapter you want to start learning from absolute basics...'
                : studyMode === 'pyqs'
                ? 'Ask for authentic SRM PYQs to solve on any topic (e.g. CPU Scheduling, Fourier Series, Matrix Inverse)...'
                : studyMode === 'notes'
                ? 'Ask questions from official SRM lecture notes and syllabus...'
                : 'Ask Shiro about SRM courses, math, code, algorithms, or exam concepts...'
            }
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isStreaming}
          />

          <div className="input-toolbar">
            <div className="input-badges-left">
              {/* File Attachment Button */}
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept="image/*,application/pdf,.docx,.pptx,.txt"
                style={{ display: 'none' }}
                onChange={handleFileSelect}
              />

              <button
                className="attach-file-btn"
                onClick={() => fileInputRef.current?.click()}
                disabled={isStreaming || isUploading}
                title="Attach student slide image, diagram, or PDF notes"
              >
                {isUploading ? <Loader2 size={14} className="spin-animate" /> : <Paperclip size={14} />}
                <span>{isUploading ? 'Uploading...' : 'Attach'}</span>
              </button>
            </div>

            <div className="input-controls-right">
              {isStreaming ? (
                <button className="stop-btn" onClick={onStop} title="Stop generation">
                  <Square size={14} fill="currentColor" />
                </button>
              ) : (
                <button
                  className="send-btn"
                  disabled={!canSend}
                  onClick={handleSendClick}
                  title="Send inquiry to Shiro (Enter)"
                >
                  <ArrowUp size={18} />
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Footer Hint Text */}
        <div className="input-hint">
          {studyMode === 'learn_basics' ? (
            <span>🚀 <strong>Learn from Basics:</strong> Shiro teaches step-by-step from zero with intuition, analogies, and conceptual check-ins.</span>
          ) : studyMode === 'pyqs' ? (
            <span>🎯 <strong>Exam PYQs:</strong> Pulls authentic past exam questions directly from SRM's 17,000+ question bank database topic-wise.</span>
          ) : studyMode === 'notes' ? (
            <span>📝 <strong>Lecture Notes:</strong> Shiro answers strictly using official SRM lecture materials & curriculum.</span>
          ) : (
            <span>Shiro grounds technical answers in SRMIST coursework and reasons over your attached files.</span>
          )}
        </div>
      </div>
    </div>
  );
}
