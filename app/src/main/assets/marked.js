/*
 * Bundled Markdown parser for the Android preview WebView.
 *
 * This file previously contained a modified legacy Marked build whose
 * synchronous render function wrote directly into #content and returned
 * undefined. The preview bridge expects the parser to RETURN HTML, so the
 * implementation below provides that contract explicitly.
 */
;(function (root) {
  'use strict';

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  function escapeAttribute(value) {
    return escapeHtml(value).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function inline(text) {
    text = escapeHtml(text);
    text = text.replace(/!\[([^\]]*)\]\(([^\s)]+)(?:\s+["']([^"']*)["'])?\)/g,
      function (_, alt, href, title) {
        var titleAttr = title ? ' title="' + escapeAttribute(title) + '"' : '';
        return '<img src="' + escapeAttribute(href) + '" alt="' + escapeAttribute(alt) + '"' + titleAttr + '>';
      });
    text = text.replace(/\[([^\]]+)\]\(([^\s)]+)(?:\s+["']([^"']*)["'])?\)/g,
      function (_, label, href, title) {
        var titleAttr = title ? ' title="' + escapeAttribute(title) + '"' : '';
        return '<a href="' + escapeAttribute(href) + '"' + titleAttr + '>' + label + '</a>';
      });
    text = text.replace(/`([^`]+)`/g, '<code>$1</code>');
    text = text.replace(/\*\*([^*]+)\*\*|__([^_]+)__/g, function (_, a, b) {
      return '<strong>' + (a || b) + '</strong>';
    });
    text = text.replace(/\*([^*]+)\*|_([^_]+)_/g, function (_, a, b) {
      return '<em>' + (a || b) + '</em>';
    });
    text = text.replace(/~~([^~]+)~~/g, '<del>$1</del>');
    return text;
  }

  function parse(markdown) {
    var lines = String(markdown == null ? '' : markdown).replace(/\r\n?/g, '\n').split('\n');
    var html = [];
    var paragraph = [];
    var listType = null;
    var codeLines = null;

    function flushParagraph() {
      if (paragraph.length) {
        html.push('<p>' + inline(paragraph.join('<br>')) + '</p>');
        paragraph = [];
      }
    }

    function closeList() {
      if (listType) {
        html.push('</' + listType + '>');
        listType = null;
      }
    }

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var match;

      if (codeLines !== null) {
        if (/^\s*(```|~~~)/.test(line)) {
          html.push('<pre><code>' + escapeHtml(codeLines.join('\n')) + '</code></pre>');
          codeLines = null;
        } else {
          codeLines.push(line);
        }
        continue;
      }

      if (/^\s*(```|~~~)/.test(line)) {
        flushParagraph();
        closeList();
        codeLines = [];
        continue;
      }

      if (/^\s*$/.test(line)) {
        flushParagraph();
        closeList();
        continue;
      }

      if ((match = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line))) {
        flushParagraph();
        closeList();
        var level = match[1].length;
        html.push('<h' + level + '>' + inline(match[2]) + '</h' + level + '>');
        continue;
      }

      if (/^\s{0,3}([-*_])(?:\s*\1){2,}\s*$/.test(line)) {
        flushParagraph();
        closeList();
        html.push('<hr>');
        continue;
      }

      if ((match = /^>\s?(.*)$/.exec(line))) {
        flushParagraph();
        closeList();
        var quote = [match[1]];
        while (i + 1 < lines.length && /^>\s?/.test(lines[i + 1])) {
          i++;
          quote.push(lines[i].replace(/^>\s?/, ''));
        }
        html.push('<blockquote><p>' + inline(quote.join('<br>')) + '</p></blockquote>');
        continue;
      }

      if ((match = /^\s*[-+*]\s+(.+)$/.exec(line))) {
        flushParagraph();
        if (listType !== 'ul') {
          closeList();
          listType = 'ul';
          html.push('<ul>');
        }
        html.push('<li>' + inline(match[1]) + '</li>');
        continue;
      }

      if ((match = /^\s*\d+\.\s+(.+)$/.exec(line))) {
        flushParagraph();
        if (listType !== 'ol') {
          closeList();
          listType = 'ol';
          html.push('<ol>');
        }
        html.push('<li>' + inline(match[1]) + '</li>');
        continue;
      }

      closeList();
      paragraph.push(line);
    }

    if (codeLines !== null) {
      html.push('<pre><code>' + escapeHtml(codeLines.join('\n')) + '</code></pre>');
    }
    flushParagraph();
    closeList();

    return html.join('\n');
  }

  function marked(markdown, options) {
    return parse(markdown, options);
  }

  marked.parse = parse;
  marked.defaults = { gfm: true, breaks: true };
  root.marked = marked;
}(typeof window !== 'undefined' ? window : this));
