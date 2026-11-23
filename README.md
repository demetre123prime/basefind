# NexCode

NexCode is a local-first Windows coding assistant. The inference engine is written in Rust and runs on the user's PC. It uses the open-source [Cera](https://github.com/hyeons-lab/cera) inference engine and GGUF model files.

## First install

The Windows setup EXE includes the **Qwen2.5-Coder 0.5B Q4_K_M starter model (491 MB)**. This makes the installer roughly 500 MB larger, but the user does not need to download the starter model separately. After installation, NexCode automatically loads the bundled starter model on first launch.

The app offers an optional **Qwen2.5-Coder 1.5B Q4_K_M model (~1.12 GB)** in Manage models. It is downloaded only if the user chooses it.

## What works

- Local text generation using Cera, a Rust-native inference engine.
- GGUF model loading with memory mapping.
- CPU inference with SIMD paths where supported by the processor.
- A reduced 2,048-token context and 128-token prompt micro-batches to limit memory use on lower-end PCs.
- Token-by-token response streaming in the UI.
- Automatic first-run loading of the bundled 0.5B starter model.
- Optional in-app download and loading of the 1.5B model.
- No cloud API key, subscription, or usage fee. Once installed, prompts and model inference stay on the device.

The 0.5B model is a lightweight starter, not a high-end coding model. Smaller models have limited coding ability compared with larger models. Actual RAM use and speed depend on the device and prompt length.

## Models and licences

| Model | Size | Included? | Use |
|---|---:|---|---|
| Qwen2.5-Coder 0.5B Instruct Q4_K_M | 491 MB | Included in Windows setup | Starter model, lower-memory systems |
| Qwen2.5-Coder 1.5B Instruct Q4_K_M | 1.12 GB | Optional download | More capable model for PCs with more available RAM |

The model files are distributed from the official Qwen Hugging Face GGUF repositories:
- 0.5B: https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF
- 1.5B: https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF

Qwen's model repositories include their licence notice. Preserve the original model licence and notices when redistributing model weights.

## Build on Windows

Requirements:
- Windows 10 or 11, 64-bit
- Node.js 20+
- Rust stable compatible with Cera 0.7.5 (Rust 1.94+)
- Microsoft C++ Build Tools and WebView2 runtime
- Internet connection for fetching the bundled starter model during packaging

Prepare the bundled model before building:

```powershell
New-Item -ItemType Directory -Force src-tauri/resources/models
curl.exe -L --fail --retry 3 "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf" -o "src-tauri/resources/models/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf"
npm install
npm run tauri:build
```

The NSIS installer is generated under `src-tauri/target/release/bundle/nsis/`. GitHub Actions downloads the starter model, packages it into the setup EXE, and publishes the installer under Releases.

## Local storage and privacy

The bundled 0.5B model is installed in the app's resource directory. The optional 1.5B model is stored under NexCode's application data folder in `models`. Prompts and responses are processed by the local Rust runtime. The only normal network access is the optional model download from Hugging Face. Once a model is present, generation works offline.

## Open-source components

- **Cera**: Rust inference engine integrated as a Cargo dependency. Licensed under Apache-2.0 OR MIT. Source: https://github.com/hyeons-lab/cera
- **Qwen2.5-Coder**: model weights published by Qwen. Refer to the official model repository for the precise licence and redistribution notices.

NexCode integrates Cera's public Rust library. It does not copy or rebrand Cera's source code.

## Project status

This is an early local-chat foundation. It does not yet include autonomous file editing, terminal execution, repository indexing, multi-agent workflows, or donations. These features need separate implementation and careful user-controlled permissions.
