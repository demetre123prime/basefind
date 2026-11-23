import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import './style.css';

document.querySelector('#app').innerHTML = `
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">N</span><span>NexCode</span></div>
      <button class="new-chat" id="newChat">＋ <span>New chat</span></button>
      <div class="side-label">WORKSPACE</div>
      <div class="side-item active"><span>✳</span> Local AI</div>
      <div class="side-item"><span>⌘</span> Code assistant</div>
      <div class="side-spacer"></div>
      <div class="privacy-card">
        <span class="privacy-dot"></span>
        <div><b>Private by design</b><small>Inference stays on this PC</small></div>
      </div>
      <div class="side-footer">NEXCODE DESKTOP · 0.2</div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <div class="eyebrow">YOUR LOCAL AI WORKSPACE</div>
          <h1>Build something great.</h1>
          <p class="subtitle">A coding assistant that runs on your device.</p>
        </div>
        <div class="top-actions">
          <span class="runtime-badge" id="runtimeBadge"><i></i> NO MODEL LOADED</span>
          <button class="manage-button" id="manageModels">Manage models</button>
        </div>
      </header>

      <section id="modelPanel" class="model-panel">
        <div class="panel-heading">
          <div>
            <div class="eyebrow">LOCAL MODEL LIBRARY</div>
            <h2>Choose your AI model</h2>
            <p>Download once, then chat offline. Model files stay on this device.</p>
          </div>
          <button class="panel-close" id="closeModels" aria-label="Hide model library">×</button>
        </div>
        <div id="modelCards" class="model-grid">
          <div class="loading-card">Loading available models…</div>
        </div>
        <p id="modelStatus" class="model-status">No model has been downloaded yet.</p>
      </section>

      <section id="chat" class="chat-area">
        <div id="welcome" class="welcome">
          <div class="welcome-orb">✳</div>
          <div class="eyebrow">NEXCODE LOCAL</div>
          <h2>Your ideas stay yours.</h2>
          <p>Choose and load a model to start coding with a private, on-device assistant.</p>
          <div class="suggestions">
            <button class="suggest">Explain a code snippet</button>
            <button class="suggest">Help me debug an error</button>
            <button class="suggest">Build a Rust project</button>
          </div>
        </div>
      </section>

      <form id="chatForm" class="composer">
        <textarea id="prompt" rows="2" placeholder="Load a model to start chatting…" disabled></textarea>
        <div class="composer-bottom">
          <span id="composerHint">Local mode · No API key · No usage fees</span>
          <button id="send" type="submit" disabled>↑</button>
        </div>
      </form>
      <div class="bottom-note">AI responses can be incorrect. Review generated code before using it.</div>
    </main>
  </div>
`;

const chat = document.querySelector('#chat');
const promptBox = document.querySelector('#prompt');
const modelPanel = document.querySelector('#modelPanel');
const modelCards = document.querySelector('#modelCards');
const modelStatus = document.querySelector('#modelStatus');
const runtimeBadge = document.querySelector('#runtimeBadge');
const sendButton = document.querySelector('#send');
let loadedModel = null;
let busy = false;
let activeAnswer = null;

function addMessage(role, text) {
  document.querySelector('#welcome')?.remove();
  const article = document.createElement('article');
  article.className = `message ${role}`;
  const label = document.createElement('div');
  label.className = 'message-label';
  label.textContent = role === 'user' ? 'YOU' : 'NEXCODE · LOCAL';
  const body = document.createElement('div');
  body.className = 'message-body';
  body.textContent = text;
  article.append(label, body);
  chat.append(article);
  chat.scrollTop = chat.scrollHeight;
  return body;
}

function setLoaded(model) {
  loadedModel = model || null;
  promptBox.disabled = !loadedModel || busy;
  sendButton.disabled = !loadedModel || busy;
  promptBox.placeholder = loadedModel
    ? 'Message your local coding assistant…'
    : 'Download and load a model to start chatting…';
  runtimeBadge.innerHTML = loadedModel
    ? '<i></i> RUNNING LOCALLY'
    : '<i></i> NO MODEL LOADED';
  runtimeBadge.classList.toggle('ready', Boolean(loadedModel));
  document.querySelector('#composerHint').textContent = loadedModel
    ? `Local model · ${loadedModel} · Offline after download`
    : 'Local mode · No API key · No usage fees';
}

function formatBytes(bytes) {
  if (!bytes) return 'Downloading…';
  return bytes >= 1_000_000_000
    ? `${(bytes / 1_000_000_000).toFixed(1)} GB`
    : `${Math.round(bytes / 1_000_000)} MB`;
}

async function refreshModels() {
  const models = await invoke('available_models');
  const loaded = models.find(model => model.loaded);
  setLoaded(loaded?.name || null);
  modelCards.innerHTML = models.map(model => `
    <article class="model-card ${model.loaded ? 'selected' : ''}">
      <div class="model-topline">
        <span class="model-chip">${model.id.includes('05b') ? 'LOW MEMORY' : 'MORE CAPABLE'}</span>
        <span class="model-size">${model.sizeLabel}</span>
      </div>
      <h3>${model.name}</h3>
      <p>${model.description}</p>
      <div class="model-meta"><span>◈ ${model.ramLabel}</span><span>GGUF · Q4_K_M</span></div>
      <progress id="progress-${model.id}" value="0" max="100" class="download-progress"></progress>
      <button class="model-action ${model.loaded ? 'is-loaded' : ''}" data-action="${model.installed ? 'load' : 'download'}" data-id="${model.id}" ${busy ? 'disabled' : ''}>
        ${model.loaded ? '✓ Loaded' : model.installed ? 'Load model' : `Download · ${model.sizeLabel}`}
      </button>
    </article>
  `).join('');

  modelCards.querySelectorAll('button[data-action]').forEach(button => {
    button.addEventListener('click', async () => {
      const id = button.dataset.id;
      const action = button.dataset.action;
      button.disabled = true;
      try {
        if (action === 'download') {
          modelStatus.textContent = 'Starting secure model download…';
          await invoke('download_model', { modelId: id });
          modelStatus.textContent = 'Download complete. Load the model to begin.';
          await refreshModels();
        } else {
          busy = true;
          modelStatus.textContent = 'Loading model into memory…';
          await invoke('load_local_model', { modelId: id });
          await refreshModels();
          modelPanel.classList.add('hidden');
          modelStatus.textContent = 'Ready. You can now chat offline.';
          chat.innerHTML = '';
          showWelcome();
          promptBox.focus();
        }
      } catch (error) {
        modelStatus.textContent = `Error: ${String(error)}`;
        button.disabled = false;
      } finally {
        busy = false;
        setLoaded(loadedModel);
        modelCards.querySelectorAll('button[data-action]').forEach(b => b.disabled = false);
      }
    });
  });

  if (loaded) {
    modelPanel.classList.add('hidden');
    modelStatus.textContent = 'A local model is loaded and ready.';
  } else {
    modelPanel.classList.remove('hidden');
  }
}

function showWelcome() {
  const welcome = document.createElement('div');
  welcome.id = 'welcome';
  welcome.className = 'welcome';
  welcome.innerHTML = `
    <div class="welcome-orb">✳</div>
    <div class="eyebrow">NEXCODE LOCAL</div>
    <h2>What are we building today?</h2>
    <p>Ask for help with code, explore an idea, or work through an error.</p>
    <div class="suggestions">
      <button class="suggest">Explain a code snippet</button>
      <button class="suggest">Help me debug an error</button>
      <button class="suggest">Build a Rust project</button>
    </div>`;
  chat.append(welcome);
  welcome.querySelectorAll('.suggest').forEach(button => {
    button.addEventListener('click', () => {
      promptBox.value = button.textContent;
      promptBox.focus();
    });
  });
}

document.querySelector('#manageModels').addEventListener('click', () => {
  modelPanel.classList.toggle('hidden');
});
document.querySelector('#closeModels').addEventListener('click', () => {
  if (loadedModel) modelPanel.classList.add('hidden');
});

document.querySelector('#newChat').addEventListener('click', async () => {
  if (busy) return;
  chat.innerHTML = '';
  showWelcome();
  if (loadedModel) {
    busy = true;
    setLoaded(loadedModel);
    modelStatus.textContent = 'Resetting the local conversation…';
    try {
      await invoke('reset_local_chat');
      modelStatus.textContent = 'New conversation ready.';
    } catch (error) {
      modelStatus.textContent = String(error);
    } finally {
      busy = false;
      setLoaded(loadedModel);
    }
  }
});

document.querySelectorAll('.suggest').forEach(button => {
  button.addEventListener('click', () => {
    promptBox.value = button.textContent;
    promptBox.focus();
  });
});

document.querySelector('#chatForm').addEventListener('submit', async event => {
  event.preventDefault();
  const text = promptBox.value.trim();
  if (!text || !loadedModel || busy) return;

  promptBox.value = '';
  addMessage('user', text);
  const answer = addMessage('assistant', 'Generating locally…');
  activeAnswer = answer;
  busy = true;
  setLoaded(loadedModel);

  try {
    const result = await invoke('chat_local', { prompt: text });
    if (!answer.textContent.trim()) answer.textContent = result || '(The model returned an empty response.)';
  } catch (error) {
    answer.textContent = `Local AI error: ${String(error)}`;
  } finally {
    activeAnswer = null;
    busy = false;
    setLoaded(loadedModel);
    promptBox.focus();
    chat.scrollTop = chat.scrollHeight;
  }
});

listen('assistant-delta', event => {
  if (!activeAnswer) return;
  activeAnswer.textContent += event.payload.text || '';
  chat.scrollTop = chat.scrollHeight;
}).catch(() => {});

listen('model-download-progress', event => {
  const progress = event.payload;
  const bar = document.querySelector(`#progress-${progress.modelId}`);
  const percent = progress.total > 0
    ? Math.min(100, Math.round(progress.downloaded / progress.total * 100))
    : 0;
  if (bar) bar.value = percent;
  modelStatus.textContent = `Downloading model… ${percent}% · ${formatBytes(progress.downloaded)} / ${formatBytes(progress.total)}`;
}).catch(() => {});

refreshModels().catch(error => {
  modelStatus.textContent = `Could not read local model library: ${String(error)}`;
});
