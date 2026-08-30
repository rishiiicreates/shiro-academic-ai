import React from 'react';
import { RotateCcw, AlertTriangle } from 'lucide-react';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('Shiro ErrorBoundary caught an error:', error, errorInfo);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
    if (this.props.onReset) {
      this.props.onReset();
    } else {
      window.location.reload();
    }
  };

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
          width: '100vw',
          backgroundColor: 'var(--bg-app, #fcfaf6)',
          color: 'var(--text-primary, #262320)',
          fontFamily: 'var(--font-body, -apple-system, sans-serif)',
          padding: '24px',
          boxSizing: 'border-box'
        }}>
          <div style={{
            maxWidth: '480px',
            width: '100%',
            backgroundColor: 'var(--bg-surface, #ffffff)',
            border: '2px solid var(--border-color, #262320)',
            borderRadius: '16px',
            boxShadow: 'var(--ink-shadow, 3px 3px 0px #262320)',
            padding: '32px 24px',
            textAlign: 'center',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: '16px'
          }}>
            <img
              src="/assets/sleeping-dog.svg"
              alt="Shiro Resting"
              style={{ width: '80px', height: '80px', objectFit: 'contain' }}
              onError={(e) => { e.target.style.display = 'none'; }}
            />
            
            <div>
              <h2 style={{
                fontFamily: 'var(--font-doodle, cursive)',
                fontSize: '22px',
                margin: '0 0 6px 0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px'
              }}>
                <AlertTriangle size={20} color="#eab308" />
                Oops! Something went off track
              </h2>
              <p style={{
                fontSize: '14px',
                color: 'var(--text-secondary, #524c46)',
                margin: 0,
                lineHeight: 1.5
              }}>
                Shiro hit an unexpected bump while rendering. Don't worry, your past chats are safely stored.
              </p>
            </div>

            {this.state.error && (
              <div style={{
                width: '100%',
                backgroundColor: 'var(--bg-subtle, #ede6d8)',
                borderRadius: '8px',
                padding: '10px 12px',
                fontSize: '12px',
                fontFamily: 'var(--font-mono, monospace)',
                color: 'var(--text-muted, #827a72)',
                textAlign: 'left',
                overflowX: 'auto',
                boxSizing: 'border-box'
              }}>
                {this.state.error.message || String(this.state.error)}
              </div>
            )}

            <button
              onClick={this.handleReset}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                backgroundColor: 'var(--accent-warm, #262320)',
                color: 'var(--bg-app, #fcfaf6)',
                border: '2px solid var(--border-color, #262320)',
                borderRadius: '10px',
                padding: '10px 20px',
                fontSize: '14px',
                fontWeight: 600,
                cursor: 'pointer',
                boxShadow: 'var(--ink-shadow-sm, 2px 2px 0px #262320)'
              }}
            >
              <RotateCcw size={15} />
              <span>Restart Study Session</span>
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
