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
      <div class="side-footer">NEXCODE DESKTOP · 0.3</div>
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
            <p>The small starter model comes inside setup. The larger model is optional.</p>
          </div>
          <button class="panel-close" id="closeModels" aria-label="Hide model library">×</button>
        </div>
        <div id="starterSetup" class="starter-setup hidden">
          <div class="setup-orb"><span>✳</span></div>
          <div class="setup-copy">
            <div class="setup-kicker"><i></i> FIRST-RUN SETUP</div>
            <h3>Preparing your local AI</h3>
            <p>The included 491 MB starter model is being loaded on this device.</p>
            <div class="setup-progress"><span id="setupProgressFill"></span></div>
          </div>
          <div class="setup-percent" id="setupPercent">READY</div>
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
let starterReady = false;

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

async function refreshModels(keepOpen = false) {
  const models = await invoke('available_models');
  const loaded = models.find(model => model.loaded);
  setLoaded(loaded?.name || null);
  modelCards.innerHTML = models.map(model => `
    <article class="model-card ${model.loaded ? 'selected' : ''} ${model.bundled ? 'starter-card' : 'optional-card'}">
      <div class="model-topline">
        <span class="model-chip">${model.bundled ? 'INCLUDED IN SETUP' : 'OPTIONAL UPGRADE'}</span>
        <span class="model-size">${model.sizeLabel}</span>
      </div>
      <h3>${model.name}</h3>
      <p>${model.description}</p>
      <div class="model-meta"><span>◈ ${model.ramLabel}</span><span>GGUF · Q4_K_M</span></div>
      <progress id="progress-${model.id}" value="0" max="100" class="download-progress"></progress>
      <button class="model-action ${model.loaded ? 'is-loaded' : ''}" data-action="${model.installed ? 'load' : 'download'}" data-id="${model.id}" ${busy ? 'disabled' : ''}>
        ${model.loaded ? '✓ Loaded' : model.installed ? (model.bundled ? 'Load starter model' : 'Load model') : `Download · ${model.sizeLabel}`}
      </button>
    </article>
  `).join('');

  modelCards.querySelectorAll('button[data-action]').forEach(button => {
    button.addEventListener('click', async () => {
      const id = button.dataset.id;
      const action = button.dataset.action;
      busy = true;
      button.disabled = true;
      setLoaded(loadedModel);
      try {
        if (action === 'download') {
          setModelStatus('Downloading optional model securely…', true);
          await invoke('download_model', { modelId: id });
          setModelStatus('Download complete. It is ready to load.');
          await refreshModels(true);
        } else {
          setModelStatus('Loading model into memory…', true);
          await invoke('load_local_model', { modelId: id });
          starterReady = id === 'qwen-coder-05b' || starterReady;
          await refreshModels();
          modelPanel.classList.add('hidden');
          setModelStatus('Ready. Your AI is running locally.');
          chat.innerHTML = '';
          showWelcome();
          promptBox.focus();
        }
      } catch (error) {
        setModelStatus(`Setup error: ${String(error)}`);
        button.disabled = false;
      } finally {
        busy = false;
        setLoaded(loadedModel);
        modelCards.querySelectorAll('button[data-action]').forEach(b => b.disabled = false);
      }
    });
  });

  if (loaded && !keepOpen) {
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

function setModelStatus(message, active = false) {
  modelStatus.textContent = message;
  modelStatus.classList.toggle('working', active);
}

async function startFirstRunSetup() {
  const starterId = 'qwen-coder-05b';
  const setupPanel = document.querySelector('#starterSetup');
  const setupFill = document.querySelector('#setupProgressFill');
  const setupPercent = document.querySelector('#setupPercent');

  try {
    let models = await invoke('available_models');
    const starter = models.find(model => model.id === starterId);
    if (!starter) throw new Error('Starter model is missing from the model library.');

    if (!starter.installed) {
      modelPanel.classList.remove('hidden');
      setupPanel.classList.remove('hidden');
      setupPercent.textContent = 'DOWNLOAD';
      setModelStatus('Starter model not found in setup. Downloading it now…', true);
      await invoke('download_model', { modelId: starterId });
    }

    models = await invoke('available_models');
    const ready = models.find(model => model.id === starterId);
    if (!ready?.installed) throw new Error('Starter model could not be installed.');

    busy = true;
    modelPanel.classList.remove('hidden');
    setupPanel.classList.remove('hidden');
    setupPanel.classList.add('is-loading');
    setupPercent.textContent = 'LOADING';
    setupFill.style.width = '38%';
    setModelStatus('Loading the included starter model into memory…', true);
    setLoaded(null);

    await invoke('load_local_model', { modelId: starterId });

    setupFill.style.width = '100%';
    setupPanel.classList.remove('is-loading');
    setupPanel.classList.add('is-complete');
    setupPercent.textContent = 'READY';
    starterReady = true;
    await refreshModels();
    setModelStatus('Starter model is ready. NexCode is now running fully locally.');
    chat.innerHTML = '';
    showWelcome();
  } catch (error) {
    setupPanel.classList.remove('hidden', 'is-loading');
    setupPercent.textContent = 'RETRY';
    setModelStatus(`Starter setup could not finish: ${String(error)}`, false);
    modelPanel.classList.remove('hidden');
  } finally {
    busy = false;
    setLoaded(loadedModel);
    modelCards.querySelectorAll('button[data-action]').forEach(button => button.disabled = false);
  }
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
  const answer = addMessage('assistant', '');
  answer.classList.add('streaming');
  activeAnswer = answer;
  busy = true;
  setLoaded(loadedModel);

  try {
    const result = await invoke('chat_local', { prompt: text });
    if (!answer.textContent.trim()) answer.textContent = result || '(The model returned an empty response.)';
  } catch (error) {
    answer.textContent = `Local AI error: ${String(error)}`;
  } finally {
    answer.classList.remove('streaming');
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
  const fill = document.querySelector('#setupProgressFill');
  const starterPercent = document.querySelector('#setupPercent');
  if (progress.modelId === 'qwen-coder-05b' && fill) {
    fill.style.width = `${percent}%`;
    if (starterPercent) starterPercent.textContent = `${percent}%`;
  }
  setModelStatus(`Downloading model… ${percent}% · ${formatBytes(progress.downloaded)} / ${formatBytes(progress.total)}`, true);
}).catch(() => {});

startFirstRunSetup().catch(error => {
  setModelStatus(`Could not start local setup: ${String(error)}`);
});
