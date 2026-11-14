const messages = document.getElementById('messages');
const form = document.getElementById('composer');
const question = document.getElementById('question');
const send = document.getElementById('send');
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

let toolPanel = null;
let toolCount = 0;

function appendTool(kind, text) {
  if (!toolPanel) {
    toolPanel = document.createElement('details');
    toolPanel.className = 'tools';
    const summary = document.createElement('summary');
    toolPanel.appendChild(summary);
    messages.appendChild(toolPanel);
  }
  toolCount += 1;
  toolPanel.querySelector('summary').textContent = '查看工具调用（' + toolCount + '）';
  const item = document.createElement('div');
  item.className = 'msg ' + kind;
  item.textContent = labels[kind] + '\n' + text;
  toolPanel.appendChild(item);
  if (!status.hidden) {
    messages.appendChild(status);
  }
  messages.scrollTop = messages.scrollHeight;
}

function append(kind, text) {
  if (kind === 'tool_call' || kind === 'tool_result') {
    appendTool(kind, text);
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
  messages.appendChild(item);
  if (!status.hidden) {
    messages.appendChild(status);
  }
  messages.scrollTop = messages.scrollHeight;
}

function setBusy(busy) {
  send.disabled = busy;
  question.disabled = busy;
  status.hidden = !busy;
  if (busy) {
    messages.appendChild(status);
    messages.scrollTop = messages.scrollHeight;
  }
}

window.desktop.onEvent((payload) => {
  const kind = payload.event === 'answer' ? 'answer' : payload.event;
  append(kind === 'message' ? 'answer' : kind, payload.data || '');
});

window.desktop.onDone(() => setBusy(false));

form.addEventListener('submit', (event) => {
  event.preventDefault();
  const text = question.value.trim();
  if (!text || send.disabled) {
    return;
  }
  append('user', text);
  question.value = '';
  toolPanel = null;
  toolCount = 0;
  setBusy(true);
  window.desktop.ask(text);
});
