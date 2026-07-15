const form = document.querySelector("#chat-form");
const messageInput = document.querySelector("#message");
const messages = document.querySelector("#messages");
const sendButton = document.querySelector("#send-button");
const statusText = document.querySelector("#status");

function appendMessage(role, content, extraClass = "") {
    const article = document.createElement("article");
    article.className = `message ${role} ${extraClass}`.trim();

    const label = document.createElement("span");
    label.textContent = role === "user" ? "你" : "助手";

    const paragraph = document.createElement("p");
    paragraph.textContent = content;

    article.append(label, paragraph);
    messages.append(article);
    messages.scrollTop = messages.scrollHeight;
}

async function sendMessage(message) {
    const response = await fetch("/api/v1/chat", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({message})
    });

    const body = await response.json();
    if (!response.ok) {
        throw new Error(body.message || "请求失败");
    }
    return body.answer;
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const message = messageInput.value.trim();
    if (!message) {
        return;
    }

    appendMessage("user", message);
    messageInput.value = "";
    sendButton.disabled = true;
    statusText.textContent = "正在等待模型返回完整答案…";

    try {
        const answer = await sendMessage(message);
        appendMessage("assistant", answer);
        statusText.textContent = "完成";
    } catch (error) {
        appendMessage("assistant", error.message, "error");
        statusText.textContent = "请求失败";
    } finally {
        sendButton.disabled = false;
        messageInput.focus();
    }
});

