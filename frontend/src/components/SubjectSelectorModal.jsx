import React, { useState, useMemo, useEffect } from 'react';
import { 
  X, 
  Search, 
  Check, 
  BookOpen, 
  RotateCcw
} from 'lucide-react';
import srmCurriculumData from '../data/srm_curriculum.json';

export default function SubjectSelectorModal({
  isOpen,
  onClose,
  subject,
  setSubject,
  metadata
}) {
  const [searchQuery, setSearchQuery] = useState('');

  // Lock body scroll when modal is open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [isOpen]);

  // Extract unique subject names from curriculum data & metadata
  const allSubjects = useMemo(() => {
    const set = new Set();
    
    if (srmCurriculumData) {
      Object.values(srmCurriculumData).forEach((subs) => {
        subs.forEach((s) => {
          if (s.name) set.add(s.name.trim());
        });
      });
    }

    if (metadata?.subjects) {
      metadata.subjects.forEach((s) => set.add(s.trim()));
    }

    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [metadata]);

  // Filtered subjects based on search query
  const filteredSubjects = useMemo(() => {
    if (!searchQuery.trim()) {
      return allSubjects;
    }
    const q = searchQuery.toLowerCase().trim();
    return allSubjects.filter((s) => s.toLowerCase().includes(q));
  }, [searchQuery, allSubjects]);

  if (!isOpen) return null;

  const handleSelectSubject = (subName) => {
    if (subject === subName) {
      setSubject('');
    } else {
      setSubject(subName);
    }
    onClose();
  };

  const handleClear = () => {
    setSubject('');
    setSearchQuery('');
  };

  return (
    <div className="modal-overlay modal-backdrop" onClick={onClose}>
      <div className="subject-picker-modal" onClick={(e) => e.stopPropagation()}>
        {/* Modal Header */}
        <div className="subject-picker-header">
          <div className="subject-picker-header-left">
            <div className="subject-picker-icon">
              <BookOpen size={20} />
            </div>
            <div>
              <h2 className="subject-picker-title">Select SRM Course Subject</h2>
              <p className="subject-picker-subtitle">
                Focus Shiro's knowledge base and study materials on a specific course.
              </p>
            </div>
          </div>
          <button className="subject-picker-close" onClick={onClose} title="Close (Esc)">
            <X size={18} />
          </button>
        </div>

        {/* Prominent Search Bar */}
        <div className="subject-picker-search-container">
          <div className="subject-picker-search-bar">
            <Search size={16} className="search-icon" />
            <input
              type="text"
              className="subject-picker-search-input"
              placeholder="Search subjects (e.g. Operating Systems, DAA, Linear Algebra, AI, ML...)"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              autoFocus
            />
            {searchQuery && (
              <button className="clear-search-btn" onClick={() => setSearchQuery('')}>
                <X size={14} />
              </button>
            )}
          </div>
        </div>

        {/* Subjects List Grid */}
        <div className="subject-picker-body">
          {filteredSubjects.length === 0 ? (
            <div className="subject-picker-empty">
              <BookOpen size={30} color="var(--text-muted)" />
              <p>No subjects found matching "{searchQuery}"</p>
            </div>
          ) : (
            <div className="subject-picker-grid">
              {filteredSubjects.map((subName) => {
                const isSelected = subject === subName;
                return (
                  <button
                    key={subName}
                    className={`subject-picker-card ${isSelected ? 'selected' : ''}`}
                    onClick={() => handleSelectSubject(subName)}
                  >
                    <div className="subject-picker-card-inner">
                      <span className="subject-picker-name" title={subName}>
                        {subName}
                      </span>
                      {isSelected && (
                        <div className="subject-selected-check">
                          <Check size={14} />
                        </div>
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="subject-picker-footer">
          <div className="subject-picker-footer-left">
            {subject ? (
              <div className="subject-active-badge">
                <span className="active-label">Selected:</span>
                <span className="active-value">{subject}</span>
                <button className="clear-subject-pill-btn" onClick={handleClear} title="Clear subject filter">
                  <RotateCcw size={11} />
                  <span>Clear</span>
                </button>
              </div>
            ) : (
              <span className="no-subject-text">Searching across all SRM subjects</span>
            )}
          </div>

          <div className="subject-picker-footer-right">
            <button className="secondary-picker-btn" onClick={onClose}>
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
