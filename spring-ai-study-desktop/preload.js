const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktop', {
  ask(question, conversationId) {
    ipcRenderer.send('ask', { question, conversationId });
  },
  onEvent(callback) {
    ipcRenderer.on('agent-event', (_event, payload) => callback(payload));
  },
  onDone(callback) {
    ipcRenderer.on('agent-done', () => callback());
  }
});
