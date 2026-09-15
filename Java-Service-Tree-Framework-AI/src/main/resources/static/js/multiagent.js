(function () {
  'use strict';

  var AS_GROUPS = [
    {
      label: 'Requirement', ko: '착수 지시서 변환', icon: 'ic-exchange', cls: 'as-group-requirement',
      items: [
        { title: '샘플로 보여줘', q: '샘플로 보여줘' },
        { title: '완료 조건이 없는 요구사항', q: '요구사항명: 사용자 목록 엑셀 다운로드 / 상세: 관리자는 사용자 목록을 엑셀로 내려받을 수 있어야 한다.' },
        { title: '요구사항 2건이 섞인 행', q: '요구사항명: 회원가입 / 상세: 가입 시 이메일 인증을 거치고, 가입이 끝나면 환영 메일을 발송한다. / 완료 조건: 인증과 메일 발송이 정상 동작할 것' }
      ]
    },
    {
      label: 'Acceptance', ko: '완료 조건 검토', icon: 'ic-check-square', cls: 'as-group-acceptance',
      items: [
        { title: '모호한 성능 조건', q: '요구사항명: 상품 검색 / 완료 조건: 검색 결과가 빠르게 표시될 것' },
        { title: '모호한 안정성 조건', q: '요구사항명: 주문 결제 처리 / 완료 조건: 시스템이 안정적으로 동작할 것' },
        { title: '모호한 편의성 조건', q: '요구사항명: 비밀번호 재설정 / 완료 조건: 사용자가 편리하게 재설정할 수 있을 것' }
      ]
    },
    {
      label: 'PMBOK', ko: '프로젝트 관리', icon: 'ic-book', cls: 'as-group-pmbok',
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

  var TERMS_HTML = [
    '<p class="as-terms-eyebrow">Terms of Service</p>',
    '<div class="as-terms-title">313DEVGRP 이용약관</div>',
    '<p class="as-terms-meta">MultiAgent 서비스 이용에 관한 회사와 사용자의 권리·의무 및 책임 사항을 규정합니다.</p>',
    '<h5 class="as-terms-h">제1조 (목적)</h5>',
    '<p>본 이용약관은 313DEVGRP가 제공하는 MultiAgent 서비스 이용과 관련하여 313DEVGRP와 사용자 간의 권리, 의무 및 책임에 관한 사항을 규정함을 목적으로 합니다.</p>',
    '<h5 class="as-terms-h">제2조 (용어의 정의)</h5>',
    '<p class="as-terms-sub">1. "서비스"란 313DEVGRP가 생성형 AI를 이용하여 제공하는 서비스를 말합니다.</p>',
    '<p class="as-terms-sub">2. "입력값"이란 사용자가 서비스에 입력하는 글·정보 등의 데이터를, "생성물"이란 그 결과로 생성된 답변 등 산출물을 말합니다.</p>',
    '<h5 class="as-terms-h">제3조 (서비스 사용 시 주의사항)</h5>',
    '<p class="as-terms-sub">1. 서비스는 불완전·부정확하거나 신뢰할 수 없는 생성물을 제공할 수 있습니다. 사용자는 정확성·적절성을 최종적으로 스스로 검증하여 이용하여야 합니다.</p>',
    '<p class="as-terms-sub">2. 입력값에 민감정보·타인의 개인정보, 영업비밀, 타인의 지식재산권을 침해하는 사항 등이 포함되지 않도록 하여야 합니다.</p>',
    '<h5 class="as-terms-h">제4조 (데이터 수집 및 활용)</h5>',
    '<p>313DEVGRP는 더 나은 서비스 제공을 위하여 서비스 데이터를 수집·저장하고 품질 향상 목적으로 활용할 수 있습니다.</p>',
    '<h5 class="as-terms-h">제5조 (면책)</h5>',
    '<p>서비스는 관련 법률이 허용하는 한도 내에서 어떠한 보증도 없이 \'있는 그대로\' 제공되며, 313DEVGRP의 고의·중대한 과실이 아닌 손해에 대해 책임지지 않습니다.</p>',
    '<h5 class="as-terms-h">제6조 (준거법)</h5>',
    '<p>본 약관은 대한민국의 법률이 적용되며, 분쟁에 대한 소송은 관할 법원에서 결정합니다.</p>'
  ].join('');

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
  function icon(name, cls) { return '<svg class="ic' + (cls ? ' ' + cls : '') + '"><use href="#' + name + '"></use></svg>'; }
  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  document.addEventListener('DOMContentLoaded', function () {
    renderSuggest();
    $('#as_terms_box').innerHTML = TERMS_HTML;
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
      setAiBadge(res.ok ? 'ok' : 'warn', res.ok ? 'Ready' : 'Warn');
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
      note.innerHTML = icon('ic-stop-circle') + ' 답변을 중단했어요';
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
    box.querySelector('.as-progress-step').innerHTML = icon('ic-spinner') + '<span>' + escapeHtml(text) + '</span>';
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
    h.innerHTML = icon('ic-comment') + ' MultiAgent 답변';
    ai.insertBefore(h, ai.querySelector('.as-answer-body'));
  }

  function appendMessageActions(msg) {
    if (msg.querySelector('.as-msg-actions')) return;
    var bar = document.createElement('div');
    bar.className = 'as-msg-actions';
    bar.innerHTML =
      '<button type="button" class="as-msg-action" data-action="like" title="좋아요">' + icon('ic-thumbs-up') + '</button>' +
      '<button type="button" class="as-msg-action" data-action="dislike" title="싫어요">' + icon('ic-thumbs-down') + '</button>' +
      '<span class="as-msg-thanks" style="display:none;">' + icon('ic-check') + ' 감사합니다</span>';
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
        '<div class="chat-user-avatar">' + icon('ic-user') + '</div>' +
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
          '<div class="chat-ai-avatar">' + icon('ic-logo') + '</div>' +
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
          icon('ic-comment') + '<span class="as-history-title">' + escapeHtml(conv.title) + '</span>' +
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
