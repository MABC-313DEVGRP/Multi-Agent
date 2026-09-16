(function () {
  'use strict';

  var AS_GROUPS = [
    {
      label: 'Requirement', ko: '착수 지시서 변환', icon: 'fa-exchange', cls: 'as-group-requirement',
      items: [
        { title: '샘플로 보여줘', q: '샘플로 보여줘' },
        { title: '완료 조건이 없는 요구사항', q: '요구사항명: 사용자 목록 엑셀 다운로드 / 상세: 관리자는 사용자 목록을 엑셀로 내려받을 수 있어야 한다.' },
        { title: '요구사항 2건이 섞인 행', q: '요구사항명: 회원가입 / 상세: 가입 시 이메일 인증을 거치고, 가입이 끝나면 환영 메일을 발송한다. / 완료 조건: 인증과 메일 발송이 정상 동작할 것' }
      ]
    },
    {
      label: 'Acceptance', ko: '완료 조건 검토', icon: 'fa-check-square-o', cls: 'as-group-acceptance',
      items: [
        { title: '모호한 성능 조건', q: '요구사항명: 상품 검색 / 완료 조건: 검색 결과가 빠르게 표시될 것' },
        { title: '모호한 안정성 조건', q: '요구사항명: 주문 결제 처리 / 완료 조건: 시스템이 안정적으로 동작할 것' },
        { title: '모호한 편의성 조건', q: '요구사항명: 비밀번호 재설정 / 완료 조건: 사용자가 편리하게 재설정할 수 있을 것' }
      ]
    },
    {
      label: 'PMBOK', ko: '프로젝트 관리', icon: 'fa-book', cls: 'as-group-pmbok',
      items: [
        { title: '범위 기술서와 WBS', q: '범위 기술서와 WBS는 무엇이 달라?' },
        { title: '인수 조건 작성법', q: '인수 조건은 어떻게 써야 해?' },
        { title: '범위 검증과 품질 통제', q: '범위 검증과 품질 통제의 차이는 뭐야?' }
      ]
    }
  ];

  var AS_PLACEHOLDERS = [
    '요구사항정의서에서 요구사항 행을 복사해 붙여 넣어 보세요',
    '샘플로 보여줘',
    '요구사항명: 상품 검색 / 완료 조건: 검색 결과가 빠르게 표시될 것',
    '인수 조건은 어떻게 써야 해?'
  ];

  var HISTORY_TITLE_MAX = 40;
  var INPUT_MAX_HEIGHT = 160;

  // 첨부: 파일을 읽어 붙여넣은 것처럼 이 질의와 함께 스트림으로 보낸다
  var AS_FILE_AUTO_QUERY = '첨부한 요구사항정의서 파일을 작업 지시서로 바꿔 줘.';
  var AS_STREAM_BODY_MAX = 200 * 1024;   // AI 모듈 JSON 본문 한도 아래로 유지

  // 이용약관 전문은 index.html 에 인라인으로 두었다. JS 로 주입하지 않는다.

  var asStreaming = false;
  var asChatStarted = false;
  var asCurrentStreamId = null;
  var asFetchController = null;
  var asStreamReader = null;
  var asPhTimer = null;

  var asConversations = [];   // [{ id, title, fullTitle, chatHtml }] — 최신이 앞
  var asActiveConvId = null;
  var asConvSeq = 0;

  function $(sel) { return document.querySelector(sel); }
  function icon(name, cls) { return '<i class="fa ' + name + (cls ? ' ' + cls : '') + '"></i>'; }
  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  document.addEventListener('DOMContentLoaded', function () {
    renderSuggest();
    renderHistoryList();
    bindEvents();
    startPlaceholderRotator();
    refreshChatStatus();
    $('#as_input').focus();
  });

  ////////////////////////////////////////////////////////////////////////////
  // 이벤트 바인딩
  ////////////////////////////////////////////////////////////////////////////
  function bindEvents() {
    $('#as_send_btn').addEventListener('click', submitInput);
    $('#as_stop_btn').addEventListener('click', abortStreaming);

    var input = $('#as_input');
    input.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter' || e.shiftKey) return;
      if (e.isComposing) return;      // 한글 조합 확정 Enter 는 보내지 않는다
      e.preventDefault();
      submitInput();
    });
    input.addEventListener('input', function () {
      var ready = this.value.trim().length > 0;
      var btn = $('#as_send_btn');
      btn.classList.toggle('is-ready', ready);
      btn.disabled = !ready;
      fitInputHeight(this);
    });

    // 예상 질문 · 대화 기록 · 피드백 (위임)
    document.addEventListener('click', function (e) {
      var q = e.target.closest('.as-suggest-q');
      if (q) { ask(q.getAttribute('data-q')); return; }

      var item = e.target.closest('.as-history-item');
      if (item) { openConversation(item.getAttribute('data-conv-id')); return; }

      var act = e.target.closest('.as-msg-action');
      if (act) {
        var bar = act.closest('.as-msg-actions');
        bar.querySelectorAll('.as-msg-action').forEach(function (b) { b.remove(); });
        bar.querySelector('.as-msg-thanks').style.display = 'inline-flex';
        return;
      }
    });

    $('#as_new_chat_btn').addEventListener('click', startNewConversation);

    // 엑셀·CSV 파일 첨부 — 고르면 바로 읽어 변환한다
    $('#as_attach_btn').addEventListener('click', function () {
      if (!asStreaming) $('#as_file_input').click();
    });
    $('#as_file_input').addEventListener('change', function () {
      var file = this.files && this.files[0];
      this.value = '';   // 같은 파일을 다시 고를 수 있게 비운다
      if (file) uploadAttachment(file);
    });

    // 이용약관 펼치기/접기
    $('#as_terms').addEventListener('click', function (e) {
      e.preventDefault();
      var opening = !this.classList.contains('open');
      this.classList.toggle('open');
      $('#as_terms_label').textContent = opening ? '이용약관 닫기' : '이용약관 보기';
      $('#as_terms_box').style.display = opening ? 'block' : 'none';
    });
  }

  ////////////////////////////////////////////////////////////////////////////
  // 상태 배지 — /multiagent/validate 로 백엔드 도달만 확인 (AI 호출 없음)
  ////////////////////////////////////////////////////////////////////////////
  function refreshChatStatus() {
    fetch('/multiagent/validate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ queryText: '상태 확인', sessionId: 'status-check' })
    }).then(function (res) {
      setAiBadge(res.ok ? 'ok' : 'warn', res.ok ? 'All Good' : 'Ready');
    }).catch(function () {
      setAiBadge('err', 'Offline');
    });
  }

  function setAiBadge(state, label) {
    var badge = $('#as_badge');
    badge.classList.remove('as-badge-ok', 'as-badge-warn', 'as-badge-err');
    badge.classList.add('as-badge-' + state, 'is-visible');
    badge.querySelector('.as-badge-label').textContent = label;
  }

  ////////////////////////////////////////////////////////////////////////////
  // 질문 처리
  ////////////////////////////////////////////////////////////////////////////
  function submitInput() {
    if (asStreaming) return;
    var input = $('#as_input');
    var text = input.value.trim();
    if (!text) return;
    input.value = '';
    var btn = $('#as_send_btn');
    btn.classList.remove('is-ready');
    btn.disabled = true;
    fitInputHeight(input);
    ask(text);
  }

  function ask(text) {
    if (asStreaming || !text) return;
    if (!asActiveConvId) createConversation(text);
    startChatIfNeeded();
    appendUserMessage(text);
    streamAnswer(appendAiMessage(), text);
  }

  // 첨부 — 파일을 브라우저에서 읽어(attach.js) 붙여넣은 것처럼 질의로 보낸다.
  //   서버는 파일을 보관하지 않는다. 읽은 행 텍스트를 그대로 /multiagent/stream 으로 전달한다.
  function uploadAttachment(file) {
    if (asStreaming || !window.ASAttach) return;
    var label = '📎 ' + String(file.name || '');
    if (!asActiveConvId) createConversation(label);
    startChatIfNeeded();
    appendUserMessage(label);
    var msg = appendAiMessage();

    // 읽는 동안 다른 전송을 막는다(중지 버튼 노출)
    asStreaming = true;
    asFetchController = null;
    toggleStopButton(true);
    renderProgress(msg, '파일을 읽고 있어요');

    window.ASAttach.readFile(file).then(function (parsed) {
      if (!asStreaming) return;   // 읽는 중 중지됨
      var note = parsed.truncated
        ? '[안내: 파일 총 ' + parsed.totalRows + '행 중 앞 ' + parsed.rows + '행만 변환 대상으로 보냅니다]\n'
        : '';
      var query = AS_FILE_AUTO_QUERY + '\n\n' + note + '[파일: ' + parsed.name + ']\n' + parsed.text;
      if (new TextEncoder().encode(query).length > AS_STREAM_BODY_MAX) {
        errorStream(msg, '파일 내용이 많아 한 번에 보내기 어려워요. 변환할 행을 몇 건씩 나눠 붙여 넣어 주세요.', null);
        return;
      }
      streamAnswer(msg, query);   // 진행 표시는 답변이 시작되면 지워진다
    }).catch(function (e) {
      errorStream(msg, parseFailMessage(e && e.reason), null);
    });
  }

  // 읽지 못한 사유별 안내. 추측해서 읽지 않고 붙여넣기로 돌린다.
  function parseFailMessage(reason) {
    switch (reason) {
      case 'UNSUPPORTED': return '엑셀(.xlsx)이나 CSV 파일만 읽을 수 있어요. .xls 는 .xlsx 로 다시 저장하거나, 변환할 행을 복사해 붙여 넣어 주세요.';
      case 'TOO_LARGE': return '파일이 너무 커요(최대 5MB). 변환할 행을 복사해 붙여 넣어 주세요.';
      case 'NO_DATA': return '파일에서 데이터 행을 찾지 못했어요. 요구사항을 채운 파일을 올리거나 행을 붙여 넣어 주세요.';
      default: return '파일을 읽지 못했어요. 변환할 행을 복사해 붙여 넣어 주세요.';
    }
  }

  function fitInputHeight(el) {
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, INPUT_MAX_HEIGHT) + 'px';
    scrollChatToBottom();
  }

  ////////////////////////////////////////////////////////////////////////////
  // 답변 — 스트리밍
  ////////////////////////////////////////////////////////////////////////////
  function streamAnswer(msg, queryText) {
    var raw = msg.querySelector('.as-stream-raw');
    var streamId = 'as-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
    var params = JSON.stringify({ queryText: queryText, sessionId: streamId, language: 'ko' });

    var accumulated = '';
    var preBuffer = '';
    var sseBuffer = '';
    var receivedJson = false;
    var progressShown = false;
    var aborted = false;
    var controller = new AbortController();
    var reader = null;

    function isActive() { return !aborted && asCurrentStreamId === streamId; }
    function markDone() { if (asCurrentStreamId === streamId) asFetchController = null; }
    function onError(err) {
      markDone();
      if (err && err.name === 'AbortError') aborted = true;
      if (!isActive()) return;
      errorStream(msg, '답변을 가져오지 못했어요. 백엔드가 실행 중인지(8080), UPSTAGE_API_KEY 가 설정됐는지 확인해 주세요.', streamId);
    }

    controller.signal.addEventListener('abort', function () { aborted = true; });

    asCurrentStreamId = streamId;
    asFetchController = controller;
    asStreaming = true;
    toggleStopButton(true);

    fetch('/multiagent/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
      body: params,
      signal: controller.signal
    }).then(function (res) {
      markDone();
      if (!isActive()) return;
      if (res.status !== 200 || !res.body) throw new Error('stream status ' + res.status);

      reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
      asStreamReader = reader;

      function pump(result) {
        if (!isActive()) return;
        if (result.done) {
          if (!receivedJson) accumulated = preBuffer;
          finishStream(msg, accumulated, streamId);
          return;
        }

        sseBuffer += result.value;
        var events = sseBuffer.split('\n\n');
        sseBuffer = events.pop();

        for (var i = 0; i < events.length; i++) {
          var lines = events[i].split('\n');
          var dataLines = [];
          for (var j = 0; j < lines.length; j++) {
            if (lines[j].indexOf('data:') === 0) dataLines.push(lines[j].slice(5));
          }
          if (!dataLines.length) continue;

          var chunk = dataLines.join('\n');     // 원문 그대로 (선행 공백 보존)
          var trimmed = chunk.trim();
          if (!trimmed || trimmed === '[DONE]') continue;

          // JSON 이면 진행 표시 이벤트, 아니면 답변 텍스트
          var evt = trimmed.charAt(0) === '{' ? parseEvent(trimmed) : null;
          if (evt) {
            receivedJson = true;
            preBuffer = '';
            if (evt.progress) { progressShown = true; renderProgress(msg, evt.progress); }
            continue;
          }

          markAnswerStarted(msg);
          if (progressShown) { progressShown = false; clearProgress(msg); }
          if (receivedJson) accumulated += chunk; else preBuffer += chunk;
        }

        raw.textContent = receivedJson ? accumulated : preBuffer;
        scrollChatToBottom();
        reader.read().then(pump).catch(onError);
      }

      reader.read().then(pump).catch(onError);
    }).catch(onError);
  }

  function parseEvent(text) {
    try { var j = JSON.parse(text); return j && typeof j === 'object' ? j : null; }
    catch (e) { return null; }
  }

  function abortStreaming() {
    if (asFetchController) { asFetchController.abort(); asFetchController = null; }
    if (asCurrentStreamId) {
      fetch('/multiagent/stop-stream?sessionId=' + encodeURIComponent(asCurrentStreamId)).catch(function () {});
    }
    if (asStreamReader) { asStreamReader.cancel().catch(function () {}); asStreamReader = null; }
    asStreaming = false;
    asCurrentStreamId = null;
    toggleStopButton(false);

    var streaming = $('#chat').querySelector('.chat-message.streaming');
    if (streaming) {
      streaming.classList.remove('streaming');
      clearProgress(streaming);
      var ai = streaming.querySelector('.as-answer-ai');
      var note = document.createElement('div');
      note.className = 'as-stopped';
      note.innerHTML = icon('fa-stop-circle') + ' 답변을 중단했어요';
      ai.appendChild(note);
    }
  }

  function finishStream(msg, markdownText, streamId) {
    if (streamId && asCurrentStreamId !== streamId) return;
    asStreaming = false;
    asCurrentStreamId = null;
    asStreamReader = null;
    toggleStopButton(false);
    msg.classList.remove('streaming');
    clearProgress(msg);
    msg.querySelector('.as-answer-body').innerHTML = renderMarkdown(markdownText || '');
    appendMessageActions(msg);
    saveActiveConversation();
    scrollChatToBottom(true);
  }

  function errorStream(msg, message, streamId) {
    if (streamId && asCurrentStreamId !== streamId) return;
    asStreaming = false;
    asCurrentStreamId = null;
    asStreamReader = null;
    toggleStopButton(false);
    msg.classList.remove('streaming');
    clearProgress(msg);
    var body = msg.querySelector('.as-answer-body');
    body.classList.add('is-error');
    body.textContent = message || '답변을 가져오지 못했어요. 다시 물어봐 주세요.';
    saveActiveConversation();
  }

  // 첫 토큰까지의 공백을 메우는 진행 표시(서버 progress 이벤트). 이 백엔드는 보통 안 보낸다.
  function renderProgress(msg, text) {
    var ai = msg.querySelector('.as-answer-ai');
    var box = ai.querySelector('.as-progress');
    if (!box) {
      box = document.createElement('div');
      box.className = 'as-progress';
      box.innerHTML = '<span class="as-progress-step"></span>';
      ai.insertBefore(box, ai.firstChild);
      msg.classList.add('has-progress');
    }
    box.querySelector('.as-progress-step').innerHTML = icon('fa-spinner') + '<span>' + escapeHtml(text) + '</span>';
    requestAnimationFrame(function () { box.classList.add('in'); });
    scrollChatToBottom();
  }

  function clearProgress(msg) {
    var box = msg.querySelector('.as-progress');
    if (!box) return;
    msg.classList.remove('has-progress');
    box.remove();
  }

  function markAnswerStarted(msg) {
    var ai = msg.querySelector('.as-answer-ai');
    if (ai.querySelector('.as-sec-h')) return;
    var h = document.createElement('div');
    h.className = 'as-sec-h';
    h.innerHTML = icon('fa-comment-o') + ' MultiAgent 답변';
    ai.insertBefore(h, ai.querySelector('.as-answer-body'));
  }

  function appendMessageActions(msg) {
    if (msg.querySelector('.as-msg-actions')) return;
    var bar = document.createElement('div');
    bar.className = 'as-msg-actions';
    bar.innerHTML =
      '<button type="button" class="as-msg-action" data-action="like" title="좋아요">' + icon('fa-thumbs-o-up') + '</button>' +
      '<button type="button" class="as-msg-action" data-action="dislike" title="싫어요">' + icon('fa-thumbs-o-down') + '</button>' +
      '<span class="as-msg-thanks" style="display:none;">' + icon('fa-check') + ' 감사합니다</span>';
    msg.querySelector('.text').after(bar);
  }

  ////////////////////////////////////////////////////////////////////////////
  // 채팅 렌더 헬퍼
  ////////////////////////////////////////////////////////////////////////////
  function startChatIfNeeded() {
    if (asChatStarted) return;
    asChatStarted = true;
    $('#greeting_message').style.display = 'none';
    $('#chat_input_area').classList.add('input-pinned');
    var mc = $('#main_content');
    mc.classList.remove('flex-center');
    mc.classList.add('flex-space-between');
    $('#chat_area_wrapper').style.display = 'flex';
    $('#chat_container').style.display = 'block';
  }

  function resetChatView() {
    asChatStarted = false;
    $('#chat_area_wrapper').style.display = 'none';
    $('#chat_container').style.display = 'none';
    var mc = $('#main_content');
    mc.classList.remove('flex-space-between');
    mc.classList.add('flex-center');
    $('#chat_input_area').classList.remove('input-pinned');
    $('#greeting_message').style.display = '';
    fitInputHeight($('#as_input'));
  }

  function appendUserMessage(text) {
    var wrap = document.createElement('div');
    wrap.className = 'chat-message';
    wrap.innerHTML =
      '<div class="user-chat-row">' +
        '<span class="text user-text">' + escapeHtml(text) + '</span>' +
        '<div class="chat-user-avatar">' + icon('fa-user') + '</div>' +
      '</div>';
    $('#chat').appendChild(wrap);
    scrollChatToBottom(true);
  }

  function appendAiMessage() {
    var msg = document.createElement('div');
    msg.className = 'chat-message streaming';
    msg.innerHTML =
      '<div style="padding-left:1rem;">' +
        '<div class="chat-ai-header">' +
          '<div class="chat-ai-avatar">' + icon('fa-life-ring') + '</div>' +
          '<span class="chat-ai-label">MultiAgent</span>' +
        '</div>' +
        '<div class="text">' +
          '<div class="as-answer-ai">' +
            '<div class="as-answer-body"><span class="as-stream-raw"></span><span class="as-stream-cursor"></span></div>' +
          '</div>' +
        '</div>' +
      '</div>';
    $('#chat').appendChild(msg);
    scrollChatToBottom(true);
    return msg;
  }

  function scrollChatToBottom(force) {
    var el = $('#chat_container');
    if (!el) return;
    if (force || el.scrollHeight - el.scrollTop - el.clientHeight < 160) {
      el.scrollTop = el.scrollHeight;
    }
  }

  function toggleStopButton(streaming) {
    $('#as_send_btn').style.display = streaming ? 'none' : 'flex';
    $('#as_stop_btn').style.display = streaming ? 'flex' : 'none';
    $('#as_history_pane').classList.toggle('is-locked', !!streaming);
    $('#as_new_chat_btn').disabled = !!streaming;
    $('#as_attach_btn').disabled = !!streaming;
  }

  ////////////////////////////////////////////////////////////////////////////
  // 좌측 — 대화 기록 (브라우저 메모리에만 둔다. 새로고침하면 사라진다)
  ////////////////////////////////////////////////////////////////////////////
  function createConversation(text) {
    var firstLine = String(text).split('\n')[0].replace(/\r/g, '').replace(/\t/g, ' ').trim();
    var conv = {
      id: 'c' + (++asConvSeq),
      fullTitle: firstLine,
      title: firstLine.length > HISTORY_TITLE_MAX ? firstLine.slice(0, HISTORY_TITLE_MAX) + '…' : firstLine,
      chatHtml: ''
    };
    asConversations.unshift(conv);
    asActiveConvId = conv.id;
    renderHistoryList();
  }

  function findConversation(id) {
    for (var i = 0; i < asConversations.length; i++) {
      if (asConversations[i].id === id) return asConversations[i];
    }
    return null;
  }

  function saveActiveConversation() {
    var conv = findConversation(asActiveConvId);
    if (conv) conv.chatHtml = $('#chat').innerHTML;
  }

  function openConversation(id) {
    if (asStreaming || id === asActiveConvId) return;
    var conv = findConversation(id);
    if (!conv) return;
    saveActiveConversation();
    asActiveConvId = id;
    $('#chat').innerHTML = conv.chatHtml;
    startChatIfNeeded();
    renderHistoryList();
    scrollChatToBottom(true);
  }

  function startNewConversation() {
    if (asStreaming) return;
    if (asActiveConvId) {
      saveActiveConversation();
      asActiveConvId = null;
      $('#chat').innerHTML = '';
      resetChatView();
      renderHistoryList();
    }
    $('#as_input').focus();
  }

  function renderHistoryList() {
    var list = $('#as_history_list');
    if (!asConversations.length) {
      list.innerHTML = '<div class="as-history-empty">아직 대화가 없어요. 질문하면 여기에 쌓여요.</div>';
      return;
    }
    var html = '';
    asConversations.forEach(function (conv) {
      html +=
        '<button type="button" class="as-history-item' + (conv.id === asActiveConvId ? ' is-active' : '') + '"' +
          ' data-conv-id="' + escapeHtml(conv.id) + '" title="' + escapeHtml(conv.fullTitle) + '">' +
          icon('fa-comment-o') + '<span class="as-history-title">' + escapeHtml(conv.title) + '</span>' +
        '</button>';
    });
    list.innerHTML = html;
  }

  ////////////////////////////////////////////////////////////////////////////
  // greeting — 예상 질문 배너
  ////////////////////////////////////////////////////////////////////////////
  function renderSuggest() {
    var html = '';
    AS_GROUPS.forEach(function (g) {
      html += '<div class="as-suggest-col">';
      html +=
        '<div class="as-suggest-side">' +
          '<div class="as-suggest-avatar ' + g.cls + '">' + icon(g.icon) + '</div>' +
          '<div class="as-suggest-name">' + escapeHtml(g.label) + '</div>' +
          '<div class="as-suggest-ko">' + escapeHtml(g.ko) + '</div>' +
        '</div>';
      g.items.forEach(function (item) {
        html += '<button type="button" class="as-suggest-q" data-q="' + escapeHtml(item.q) + '">' +
          '<span class="as-dot">·</span>' + escapeHtml(item.title) + '</button>';
      });
      html += '</div>';
    });
    $('#as_suggest').innerHTML = html;
  }

  ////////////////////////////////////////////////////////////////////////////
  // 입력창 placeholder — 예시 질문 타이핑 로테이션
  ////////////////////////////////////////////////////////////////////////////
  function startPlaceholderRotator() {
    var input = $('#as_input');
    if (!input) return;
    var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var i = 0, pos = 0, deleting = false;

    function tick() {
      var text = AS_PLACEHOLDERS[i];
      if (input.value) { asPhTimer = setTimeout(tick, 400); return; }

      if (reduce) {
        input.setAttribute('placeholder', text);
        i = (i + 1) % AS_PLACEHOLDERS.length;
        asPhTimer = setTimeout(tick, 2600);
        return;
      }
      if (!deleting) {
        pos++;
        input.setAttribute('placeholder', text.slice(0, pos));
        if (pos >= text.length) { deleting = true; asPhTimer = setTimeout(tick, 1500); return; }
        asPhTimer = setTimeout(tick, 55);
      } else {
        pos--;
        input.setAttribute('placeholder', text.slice(0, pos));
        if (pos <= 0) { deleting = false; i = (i + 1) % AS_PLACEHOLDERS.length; asPhTimer = setTimeout(tick, 350); return; }
        asPhTimer = setTimeout(tick, 28);
      }
    }
    clearTimeout(asPhTimer);
    tick();
  }

  ////////////////////////////////////////////////////////////////////////////
  // 마크다운 렌더 (경량) + 정제
  ////////////////////////////////////////////////////////////////////////////
  function renderMarkdown(src) {
    src = String(src == null ? '' : src).replace(/\r\n?/g, '\n');
    var lines = src.split('\n');
    var out = [];
    var i = 0;

    function inline(t) {
      t = escapeHtml(t);
      t = t.replace(/`([^`]+)`/g, function (_, c) { return '<code>' + c + '</code>'; });
      t = t.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
      t = t.replace(/__([^_]+)__/g, '<strong>$1</strong>');
      t = t.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>');
      t = t.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
      return t;
    }
    function splitRow(line) {
      return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(function (c) { return c.trim(); });
    }

    while (i < lines.length) {
      var line = lines[i];

      if (/^```/.test(line)) {
        var buf = []; i++;
        while (i < lines.length && !/^```/.test(lines[i])) { buf.push(lines[i]); i++; }
        i++;
        out.push('<pre><code>' + escapeHtml(buf.join('\n')) + '</code></pre>');
        continue;
      }
      if (/^\s*([-*_])\1\1[-*_ ]*$/.test(line)) { out.push('<hr>'); i++; continue; }

      var h = /^(#{1,6})\s+(.*)$/.exec(line);
      if (h) { var lv = h[1].length; out.push('<h' + lv + '>' + inline(h[2]) + '</h' + lv + '>'); i++; continue; }

      if (/^\s*>\s?/.test(line)) {
        var qb = [];
        while (i < lines.length && /^\s*>\s?/.test(lines[i])) { qb.push(lines[i].replace(/^\s*>\s?/, '')); i++; }
        out.push('<blockquote>' + renderMarkdown(qb.join('\n')) + '</blockquote>');
        continue;
      }
      // GFM 표
      if (line.indexOf('|') >= 0 && i + 1 < lines.length &&
          /^\s*\|?[\s:|-]*-[\s:|-]*$/.test(lines[i + 1]) && lines[i + 1].indexOf('|') >= 0) {
        var header = splitRow(line); i += 2;
        var rows = [];
        while (i < lines.length && lines[i].indexOf('|') >= 0 && lines[i].trim() !== '') { rows.push(splitRow(lines[i])); i++; }
        var tbl = '<table><thead><tr>' + header.map(function (c) { return '<th>' + inline(c) + '</th>'; }).join('') + '</tr></thead><tbody>';
        rows.forEach(function (r) { tbl += '<tr>' + r.map(function (c) { return '<td>' + inline(c) + '</td>'; }).join('') + '</tr>'; });
        out.push(tbl + '</tbody></table>');
        continue;
      }
      // 목록
      if (/^\s*([-*+]|\d+\.)\s+/.test(line)) {
        var ordered = /^\s*\d+\.\s+/.test(line);
        var items = [];
        while (i < lines.length && /^\s*([-*+]|\d+\.)\s+/.test(lines[i])) {
          items.push(lines[i].replace(/^\s*([-*+]|\d+\.)\s+/, '')); i++;
        }
        var tag = ordered ? 'ol' : 'ul';
        out.push('<' + tag + '>' + items.map(function (it) { return '<li>' + inline(it) + '</li>'; }).join('') + '</' + tag + '>');
        continue;
      }
      if (/^\s*$/.test(line)) { i++; continue; }

      var para = [line]; i++;
      while (i < lines.length && !/^\s*$/.test(lines[i]) &&
             !/^(#{1,6}\s|```|\s*>|\s*([-*+]|\d+\.)\s)/.test(lines[i])) { para.push(lines[i]); i++; }
      out.push('<p>' + para.map(inline).join('<br>') + '</p>');
    }
    return sanitizeHtml(out.join('\n'));
  }

  function sanitizeHtml(html) {
    var box = document.createElement('div');
    box.innerHTML = html || '';
    box.querySelectorAll('script, iframe, object, embed').forEach(function (el) { el.remove(); });
    box.querySelectorAll('*').forEach(function (el) {
      for (var i = el.attributes.length - 1; i >= 0; i--) {
        var attr = el.attributes[i];
        var name = attr.name.toLowerCase();
        if (name.indexOf('on') === 0 ||
            ((name === 'href' || name === 'src') && isUnsafeUrl(attr.value))) {
          el.removeAttribute(attr.name);
        }
      }
    });
    return box.innerHTML;
  }

  function isUnsafeUrl(value) {
    var url = String(value || '').replace(/\s+/g, '').toLowerCase();
    return url.indexOf('javascript:') === 0 || url.indexOf('vbscript:') === 0 || url.indexOf('data:text') === 0;
  }
})();
