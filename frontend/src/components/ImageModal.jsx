import React from 'react';
import { X, ExternalLink, Image as ImageIcon } from 'lucide-react';

export default function ImageModal({ image, onClose }) {
  if (!image) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="image-modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ImageIcon size={18} color="var(--accent-warm)" />
            <span>Extracted SRM Lecture Figure</span>
          </div>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">
            <X size={18} />
          </button>
        </div>

        <div className="image-modal-body">
          <div className="image-preview-wrapper">
            <img src={image.url} alt={image.caption} />
          </div>
          <div className="image-modal-caption">
            <strong>Source:</strong> {image.caption} • <em>{image.subject}</em>
            <a
              href={image.url}
              target="_blank"
              rel="noopener noreferrer"
              className="image-open-external"
            >
              <ExternalLink size={13} />
              <span>Open full size</span>
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}
