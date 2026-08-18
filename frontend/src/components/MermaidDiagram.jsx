import React, { useEffect, useRef, useState } from 'react';
import mermaid from 'mermaid';
import { Eye, Code2 } from 'lucide-react';

function getMermaidConfig() {
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark';

  return {
    startOnLoad: false,
    suppressErrorRendering: true,
    securityLevel: 'loose',
    fontFamily: 'Shantell Sans, Kalam, cursive, sans-serif',
    theme: isDark ? 'dark' : 'base',
    flowchart: {
      useMaxWidth: true,
      htmlLabels: true,
      curve: 'basis',
      nodeSpacing: 30,
      rankSpacing: 35,
      padding: 10
    },
    themeVariables: isDark ? {
      fontFamily: 'Shantell Sans, Kalam, cursive, sans-serif',
      fontSize: '12px',
      primaryColor: '#333120',
      primaryTextColor: '#f8fafc',
      primaryBorderColor: '#e2e8f0',
      lineColor: '#cbd5e1',
      secondaryColor: '#1e293b',
      tertiaryColor: '#1e293b',
      edgeLabelBackground: '#1e293b',
      nodeBorder: '#94a3b8',
      clusterBkg: '#1e293b',
      clusterBorder: '#64748b',
      mainBkg: '#0f172a',
      titleColor: '#f8fafc',
      stateLabelColor: '#f8fafc',
      stateBkg: '#1e293b',
      stateBorder: '#94a3b8'
    } : {
      fontFamily: 'Shantell Sans, Kalam, cursive, sans-serif',
      fontSize: '12px',
      primaryColor: '#fef08a',
      primaryTextColor: '#1c1917',
      primaryBorderColor: '#262320',
      lineColor: '#262320',
      secondaryColor: '#bae6fd',
      tertiaryColor: '#bbf7d0',
      edgeLabelBackground: '#ffffff',
      nodeBorder: '#262320',
      clusterBkg: '#fffefb',
      clusterBorder: '#262320',
      mainBkg: '#ffffff',
      titleColor: '#1c1917',
      stateLabelColor: '#1c1917',
      stateBkg: '#fef9c3',
      stateBorder: '#262320'
    }
  };
}

/**
 * Clean and normalize Mermaid chart code before rendering
 */
function cleanMermaidText(raw) {
  if (!raw) return '';
  let chart = raw.trim();

  // Strip code fences if present
  chart = chart
    .replace(/^```(mermaid|stateDiagram-v2|stateDiagram|flowchart|mindmap|graph)?\s*/i, '')
    .replace(/\s*```$/, '')
    .trim();

  // If no chart type is declared at the top, default to flowchart TD
  const hasType = /^(flowchart|graph|mindmap|sequenceDiagram|classDiagram|stateDiagram|stateDiagram-v2|erDiagram|gantt|pie|gitGraph)\b/i.test(chart);
  if (!hasType) {
    chart = 'flowchart TD\n' + chart;
  }

  return chart;
}

/**
 * Fix unquoted node labels with special characters like colons, slashes, or nested brackets
 */
function autoQuoteLabels(chart) {
  if (!/^(flowchart|graph)\b/i.test(chart)) {
    return chart;
  }

  return chart
    // 1. Rhombus / Decision nodes: B{Label with : or /} -> B{"Label with : or /"}
    .replace(/(\b[a-zA-Z0-9_-]+)\{([^{}"\r\n]+)\}/g, (match, id, text) => {
      const trimmed = text.trim();
      return `${id}{"${trimmed.replace(/"/g, "'")}"}`;
    })
    // 2. Stadium / Capsule nodes: A([Label]) -> A(["Label"])
    .replace(/(\b[a-zA-Z0-9_-]+)\(\[([^\[\]"\r\n]+)\]\)/g, (match, id, text) => {
      const trimmed = text.trim();
      return `${id}(["${trimmed.replace(/"/g, "'")}"])`;
    })
    // 3. Rectangular nodes: A[Label] -> A["Label"]
    .replace(/(\b[a-zA-Z0-9_-]+)\[([^\[\]"\r\n]+)\]/g, (match, id, text) => {
      const trimmed = text.trim();
      return `${id}["${trimmed.replace(/"/g, "'")}"]`;
    })
    // 4. Edge labels: -->|Label| -> -->|"Label"|
    .replace(/\|([^"|\r\n]+)\|/g, (match, text) => {
      const trimmed = text.trim();
      return `|"${trimmed.replace(/"/g, "'")}"|`;
    });
}

function processRenderedSvg(rawSvg) {
  if (!rawSvg) return '';
  return rawSvg.replace(/<svg\s+([^>]*?)>/i, (match, attrs) => {
    // Strip existing fixed width/height/max-width styles from root tag
    const cleanAttrs = attrs
      .replace(/style="[^"]*"/gi, '')
      .replace(/width="[^"]*"/gi, '')
      .replace(/height="[^"]*"/gi, '');
    return `<svg ${cleanAttrs} style="width: 100%; max-height: 360px; height: auto; display: block; margin: 0 auto;">`;
  });
}

export default function MermaidDiagram({ chart }) {
  const [svgContent, setSvgContent] = useState('');
  const [renderError, setRenderError] = useState(false);
  const [showCode, setShowCode] = useState(false);

  useEffect(() => {
    try {
      mermaid.initialize(getMermaidConfig());
    } catch (e) {
      console.warn('Failed to init mermaid:', e);
    }

    let isMounted = true;

    const renderChart = async () => {
      if (!chart || !chart.trim()) return;

      const baseCleaned = cleanMermaidText(chart);
      if (baseCleaned.length < 5) return;

      // Pass 1: Try rendering cleaned code
      const uniqueId1 = 'mermaid_' + Math.random().toString(36).replace(/[^a-z0-9]/g, '').substring(0, 8);
      const tempContainer = document.createElement('div');
      tempContainer.id = 'container_' + uniqueId1;
      tempContainer.style.position = 'fixed';
      tempContainer.style.left = '-9999px';
      tempContainer.style.top = '-9999px';
      tempContainer.style.visibility = 'hidden';
      document.body.appendChild(tempContainer);

      try {
        const { svg } = await mermaid.render(uniqueId1, baseCleaned, tempContainer);
        if (tempContainer.parentNode) {
          tempContainer.parentNode.removeChild(tempContainer);
        }
        if (isMounted) {
          setSvgContent(processRenderedSvg(svg));
          setRenderError(false);
        }
        return;
      } catch (err1) {
        // Pass 2: Try auto-quoting labels
        try {
          const autoQuoted = autoQuoteLabels(baseCleaned);
          const uniqueId2 = 'mermaid_' + Math.random().toString(36).replace(/[^a-z0-9]/g, '').substring(0, 8);
          const { svg } = await mermaid.render(uniqueId2, autoQuoted, tempContainer);
          if (tempContainer.parentNode) {
            tempContainer.parentNode.removeChild(tempContainer);
          }
          if (isMounted) {
            setSvgContent(processRenderedSvg(svg));
            setRenderError(false);
          }
          return;
        } catch (err2) {
          if (tempContainer.parentNode) {
            tempContainer.parentNode.removeChild(tempContainer);
          }
          console.warn('[Mermaid] Rendering failed for diagram:', baseCleaned, err2);
          if (isMounted) {
            setRenderError(true);
          }
        }
      }
    };

    renderChart();

    return () => {
      isMounted = false;
    };
  }, [chart]);

  if (renderError || !svgContent) {
    return (
      <div className="mermaid-fallback-box">
        <div className="mermaid-fallback-title">
          <span>📊 Flowchart / Process Diagram:</span>
        </div>
        <pre className="mermaid-fallback-code">
          <code>{cleanMermaidText(chart)}</code>
        </pre>
      </div>
    );
  }

  return (
    <div className="mermaid-diagram-card">
      <div className="mermaid-card-header">
        <span className="mermaid-card-badge">📊 Flowchart & Mindmap</span>
        <button
          className="mermaid-toggle-code-btn"
          onClick={() => setShowCode(!showCode)}
          title={showCode ? "Show visual diagram" : "View diagram source code"}
        >
          {showCode ? <Eye size={12} /> : <Code2 size={12} />}
          <span>{showCode ? 'Diagram' : 'Code'}</span>
        </button>
      </div>
      {showCode ? (
        <pre className="mermaid-fallback-code" style={{ margin: '8px 12px 12px 12px', width: '100%' }}>
          <code>{cleanMermaidText(chart)}</code>
        </pre>
      ) : (
        <div
          className="mermaid-svg-viewport"
          dangerouslySetInnerHTML={{ __html: svgContent }}
        />
      )}
    </div>
  );
}
