import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import 'katex/dist/katex.min.css';
import { Copy, Check, FileText, Code2, ZoomIn } from 'lucide-react';
import MermaidDiagram from './MermaidDiagram';

function CodeBlock({ language, value }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(value);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="code-block-wrapper">
      <div className="code-block-header">
        <span className="code-lang-label">
          <Code2 size={12} />
          {language || 'code'}
        </span>
        <button className="code-copy-btn" onClick={handleCopy}>
          {copied ? <Check size={12} color="#16a34a" /> : <Copy size={12} />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>
      <pre className="code-content">
        <code>{value}</code>
      </pre>
    </div>
  );
}

function preprocessMarkdown(content) {
  if (!content) return '';
  let text = String(content);

  // 1. Normalize line endings
  text = text.replace(/\r\n/g, '\n');

  // 2. Fix glued closing $$ to words (e.g. \end{cases}$$ It looks intimidating)
  text = text.replace(/(\$\$)\s*([A-Za-z0-9#\`\*\>])/g, (match, dollar, nextChar) => {
    return '$$\n\n' + nextChar;
  });

  // 3. Fix missing opening $$ for LaTeX environments (\begin{cases...})
  const envRegex = /\\begin\{(cases|aligned|matrix|pmatrix|bmatrix|array|split|equation)\}/g;
  let match;
  let buffer = '';
  let lastIndex = 0;

  while ((match = envRegex.exec(text)) !== null) {
    const matchPos = match.index;
    const textBefore = text.slice(0, matchPos);
    const dollarMatches = textBefore.match(/\$\$/g);
    const dollarCount = dollarMatches ? dollarMatches.length : 0;
    const isInsideMath = (dollarCount % 2 === 1);

    if (!isInsideMath) {
      buffer += text.slice(lastIndex, matchPos);
      buffer += '\n\n$$\n' + match[0];
      lastIndex = matchPos + match[0].length;
    }
  }
  buffer += text.slice(lastIndex);
  text = buffer;

  // 4. Fix missing closing $$ for LaTeX environments (\end{cases...})
  const endRegex = /\\end\{(cases|aligned|matrix|pmatrix|bmatrix|array|split|equation)\}/g;
  let endMatch;
  let endBuffer = '';
  let endLastIndex = 0;

  while ((endMatch = endRegex.exec(text)) !== null) {
    const endPos = endMatch.index + endMatch[0].length;
    const textAfter = text.slice(endPos, endPos + 30);
    const textBefore = text.slice(0, endPos);
    const dollarMatches = textBefore.match(/\$\$/g);
    const dollarCount = dollarMatches ? dollarMatches.length : 0;
    const isInsideMath = (dollarCount % 2 === 1);

    if (isInsideMath && !textAfter.trim().startsWith('$$')) {
      endBuffer += text.slice(endLastIndex, endPos);
      endBuffer += '\n$$\n\n';
      endLastIndex = endPos;
    }
  }
  endBuffer += text.slice(endLastIndex);
  text = endBuffer;

  // 5. Clean up leading '>' blockquote markers inside $$ ... $$ blocks so KaTeX does not parse them as mathematical operators
  text = text.replace(/\$\$([\s\S]*?)\$\$/g, (match, mathContent) => {
    const cleaned = mathContent.replace(/^[ \t]*>[ \t]?/gm, '');
    return '$$\n' + cleaned.trim() + '\n$$';
  });

  // 6. Ensure display math $$ has blank lines around it for remark-math
  text = text.replace(/([^\n])\s*\$\$/g, (match, p1) => p1 + '\n\n$$');
  text = text.replace(/\$\$\s*([^\n])/g, (match, p1) => '$$\n\n' + p1);

  // 6. Ensure ``` code fences start on a clean new line
  text = text.replace(/([^\n])\s*```(\w*)/g, (match, prefix, lang) => {
    return prefix + '\n\n```' + lang;
  });
  
  // 7. Ensure closing ``` is followed by newline if attached to text
  text = text.replace(/(\n```[^\n]*\n[\s\S]*?\n```)\s*([A-Za-z0-9#\*\>])/g, (match, codeFence, nextChar) => {
    return codeFence + '\n\n' + nextChar;
  });

  // 8. Ensure markdown headers (###) always start on a clean new line
  text = text.replace(/(^|[^\n])\s*(#{1,6}\s+[^\n]+)/g, (match, prefix, header) => {
    return prefix ? prefix + '\n\n' + header : header;
  });

  // 9. Normalize indented list items inside blockquotes to prevent accidental 4-space code block triggers
  text = text.replace(/^([ \t]*>[ \t]*)[ \t]{2,}([-*+]|\d+\.)/gm, '$1 $2');

  // 10. Ensure blockquote blocks are preceded by a blank line, without breaking consecutive > lines
  const lines = text.split('\n');
  const processedLines = [];
  for (let i = 0; i < lines.length; i++) {
    const curr = lines[i];
    const isQuote = /^[ \t]*>[ \t]/.test(curr);
    const prev = i > 0 ? lines[i - 1] : '';
    const wasQuote = /^[ \t]*>[ \t]/.test(prev);
    const wasBlank = prev.trim() === '';

    if (isQuote && !wasQuote && !wasBlank) {
      processedLines.push('');
    }
    processedLines.push(curr);
  }
  text = processedLines.join('\n');

  return text;
}

const MessageItem = React.memo(function MessageItem({ message, isStreaming, onOpenImageModal }) {
  const [copied, setCopied] = useState(false);
  const isUser = message.role === 'user';

  const processedContent = React.useMemo(() => {
    return preprocessMarkdown(message.content);
  }, [message.content]);

  const handleCopy = () => {
    if (!message.content) return;
    navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={`message-row ${isUser ? 'user-row' : 'assistant-row'}`}>
      {/* Avatar */}
      <div className={`avatar ${isUser ? 'user-avatar' : 'assistant-avatar'}`}>
        {isUser ? (
          <span className="avatar-letter">U</span>
        ) : (
          <img src="/assets/happy-dog.svg" alt="Shiro Dog Avatar" className="assistant-avatar-dog-img" />
        )}
      </div>

      {/* Message Bubble */}
      {isUser ? (
        <div className="user-bubble">
          {/* Render User Attached Images/Files as Compact Cards */}
          {message.attachments && message.attachments.length > 0 && (
            <div className="user-attachments-grid">
              {message.attachments.map((att, idx) => {
                const isImage = att.mimeType?.startsWith('image/') || att.localUrl || att.fileUri?.match(/\.(png|jpg|jpeg|webp)$/i);
                const imgSrc = att.localUrl || (att.fileUri ? `http://127.0.0.1:8001/images/${att.fileUri.split('/').pop()}` : null);

                if (isImage && imgSrc) {
                  return (
                    <div
                      key={idx}
                      className="user-attachment-image-card"
                      onClick={() => onOpenImageModal && onOpenImageModal({ url: imgSrc, caption: att.displayName || 'Uploaded Image', subject: 'User Upload' })}
                      title="Click to zoom image"
                    >
                      <img src={imgSrc} alt={att.displayName || 'Uploaded Attachment'} className="user-attachment-thumb" />
                      <div className="user-attachment-overlay">
                        <ZoomIn size={14} color="#ffffff" />
                        <span>Zoom</span>
                      </div>
                    </div>
                  );
                }

                return (
                  <div key={idx} className="user-attachment-file-pill">
                    <FileText size={13} />
                    <span className="user-attachment-filename" title={att.displayName}>
                      {att.displayName || 'Document'}
                    </span>
                  </div>
                );
              })}
            </div>
          )}

          {message.content && (
            <div className="user-text-content">{message.content}</div>
          )}
        </div>
      ) : (
        <div className="assistant-bubble">
          {/* Header */}
          <div className="assistant-header">
            <span className="assistant-name">Shiro</span>
          </div>

          {/* Body with Markdown & KaTeX */}
          <div className="assistant-body">
            {processedContent ? (
              <div className="markdown-body">
                <ReactMarkdown
                  remarkPlugins={[remarkGfm, remarkMath]}
                  rehypePlugins={[[rehypeKatex, { output: 'htmlAndMathml', throwOnError: false, strict: false }]]}
                  components={{
                    pre({ children }) {
                      return <>{children}</>;
                    },
                    code({ node, inline, className, children, ...props }) {
                      const match = /language-(\w+)/.exec(className || '');
                      const codeText = String(children).replace(/\n$/, '');

                      if (!inline && match && match[1] === 'mermaid') {
                        return <MermaidDiagram chart={codeText} />;
                      }

                      return !inline ? (
                        <CodeBlock language={match ? match[1] : ''} value={codeText} />
                      ) : (
                        <code className="inline-code" {...props}>
                          {children}
                        </code>
                      );
                    },
                    table({ children }) {
                      return (
                        <div className="table-wrapper">
                          <table>{children}</table>
                        </div>
                      );
                    },
                    img({ src, alt }) {
                      return (
                        <img
                          src={src}
                          alt={alt}
                          className="markdown-embedded-img"
                          onClick={() => onOpenImageModal && onOpenImageModal({ url: src, caption: alt, subject: 'Course Material' })}
                          title="Click to view full image"
                        />
                      );
                    }
                  }}
                >
                  {processedContent}
                </ReactMarkdown>
              </div>
            ) : (
              isStreaming && (
                <span style={{ color: 'var(--text-muted)', fontStyle: 'italic', fontFamily: 'var(--font-doodle)', fontSize: '15px' }}>
                  ✏️ Sketching explanation and notes...
                </span>
              )
            )}
            {isStreaming && <span className="streaming-cursor" />}
          </div>

          {/* Clean Action Bar */}
          {message.content && (
            <div className="assistant-footer-bar">
              <div className="assistant-footer-controls">
                <button
                  className="theme-toggle-btn"
                  style={{ fontSize: '11.5px', padding: '3px 8px' }}
                  onClick={handleCopy}
                  title="Copy explanation to clipboard"
                >
                  {copied ? <Check size={12} color="#16a34a" /> : <Copy size={12} />}
                  <span>{copied ? 'Copied' : 'Copy'}</span>
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

export default MessageItem;
