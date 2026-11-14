const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktop', {
  ask(question, conversationId, root) {
    ipcRenderer.send('ask', {question, conversationId, root});
  },
  chooseDirectory() {
    return ipcRenderer.invoke('choose-directory');
  },
  onEvent(callback) {
    ipcRenderer.on('agent-event', (_event, payload) => callback(payload));
  },
  onDone(callback) {
    ipcRenderer.on('agent-done', () => callback());
  }
});
