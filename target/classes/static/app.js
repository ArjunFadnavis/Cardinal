const POLL_MS = 3000;

let boats = [];
let currentView = "desk";
let pollTimer = null;

const customerInput = document.getElementById("customer-name");
const deskList = document.getElementById("desk-boat-list");
const beachList = document.getElementById("beach-boat-list");
const connectionStatus = document.getElementById("connection-status");
const lastUpdated = document.getElementById("last-updated");
const toastEl = document.getElementById("toast");

document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => setView(tab.dataset.view));
});

function setView(view) {
    currentView = view;
    document.querySelectorAll(".tab").forEach((t) => {
        t.classList.toggle("active", t.dataset.view === view);
    });
    document.getElementById("desk-view").classList.toggle("active", view === "desk");
    document.getElementById("beach-view").classList.toggle("active", view === "beach");
    document.getElementById("wips-view").classList.toggle("active", view === "wips");
    if (view === "wips" && typeof refreshWips === "function") {
        refreshWips();
    }
    render();
}

function apiUrl(path) {
    return path;
}

async function fetchBoats() {
    const res = await fetch(apiUrl("/api/boats"));
    if (!res.ok) {
        throw new Error("Could not load boats");
    }
    return res.json();
}

async function post(path, body) {
    const res = await fetch(apiUrl(path), {
        method: "POST",
        headers: body ? { "Content-Type": "application/json" } : {},
        body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
        const text = await res.text();
        let message = text;
        try {
            const json = JSON.parse(text);
            message = json.message || json.error || text;
        } catch (_) {
            /* plain text error */
        }
        throw new Error(message || "Request failed");
    }
    return res.json();
}

function showToast(message, isError = false) {
    toastEl.textContent = message;
    toastEl.classList.toggle("error", isError);
    toastEl.classList.remove("hidden");
    clearTimeout(showToast._timer);
    showToast._timer = setTimeout(() => toastEl.classList.add("hidden"), 4000);
}

function formatTime(date) {
    return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

function formatAssignedAt(isoString) {
    if (!isoString) return "";
    return "Assigned " + formatTime(new Date(isoString));
}

function statusLabel(status) {
    if (status === "AVAILABLE") return "Available";
    if (status === "WAITLISTED") return "Waitlisted";
    if (status === "ASSIGNED") return "Assigned";
    if (status === "OUT") return "Out";
    return status;
}

function statusClass(status) {
    if (status === "AVAILABLE") return "status-available";
    if (status === "WAITLISTED") return "status-waitlisted";
    if (status === "ASSIGNED") return "status-assigned";
    if (status === "OUT") return "status-out";
    return "";
}

function groupByType(boatList) {
    const groups = new Map();
    for (const boat of boatList) {
        if (!groups.has(boat.boatType)) {
            groups.set(boat.boatType, []);
        }
        groups.get(boat.boatType).push(boat);
    }
    return groups;
}

function renderBoatGroups(container, boatList, rowBuilder) {
    container.innerHTML = "";
    if (boatList.length === 0) {
        container.innerHTML = '<p class="empty">No boats configured.</p>';
        return;
    }

    for (const [type, typeBoats] of groupByType(boatList)) {
        const group = document.createElement("section");
        group.className = "boat-group";
        group.innerHTML = `<h2>${escapeHtml(type)}</h2>`;

        for (const boat of typeBoats) {
            group.appendChild(rowBuilder(boat));
        }
        container.appendChild(group);
    }
}

function escapeHtml(text) {
    const el = document.createElement("span");
    el.textContent = text;
    return el.innerHTML;
}

function renderDesk() {
    renderBoatGroups(deskList, boats, (boat) => {
        const row = document.createElement("div");
        row.className = "boat-row";

        const customerHtml = boat.customerName
            ? `<div class="customer">${escapeHtml(boat.customerName)}</div>`
            : "";

        row.innerHTML = `
            <div class="boat-number">${escapeHtml(boat.boatNumber)}</div>
            <div class="boat-meta">
                ${customerHtml}
                <span class="status-badge ${statusClass(boat.status)}">${statusLabel(boat.status)}</span>
            </div>
        `;

        const actions = document.createElement("div");
        if (boat.status === "AVAILABLE" || boat.status === "WAITLISTED") {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "btn btn-assign";
            btn.textContent = boat.status === "WAITLISTED" ? "Assign (waivers)" : "Assign";
            btn.addEventListener("click", () => {
                if (boat.customerName) {
                    customerInput.value = boat.customerName;
                }
                assignBoat(boat.boatNumber);
            });
            actions.appendChild(btn);
        }
        row.appendChild(actions);
        return row;
    });
}

function renderBeach() {
    const beachBoats = boats.filter((b) => b.status === "ASSIGNED" || b.status === "OUT");
    renderBoatGroups(beachList, beachBoats, (boat) => {
        const row = document.createElement("div");
        row.className = "boat-row";

        const timeLine = boat.assignedAt
            ? `<div class="time-note">${escapeHtml(formatAssignedAt(boat.assignedAt))}</div>`
            : "";

        row.innerHTML = `
            <div class="boat-number">${escapeHtml(boat.boatNumber)}</div>
            <div class="boat-meta">
                <div class="customer">${escapeHtml(boat.customerName || "—")}</div>
                ${timeLine}
                <span class="status-badge ${statusClass(boat.status)}">${statusLabel(boat.status)}</span>
            </div>
        `;

        const actions = document.createElement("div");
        if (boat.status === "ASSIGNED") {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "btn btn-send";
            btn.textContent = "Send out";
            btn.addEventListener("click", () => sendBoat(boat.boatNumber));
            actions.appendChild(btn);
        } else if (boat.status === "OUT") {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "btn btn-return";
            btn.textContent = "Returned";
            btn.addEventListener("click", () => returnBoat(boat.boatNumber));
            actions.appendChild(btn);
        }
        row.appendChild(actions);
        return row;
    });

    if (beachBoats.length === 0) {
        beachList.innerHTML = '<p class="empty">No customers waiting or out on the water.</p>';
    }
}

function render() {
    if (currentView === "desk") {
        renderDesk();
    } else if (currentView === "beach") {
        renderBeach();
    }
}

async function refresh() {
    try {
        boats = await fetchBoats();
        connectionStatus.textContent = "Connected";
        lastUpdated.textContent = "Updated " + formatTime(new Date());
        render();
        if (typeof refreshWips === "function") {
            await refreshWips();
        }
    } catch (err) {
        connectionStatus.textContent = "Offline";
        showToast(err.message, true);
    }
}

async function assignBoat(boatNumber) {
    const name = customerInput.value.trim();
    if (!name) {
        showToast("Enter the customer name first", true);
        customerInput.focus();
        return;
    }
    try {
        await post(`/api/boats/${encodeURIComponent(boatNumber)}/assign`, {
            customerName: name,
        });
        customerInput.value = "";
        customerInput.focus();
        showToast(`Assigned ${boatNumber} to ${name}`);
        await refresh();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function sendBoat(boatNumber) {
    try {
        await post(`/api/boats/${encodeURIComponent(boatNumber)}/send`);
        showToast(`${boatNumber} sent out`);
        await refresh();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function returnBoat(boatNumber) {
    try {
        await post(`/api/boats/${encodeURIComponent(boatNumber)}/return`);
        showToast(`${boatNumber} is back and available`);
        await refresh();
    } catch (err) {
        showToast(err.message, true);
    }
}

function startPolling() {
    refresh();
    pollTimer = setInterval(refresh, POLL_MS);
}


const exportBtn = document.getElementById("export-excel-btn");
const exportResult = document.getElementById("export-result");

exportBtn.addEventListener("click", async () => {
    exportBtn.disabled = true;
    try {
        const result = await post("/api/export/excel");
        if (result.rowsAppended === 0) {
            exportResult.textContent = "Nothing new to export.";
            showToast("No completed rentals to export");
        } else {
            exportResult.textContent = `Added ${result.rowsAppended} row(s) to ${result.filePath}`;
            showToast(`Exported ${result.rowsAppended} rental(s) to Excel`);
        }
    } catch (err) {
        exportResult.textContent = "";
        showToast(err.message, true);
    } finally {
        exportBtn.disabled = false;
    }
});

startPolling();
