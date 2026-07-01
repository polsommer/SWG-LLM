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

function setCouncilStatus(state, message) {
  const line = document.getElementById("councilStatusLine");
  const detail = document.getElementById("councilStatusMessage");
  if (line) {
    line.className = `backend-status backend-status-${state}`;
    line.textContent = message;
  }
  if (detail) {
    detail.textContent = message;
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

function renderCouncil(snapshot) {
  const settings = snapshot.settings || {};
  const decision = snapshot.decision || {};
  const tests = snapshot.tests || {};
  const git = snapshot.git || {};
  const publish = decision.git_publish || null;
  const transcript = snapshot.transcript || [];
  const votes = snapshot.votes || [];
  const context = snapshot.context || {};
  const intelligence = context.intelligence || {};
  const workspaceLearning = context.workspace_learning || {};

  document.getElementById("councilEnabled").checked = !!settings.enabled;
  document.getElementById("councilAutoCommit").checked = !!settings.auto_commit_enabled;
  document.getElementById("councilAutoPush").checked = !!settings.auto_push_enabled;
  document.getElementById("councilPollSeconds").value = settings.poll_seconds || 45;
  document.getElementById("councilThreshold").value = settings.auto_approve_threshold || 2;
  document.getElementById("councilModel").value = settings.model || "qwen2.5:7b-instruct-q4_K_M";
  document.getElementById("councilTestCommand").value = settings.test_command || "";

  document.getElementById("councilSummary").innerHTML = `
    <div class="highlight-card">
      <span class="metric-label">State</span>
      <strong>${escapeHtml(snapshot.state || "idle")}</strong>
      <p>Last run: ${escapeHtml(snapshot.last_run_at || "Not run yet")}</p>
    </div>
    <div class="highlight-card">
      <span class="metric-label">Decision</span>
      <strong>${decision.approved ? "Approved" : "Needs Revision"}</strong>
      <p>${escapeHtml(decision.rationale || "No decision yet.")}</p>
    </div>
    <div class="highlight-card">
      <span class="metric-label">Votes</span>
      <strong>${votes.length}</strong>
      <p>${decision.approve_votes || 0} approve, ${decision.revise_votes || 0} revise, ${decision.reject_votes || 0} reject</p>
    </div>
    <div class="highlight-card">
      <span class="metric-label">Git Publish</span>
      <strong>${publish ? (publish.pushed ? "Pushed" : publish.committed ? "Committed" : "No Commit") : "Idle"}</strong>
      <p>${escapeHtml(publish ? publish.message || "Council attempted Git publish." : "No Git publish action recorded yet.")}</p>
    </div>
    <div class="highlight-card">
      <span class="metric-label">Inference Inputs</span>
      <strong>${(intelligence.suggested_tasks || []).length}</strong>
      <p>${escapeHtml(intelligence.last_run_at ? "Council is using background focus areas and suggested tasks." : "No background inference snapshot yet.")}</p>
    </div>
    <div class="highlight-card">
      <span class="metric-label">Learning Inputs</span>
      <strong>${(workspaceLearning.recent_items || []).length}</strong>
      <p>${escapeHtml(workspaceLearning.last_run_at ? "Council is reading recent learned-file conclusions." : "No learned-file snapshot yet.")}</p>
    </div>
  `;

  document.getElementById("councilTests").innerHTML = `
    <h3>Latest Test Command</h3>
    <ul>
      <li>Command: ${escapeHtml(tests.command || "Not run yet")}</li>
      <li>Success: ${tests.success ? "Yes" : "No"}</li>
      <li>Return code: ${escapeHtml(tests.return_code ?? "n/a")}</li>
      <li>Duration: ${escapeHtml(tests.duration_seconds ?? "n/a")}s</li>
    </ul>
    <h3>Stdout Tail</h3>
    <pre>${escapeHtml(tests.stdout_tail || "No stdout captured.")}</pre>
    <h3>Stderr Tail</h3>
    <pre>${escapeHtml(tests.stderr_tail || "No stderr captured.")}</pre>
  `;

  const entries = (git.entries || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");
  document.getElementById("councilGit").innerHTML = `
    <div class="memory-item">
      <span>Branch</span>
      <strong>${escapeHtml(git.branch || "unknown")}</strong>
      <p>Origin: ${escapeHtml(git.origin_url || "not configured")}</p>
    </div>
    <div class="memory-item">
      <span>Expected Remote Match</span>
      <strong>${git.origin_matches_expected ? "Yes" : "No"}</strong>
      <p>${escapeHtml(git.diff_stat || "No diff stat available.")}</p>
    </div>
    <div class="memory-item">
      <span>Worktree Entries</span>
      <ul>${entries || "<li>No worktree changes detected</li>"}</ul>
    </div>
    <div class="memory-item">
      <span>Focus Areas</span>
      <ul>${(intelligence.focus_areas || []).map((item) => `<li>${escapeHtml(item.title || "Focus area")}</li>`).join("") || "<li>No inferred focus areas yet</li>"}</ul>
    </div>
    <div class="memory-item">
      <span>Learned Files</span>
      <ul>${(workspaceLearning.recent_items || []).map((item) => `<li>${escapeHtml(item.source_path || "workspace file")}</li>`).join("") || "<li>No learned files available yet</li>"}</ul>
    </div>
  `;

  const transcriptCards = transcript.map((item) => `
    <article class="work-card">
      <div class="work-card-head">
        <div>
          <span class="work-priority">${escapeHtml(item.vote || item.role || "note")}</span>
          <h4>${escapeHtml(item.speaker || "Council")}</h4>
        </div>
      </div>
      <p>${escapeHtml(item.message || "No transcript entry.")}</p>
    </article>
  `).join("");
  document.getElementById("councilTranscript").innerHTML = transcriptCards || '<article class="work-card"><h4>No council talk yet</h4><p>Run the council once or wait for the background cycle to detect worktree changes.</p></article>';
}

async function refreshCouncil() {
  try {
    const data = await fetchJson("/api/council");
    renderCouncil(data.background_council || {});
    const snapshot = data.background_council || {};
    if (snapshot.last_error) {
      setCouncilStatus("offline", `Council error: ${snapshot.last_error}`);
    } else {
      setCouncilStatus("online", `Council ${snapshot.state || "idle"}${snapshot.last_run_at ? ` | last run ${snapshot.last_run_at}` : ""}`);
    }
  } catch (error) {
    setCouncilStatus("offline", error.message || "Failed to load council status.");
  }
}

document.getElementById("saveCouncilSettingsBtn").addEventListener("click", async () => {
  setCouncilStatus("pending", "Saving council settings...");
  try {
    await fetchJson("/api/council/settings", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        enabled: document.getElementById("councilEnabled").checked,
        auto_commit_enabled: document.getElementById("councilAutoCommit").checked,
        auto_push_enabled: document.getElementById("councilAutoPush").checked,
        poll_seconds: Number(document.getElementById("councilPollSeconds").value || 45),
        auto_approve_threshold: Number(document.getElementById("councilThreshold").value || 2),
        model: document.getElementById("councilModel").value,
        test_command: document.getElementById("councilTestCommand").value,
      }),
    });
    setCouncilStatus("online", "Council settings saved.");
    await refreshCouncil();
  } catch (error) {
    setCouncilStatus("offline", error.message || "Saving council settings failed.");
  }
});

document.getElementById("runCouncilBtn").addEventListener("click", async () => {
  const runBtn = document.getElementById("runCouncilBtn");
  runBtn.disabled = true;
  setCouncilStatus("pending", "Running council now. Tests and debate may take a little while...");
  try {
    await fetchJson("/api/council/run", {
      method: "POST",
    });
    setCouncilStatus("online", "Council run completed.");
    await refreshCouncil();
  } catch (error) {
    setCouncilStatus("offline", error.message || "Council run failed.");
  } finally {
    runBtn.disabled = false;
  }
});

refreshCouncil();
window.setInterval(refreshCouncil, 5000);
