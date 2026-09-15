(function () {
  'use strict';

  var MAX_BYTES = 5 * 1024 * 1024;  // 서버 파싱 상한과 같은 값
  var MAX_ROWS = 300;               // 한 번에 보낼 행 안전 상한

  window.ASAttach = { readFile: readFile, MAX_BYTES: MAX_BYTES };

  function readFile(file) {
    return new Promise(function (resolve, reject) {
      var name = String(file.name || '');
      if (!/\.(xlsx|csv)$/i.test(name)) return reject({ reason: 'UNSUPPORTED' });
      if (file.size > MAX_BYTES) return reject({ reason: 'TOO_LARGE' });

      var isCsv = /\.csv$/i.test(name);
      var work = isCsv
        ? file.text().then(parseCsv)
        : file.arrayBuffer().then(readXlsx);

      work.then(function (rows) { resolve(finalize(name, rows)); })
        .catch(function (e) { reject(e && e.reason ? e : { reason: 'PARSE' }); });
    });
  }

  function finalize(name, rows) {
    rows = (rows || []).filter(function (r) {
      return r && r.some(function (c) { return String(c).trim() !== ''; });
    });
    if (!rows.length) throw { reason: 'NO_DATA' };
    var truncated = rows.length > MAX_ROWS;
    var used = truncated ? rows.slice(0, MAX_ROWS) : rows;
    var text = used.map(function (r) {
      return r.map(function (c) { return String(c == null ? '' : c); }).join('\t');
    }).join('\n');
    return { name: name, rows: used.length, totalRows: rows.length, truncated: truncated, text: text };
  }

  // ───────── CSV ─────────
  function parseCsv(text) {
    text = String(text || '').replace(/^﻿/, '').replace(/\r\n?/g, '\n');
    var rows = [], row = [], field = '', inQ = false;
    for (var i = 0; i < text.length; i++) {
      var c = text[i];
      if (inQ) {
        if (c === '"') { if (text[i + 1] === '"') { field += '"'; i++; } else inQ = false; }
        else field += c;
      } else if (c === '"') { inQ = true; }
      else if (c === ',') { row.push(field); field = ''; }
      else if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; }
      else field += c;
    }
    if (field !== '' || row.length) { row.push(field); rows.push(row); }
    return rows;
  }

  // ───────── XLSX (ZIP + inflate + XML) ─────────
  function readXlsx(buf) {
    return readZip(new Uint8Array(buf)).then(function (files) {
      var dec = new TextDecoder('utf-8');
      var shared = files['xl/sharedStrings.xml'] ? parseShared(dec.decode(files['xl/sharedStrings.xml'])) : [];
      var sheetPath = firstSheetPath(files, dec);
      if (!sheetPath || !files[sheetPath]) throw { reason: 'NO_DATA' };
      return parseSheet(dec.decode(files[sheetPath]), shared);
    });
  }

  function inflateRaw(bytes) {
    if (typeof DecompressionStream === 'undefined') return Promise.reject({ reason: 'PARSE' });
    var ds = new DecompressionStream('deflate-raw');
    return new Response(new Blob([bytes]).stream().pipeThrough(ds)).arrayBuffer()
      .then(function (ab) { return new Uint8Array(ab); });
  }

  // ZIP 중앙 디렉터리로 엔트리를 읽어 필요한 파일만 해제
  function readZip(bytes) {
    var dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    // EOCD 탐색 (뒤에서부터)
    var eocd = -1;
    var min = Math.max(0, bytes.length - 22 - 65536);
    for (var i = bytes.length - 22; i >= min; i--) {
      if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) return Promise.reject({ reason: 'PARSE' });

    var count = dv.getUint16(eocd + 10, true);
    var cdOff = dv.getUint32(eocd + 16, true);
    var want = {
      'xl/sharedStrings.xml': 1, 'xl/workbook.xml': 1, 'xl/_rels/workbook.xml.rels': 1
    };
    var entries = [];
    var p = cdOff;
    for (var n = 0; n < count; n++) {
      if (dv.getUint32(p, true) !== 0x02014b50) break;
      var method = dv.getUint16(p + 10, true);
      var compSize = dv.getUint32(p + 20, true);
      var fnLen = dv.getUint16(p + 28, true);
      var extraLen = dv.getUint16(p + 30, true);
      var commentLen = dv.getUint16(p + 32, true);
      var localOff = dv.getUint32(p + 42, true);
      var name = new TextDecoder('utf-8').decode(bytes.subarray(p + 46, p + 46 + fnLen));
      if (want[name] || /^xl\/worksheets\/sheet\d+\.xml$/i.test(name)) {
        entries.push({ name: name, method: method, compSize: compSize, localOff: localOff });
      }
      p += 46 + fnLen + extraLen + commentLen;
    }

    var out = {};
    var chain = Promise.resolve();
    entries.forEach(function (e) {
      chain = chain.then(function () {
        var lfnLen = dv.getUint16(e.localOff + 26, true);
        var lexLen = dv.getUint16(e.localOff + 28, true);
        var start = e.localOff + 30 + lfnLen + lexLen;
        var comp = bytes.subarray(start, start + e.compSize);
        if (e.method === 0) { out[e.name] = comp; return; }
        if (e.method !== 8) return;
        return inflateRaw(comp).then(function (data) { out[e.name] = data; });
      });
    });
    return chain.then(function () { return out; });
  }

  function firstSheetPath(files, dec) {
    try {
      var wb = new DOMParser().parseFromString(dec.decode(files['xl/workbook.xml']), 'application/xml');
      var sheet = wb.getElementsByTagName('sheet')[0];
      var rid = sheet.getAttribute('r:id') || sheet.getAttribute('id');
      var rels = new DOMParser().parseFromString(dec.decode(files['xl/_rels/workbook.xml.rels']), 'application/xml');
      var list = rels.getElementsByTagName('Relationship');
      for (var i = 0; i < list.length; i++) {
        if (list[i].getAttribute('Id') === rid) {
          var target = list[i].getAttribute('Target').replace(/^\//, '');
          return target.indexOf('xl/') === 0 ? target : 'xl/' + target;
        }
      }
    } catch (e) { /* fallthrough */ }
    var names = Object.keys(files).filter(function (n) {
      return /^xl\/worksheets\/sheet\d+\.xml$/i.test(n);
    }).sort();
    return names[0];
  }

  function parseShared(xml) {
    var doc = new DOMParser().parseFromString(xml, 'application/xml');
    var sis = doc.getElementsByTagName('si');
    var arr = [];
    for (var i = 0; i < sis.length; i++) {
      var ts = sis[i].getElementsByTagName('t');
      var s = '';
      for (var j = 0; j < ts.length; j++) s += ts[j].textContent;
      arr.push(s);
    }
    return arr;
  }

  function parseSheet(xml, shared) {
    var doc = new DOMParser().parseFromString(xml, 'application/xml');
    var rowsEl = doc.getElementsByTagName('row');
    var rows = [];
    for (var i = 0; i < rowsEl.length; i++) {
      var cells = rowsEl[i].getElementsByTagName('c');
      var map = {}, maxCol = -1;
      for (var j = 0; j < cells.length; j++) {
        var c = cells[j];
        var col = colIndex(c.getAttribute('r') || '');
        if (col < 0) col = j;
        var t = c.getAttribute('t');
        var val = '';
        if (t === 'inlineStr') {
          var isT = c.getElementsByTagName('t');
          for (var k = 0; k < isT.length; k++) val += isT[k].textContent;
        } else {
          var v = c.getElementsByTagName('v')[0];
          var raw = v ? v.textContent : '';
          val = (t === 's') ? (shared[parseInt(raw, 10)] || '') : raw;
        }
        map[col] = val;
        if (col > maxCol) maxCol = col;
      }
      var row = [];
      for (var m = 0; m <= maxCol; m++) row.push(map[m] != null ? map[m] : '');
      rows.push(row);
    }
    return rows;
  }

  function colIndex(ref) {
    var m = /^([A-Za-z]+)/.exec(ref);
    if (!m) return -1;
    var s = m[1].toUpperCase(), n = 0;
    for (var i = 0; i < s.length; i++) n = n * 26 + (s.charCodeAt(i) - 64);
    return n - 1;
  }
})();
