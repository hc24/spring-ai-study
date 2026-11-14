const {app, BrowserWindow, dialog, ipcMain} = require('electron');
const http = require('http');
const path = require('path');

const AGENT_HOST = '127.0.0.1';
const AGENT_PORT = 8080;

function createWindow() {
  const window = new BrowserWindow({
    width: 760,
    height: 820,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  window.loadFile('index.html');
}

function parseEvent(block) {
  let eventName = 'message';
  const dataLines = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).replace(/^ /, ''));
    }
  }
  return { event: eventName, data: dataLines.join('\n') };
}

function streamQuestion(question, conversationId, root, sender) {
  const query = new URLSearchParams({ question: question });
  if (conversationId) {
    query.set('conversationId', conversationId);
  }
  if (root) {
    query.set('root', root);
  }
  const request = http.get({
    hostname: AGENT_HOST,
    port: AGENT_PORT,
    path: '/react/stream?' + query.toString(),
    headers: { Accept: 'text/event-stream' }
  }, (response) => {
    if (response.statusCode !== 200) {
      response.resume();
      sender.send('agent-event', {
        event: 'error',
        data: 'Agent 返回 HTTP ' + response.statusCode
      });
      sender.send('agent-done');
      return;
    }

    let buffer = '';
    response.setEncoding('utf8');
    response.on('data', (chunk) => {
      buffer += chunk;
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        if (block.trim()) {
          sender.send('agent-event', parseEvent(block));
        }
        boundary = buffer.indexOf('\n\n');
      }
    });
    response.on('end', () => {
      if (buffer.trim()) {
        sender.send('agent-event', parseEvent(buffer));
      }
      sender.send('agent-done');
    });
  });

  request.on('error', () => {
    sender.send('agent-event', {
      event: 'error',
      data: '无法连接 Agent（127.0.0.1:8080）。请先启动 spring-ai-study-agent。'
    });
    sender.send('agent-done');
  });
}

app.whenReady().then(() => {
  ipcMain.handle('choose-directory', async () => {
    const result = await dialog.showOpenDialog({properties: ['openDirectory']});
    if (result.canceled || result.filePaths.length === 0) {
      return '';
    }
    return result.filePaths[0];
  });
  ipcMain.on('ask', (event, payload) => {
    const question = typeof payload === 'string' ? payload : payload.question;
    const conversationId = typeof payload === 'string' ? '' : payload.conversationId;
    const root = typeof payload === 'string' ? '' : payload.root;
    streamQuestion(String(question || ''), String(conversationId || ''), String(root || ''), event.sender);
  });
  createWindow();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
