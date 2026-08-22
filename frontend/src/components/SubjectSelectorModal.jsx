import React, { useState, useMemo, useEffect } from 'react';
import { 
  X, 
  Search, 
  Check, 
  BookOpen, 
  RotateCcw,
  Sparkles
} from 'lucide-react';
import srmCurriculumData from '../data/srm_curriculum.json';

const SEMESTERS = ['All', 'Sem 1', 'Sem 2', 'Sem 3', 'Sem 4', 'Sem 5', 'Sem 6', 'Sem 7', 'Sem 8'];

export default function SubjectSelectorModal({
  isOpen,
  onClose,
  subject,
  setSubject,
  metadata
}) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSem, setSelectedSem] = useState('All');

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

  // Extract structured list of subjects with semester tags
  const subjectList = useMemo(() => {
    const list = [];
    const seen = new Set();
    
    if (srmCurriculumData) {
      Object.entries(srmCurriculumData).forEach(([semName, subs]) => {
        subs.forEach((s) => {
          if (s.name && !seen.has(s.name.trim())) {
            seen.add(s.name.trim());
            list.push({
              name: s.name.trim(),
              semester: semName,
              categories: s.categories || []
            });
          }
        });
      });
    }

    if (metadata?.subjects) {
      metadata.subjects.forEach((subName) => {
        const cleanName = subName.trim();
        if (!seen.has(cleanName)) {
          seen.add(cleanName);
          list.push({
            name: cleanName,
            semester: 'All',
            categories: ['Notes', 'PYQs']
          });
        }
      });
    }

    return list.sort((a, b) => a.name.localeCompare(b.name));
  }, [metadata]);

  // Filtered subjects based on search query & selected semester tab
  const filteredSubjects = useMemo(() => {
    let result = subjectList;

    if (selectedSem !== 'All') {
      const semNum = selectedSem.replace('Sem ', 'Semester ');
      result = result.filter((s) => s.semester === semNum || s.semester === 'All');
    }

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter((s) => 
        s.name.toLowerCase().includes(q) || 
        s.semester.toLowerCase().includes(q)
      );
    }

    return result;
  }, [searchQuery, selectedSem, subjectList]);

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
    setSelectedSem('All');
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
              <h2 className="subject-picker-title">Select Focus Subject</h2>
              <p className="subject-picker-subtitle">
                Target Shiro's dense vector search and exam questions on a specific course.
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
              placeholder="Search by subject name or keyword (e.g. OS, DAA, DBMS, Calculus, AI...)"
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

          {/* Semester Filter Tabs */}
          <div className="semester-tabs-row">
            {SEMESTERS.map((sem) => (
              <button
                key={sem}
                type="button"
                className={`sem-filter-pill ${selectedSem === sem ? 'active' : ''}`}
                onClick={() => setSelectedSem(sem)}
              >
                {sem}
              </button>
            ))}
          </div>
        </div>

        {/* Subjects List Grid */}
        <div className="subject-picker-body">
          {filteredSubjects.length === 0 ? (
            <div className="subject-picker-empty">
              <BookOpen size={30} color="var(--text-muted)" />
              <p>No subjects found matching "{searchQuery}"</p>
              <button className="empty-clear-filter-btn" onClick={handleClear}>
                Reset Search Filters
              </button>
            </div>
          ) : (
            <div className="subject-picker-grid">
              {filteredSubjects.map((sub) => {
                const isSelected = subject === sub.name;
                return (
                  <button
                    key={sub.name}
                    type="button"
                    className={`subject-picker-card ${isSelected ? 'selected' : ''}`}
                    onClick={() => handleSelectSubject(sub.name)}
                  >
                    <div className="subject-picker-card-inner">
                      <div className="subject-card-details">
                        <span className="subject-picker-name" title={sub.name}>
                          {sub.name}
                        </span>
                        {sub.semester !== 'All' && (
                          <span className="subject-card-sem-tag">{sub.semester}</span>
                        )}
                      </div>
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
              <span className="no-subject-text">
                <Sparkles size={13} style={{ display: 'inline', marginRight: 4 }} />
                Searching across all 68+ SRM course subjects
              </span>
            )}
          </div>

          <div className="subject-picker-footer-right">
            <button type="button" className="secondary-picker-btn" onClick={onClose}>
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
