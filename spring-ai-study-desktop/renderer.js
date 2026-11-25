const messages = document.getElementById('messages');
const form = document.getElementById('composer');
const question = document.getElementById('question');
const send = document.getElementById('send');
const newChat = document.getElementById('new-chat');
const tabs = document.getElementById('tabs');
const usageEl = document.getElementById('usage');
let grantedRoot = '';
let conversations = [];
let active = null;
let inflight = null;
let streamOpen = false;
let nextLabel = 1;
const status = document.createElement('div');
status.className = 'status';
status.hidden = true;
status.textContent = '正在路上…';

const labels = {
  tool_call: '调用工具',
  tool_result: '工具结果',
  error: '错误'
};

const markdown = window.markdownit
  ? window.markdownit({ html: false, linkify: true, breaks: true })
  : null;

function renderAnswer(text) {
  if (!markdown || !window.DOMPurify) {
    return null;
  }
  return window.DOMPurify.sanitize(markdown.render(text || ''));
}

function createConversation() {
  const conversation = {
    id: crypto.randomUUID(),
    label: '对话 ' + nextLabel,
    panel: document.createElement('div'),
    usage: null,
    usageRequest: 0,
    toolPanel: null,
    toolCount: 0,
    answerBubble: null,
    answerText: '',
    closed: false
  };
  nextLabel += 1;
  return conversation;
}

function usd(value) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) {
    return '0.0000';
  }
  return amount.toFixed(4);
}

function renderUsage(usage) {
  if (!usage) {
    usageEl.textContent = '0 tokens · $0.0000 · 剩余 —';
    return;
  }
  const tokens = Number(usage.totalTokens || 0).toLocaleString('en-US');
  usageEl.textContent = tokens + ' tokens · $' + usd(usage.estimatedUsd) + ' · 剩余 $' + usd(usage.remainingUsd);
}

function refreshUsage(conversation) {
  if (!conversation || conversation.closed || !window.desktop.usage) {
    return;
  }
  const requestId = conversation.usageRequest + 1;
  conversation.usageRequest = requestId;
  window.desktop.usage(conversation.id).then((usage) => {
    if (conversation.closed || requestId !== conversation.usageRequest || !usage) {
      return;
    }
    conversation.usage = usage;
    if (conversation === active) {
      renderUsage(usage);
    }
  }).catch(() => {
    // Keep the numbers already shown for this conversation.
  });
}

function renderTabs() {
  tabs.replaceChildren();
  conversations.forEach((conversation) => {
    const tab = document.createElement('div');
    tab.className = 'tab' + (conversation === active ? ' active' : '');
    const select = document.createElement('button');
    select.type = 'button';
    select.className = 'tab-label';
    select.textContent = conversation.label;
    select.addEventListener('click', () => activate(conversation));
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'tab-close';
    close.setAttribute('aria-label', '关闭');
    close.textContent = '×';
    close.addEventListener('click', (event) => closeConversation(conversation, event));
    tab.append(select, close);
    tabs.appendChild(tab);
  });
}

function syncComposer() {
  const busyHere = streamOpen && inflight === active;
  send.disabled = streamOpen;
  question.disabled = streamOpen;
  status.hidden = !busyHere;
  if (busyHere) {
    active.panel.appendChild(status);
    messages.scrollTop = messages.scrollHeight;
  }
}

function activate(conversation) {
  active = conversation;
  messages.replaceChildren(conversation.panel);
  renderTabs();
  renderUsage(conversation.usage);
  syncComposer();
  refreshUsage(conversation);
}

function closeConversation(conversation, event) {
  event.stopPropagation();
  const index = conversations.indexOf(conversation);
  if (index < 0) {
    return;
  }
  conversation.closed = true;
  const wasActive = conversation === active;
  conversations.splice(index, 1);
  if (conversations.length === 0) {
    const fresh = createConversation();
    conversations.push(fresh);
    activate(fresh);
    return;
  }
  if (wasActive) {
    activate(conversations[Math.min(index, conversations.length - 1)]);
    return;
  }
  renderTabs();
}

function scrollIfVisible(conversation) {
  if (conversation === active) {
    messages.scrollTop = messages.scrollHeight;
  }
}

function stickStatus(conversation) {
  if (streamOpen && inflight === conversation) {
    conversation.panel.appendChild(status);
  }
}

function appendDelta(conversation, text) {
  if (!conversation.answerBubble) {
    conversation.answerBubble = document.createElement('div');
    conversation.answerBubble.className = 'msg answer markdown';
    conversation.panel.appendChild(conversation.answerBubble);
  }
  conversation.answerText += text;
  const html = renderAnswer(conversation.answerText);
  if (html != null) {
    conversation.answerBubble.innerHTML = html;
  } else {
    conversation.answerBubble.textContent = conversation.answerText;
  }
  stickStatus(conversation);
  scrollIfVisible(conversation);
}

function appendTool(conversation, kind, text) {
  if (!conversation.toolPanel) {
    conversation.toolPanel = document.createElement('details');
    conversation.toolPanel.className = 'tools';
    const summary = document.createElement('summary');
    conversation.toolPanel.appendChild(summary);
    conversation.panel.appendChild(conversation.toolPanel);
  }
  conversation.toolCount += 1;
  conversation.toolPanel.querySelector('summary').textContent = '查看工具调用（' + conversation.toolCount + '）';
  const item = document.createElement('div');
  item.className = 'msg ' + kind;
  item.textContent = labels[kind] + '\n' + text;
  conversation.toolPanel.appendChild(item);
  stickStatus(conversation);
  scrollIfVisible(conversation);
}

function append(conversation, kind, text) {
  if (kind === 'tool_call' || kind === 'tool_result') {
    appendTool(conversation, kind, text);
    return;
  }
  const item = document.createElement('div');
  item.className = 'msg ' + kind;
  const html = kind === 'answer' ? renderAnswer(text) : null;
  if (html != null) {
    item.classList.add('markdown');
    item.innerHTML = html;
  } else if (labels[kind]) {
    item.textContent = labels[kind] + '\n' + text;
  } else {
    item.textContent = text;
  }
  conversation.panel.appendChild(item);
  stickStatus(conversation);
  scrollIfVisible(conversation);
}

window.desktop.onEvent((payload) => {
  const conversation = inflight;
  if (!conversation || conversation.closed) {
    return;
  }
  if (payload.event === 'delta') {
    appendDelta(conversation, payload.data || '');
    return;
  }
  const kind = payload.event === 'answer' ? 'answer' : payload.event;
  append(conversation, kind === 'message' ? 'answer' : kind, payload.data || '');
});

window.desktop.onDone(() => {
  const finished = inflight;
  inflight = null;
  streamOpen = false;
  syncComposer();
  if (finished && !finished.closed) {
    refreshUsage(finished);
  }
  if (active && active !== finished) {
    refreshUsage(active);
  }
});

form.addEventListener('submit', (event) => {
  event.preventDefault();
  const text = question.value.trim();
  if (!text || streamOpen || !active) {
    return;
  }
  append(active, 'user', text);
  question.value = '';
  active.toolPanel = null;
  active.toolCount = 0;
  active.answerBubble = null;
  active.answerText = '';
  inflight = active;
  streamOpen = true;
  syncComposer();
  window.desktop.ask(text, active.id, grantedRoot);
});

document.getElementById('choose-root').addEventListener('click', async () => {
  const selected = await window.desktop.chooseDirectory();
  if (!selected) {
    return;
  }
  grantedRoot = selected;
  document.getElementById('root-label').textContent = selected;
});

document.getElementById('clear-root').addEventListener('click', () => {
  grantedRoot = '';
  document.getElementById('root-label').textContent = '未选择目录';
});

newChat.addEventListener('click', () => {
  const conversation = createConversation();
  conversations.push(conversation);
  activate(conversation);
  question.focus();
});

const first = createConversation();
conversations.push(first);
activate(first);
