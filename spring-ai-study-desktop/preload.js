const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktop', {
  ask(question) {
    ipcRenderer.send('ask', question);
  },
  onEvent(callback) {
    ipcRenderer.on('agent-event', (_event, payload) => callback(payload));
  },
  onDone(callback) {
    ipcRenderer.on('agent-done', () => callback());
  }
});
