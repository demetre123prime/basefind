# NexCode

NexCode is a local-first Windows coding assistant. The inference engine runs in Rust on your PC; model weights are separate downloads, not part of the installer.

## What works in this build

- Download and manage small GGUF coding models inside the app.
- Run inference locally with the Rust-native [Cera](https://github.com/hyeons-lab/cera) engine.
- CPU inference with SIMD acceleration where the processor supports it.
- GGUF memory-mapped model loading to avoid copying the entire model file into a second in-memory buffer.
- A reduced 2,048-token context and 128-token prompt micro-batches to keep the first release suitable for lower-memory machines.
- No cloud API key, subscription, or per-message fee.
- After a model is downloaded, chat inference works offline. The app does not send prompts to a hosted AI service.

## Models

The model library currently offers Qwen2.5-Coder Instruct in two sizes. Both are published by Qwen under Apache-2.0 model licensing and are downloaded from the official Qwen Hugging Face repositories.

| Model | Quantization | Download size (approx.) | Intended use |
|---|---|---:|---|
| Qwen2.5-Coder 0.5B | Q4_K_M | 490 MB | Low-memory PCs and short coding tasks |
| Qwen2.5-Coder 1.5B | Q4_K_M | 1.1 GB | PCs with more available RAM |

The 0.5B model is the lighter option, not a high-end coding model. Model speed and quality depend on your CPU, available RAM, context size, and the task. A model download requires an internet connection once; local generation does not.

## Build on Windows

Requirements:
- Windows 10 or 11, 64-bit
- Node.js 20+
- Rust 1.94+
- Microsoft C++ Build Tools and WebView2 runtime

Build commands:

```powershell
npm install
npm run tauri:build
```

The NSIS installer is generated under `src-tauri/target/release/bundle/nsis/`. GitHub Actions also publishes a Windows installer under the repository's Releases page.

## Local storage and privacy

Downloaded model files are stored in NexCode's application data folder under `models`. Chat prompts and model responses are processed by the local Rust runtime. The only network transfer in local mode is the model download from Hugging Face. Do not add confidential material to chats unless you are comfortable storing it on this PC.

## Open-source components and model notices

- **Cera**: Rust inference engine integrated as a Cargo dependency. Licensed under Apache-2.0 OR MIT. Source: https://github.com/hyeons-lab/cera
- **Qwen2.5-Coder Instruct GGUF**: model weights published by Qwen under Apache-2.0. The model files are not bundled with NexCode; they are fetched from Qwen's official Hugging Face repositories when a user selects Download.

NexCode integrates Cera's public Rust library; it does not copy or rebrand Cera's source code. Keep the upstream licenses and model license notices with any redistribution.

## Project status

This is an early local-chat foundation. It does not yet include autonomous file editing, terminal execution, repository indexing, multi-agent workflows, or donations. Those should be added in later updates with explicit user approval for any file or command actions.
