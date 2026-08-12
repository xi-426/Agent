const state = {
    auth: readStoredAuth(),
    view: "chat",
    sessions: [],
    knowledgeBases: [],
    documents: [],
    selectedSessionId: null,
    selectedKnowledgeBaseId: null,
    sidebarFilter: "",
    evaluationRequest: null
};

const AUTH_STORAGE_KEY = "zhiyu-agent-auth";

const elements = {
    authScreen: document.querySelector("#auth-screen"),
    authError: document.querySelector("#auth-error"),
    loginForm: document.querySelector("#login-form"),
    registerForm: document.querySelector("#register-form"),
    guestButton: document.querySelector("#guest-button"),
    accountButton: document.querySelector("#account-button"),
    currentUserLabel: document.querySelector("#current-user-label"),
    railButtons: document.querySelectorAll(".rail-button[data-view]"),
    views: document.querySelectorAll(".app-view"),
    panelKicker: document.querySelector("#panel-kicker"),
    panelTitle: document.querySelector("#panel-title"),
    newResourceButton: document.querySelector("#new-resource-button"),
    sidebarSearch: document.querySelector("#sidebar-search"),
    sidebarList: document.querySelector("#sidebar-list"),
    chatTitle: document.querySelector("#chat-title"),
    chatSubtitle: document.querySelector("#chat-subtitle"),
    chatForm: document.querySelector("#chat-form"),
    messageInput: document.querySelector("#message"),
    messages: document.querySelector("#messages"),
    sendButton: document.querySelector("#send-button"),
    statusText: document.querySelector("#status"),
    characterCount: document.querySelector("#character-count"),
    agentQuickActions: document.querySelector("#agent-quick-actions"),
    knowledgeEmpty: document.querySelector("#knowledge-empty"),
    knowledgeContent: document.querySelector("#knowledge-content"),
    knowledgeTitle: document.querySelector("#knowledge-title"),
    knowledgeDescription: document.querySelector("#knowledge-description"),
    knowledgeIdBadge: document.querySelector("#knowledge-id-badge"),
    emptyCreateKb: document.querySelector("#empty-create-kb"),
    uploadForm: document.querySelector("#upload-form"),
    documentFile: document.querySelector("#document-file"),
    fileLabel: document.querySelector("#file-label"),
    uploadButton: document.querySelector("#upload-button"),
    uploadResult: document.querySelector("#upload-result"),
    documentCount: document.querySelector("#document-count"),
    documentList: document.querySelector("#document-list"),
    ragMessages: document.querySelector("#rag-messages"),
    ragForm: document.querySelector("#rag-form"),
    ragQuestion: document.querySelector("#rag-question"),
    ragSendButton: document.querySelector("#rag-send-button"),
    evaluationKb: document.querySelector("#evaluation-kb"),
    evaluationFile: document.querySelector("#evaluation-file"),
    runEvaluationButton: document.querySelector("#run-evaluation-button"),
    evaluationStatus: document.querySelector("#evaluation-status"),
    evaluationResults: document.querySelector("#evaluation-results"),
    metricTotal: document.querySelector("#metric-total"),
    metricHit: document.querySelector("#metric-hit"),
    metricAccuracy: document.querySelector("#metric-accuracy"),
    metricConfig: document.querySelector("#metric-config"),
    createSessionDialog: document.querySelector("#create-session-dialog"),
    createSessionForm: document.querySelector("#create-session-form"),
    sessionTitleInput: document.querySelector("#session-title-input"),
    createKbDialog: document.querySelector("#create-kb-dialog"),
    createKbForm: document.querySelector("#create-kb-form"),
    kbNameInput: document.querySelector("#kb-name-input"),
    kbDescriptionInput: document.querySelector("#kb-description-input"),
    toast: document.querySelector("#toast")
};

let toastTimer;

function readStoredAuth() {
    try {
        const auth = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY));
        if (!auth?.accessToken || (auth.expiresAt && auth.expiresAt <= Date.now())) {
            localStorage.removeItem(AUTH_STORAGE_KEY);
            return null;
        }
        return auth;
    } catch {
        localStorage.removeItem(AUTH_STORAGE_KEY);
        return null;
    }
}

function storeAuth(response) {
    state.auth = {
        ...response,
        expiresAt: Date.now() + response.expiresInSeconds * 1000
    };
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(state.auth));
}

function clearAuth() {
    state.auth = null;
    state.sessions = [];
    state.knowledgeBases = [];
    state.documents = [];
    state.selectedSessionId = null;
    state.selectedKnowledgeBaseId = null;
    localStorage.removeItem(AUTH_STORAGE_KEY);
}

async function api(path, options = {}) {
    const method = options.method || "GET";
    const headers = new Headers(options.headers || {});
    const requiresAuth = options.auth !== false;

    if (requiresAuth && state.auth?.accessToken) {
        headers.set("Authorization", `Bearer ${state.auth.accessToken}`);
    }

    let body = options.body;
    if (body && !(body instanceof FormData)) {
        headers.set("Content-Type", "application/json");
        body = JSON.stringify(body);
    }

    const response = await fetch(path, {method, headers, body});
    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json")
        ? await response.json().catch(() => null)
        : await response.text().catch(() => "");

    if (!response.ok) {
        if (response.status === 401 && requiresAuth && state.auth) {
            clearAuth();
            showAuthScreen();
            renderUserState();
        }
        const requestId = payload?.requestId ? `（请求编号：${payload.requestId}）` : "";
        throw new Error(`${payload?.message || "请求失败"}${requestId}`);
    }
    return payload;
}

function showToast(message, type = "success") {
    clearTimeout(toastTimer);
    elements.toast.textContent = message;
    elements.toast.className = `toast show ${type === "error" ? "error" : ""}`.trim();
    toastTimer = setTimeout(() => {
        elements.toast.classList.remove("show");
    }, 3200);
}

function showAuthScreen() {
    elements.authScreen.classList.remove("hidden");
}

function hideAuthScreen() {
    elements.authScreen.classList.add("hidden");
    elements.authError.textContent = "";
}

function renderUserState() {
    if (state.auth) {
        elements.currentUserLabel.textContent = `${state.auth.displayName} · ${state.auth.email}`;
        elements.accountButton.title = "退出登录";
        elements.accountButton.setAttribute("aria-label", "退出登录");
    } else {
        elements.currentUserLabel.textContent = "游客模式 · 点击设置登录";
        elements.accountButton.title = "登录";
        elements.accountButton.setAttribute("aria-label", "登录");
    }
}

async function loadWorkspaceData() {
    if (!state.auth) {
        renderSidebar();
        updateEvaluationKnowledgeBases();
        return;
    }
    elements.sidebarList.innerHTML = '<div class="sidebar-loading">正在载入…</div>';
    try {
        const [sessions, knowledgeBases] = await Promise.all([
            api("/api/v1/chat/sessions"),
            api("/api/v1/knowledge-bases")
        ]);
        state.sessions = sessions;
        state.knowledgeBases = knowledgeBases;
        updateEvaluationKnowledgeBases();
        renderSidebar();
    } catch (error) {
        showToast(error.message, "error");
        renderSidebar();
    }
}

function switchView(view) {
    if (view !== "chat" && !state.auth) {
        showAuthScreen();
        showToast("登录后才能使用知识库和评测", "error");
        return;
    }

    state.view = view;
    elements.railButtons.forEach(button => {
        button.classList.toggle("active", button.dataset.view === view);
    });
    elements.views.forEach(viewElement => {
        viewElement.classList.toggle("active", viewElement.id === `view-${view}`);
    });

    const panelConfig = {
        chat: ["ZHIYU AGENT", "会话", "搜索会话", "新建 Agent 会话"],
        knowledge: ["KNOWLEDGE BASE", "知识库", "搜索知识库", "新建知识库"],
        evaluation: ["RAG EVALUATION", "评测目标", "搜索知识库", "评测使用已有知识库"]
    }[view];

    elements.panelKicker.textContent = panelConfig[0];
    elements.panelTitle.textContent = panelConfig[1];
    elements.sidebarSearch.placeholder = panelConfig[2];
    elements.newResourceButton.title = panelConfig[3];
    elements.newResourceButton.setAttribute("aria-label", panelConfig[3]);
    elements.newResourceButton.hidden = view === "evaluation";
    elements.sidebarSearch.value = "";
    state.sidebarFilter = "";
    renderSidebar();
}

function renderSidebar() {
    elements.sidebarList.replaceChildren();
    const filter = state.sidebarFilter.toLowerCase();

    if (state.view === "chat") {
        elements.sidebarList.append(createSidebarItem({
            id: null,
            title: "临时流式对话",
            subtitle: "无需登录，不保存记录",
            avatar: "流",
            avatarClass: "avatar-agent",
            active: state.selectedSessionId === null,
            onClick: () => selectSession(null)
        }));

        if (!state.auth) {
            appendSidebarEmpty("登录后可创建长期会话，并使用记忆与待办工具。");
            return;
        }

        appendSectionLabel("我的 Agent 会话");
        const sessions = state.sessions.filter(session =>
            (session.title || "未命名会话").toLowerCase().includes(filter));
        if (!sessions.length) {
            appendSidebarEmpty(filter ? "没有匹配的会话" : "点击右上角 + 创建第一个会话");
            return;
        }
        sessions.forEach(session => {
            elements.sidebarList.append(createSidebarItem({
                id: session.id,
                title: session.title || "未命名会话",
                subtitle: "记忆 · 待办 Tool Calling",
                avatar: "智",
                avatarClass: "avatar-data",
                active: state.selectedSessionId === session.id,
                badge: `#${session.id}`,
                onClick: () => selectSession(session.id)
            }));
        });
        return;
    }

    const knowledgeBases = state.knowledgeBases.filter(item =>
        item.name.toLowerCase().includes(filter));
    if (!knowledgeBases.length) {
        appendSidebarEmpty(filter ? "没有匹配的知识库" : "点击右上角 + 创建第一个知识库");
        return;
    }

    knowledgeBases.forEach(knowledgeBase => {
        elements.sidebarList.append(createSidebarItem({
            id: knowledgeBase.id,
            title: knowledgeBase.name,
            subtitle: knowledgeBase.description || "暂无描述",
            avatar: "知",
            avatarClass: "avatar-rag",
            active: state.selectedKnowledgeBaseId === knowledgeBase.id,
            badge: `#${knowledgeBase.id}`,
            onClick: async () => {
                await selectKnowledgeBase(knowledgeBase.id);
                if (state.view === "evaluation") {
                    elements.evaluationKb.value = String(knowledgeBase.id);
                }
            }
        }));
    });
}

function createSidebarItem(config) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `conversation ${config.active ? "active" : ""}`.trim();
    button.addEventListener("click", config.onClick);

    const avatar = document.createElement("span");
    avatar.className = `avatar ${config.avatarClass}`;
    avatar.textContent = config.avatar;

    const copy = document.createElement("span");
    copy.className = "conversation-copy";
    const meta = document.createElement("span");
    meta.className = "conversation-meta";
    const title = document.createElement("strong");
    title.textContent = config.title;
    meta.append(title);
    if (config.badge) {
        const badge = document.createElement("span");
        badge.className = "mini-badge";
        badge.textContent = config.badge;
        meta.append(badge);
    }
    const subtitle = document.createElement("span");
    subtitle.className = "conversation-preview";
    subtitle.textContent = config.subtitle;
    copy.append(meta, subtitle);
    button.append(avatar, copy);
    return button;
}

function appendSectionLabel(text) {
    const label = document.createElement("div");
    label.className = "section-label";
    label.textContent = text;
    elements.sidebarList.append(label);
}

function appendSidebarEmpty(text) {
    const empty = document.createElement("div");
    empty.className = "sidebar-empty";
    empty.textContent = text;
    elements.sidebarList.append(empty);
}

async function selectSession(sessionId) {
    state.selectedSessionId = sessionId;
    elements.agentQuickActions.classList.toggle("hidden", sessionId === null);
    renderSidebar();
    if (sessionId === null) {
        elements.chatTitle.textContent = "临时流式对话";
        elements.chatSubtitle.innerHTML = "<i></i>无需登录 · 不保存记录";
        resetChatMessages("你好！当前是临时流式对话。登录后新建 Agent 会话，即可使用近期消息记忆、待办查询和二次确认创建。");
        return;
    }

    const session = state.sessions.find(item => item.id === sessionId);
    elements.chatTitle.textContent = session?.title || "Agent 会话";
    elements.chatSubtitle.innerHTML = "<i></i>Redis 记忆 · PostgreSQL 历史 · 待办工具";
    elements.messages.innerHTML = '<div class="message-date">正在加载历史消息…</div>';
    try {
        const history = await api(`/api/v1/chat/sessions/${sessionId}/messages`);
        elements.messages.innerHTML = '<div class="message-date">最近消息</div>';
        if (!history.length) {
            appendChatMessage("assistant", "这是一个新的 Agent 会话。你可以让我记住信息、查询待办，或者准备创建待办事项。", "", false);
        } else {
            history.forEach(message => {
                appendChatMessage(message.role === "USER" ? "user" : "assistant", message.content, "", false);
            });
        }
        scrollChatToLatest();
    } catch (error) {
        resetChatMessages(error.message, true);
    }
}

function resetChatMessages(message, error = false) {
    elements.messages.innerHTML = '<div class="message-date">今天</div>';
    appendChatMessage("assistant", message, error ? "error" : "", false);
}

function appendChatMessage(role, content, extraClass = "", scroll = true) {
    const article = document.createElement("article");
    article.className = `message-row ${role} ${extraClass}`.trim();
    const avatar = document.createElement("span");
    avatar.className = role === "user" ? "avatar avatar-user" : "avatar avatar-agent";
    avatar.textContent = role === "user" ? "我" : "智";
    avatar.setAttribute("aria-hidden", "true");
    const wrapper = document.createElement("div");
    wrapper.className = "message-content";
    const sender = document.createElement("span");
    sender.className = "sender-name";
    sender.textContent = role === "user" ? "我" : "知屿助手";
    const bubble = document.createElement("div");
    bubble.className = "message-bubble";
    const paragraph = document.createElement("p");
    paragraph.textContent = content;
    const time = document.createElement("time");
    time.textContent = formatTime(new Date());
    bubble.append(paragraph);
    wrapper.append(sender, bubble, time);
    article.append(avatar, wrapper);
    elements.messages.append(article);
    if (scroll) scrollChatToLatest();
    return {article, bubble, paragraph};
}

function showTypingIndicator(container) {
    const dots = document.createElement("span");
    dots.className = "typing-dots";
    dots.setAttribute("aria-label", "助手正在输入");
    dots.innerHTML = "<i></i><i></i><i></i>";
    container.append(dots);
    return dots;
}

function formatTime(date) {
    return new Intl.DateTimeFormat("zh-CN", {hour: "2-digit", minute: "2-digit", hour12: false}).format(date);
}

function scrollChatToLatest() {
    elements.messages.scrollTop = elements.messages.scrollHeight;
}

function updateChatInput() {
    elements.characterCount.textContent = elements.messageInput.value.length;
    elements.messageInput.style.height = "auto";
    elements.messageInput.style.height = `${Math.min(elements.messageInput.scrollHeight, 150)}px`;
}

function readSseEvent(eventText) {
    return eventText.split(/\r?\n/)
        .filter(line => line.startsWith("data:"))
        .map(line => line.slice(5).replace(/^ /, ""))
        .join("\n");
}

async function streamPublicMessage(message, onChunk) {
    const response = await fetch("/api/v1/chat/stream", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({message})
    });
    if (!response.ok) {
        const payload = await response.json().catch(() => null);
        throw new Error(payload?.message || "请求失败");
    }
    if (!response.body) throw new Error("浏览器没有收到流式响应");

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    while (true) {
        const {done, value} = await reader.read();
        buffer += decoder.decode(value, {stream: !done});
        const events = buffer.split(/\r?\n\r?\n/);
        buffer = events.pop() || "";
        events.forEach(eventText => {
            const chunk = readSseEvent(eventText);
            if (chunk && chunk !== "[DONE]") onChunk(chunk);
        });
        if (done) break;
    }
    const lastChunk = readSseEvent(buffer);
    if (lastChunk && lastChunk !== "[DONE]") onChunk(lastChunk);
}

async function submitChat(message) {
    appendChatMessage("user", message);
    elements.sendButton.disabled = true;
    elements.statusText.textContent = state.selectedSessionId === null ? "正在接收流式回答…" : "Agent 正在处理…";
    const answerMessage = appendChatMessage("assistant", "", "is-streaming");
    const dots = showTypingIndicator(answerMessage.bubble);

    try {
        if (state.selectedSessionId === null) {
            let answer = "";
            await streamPublicMessage(message, chunk => {
                dots.remove();
                answer += chunk;
                answerMessage.paragraph.textContent = answer;
                scrollChatToLatest();
            });
            if (!answer) answerMessage.paragraph.textContent = "模型没有返回文字内容。";
        } else {
            const response = await api(`/api/v1/chat/sessions/${state.selectedSessionId}`, {
                method: "POST",
                body: {message}
            });
            dots.remove();
            answerMessage.paragraph.textContent = response.answer;
        }
        elements.statusText.textContent = "回答完成";
    } catch (error) {
        dots.remove();
        answerMessage.article.classList.add("error");
        answerMessage.paragraph.textContent = error.message;
        elements.statusText.textContent = "请求失败";
    } finally {
        answerMessage.article.classList.remove("is-streaming");
        elements.sendButton.disabled = false;
        elements.messageInput.focus();
        scrollChatToLatest();
    }
}

async function selectKnowledgeBase(knowledgeBaseId) {
    state.selectedKnowledgeBaseId = knowledgeBaseId;
    renderSidebar();
    const knowledgeBase = state.knowledgeBases.find(item => item.id === knowledgeBaseId);
    if (!knowledgeBase) return;
    elements.knowledgeEmpty.hidden = true;
    elements.knowledgeContent.hidden = false;
    elements.knowledgeTitle.textContent = knowledgeBase.name;
    elements.knowledgeDescription.textContent = knowledgeBase.description || "暂无描述";
    elements.knowledgeIdBadge.textContent = `知识库 #${knowledgeBase.id}`;
    elements.ragMessages.innerHTML = '<div class="rag-welcome">问题会先经过 Top 8 向量召回和余弦距离门控，再交给模型回答。</div>';
    await loadDocuments();
}

async function loadDocuments() {
    if (!state.selectedKnowledgeBaseId) return;
    elements.documentList.innerHTML = '<div class="list-empty">正在读取文档…</div>';
    try {
        state.documents = await api(`/api/v1/knowledge-bases/${state.selectedKnowledgeBaseId}/documents`);
        renderDocuments();
    } catch (error) {
        elements.documentList.innerHTML = "";
        const empty = document.createElement("div");
        empty.className = "list-empty";
        empty.textContent = error.message;
        elements.documentList.append(empty);
    }
}

function renderDocuments() {
    elements.documentCount.textContent = `${state.documents.length} 份`;
    elements.documentList.replaceChildren();
    if (!state.documents.length) {
        const empty = document.createElement("div");
        empty.className = "list-empty";
        empty.textContent = "还没有文档，请从上方选择文件上传。";
        elements.documentList.append(empty);
        return;
    }
    state.documents.forEach(knowledgeDocument => {
        const row = window.document.createElement("article");
        row.className = "document-item";
        const type = window.document.createElement("span");
        type.className = "document-type";
        type.textContent = fileExtension(knowledgeDocument.originalName);
        const copy = window.document.createElement("span");
        copy.className = "document-copy";
        const name = window.document.createElement("strong");
        name.textContent = knowledgeDocument.originalName;
        const meta = window.document.createElement("small");
        meta.textContent = `${formatBytes(knowledgeDocument.sizeBytes)} · ${knowledgeDocument.contentType}`;
        copy.append(name, meta);
        const status = window.document.createElement("span");
        status.className = `document-status ${knowledgeDocument.status === "FAILED" ? "failed" : ""}`.trim();
        status.textContent = knowledgeDocument.status;
        row.append(type, copy, status);
        elements.documentList.append(row);
    });
}

function fileExtension(name) {
    const extension = name.split(".").pop()?.toUpperCase();
    return extension && extension.length <= 5 ? extension : "FILE";
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function appendRagMessage(role, text, sources = []) {
    elements.ragMessages.querySelector(".rag-welcome")?.remove();
    const row = document.createElement("article");
    row.className = `rag-message ${role}`;
    const body = document.createElement("div");
    body.className = "rag-message-body";
    const answer = document.createElement("div");
    answer.textContent = text;
    body.append(answer);

    if (sources.length) {
        const sourceList = document.createElement("div");
        sourceList.className = "source-list";
        sources.forEach((source, index) => {
            const item = document.createElement("article");
            item.className = "source-item";
            const title = document.createElement("strong");
            title.textContent = `资料${index + 1} · ${source.documentName} · 切片${source.chunkIndex} · 距离${source.distance.toFixed(4)}`;
            const content = document.createElement("p");
            content.textContent = source.content;
            item.append(title, content);
            sourceList.append(item);
        });
        body.append(sourceList);
    }
    row.append(body);
    elements.ragMessages.append(row);
    elements.ragMessages.scrollTop = elements.ragMessages.scrollHeight;
    return body;
}

function updateEvaluationKnowledgeBases() {
    const previous = elements.evaluationKb.value;
    elements.evaluationKb.replaceChildren();
    if (!state.knowledgeBases.length) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = "请先创建知识库";
        elements.evaluationKb.append(option);
        elements.runEvaluationButton.disabled = true;
        return;
    }
    state.knowledgeBases.forEach(knowledgeBase => {
        const option = document.createElement("option");
        option.value = String(knowledgeBase.id);
        option.textContent = `${knowledgeBase.name} (#${knowledgeBase.id})`;
        elements.evaluationKb.append(option);
    });
    elements.evaluationKb.value = previous || String(state.selectedKnowledgeBaseId || state.knowledgeBases[0].id);
    elements.runEvaluationButton.disabled = false;
}

function renderEvaluation(result) {
    const test = result.testMetrics;
    const config = result.selectedConfiguration;
    elements.metricTotal.textContent = String(
        result.calibrationMetrics.totalCases + (test?.totalCases || 0));
    elements.metricHit.textContent = test
        ? `${Math.round(test.hitAtK * 1000) / 10}%`
        : "无测试集";
    elements.metricAccuracy.textContent = test
        ? `${Math.round(test.decisionAccuracy * 1000) / 10}%`
        : "无测试集";
    elements.metricConfig.textContent = `Top ${config.topK}`;
    elements.evaluationStatus.textContent = `余弦距离阈值 ${config.maxDistance.toFixed(4)} · 纯向量排序`;
    elements.evaluationResults.replaceChildren();

    result.rawRetrievalCurve.forEach(item => {
        const row = document.createElement("tr");
        appendCell(row, `K=${item.k}`);
        appendCell(row, `${Math.round(item.hitAtK * 1000) / 10}%`);
        appendCell(row, `${Math.round(item.recallAtK * 1000) / 10}%`);
        appendCell(row, item.mrrAtK.toFixed(3));
        appendCell(row, item.ndcgAtK.toFixed(3));
        appendCell(row, item.k === config.topK ? "选定" : "—");
        elements.evaluationResults.append(row);
    });
}

function appendCell(row, text) {
    const cell = document.createElement("td");
    cell.textContent = text;
    row.append(cell);
}

function appendResultCell(row, text, type) {
    const cell = document.createElement("td");
    const badge = document.createElement("span");
    badge.className = `result-${type}`;
    badge.textContent = text;
    cell.append(badge);
    row.append(cell);
}

async function authenticate(endpoint, payload, submitButton) {
    submitButton.disabled = true;
    elements.authError.textContent = "";
    try {
        const response = await api(endpoint, {method: "POST", body: payload, auth: false});
        storeAuth(response);
        hideAuthScreen();
        renderUserState();
        await loadWorkspaceData();
        showToast(`欢迎，${response.displayName}`);
    } catch (error) {
        elements.authError.textContent = error.message;
    } finally {
        submitButton.disabled = false;
    }
}

function openCreateDialog() {
    if (!state.auth) {
        showAuthScreen();
        return;
    }
    if (state.view === "chat") {
        elements.createSessionDialog.showModal();
        elements.sessionTitleInput.focus();
    } else if (state.view === "knowledge") {
        elements.createKbDialog.showModal();
        elements.kbNameInput.focus();
    }
}

function bindEvents() {
    document.querySelectorAll(".auth-tab").forEach(tab => {
        tab.addEventListener("click", () => {
            document.querySelectorAll(".auth-tab").forEach(item => item.classList.toggle("active", item === tab));
            elements.loginForm.classList.toggle("active", tab.dataset.authTab === "login");
            elements.registerForm.classList.toggle("active", tab.dataset.authTab === "register");
            elements.authError.textContent = "";
        });
    });

    elements.loginForm.addEventListener("submit", event => {
        event.preventDefault();
        authenticate("/api/v1/auth/login", {
            email: document.querySelector("#login-email").value.trim(),
            password: document.querySelector("#login-password").value
        }, event.submitter);
    });

    elements.registerForm.addEventListener("submit", event => {
        event.preventDefault();
        authenticate("/api/v1/auth/register", {
            displayName: document.querySelector("#register-name").value.trim(),
            email: document.querySelector("#register-email").value.trim(),
            password: document.querySelector("#register-password").value
        }, event.submitter);
    });

    elements.guestButton.addEventListener("click", () => {
        hideAuthScreen();
        switchView("chat");
        selectSession(null);
    });

    elements.accountButton.addEventListener("click", () => {
        if (!state.auth) {
            showAuthScreen();
            return;
        }
        if (window.confirm("确定退出当前账号吗？")) {
            clearAuth();
            renderUserState();
            switchView("chat");
            selectSession(null);
            showAuthScreen();
        }
    });

    elements.railButtons.forEach(button => button.addEventListener("click", () => switchView(button.dataset.view)));
    elements.newResourceButton.addEventListener("click", openCreateDialog);
    elements.emptyCreateKb.addEventListener("click", () => {
        if (state.auth) elements.createKbDialog.showModal();
        else showAuthScreen();
    });

    elements.sidebarSearch.addEventListener("input", () => {
        state.sidebarFilter = elements.sidebarSearch.value.trim();
        renderSidebar();
    });

    elements.chatForm.addEventListener("submit", async event => {
        event.preventDefault();
        const message = elements.messageInput.value.trim();
        if (!message) return;
        elements.messageInput.value = "";
        updateChatInput();
        await submitChat(message);
    });

    elements.messageInput.addEventListener("input", updateChatInput);
    elements.agentQuickActions.addEventListener("click", event => {
        const button = event.target.closest("[data-prompt]");
        if (!button) return;
        elements.messageInput.value = button.dataset.prompt;
        updateChatInput();
        elements.messageInput.focus();
    });
    elements.messageInput.addEventListener("keydown", event => {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            elements.chatForm.requestSubmit();
        }
    });

    elements.createSessionForm.addEventListener("submit", async event => {
        event.preventDefault();
        const button = event.submitter;
        button.disabled = true;
        try {
            const session = await api("/api/v1/chat/sessions", {
                method: "POST",
                body: {title: elements.sessionTitleInput.value.trim()}
            });
            state.sessions.unshift(session);
            elements.createSessionDialog.close();
            elements.createSessionForm.reset();
            await selectSession(session.id);
            showToast("Agent 会话创建成功");
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            button.disabled = false;
        }
    });

    elements.createKbForm.addEventListener("submit", async event => {
        event.preventDefault();
        const button = event.submitter;
        button.disabled = true;
        try {
            const knowledgeBase = await api("/api/v1/knowledge-bases", {
                method: "POST",
                body: {
                    name: elements.kbNameInput.value.trim(),
                    description: elements.kbDescriptionInput.value.trim()
                }
            });
            state.knowledgeBases.unshift(knowledgeBase);
            elements.createKbDialog.close();
            elements.createKbForm.reset();
            updateEvaluationKnowledgeBases();
            await selectKnowledgeBase(knowledgeBase.id);
            showToast("知识库创建成功");
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            button.disabled = false;
        }
    });

    document.querySelectorAll("[data-close-dialog]").forEach(button => {
        button.addEventListener("click", () => document.querySelector(`#${button.dataset.closeDialog}`).close());
    });

    elements.documentFile.addEventListener("change", () => {
        const files = Array.from(elements.documentFile.files);
        if (!files.length) {
            elements.fileLabel.textContent = "选择一个或多个个人资料";
        } else if (files.length === 1) {
            elements.fileLabel.textContent = files[0].name;
        } else {
            elements.fileLabel.textContent = `已选择 ${files.length} 个文件`;
        }
        elements.uploadResult.hidden = true;
    });

    elements.uploadForm.addEventListener("submit", async event => {
        event.preventDefault();
        const files = Array.from(elements.documentFile.files);
        if (!state.selectedKnowledgeBaseId || !files.length) return;
        const formData = new FormData();
        files.forEach(file => formData.append("files", file));
        elements.uploadButton.disabled = true;
        elements.uploadButton.textContent = `正在处理 ${files.length} 个文件…`;
        elements.uploadResult.hidden = true;
        try {
            const result = await api(`/api/v1/knowledge-bases/${state.selectedKnowledgeBaseId}/documents/batch`, {
                method: "POST",
                body: formData
            });
            elements.uploadForm.reset();
            elements.fileLabel.textContent = "选择一个或多个个人资料";
            await loadDocuments();
            const failures = result.items.filter(item => !item.success);
            const summary = `处理完成：成功 ${result.successCount} 个，失败 ${result.failureCount} 个`;
            elements.uploadResult.textContent = failures.length
                ? `${summary}；${failures.map(item => `${item.originalName}（${item.message}）`).join("；")}`
                : summary;
            elements.uploadResult.classList.toggle("has-errors", failures.length > 0);
            elements.uploadResult.hidden = false;
            showToast(summary, failures.length ? "error" : "success");
        } catch (error) {
            elements.uploadResult.textContent = error.message;
            elements.uploadResult.classList.add("has-errors");
            elements.uploadResult.hidden = false;
            showToast(error.message, "error");
        } finally {
            elements.uploadButton.disabled = false;
            elements.uploadButton.textContent = "批量上传并处理";
        }
    });

    elements.ragForm.addEventListener("submit", async event => {
        event.preventDefault();
        const question = elements.ragQuestion.value.trim();
        if (!question || !state.selectedKnowledgeBaseId) return;
        appendRagMessage("user", question);
        elements.ragQuestion.value = "";
        elements.ragSendButton.disabled = true;
        const loading = appendRagMessage("assistant", "正在检索并组织答案…");
        try {
            const result = await api(`/api/v1/knowledge-bases/${state.selectedKnowledgeBaseId}/rag/ask`, {
                method: "POST",
                body: {question}
            });
            loading.parentElement.remove();
            appendRagMessage("assistant", result.answer, result.sources || []);
        } catch (error) {
            loading.textContent = error.message;
        } finally {
            elements.ragSendButton.disabled = false;
            elements.ragQuestion.focus();
        }
    });

    elements.evaluationKb.addEventListener("change", () => {
        const value = Number(elements.evaluationKb.value);
        if (value) {
            state.selectedKnowledgeBaseId = value;
            renderSidebar();
        }
    });

    elements.evaluationFile.addEventListener("change", async () => {
        const file = elements.evaluationFile.files[0];
        state.evaluationRequest = null;
        if (!file) return;
        try {
            const request = JSON.parse(await file.text());
            if (!Array.isArray(request.cases) || !request.cases.length) {
                throw new Error("评测文件缺少cases数组");
            }
            state.evaluationRequest = request;
            elements.evaluationStatus.textContent = `已载入 ${request.cases.length} 个真实标注问题`;
            showToast("评测标注文件载入成功");
        } catch (error) {
            elements.evaluationStatus.textContent = "评测文件无效";
            showToast(error.message, "error");
        }
    });

    elements.runEvaluationButton.addEventListener("click", async () => {
        const knowledgeBaseId = Number(elements.evaluationKb.value);
        if (!knowledgeBaseId) return;
        if (!state.evaluationRequest) {
            showToast("请先选择真实标注JSON文件", "error");
            return;
        }
        elements.runEvaluationButton.disabled = true;
        elements.runEvaluationButton.textContent = "评测中…";
        elements.evaluationStatus.textContent = `正在检索 ${state.evaluationRequest.cases.length} 个标注问题并搜索参数`;
        try {
            const result = await api(`/api/v1/knowledge-bases/${knowledgeBaseId}/evaluations/calibration`, {
                method: "POST",
                body: state.evaluationRequest
            });
            renderEvaluation(result);
            showToast("RAG真实数据校准完成");
        } catch (error) {
            elements.evaluationStatus.textContent = "评测失败";
            showToast(error.message, "error");
        } finally {
            elements.runEvaluationButton.disabled = false;
            elements.runEvaluationButton.textContent = "开始评测";
        }
    });
}

async function initialize() {
    bindEvents();
    renderUserState();
    updateChatInput();
    switchView("chat");
    selectSession(null);

    if (state.auth) {
        hideAuthScreen();
        await loadWorkspaceData();
    } else {
        showAuthScreen();
        renderSidebar();
        updateEvaluationKnowledgeBases();
    }
}

initialize();
