(function (global) {
  'use strict';

  const FRENCH_ELISION_CLITICS = new Set(['l', 'd', 'j', 'm', 't', 's', 'n', 'c', 'qu']);
  const INTERNAL_DELIMITERS = new Set(["'", '\u2019', '-', '\u2010', '\u2011']);

  function primaryLanguage(languageCode) {
    return String(languageCode || '').trim().toLowerCase().split(/[-_]/, 1)[0];
  }

  function codePointStartOffset(text, offset) {
    const coerced = Math.max(0, Math.min(text.length, Number(offset) || 0));
    if (coerced > 0 && coerced < text.length) {
      const current = text.charCodeAt(coerced);
      const previous = text.charCodeAt(coerced - 1);
      if (current >= 0xDC00 && current <= 0xDFFF && previous >= 0xD800 && previous <= 0xDBFF) {
        return coerced - 1;
      }
    }
    return coerced;
  }

  function previousCodePointStartOffset(text, offset) {
    if (offset <= 0) return 0;
    return codePointStartOffset(text, offset - 1);
  }

  function codePointAt(text, offset) {
    if (offset < 0 || offset >= text.length) return '';
    return String.fromCodePoint(text.codePointAt(offset));
  }

  function nextCodePointOffset(text, offset) {
    const char = codePointAt(text, offset);
    return char ? offset + char.length : text.length;
  }

  function isFrenchWordChar(char) {
    return !!char && /[\p{L}\p{N}\p{M}]/u.test(char);
  }

  function isLookupChar(char) {
    return !!char && !/[\s\p{P}\p{S}]/u.test(char);
  }

  function isInternalDelimiter(text, offset) {
    const char = codePointAt(text, offset);
    if (!INTERNAL_DELIMITERS.has(char)) return false;
    const previous = previousCodePointStartOffset(text, offset);
    const next = nextCodePointOffset(text, offset);
    return previous !== offset &&
      next < text.length &&
      isFrenchWordChar(codePointAt(text, previous)) &&
      isFrenchWordChar(codePointAt(text, next));
  }

  function isFrenchElisionApostrophe(text, offset) {
    const char = codePointAt(text, offset);
    if ((char !== "'" && char !== '\u2019') || !isInternalDelimiter(text, offset)) return false;

    let cliticStart = offset;
    while (cliticStart > 0) {
      const previous = previousCodePointStartOffset(text, cliticStart);
      if (!isFrenchWordChar(codePointAt(text, previous))) break;
      cliticStart = previous;
    }
    return FRENCH_ELISION_CLITICS.has(text.slice(cliticStart, offset).toLowerCase());
  }

  function startOffset(textValue, tapOffset, languageCode) {
    const text = String(textValue || '');
    const tap = codePointStartOffset(text, tapOffset);
    if (tap < 0 || tap >= text.length || !isLookupChar(codePointAt(text, tap))) return null;
    if (primaryLanguage(languageCode) !== 'fr') return tap;

    let start = tap;
    while (start > 0) {
      const previous = previousCodePointStartOffset(text, start);
      const char = codePointAt(text, previous);
      if (isFrenchWordChar(char)) {
        start = previous;
      } else if (isInternalDelimiter(text, previous)) {
        if ((char === "'" || char === '\u2019') && isFrenchElisionApostrophe(text, previous)) break;
        start = previous;
      } else {
        break;
      }
    }
    return start;
  }

  function isScanBoundaryAt(textValue, offset, languageCode, options) {
    const text = String(textValue || '');
    const normalized = codePointStartOffset(text, offset);
    const char = codePointAt(text, normalized);
    if (!char) return true;

    if (primaryLanguage(languageCode) !== 'fr') return !isLookupChar(char);
    if (isFrenchWordChar(char) || isInternalDelimiter(text, normalized)) return false;
    return !(options && options.scanAcrossSpaces === true && char === ' ');
  }

  function scan(textValue, tapOffset, languageCode, options) {
    const text = String(textValue || '');
    const start = startOffset(text, tapOffset, languageCode);
    if (start === null) return null;

    const isFrench = primaryLanguage(languageCode) === 'fr';
    const scanAcrossSpaces = isFrench && options && options.scanAcrossSpaces === true;
    const requestedMax = options && Number.isFinite(options.maxCodePoints)
      ? Math.floor(options.maxCodePoints)
      : Number.MAX_SAFE_INTEGER;
    const maxCodePoints = Math.max(0, requestedMax);
    if (maxCodePoints === 0) return null;

    let end = start;
    let count = 0;
    while (end < text.length && count < maxCodePoints) {
      const char = codePointAt(text, end);
      if (isFrench && (isFrenchWordChar(char) || isInternalDelimiter(text, end))) {
        end = nextCodePointOffset(text, end);
        count += 1;
        continue;
      }
      if (isFrench && scanAcrossSpaces && char === ' ') {
        let next = end;
        let spaces = 0;
        while (next < text.length && codePointAt(text, next) === ' ') {
          next += 1;
          spaces += 1;
        }
        if (next >= text.length || !isFrenchWordChar(codePointAt(text, next)) || count + spaces > maxCodePoints) break;
        end = next;
        count += spaces;
        continue;
      }
      if (!isFrench && isLookupChar(char)) {
        end = nextCodePointOffset(text, end);
        count += 1;
        continue;
      }
      break;
    }

    if (end <= start) return null;
    return {
      text: text.slice(start, end),
      startOffset: start,
      endOffset: end,
      tapOffset: codePointStartOffset(text, tapOffset),
    };
  }

  global.ChimahonLookupScanner = Object.freeze({
    primaryLanguage,
    scan,
    startOffset,
    isScanBoundaryAt,
  });
})(window);
