async function fetchJson(url, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("X-Session-Id", getSessionId());
  const response = await fetch(url, { ...options, headers });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.detail || "Request failed");
  }
  return payload;
}

let backendRetryTimer = null;
let backendConnected = false;
const SESSION_STORAGE_KEY = "localAgentSessionId";

function getSessionId() {
  let sessionId = window.localStorage.getItem(SESSION_STORAGE_KEY);
  if (!sessionId) {
    sessionId = (window.crypto && typeof window.crypto.randomUUID === "function")
      ? window.crypto.randomUUID()
      : `session-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    window.localStorage.setItem(SESSION_STORAGE_KEY, sessionId);
  }
  return sessionId;
}

function scheduleBackendRetry() {
  if (backendRetryTimer) {
    return;
  }
  backendRetryTimer = window.setTimeout(async () => {
    backendRetryTimer = null;
    await refreshFiles({ silent: true });
  }, 3000);
}

function renderBackendStatus(state, message) {
  const el = document.getElementById("backendStatus");
  el.className = `backend-status backend-status-${state}`;
  el.textContent = message;
}

function setOutputStatus(state, meta) {
  const stateEl = document.getElementById("outputState");
  const metaEl = document.getElementById("outputMeta");
  if (stateEl) {
    stateEl.textContent = state;
  }
  if (metaEl) {
    metaEl.textContent = meta;
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function renderModelStatus(data) {
  const container = document.getElementById("modelDeck");
  if (!container) {
    return;
  }
  const modelStatus = data.model_status || {};
  const models = (modelStatus.models || []).map((name) => `<li>${escapeHtml(name)}</li>`).join("");
  const stateLabel = modelStatus.connected
    ? (modelStatus.default_model_ready ? "Ready" : "Connected")
    : "Offline";

  container.innerHTML = `
    <div class="memory-item">
      <span>Status</span>
      <strong>${escapeHtml(stateLabel)}</strong>
      <p>${modelStatus.connected ? `${modelStatus.model_count || 0} local model(s) detected.` : "Could not reach Ollama on 127.0.0.1:11434."}</p>
    </div>
    <div class="memory-item">
      <span>Default Model</span>
      <strong>${modelStatus.default_model_ready ? "Installed" : "Missing"}</strong>
      <p>${modelStatus.default_model_ready ? "The default SWG workspace model is available." : "Install qwen2.5:7b-instruct-q4_K_M to fully enable chat and generation."}</p>
    </div>
    <div class="memory-item">
      <span>Model List</span>
      <strong>${modelStatus.model_count || 0} registered</strong>
      <ul>${models || "<li>No Ollama models installed yet</li>"}</ul>
    </div>
  `;
}

function renderMemoryDeck(data) {
  const memory = data.memory || {};
  const deck = document.getElementById("memoryDeck");
  const feed = document.getElementById("memoryFeed");
  if (!deck || !feed) {
    return;
  }

  const recentKnowledge = (memory.recent_knowledge || []).map((item) => `
    <div class="memory-item">
      <span>${escapeHtml(item.kind || "observation")}</span>
      <strong>${escapeHtml(item.summary || "No summary")}</strong>
    </div>
  `).join("");

  const lessonItems = (memory.recent_lessons || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");

  deck.innerHTML = `
    <div class="memory-item">
      <span>Knowledge Rows</span>
      <strong>${memory.knowledge_count || 0}</strong>
      <p>Auto-captured observations from successful agent work.</p>
    </div>
    <div class="memory-item">
      <span>Lessons</span>
      <strong>${memory.lesson_count || 0}</strong>
      <p>${memory.run_count || 0} total recorded run(s).</p>
    </div>
    <div class="memory-item">
      <span>Recent Lessons</span>
      <ul>${lessonItems || "<li>No lessons stored yet</li>"}</ul>
    </div>
  `;

  feed.innerHTML = recentKnowledge || "<p>No persistent knowledge notes captured yet.</p>";
}

function renderWorkspaceHighlights(data) {
  const highlights = document.getElementById("workspaceHighlights");
  const projectIndex = data.project_index || {};
  const session = data.session || {};
  const executionBoundary = data.execution_boundary || {};
  const pendingApproval = data.approval_request || null;
  const modelStatus = data.model_status || {};
  const memory = data.memory || {};

  highlights.innerHTML = `
    <div class="highlight-card">
      <span class="metric-label">Indexed Files</span>
      <strong>${projectIndex.file_count || 0}</strong>
      <p>${projectIndex.chunk_count || 0} searchable chunks</p>
    </div>
    <div class="highlight-card">
      <span class="metric-label">Session Activity</span>
      <strong>${session.request_count || 0}</strong>
      <p>${session.active_requests || 0} active, ${session.approval_count || 0} approvals</p>
    </div>
    <div class="highlight-card">
      <span class="metric-label">Model Deck</span>
      <strong>${modelStatus.model_count || 0}</strong>
      <p>${modelStatus.default_model_ready ? "Default local model is ready" : "Default local model is not installed"}</p>
    </div>
    <div class="highlight-card">
      <span class="metric-label">Memory Deck</span>
      <strong>${memory.knowledge_count || 0}</strong>
      <p>${pendingApproval ? `Approval waiting for ${escapeHtml(pendingApproval.tool_name || "tool")}` : `${executionBoundary.mode || "guarded-local"} execution`}</p>
    </div>
  `;

  const sessionState = session.active_requests > 0 ? "Busy" : "Ready";
  const indexState = projectIndex.indexed_at ? `${projectIndex.file_count || 0} files` : "Not indexed";
  const boundaryState = executionBoundary.trusted_code_only ? "Trusted only" : "Expanded";
  document.getElementById("heroSession").textContent = sessionState;
  document.getElementById("heroIndex").textContent = indexState;
  document.getElementById("heroBoundary").textContent = boundaryState;
}

function renderFiles(data) {
  const files = document.getElementById("files");
  const uploads = data.uploads
    .map((name) => `<li><a href="/uploads/${name}" target="_blank">${name}</a></li>`)
    .join("");
  const generated = data.generated
    .map((name) => `<li><a href="/generated/${name}" target="_blank">${name}</a></li>`)
    .join("");
  const lessons = data.lessons.map((line) => `<li>${escapeHtml(line)}</li>`).join("");
  const projectIndex = data.project_index || {};
  const backgroundReindex = data.background_reindex || {};
  const indexedAt = projectIndex.indexed_at || "Not indexed yet";
  const indexMode = projectIndex.index_mode || "deep";
  const truncatedFileCount = projectIndex.truncated_file_count || 0;
  const indexLimits = projectIndex.index_limits || {};
  const roots = (projectIndex.roots || []).map((root) => `<li>${escapeHtml(root)}</li>`).join("");
  const summary = projectIndex.summary || {};
  const topSymbols = (summary.top_symbols || []).map((item) => `<li>${escapeHtml(item.name)} (${item.count})</li>`).join("");
  const topExtensions = (summary.top_extensions || []).map((item) => `<li>${escapeHtml(item.name)} (${item.count})</li>`).join("");
  const topImports = (summary.top_imports || []).map((item) => `<li>${escapeHtml(item.name)} (${item.count})</li>`).join("");
  const topInheritance = (summary.top_inheritance || []).map((item) => `<li>${escapeHtml(item.name)} (${item.count})</li>`).join("");
  const topFunctions = (summary.top_functions || []).map((item) => `<li>${escapeHtml(item.name)} (${item.count})</li>`).join("");
  const topConnectedSymbols = (summary.top_connected_symbols || []).map((item) => `<li>${escapeHtml(item.name)} (${item.count})</li>`).join("");
  const semanticTopTerms = (summary.semantic_top_terms || []).map((item) => `<li>${escapeHtml(item.name)} (${item.count})</li>`).join("");
  const graph = data.project_index && data.project_index.graph ? data.project_index.graph : {};
  const semantic = data.project_index && data.project_index.semantic ? data.project_index.semantic : {};
  const session = data.session || {};
  const executionBoundary = data.execution_boundary || {};

  renderWorkspaceHighlights(data);
  renderModelStatus(data);
  renderMemoryDeck(data);

  files.innerHTML = `
    <h3>Session</h3>
    <ul>
      <li>Session requests: ${session.request_count || 0}</li>
      <li>Active requests: ${session.active_requests || 0}</li>
      <li>Approvals requested: ${session.approval_count || 0}</li>
      <li>Tool parse failures: ${session.parse_failure_count || 0}</li>
      <li>Execution denied: ${session.execution_denied_count || 0}</li>
      <li>Execution errors: ${session.execution_error_count || 0}</li>
      <li>Last session error: ${escapeHtml(session.last_error || "None")}</li>
    </ul>
    <h3>Execution Boundary</h3>
    <ul>
      <li>Mode: ${escapeHtml(executionBoundary.mode || "unknown")}</li>
      <li>Trusted code only: ${executionBoundary.trusted_code_only ? "Yes" : "No"}</li>
      <li>${escapeHtml(executionBoundary.summary || "No execution boundary summary available.")}</li>
    </ul>
    <h3>Project Index</h3>
    <ul>
      <li>Indexed at: ${escapeHtml(indexedAt)}</li>
      <li>Files: ${projectIndex.file_count || 0}</li>
      <li>Chunks: ${projectIndex.chunk_count || 0}</li>
      <li>Mode: ${escapeHtml(indexMode)}</li>
      <li>Truncated for speed: ${truncatedFileCount}</li>
      <li>Per-file text cap: ${indexLimits.max_text_chars_per_file || 0}</li>
      <li>Average chunk size: ${summary.avg_chunk_size || 0}</li>
    </ul>
    <h3>Background Reindex</h3>
    <ul>
      <li>State: ${escapeHtml(backgroundReindex.state || "unknown")}</li>
      <li>Poll interval: ${backgroundReindex.poll_seconds || 0}s</li>
      <li>Last scan: ${escapeHtml(backgroundReindex.last_scan_at || "Not scanned yet")}</li>
      <li>Last change seen: ${escapeHtml(backgroundReindex.last_change_at || "No changes detected yet")}</li>
      <li>Last auto reindex: ${escapeHtml(backgroundReindex.last_reindex_at || "No auto reindex yet")}</li>
      <li>Error: ${escapeHtml(backgroundReindex.last_error || "None")}</li>
    </ul>
    <h3>Project Roots</h3>
    <ul>${roots || "<li>No roots configured</li>"}</ul>
    <h3>Top File Types</h3>
    <ul>${topExtensions || "<li>No indexed file types yet</li>"}</ul>
    <h3>Top Symbols</h3>
    <ul>${topSymbols || "<li>No symbols extracted yet</li>"}</ul>
    <h3>Top Imports</h3>
    <ul>${topImports || "<li>No imports extracted yet</li>"}</ul>
    <h3>Top Inheritance</h3>
    <ul>${topInheritance || "<li>No inheritance extracted yet</li>"}</ul>
    <h3>Top Functions</h3>
    <ul>${topFunctions || "<li>No functions extracted yet</li>"}</ul>
    <h3>Cross-File Graph</h3>
    <ul>
      <li>Graph files: ${graph.file_count || 0}</li>
      <li>Graph symbols: ${graph.symbol_count || 0}</li>
      <li>Graph edges: ${graph.edge_count || 0}</li>
    </ul>
    <h3>Top Connected Symbols</h3>
    <ul>${topConnectedSymbols || "<li>No graph relationships extracted yet</li>"}</ul>
    <h3>Semantic Retrieval</h3>
    <ul>
      <li>Semantic docs: ${semantic.doc_count || 0}</li>
      <li>Semantic vocab: ${semantic.vocab_size || 0}</li>
    </ul>
    <h3>Top Semantic Terms</h3>
    <ul>${semanticTopTerms || "<li>No semantic terms extracted yet</li>"}</ul>
    <h3>Uploads</h3>
    <ul>${uploads || "<li>No uploads yet</li>"}</ul>
    <h3>Generated</h3>
    <ul>${generated || "<li>No generated files yet</li>"}</ul>
    <h3>Recent Lessons</h3>
    <ul>${lessons || "<li>No lessons yet</li>"}</ul>
  `;
}

function renderApproval(data) {
  const container = document.getElementById("approvalBox");
  const approval = data.approval_request || null;
  if (!approval) {
    container.innerHTML = "";
    return;
  }

  container.innerHTML = `
    <div class="approval-card">
      <h3>Approval Required</h3>
      <p>${escapeHtml(approval.reason || "The agent is waiting for approval.")}</p>
      <pre>${escapeHtml(JSON.stringify(approval.arguments || {}, null, 2))}</pre>
      <div class="approval-actions">
        <button id="approveBtn">Approve Action</button>
        <button id="rejectBtn" class="reject-btn">Reject Action</button>
      </div>
    </div>
  `;

  document.getElementById("approveBtn").addEventListener("click", approvePendingAction);
  document.getElementById("rejectBtn").addEventListener("click", rejectPendingAction);
}

function renderTrust(data) {
  const container = document.getElementById("trustPanel");
  const trust = data.trust_report || {};
  if (!trust || !Object.keys(trust).length) {
    container.innerHTML = "";
    return;
  }

  const confidenceScore = Number(trust.confidence_score || 0);
  const confidenceLabel = trust.confidence_label || "Unknown";
  const basis = trust.basis || "No trust basis available yet.";
  const planningMode = trust.planning_mode || "general";
  const repoStrategy = trust.repo_strategy || "basic";
  const strategySteps = (trust.strategy_steps || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");
  const searchedQueries = (trust.searched_queries || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");
  const inspectedPaths = (trust.inspected_paths || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");
  const conclusions = (trust.conclusions || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");
  const nextActions = (trust.next_actions || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");
  const timeline = (trust.timeline || []).map((item) => {
    const kind = escapeHtml(item.kind || "event");
    const label = escapeHtml(item.label || "");
    const detail = item.detail ? `<code>${escapeHtml(item.detail)}</code>` : "";
    return `<li><span class="timeline-kind">${kind}</span><strong>${label}</strong>${detail}</li>`;
  }).join("");

  container.innerHTML = `
    <div class="trust-card">
      <div class="trust-head">
        <div>
          <p class="trust-label">Trust Report</p>
          <h3>${escapeHtml(confidenceLabel)} confidence</h3>
          <p class="trust-mode">${escapeHtml(planningMode)} mode | ${escapeHtml(repoStrategy)} strategy</p>
        </div>
        <div class="trust-meter">
          <div class="trust-meter-bar"><span style="width: ${Math.max(6, Math.min(100, confidenceScore))}%"></span></div>
          <p>${confidenceScore}/100</p>
        </div>
      </div>
      <p class="trust-basis">${escapeHtml(basis)}</p>
      <div class="trust-grid">
        <div class="trust-block">
          <h4>Strategy</h4>
          <ul>${strategySteps || "<li>No strategy steps yet</li>"}</ul>
        </div>
        <div class="trust-block">
          <h4>Searched</h4>
          <ul>${searchedQueries || "<li>No project searches yet</li>"}</ul>
        </div>
        <div class="trust-block">
          <h4>Inspected Files</h4>
          <ul>${inspectedPaths || "<li>No direct file inspection yet</li>"}</ul>
        </div>
        <div class="trust-block">
          <h4>Conclusions</h4>
          <ul>${conclusions || "<li>No explicit conclusions yet</li>"}</ul>
        </div>
        <div class="trust-block">
          <h4>Suggested Next Actions</h4>
          <ul>${nextActions || "<li>No next actions suggested yet</li>"}</ul>
        </div>
      </div>
      <div class="trust-block trust-timeline">
        <h4>Tool Timeline</h4>
        <ul>${timeline || "<li>No tool activity yet</li>"}</ul>
      </div>
    </div>
  `;
}

function renderInsights(data) {
  const container = document.getElementById("insights");
  const sections = [
    { title: "Updates", items: data.updates || [] },
    { title: "Figured Out", items: data.figured_out || [] },
    { title: "Ideas", items: data.ideas || [] },
  ];

  container.innerHTML = sections
    .map((section) => `
      <div class="insight-block">
        <h3>${section.title}</h3>
        <ul>${section.items.length ? section.items.map((item) => `<li>${escapeHtml(item)}</li>`).join("") : "<li>No updates yet</li>"}</ul>
      </div>
    `)
    .join("");
}

function formatResponse(data) {
  const session = data.session || {};
  const sessionSection = Object.keys(session).length
    ? `\n\nSession:\n- Requests: ${session.request_count || 0}\n- Active: ${session.active_requests || 0}\n- Approvals: ${session.approval_count || 0}\n- Parse failures: ${session.parse_failure_count || 0}\n- Execution denied: ${session.execution_denied_count || 0}\n- Execution errors: ${session.execution_error_count || 0}`
    : "";
  const toolSection = data.tool_events.length
    ? `\n\nTool activity:\n- ${data.tool_events.join("\n- ")}`
    : "";
  const createdSection = data.created_files.length
    ? `\n\nCreated files:\n- ${data.created_files.join("\n- ")}`
    : "";
  return `${data.reply}${createdSection}${toolSection}${sessionSection}`;
}

async function refreshFiles(options = {}) {
  const { silent = false } = options;
  try {
    const data = await fetchJson("/api/files");
    backendConnected = true;
    renderBackendStatus("online", "Local backend connected");
    setOutputStatus("Ready", data.approval_request ? "Approval waiting" : "Awaiting request");
    renderFiles(data);
    renderApproval(data);
    return data;
  } catch (error) {
    backendConnected = false;
    renderBackendStatus("offline", "Local backend offline. Retrying automatically...");
    setOutputStatus("Offline", "Retrying backend");
    renderApproval({});
    if (!silent) {
      const output = document.getElementById("output");
      if (!output.textContent || output.textContent === "Waiting for your first prompt." || output.textContent === "Failed to fetch") {
        output.textContent = "Local backend is offline right now. The page will keep retrying until it reconnects.";
      }
    }
    scheduleBackendRetry();
    return null;
  }
}

async function approvePendingAction() {
  const output = document.getElementById("output");
  output.textContent = "Approving action...";
  setOutputStatus("Working", "Approving action");
  try {
    const data = await fetchJson("/api/approval/approve", {
      method: "POST",
    });
    output.textContent = formatResponse(data);
    renderApproval(data);
    renderTrust(data);
    renderInsights(data);
    setOutputStatus("Ready", "Approval finished");
    await refreshFiles();
  } catch (error) {
    output.textContent = backendConnected ? error.message : "Backend is offline. Waiting to reconnect...";
    renderBackendStatus("offline", "Local backend offline. Retrying automatically...");
    setOutputStatus("Offline", "Retrying backend");
    scheduleBackendRetry();
  }
}

async function rejectPendingAction() {
  const output = document.getElementById("output");
  output.textContent = "Rejecting action...";
  setOutputStatus("Working", "Rejecting action");
  try {
    const data = await fetchJson("/api/approval/reject", {
      method: "POST",
    });
    output.textContent = formatResponse(data);
    renderApproval(data);
    renderTrust(data);
    renderInsights(data);
    setOutputStatus("Ready", "Approval rejected");
    await refreshFiles();
  } catch (error) {
    output.textContent = backendConnected ? error.message : "Backend is offline. Waiting to reconnect...";
    renderBackendStatus("offline", "Local backend offline. Retrying automatically...");
    setOutputStatus("Offline", "Retrying backend");
    scheduleBackendRetry();
  }
}

document.getElementById("chatBtn").addEventListener("click", async () => {
  const output = document.getElementById("output");
  if (!backendConnected) {
    output.textContent = "Backend is offline. Waiting to reconnect before sending chat...";
    scheduleBackendRetry();
    return;
  }
  output.textContent = "Thinking...";
  setOutputStatus("Working", "Running chat request");

  try {
    const data = await fetchJson("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: document.getElementById("model").value,
        message: document.getElementById("message").value,
      }),
    });

    output.textContent = formatResponse(data);
    renderApproval(data);
    renderTrust(data);
    renderInsights(data);
    setOutputStatus("Ready", data.requires_approval ? "Approval waiting" : "Chat complete");
    await refreshFiles();
  } catch (error) {
    output.textContent = backendConnected ? error.message : "Backend is offline. Waiting to reconnect...";
    renderBackendStatus("offline", "Local backend offline. Retrying automatically...");
    setOutputStatus("Offline", "Retrying backend");
    scheduleBackendRetry();
  }
});

document.getElementById("generateBtn").addEventListener("click", async () => {
  const output = document.getElementById("output");
  if (!backendConnected) {
    output.textContent = "Backend is offline. Waiting to reconnect before generating...";
    scheduleBackendRetry();
    return;
  }
  output.textContent = "Generating file...";
  setOutputStatus("Working", "Generating artifact");

  try {
    const data = await fetchJson("/api/generate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: document.getElementById("model").value,
        filename: document.getElementById("filename").value,
        instruction: document.getElementById("instruction").value,
      }),
    });

    output.textContent = formatResponse(data);
    renderApproval(data);
    renderTrust(data);
    renderInsights(data);
    setOutputStatus("Ready", data.requires_approval ? "Approval waiting" : "Artifact complete");
    await refreshFiles();
  } catch (error) {
    output.textContent = backendConnected ? error.message : "Backend is offline. Waiting to reconnect...";
    renderBackendStatus("offline", "Local backend offline. Retrying automatically...");
    setOutputStatus("Offline", "Retrying backend");
    scheduleBackendRetry();
  }
});

document.getElementById("uploadBtn").addEventListener("click", async () => {
  const output = document.getElementById("output");
  if (!backendConnected) {
    output.textContent = "Backend is offline. Waiting to reconnect before uploading...";
    scheduleBackendRetry();
    return;
  }
  const files = document.getElementById("fileInput").files;
  if (!files.length) {
    output.textContent = "Choose at least one file first.";
    return;
  }

  output.textContent = "Uploading...";
  setOutputStatus("Working", "Uploading files");

  try {
    for (const file of files) {
      const form = new FormData();
      form.append("file", file);
      await fetchJson("/api/upload", {
        method: "POST",
        body: form,
      });
    }
    output.textContent = "Upload complete.";
    setOutputStatus("Ready", "Upload complete");
    await refreshFiles();
  } catch (error) {
    output.textContent = backendConnected ? error.message : "Backend is offline. Waiting to reconnect...";
    renderBackendStatus("offline", "Local backend offline. Retrying automatically...");
    setOutputStatus("Offline", "Retrying backend");
    scheduleBackendRetry();
  }
});

document.getElementById("reindexBtn").addEventListener("click", async () => {
  const output = document.getElementById("output");
  if (!backendConnected) {
    output.textContent = "Backend is offline. Waiting to reconnect before reindexing...";
    scheduleBackendRetry();
    return;
  }
  output.textContent = "Rebuilding project index...";
  setOutputStatus("Working", "Rebuilding index");

  try {
    const data = await fetchJson("/api/project-index", {
      method: "POST",
    });
    output.textContent = `Project index rebuilt.\n\nFiles: ${data.file_count}\nChunks: ${data.chunk_count}\nIndexed at: ${data.indexed_at}`;
    if (data.index_mode) {
      output.textContent += `\nMode: ${data.index_mode}`;
    }
    if (data.truncated_file_count) {
      output.textContent += `\nTruncated for speed: ${data.truncated_file_count}`;
    }
    renderTrust({
      trust_report: {
        confidence_score: 72,
        confidence_label: "Medium",
        basis: "This status is based on a direct project indexing run.",
        planning_mode: "repo-aware",
        repo_strategy: "index-refresh",
        strategy_steps: [
          "Refresh indexed project data",
          "Ask a focused codebase question",
          "Search and inspect likely files",
          "Answer with evidence",
        ],
        searched_queries: [],
        inspected_paths: [],
        conclusions: data.summary && data.summary.top_symbols && data.summary.top_symbols.length
          ? [`Top extracted symbols include ${data.summary.top_symbols.slice(0, 5).map((item) => item.name).join(", ")}.`]
          : [],
        next_actions: ["Ask a repo-specific question next so the agent can use the fresh index."],
        timeline: [
          { kind: "action", label: "index_project", detail: "{}" },
        ],
      },
    });
    renderInsights({
      updates: [
        `Ingested ${data.file_count || 0} files into the project index.`,
        `Built ${data.chunk_count || 0} searchable code chunks.`,
      ],
      figured_out: data.summary && data.summary.top_symbols && data.summary.top_symbols.length
        ? [`Top extracted symbols: ${data.summary.top_symbols.slice(0, 5).map((item) => item.name).join(", ")}.`]
        : [],
      ideas: ["Ask a repo-specific question next so the agent can use the fresh index."],
    });
    setOutputStatus("Ready", "Index rebuild complete");
    await refreshFiles();
  } catch (error) {
    output.textContent = backendConnected ? error.message : "Backend is offline. Waiting to reconnect...";
    renderBackendStatus("offline", "Local backend offline. Retrying automatically...");
    setOutputStatus("Offline", "Retrying backend");
    scheduleBackendRetry();
  }
});

document.getElementById("clearPromptBtn").addEventListener("click", () => {
  document.getElementById("message").value = "";
  document.getElementById("message").focus();
  setOutputStatus("Ready", "Prompt cleared");
});

document.getElementById("samplePromptBtn").addEventListener("click", () => {
  document.getElementById("message").value = "Search the repo for login flow, inspect the strongest files, and summarize what actually handles authentication.";
  document.getElementById("message").focus();
  setOutputStatus("Ready", "Sample prompt loaded");
});

document.getElementById("copyOutputBtn").addEventListener("click", async () => {
  const output = document.getElementById("output").textContent || "";
  try {
    await navigator.clipboard.writeText(output);
    setOutputStatus("Ready", "Output copied");
  } catch (error) {
    setOutputStatus("Ready", "Copy failed");
  }
});

document.getElementById("clearOutputBtn").addEventListener("click", () => {
  document.getElementById("output").textContent = "Waiting for your first prompt.";
  setOutputStatus("Idle", "Output cleared");
});

setOutputStatus("Idle", "Awaiting request");
refreshFiles();
