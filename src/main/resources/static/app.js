const form = document.querySelector("#chat-form");
const messageInput = document.querySelector("#message");
const messages = document.querySelector("#messages");
const sendButton = document.querySelector("#send-button");
const statusText = document.querySelector("#status");
const characterCount = document.querySelector("#character-count");
const sidebarTime = document.querySelector("#sidebar-time");
const promptItems = document.querySelectorAll(".prompt-item");

function appendMessage(role, content, extraClass = "") {
    const article = document.createElement("article");
    article.className = `message-row ${role} ${extraClass}`.trim();

    const avatar = document.createElement("span");
    avatar.className = role === "user"
        ? "avatar avatar-user"
        : "avatar avatar-agent";
    avatar.textContent = role === "user" ? "我" : "智";
    avatar.setAttribute("aria-hidden", "true");

    const contentWrapper = document.createElement("div");
    contentWrapper.className = "message-content";

    const senderName = document.createElement("span");
    senderName.className = "sender-name";
    senderName.textContent = role === "user" ? "我" : "知屿助手";

    const bubble = document.createElement("div");
    bubble.className = "message-bubble";

    const paragraph = document.createElement("p");
    paragraph.textContent = content;

    const time = document.createElement("time");
    time.textContent = formatTime(new Date());

    bubble.append(paragraph);
    contentWrapper.append(senderName, bubble, time);
    article.append(avatar, contentWrapper);
    messages.append(article);
    scrollToLatest();

    return {article, bubble, paragraph};
}

function formatTime(date) {
    return new Intl.DateTimeFormat("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
        hour12: false
    }).format(date);
}

function scrollToLatest() {
    messages.scrollTop = messages.scrollHeight;
}

function showTypingIndicator(bubble) {
    const dots = document.createElement("span");
    dots.className = "typing-dots";
    dots.setAttribute("aria-label", "助手正在输入");
    dots.innerHTML = "<i></i><i></i><i></i>";
    bubble.append(dots);
    return dots;
}

function resizeInput() {
    messageInput.style.height = "auto";
    messageInput.style.height = `${Math.min(messageInput.scrollHeight, 150)}px`;
}

function updateInputState() {
    characterCount.textContent = messageInput.value.length;
    resizeInput();
}

function readSseEvent(eventText) {
    return eventText
        .split(/\r?\n/)
        .filter(line => line.startsWith("data:"))
        .map(line => line.slice(5).replace(/^ /, ""))
        .join("\n");
}

async function sendMessage(message, onAnswerChunk) {
    const response = await fetch("/api/v1/chat/stream", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({message})
    });

    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.message || "请求失败");
    }

    if (!response.body) {
        throw new Error("浏览器没有收到流式响应");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (true) {
        const {done, value} = await reader.read();
        buffer += decoder.decode(value, {stream: !done});

        const events = buffer.split(/\r?\n\r?\n/);
        buffer = events.pop() || "";

        for (const eventText of events) {
            const chunk = readSseEvent(eventText);
            if (chunk && chunk !== "[DONE]") {
                onAnswerChunk(chunk);
            }
        }

        if (done) {
            break;
        }
    }

    const lastChunk = readSseEvent(buffer);
    if (lastChunk && lastChunk !== "[DONE]") {
        onAnswerChunk(lastChunk);
    }
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const message = messageInput.value.trim();
    if (!message) {
        return;
    }

    appendMessage("user", message);
    messageInput.value = "";
    updateInputState();
    sendButton.disabled = true;
    statusText.textContent = "正在接收流式回答…";

    const answerMessage = appendMessage("assistant", "", "is-streaming");
    const typingIndicator = showTypingIndicator(answerMessage.bubble);

    try {
        let answer = "";

        await sendMessage(message, chunk => {
            typingIndicator.remove();
            answer += chunk;
            answerMessage.paragraph.textContent = answer;
            scrollToLatest();
        });

        if (!answer) {
            typingIndicator.remove();
            answerMessage.paragraph.textContent = "模型没有返回文字内容。";
        }

        answerMessage.article.classList.remove("is-streaming");
        statusText.textContent = "回答完成";
    } catch (error) {
        typingIndicator.remove();
        answerMessage.article.classList.remove("is-streaming");
        answerMessage.article.classList.add("error");
        answerMessage.paragraph.textContent = error.message;
        statusText.textContent = "请求失败";
    } finally {
        sendButton.disabled = false;
        messageInput.focus();
    }
});

messageInput.addEventListener("input", updateInputState);

messageInput.addEventListener("keydown", event => {
    if (event.key === "Enter"
            && !event.shiftKey
            && !event.isComposing) {
        event.preventDefault();
        form.requestSubmit();
    }
});

promptItems.forEach(item => {
    item.addEventListener("click", () => {
        messageInput.value = item.dataset.prompt || "";
        updateInputState();
        messageInput.focus();
    });
});

sidebarTime.textContent = formatTime(new Date());
updateInputState();
messageInput.focus();
