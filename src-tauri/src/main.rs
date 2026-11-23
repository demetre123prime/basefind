use cera::{GenerateOpts, Message as CeraMessage, ModelLoader, ModelSource, SessionChat, SessionConfig};
use serde::Serialize;
use std::{
    path::PathBuf,
    sync::{Arc, Mutex},
};
use tauri::{AppHandle, Emitter, Manager, State};

const SYSTEM_PROMPT: &str = "You are NexCode, a practical and careful local coding assistant. Help users understand, write, debug, and improve code. Prefer concise explanations and complete working examples. Never claim to have changed files unless a tool actually did so.";

struct ModelSpec {
    id: &'static str,
    name: &'static str,
    description: &'static str,
    file_name: &'static str,
    download_url: &'static str,
    approximate_bytes: u64,
    size_label: &'static str,
    ram_label: &'static str,
}

const MODELS: &[ModelSpec] = &[
    ModelSpec {
        id: "qwen-coder-05b",
        name: "Qwen2.5 Coder 0.5B",
        description: "Smallest option for older PCs and limited memory. Best for short code questions and simple edits.",
        file_name: "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
        download_url: "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
        approximate_bytes: 491_000_000,
        size_label: "~490 MB",
        ram_label: "4 GB RAM class",
    },
    ModelSpec {
        id: "qwen-coder-15b",
        name: "Qwen2.5 Coder 1.5B",
        description: "More capable coding model for PCs with more available memory.",
        file_name: "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        download_url: "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        approximate_bytes: 1_120_000_000,
        size_label: "~1.1 GB",
        ram_label: "8 GB+ RAM recommended",
    },
];

#[derive(Default)]
struct LocalRuntime {
    chat: Option<SessionChat>,
    model_id: Option<String>,
    model_path: Option<PathBuf>,
    started: bool,
}

struct AppState {
    model_dir: PathBuf,
    runtime: Arc<Mutex<LocalRuntime>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ModelInfo {
    id: String,
    name: String,
    description: String,
    size_label: String,
    ram_label: String,
    installed: bool,
    loaded: bool,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DownloadProgress {
    model_id: String,
    downloaded: u64,
    total: u64,
}

fn model_spec(id: &str) -> Result<&'static ModelSpec, String> {
    MODELS
        .iter()
        .find(|model| model.id == id)
        .ok_or_else(|| "Unknown model selection".to_string())
}

fn model_info(spec: &ModelSpec, model_dir: &PathBuf, loaded_id: Option<&str>) -> ModelInfo {
    ModelInfo {
        id: spec.id.to_string(),
        name: spec.name.to_string(),
        description: spec.description.to_string(),
        size_label: spec.size_label.to_string(),
        ram_label: spec.ram_label.to_string(),
        installed: model_dir.join(spec.file_name).is_file(),
        loaded: loaded_id == Some(spec.id),
    }
}

fn create_chat(path: PathBuf) -> Result<SessionChat, String> {
    let model = ModelLoader::new(ModelSource::path(path))
        .build_generative()
        .map_err(|error| format!("Could not load this GGUF model: {error}"))?;

    let session = model
        .create_session(SessionConfig {
            max_seq_len: Some(2048),
            ubatch_size: 128,
            ..SessionConfig::default()
        })
        .map_err(|error| format!("Could not create the inference session: {error}"))?;

    drop(model);

    session
        .into_chat()
        .map_err(|(_, error)| format!("This model's chat template is not supported: {error:?}"))
}

#[tauri::command]
fn available_models(state: State<'_, AppState>) -> Vec<ModelInfo> {
    let loaded_id = state
        .runtime
        .lock()
        .ok()
        .and_then(|runtime| runtime.model_id.clone());

    MODELS
        .iter()
        .map(|spec| model_info(spec, &state.model_dir, loaded_id.as_deref()))
        .collect()
}

#[tauri::command]
async fn download_model(
    app: AppHandle,
    model_id: String,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let spec = model_spec(&model_id)?;
    let destination = state.model_dir.join(spec.file_name);

    if destination.is_file() {
        return Ok(());
    }

    tokio::fs::create_dir_all(&state.model_dir)
        .await
        .map_err(|error| format!("Could not create the model folder: {error}"))?;

    let mut response = reqwest::Client::new()
        .get(spec.download_url)
        .send()
        .await
        .map_err(|error| format!("Model download could not start: {error}"))?;

    if !response.status().is_success() {
        return Err(format!("Model host returned HTTP {}", response.status()));
    }

    let total = response
        .content_length()
        .unwrap_or(spec.approximate_bytes);
    let temporary = destination.with_extension("gguf.part");
    let mut file = tokio::fs::File::create(&temporary)
        .await
        .map_err(|error| format!("Could not create the model file: {error}"))?;

    let mut downloaded = 0_u64;
    let _ = app.emit(
        "model-download-progress",
        DownloadProgress {
            model_id: model_id.clone(),
            downloaded,
            total,
        },
    );

    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|error| format!("Model download interrupted: {error}"))?
    {
        tokio::io::AsyncWriteExt::write_all(&mut file, &chunk)
            .await
            .map_err(|error| format!("Could not save model data: {error}"))?;
        downloaded += chunk.len() as u64;

        let _ = app.emit(
            "model-download-progress",
            DownloadProgress {
                model_id: model_id.clone(),
                downloaded,
                total,
            },
        );
    }

    tokio::io::AsyncWriteExt::flush(&mut file)
        .await
        .map_err(|error| format!("Could not finish writing the model: {error}"))?;
    drop(file);

    tokio::fs::rename(&temporary, &destination)
        .await
        .map_err(|error| format!("Could not finalize the model file: {error}"))?;

    let _ = app.emit(
        "model-download-progress",
        DownloadProgress {
            model_id,
            downloaded,
            total,
        },
    );

    Ok(())
}

#[tauri::command]
async fn load_local_model(
    model_id: String,
    state: State<'_, AppState>,
) -> Result<String, String> {
    let spec = model_spec(&model_id)?;
    let model_path = state.model_dir.join(spec.file_name);

    if !model_path.is_file() {
        return Err("Download this model before loading it".into());
    }

    let runtime = Arc::clone(&state.runtime);

    tokio::task::spawn_blocking(move || {
        {
            let mut current = runtime.lock().map_err(|error| error.to_string())?;
            current.chat.take();
            current.model_id = None;
            current.model_path = None;
            current.started = false;
        }

        let chat = create_chat(model_path.clone())?;
        let mut current = runtime.lock().map_err(|error| error.to_string())?;
        current.chat = Some(chat);
        current.model_id = Some(model_id);
        current.model_path = Some(model_path);
        current.started = false;

        Ok::<String, String>("Local model loaded. NexCode can now chat offline.".into())
    })
    .await
    .map_err(|error| format!("Model loading task failed: {error}"))?
}

#[tauri::command]
async fn chat_local(prompt: String, state: State<'_, AppState>) -> Result<String, String> {
    let prompt = prompt.trim().to_string();
    if prompt.is_empty() {
        return Err("Enter a message first".into());
    }

    let runtime = Arc::clone(&state.runtime);

    tokio::task::spawn_blocking(move || {
        let mut runtime = runtime.lock().map_err(|error| error.to_string())?;
        let first_turn = !runtime.started;

        let chat = runtime
            .chat
            .as_mut()
            .ok_or_else(|| "Load a local model before chatting".to_string())?;

        let ingest_result = if first_turn {
            chat.ingest_messages(&[
                CeraMessage::system(SYSTEM_PROMPT),
                CeraMessage::user(prompt),
            ])
        } else {
            chat.ingest(&CeraMessage::user(prompt))
        };

        ingest_result.map_err(|error| format!("Could not add your message to the context: {error}"))?;

        let turn = chat
            .complete(&GenerateOpts {
                max_tokens: 384,
                temperature: 0.2,
                ..GenerateOpts::default()
            })
            .map_err(|error| format!("Local generation failed: {error}"))?;

        runtime.started = true;
        Ok::<String, String>(turn.text.trim().to_string())
    })
    .await
    .map_err(|error| format!("Local inference task failed: {error}"))?
}

#[tauri::command]
async fn reset_local_chat(state: State<'_, AppState>) -> Result<(), String> {
    let runtime = Arc::clone(&state.runtime);

    tokio::task::spawn_blocking(move || {
        let model_path = {
            let mut current = runtime.lock().map_err(|error| error.to_string())?;
            current.chat.take();
            current.started = false;
            current.model_path.clone()
        };

        let Some(path) = model_path else {
            return Ok::<(), String>(());
        };

        let chat = create_chat(path)?;
        let mut current = runtime.lock().map_err(|error| error.to_string())?;
        current.chat = Some(chat);
        Ok(())
    })
    .await
    .map_err(|error| format!("Could not reset the local chat: {error}"))?
}

fn main() {
    tauri::Builder::default()
        .setup(|app| {
            let model_dir = app.path().app_data_dir()?.join("models");
            std::fs::create_dir_all(&model_dir)?;
            app.manage(AppState {
                model_dir,
                runtime: Arc::new(Mutex::new(LocalRuntime::default())),
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            available_models,
            download_model,
            load_local_model,
            chat_local,
            reset_local_chat
        ])
        .run(tauri::generate_context!())
        .expect("NexCode failed to start");
}
