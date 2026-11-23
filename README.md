# NexCode

Windows desktop AI workspace using your own OpenAI-compatible API provider. DeepSeek defaults: endpoint https://api.deepseek.com, model deepseek-chat.

## Build on Windows
Install Node.js 20+, Rust, and Microsoft C++ Build Tools. Run npm install, then npm run tauri:build. The NSIS installer is produced under src-tauri/target/release/bundle/nsis/.

Open Settings to enter the provider endpoint, model, and API key. The key is stored in Windows Credential Manager. API usage is billed directly by your provider. NexCode currently does not process payments or take a percentage of API usage. Never commit API keys.
