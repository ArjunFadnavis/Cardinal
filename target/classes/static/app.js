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
    document.getElementById("fleet-view").classList.toggle("active", view === "fleet");
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
    if (status === "OUT_OF_SERVICE") return "Out of service";
    return status;
}

function statusClass(status) {
    if (status === "AVAILABLE") return "status-available";
    if (status === "WAITLISTED") return "status-waitlisted";
    if (status === "ASSIGNED") return "status-assigned";
    if (status === "OUT") return "status-out";
    if (status === "OUT_OF_SERVICE") return "status-out-of-service";
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

function distinctBoatTypes() {
    return [...new Set(boats.map((b) => b.boatType))].sort((a, b) =>
        a.localeCompare(b, undefined, { sensitivity: "base" })
    );
}

function refreshAddBoatTypeSelect() {
    const select = document.getElementById("add-boat-type");
    if (!select) {
        return;
    }
    const previous = select.value;
    const types = distinctBoatTypes();
    select.innerHTML = "";
    for (const type of types) {
        const opt = document.createElement("option");
        opt.value = type;
        opt.textContent = type;
        select.appendChild(opt);
    }
    if (previous && types.includes(previous)) {
        select.value = previous;
    }
    syncFleetBoatTypeFromNumber();
}

function boatTypeForNumber(boatNumber) {
    const prefix = boatNumber.trim().charAt(0).toUpperCase();
    const byPrefix = {
        C: "Canoe (2 person)",
        S: "Kayak (1 person)",
        P: "Pedal boat (4 person)",
        T: "Double kayak (2 person)",
        U: "Stand-up paddleboard (1 person)",
    };
    return byPrefix[prefix] || null;
}

function syncFleetBoatTypeFromNumber() {
    const numberInput = document.getElementById("add-boat-number");
    const typeSelect = document.getElementById("add-boat-type");
    if (!numberInput || !typeSelect || !numberInput.value.trim()) {
        return;
    }
    const inferred = boatTypeForNumber(numberInput.value);
    if (inferred && [...typeSelect.options].some((o) => o.value === inferred)) {
        typeSelect.value = inferred;
    }
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
        actions.className = "boat-actions";
        if (boat.status === "AVAILABLE" || boat.status === "WAITLISTED") {
            const assignBtn = document.createElement("button");
            assignBtn.type = "button";
            assignBtn.className = "btn btn-assign";
            assignBtn.textContent = boat.status === "WAITLISTED" ? "Assign (waivers)" : "Assign";
            assignBtn.addEventListener("click", () => {
                if (boat.customerName) {
                    customerInput.value = boat.customerName;
                }
                assignBoat(boat.boatNumber);
            });
            actions.appendChild(assignBtn);
        }
        if (boat.status === "AVAILABLE") {
            const oosBtn = document.createElement("button");
            oosBtn.type = "button";
            oosBtn.className = "btn btn-out-of-service";
            oosBtn.textContent = "Out of service";
            oosBtn.addEventListener("click", () => markOutOfService(boat.boatNumber));
            actions.appendChild(oosBtn);
        } else if (boat.status === "OUT_OF_SERVICE") {
            const rtsBtn = document.createElement("button");
            rtsBtn.type = "button";
            rtsBtn.className = "btn btn-return-to-service";
            rtsBtn.textContent = "Return to service";
            rtsBtn.addEventListener("click", () => returnToService(boat.boatNumber));
            actions.appendChild(rtsBtn);
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
        actions.className = "boat-actions";
        if (boat.status === "ASSIGNED") {
            const sendBtn = document.createElement("button");
            sendBtn.type = "button";
            sendBtn.className = "btn btn-send";
            sendBtn.textContent = "Send out";
            sendBtn.addEventListener("click", () => sendBoat(boat.boatNumber));
            actions.appendChild(sendBtn);

            const reassignBtn = document.createElement("button");
            reassignBtn.type = "button";
            reassignBtn.className = "btn btn-reassign";
            reassignBtn.textContent = "Change boat";
            reassignBtn.addEventListener("click", () => openReassignModal(boat));
            actions.appendChild(reassignBtn);
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
        refreshAddBoatTypeSelect();
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

async function markOutOfService(boatNumber) {
    try {
        await post(`/api/boats/${encodeURIComponent(boatNumber)}/out-of-service`);
        showToast(`${boatNumber} marked out of service`);
        await refresh();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function returnToService(boatNumber) {
    try {
        await post(`/api/boats/${encodeURIComponent(boatNumber)}/return-to-service`);
        showToast(`${boatNumber} is available again`);
        await refresh();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function addBoatToFleet() {
    const numberInput = document.getElementById("add-boat-number");
    const typeSelect = document.getElementById("add-boat-type");
    const boatNumber = numberInput.value.trim();
    const boatType = typeSelect.value;
    if (!boatNumber) {
        showToast("Enter a boat number", true);
        numberInput.focus();
        return;
    }
    if (!boatType) {
        showToast("Choose a boat type", true);
        return;
    }
    try {
        await post("/api/boats", { boatNumber, boatType });
        numberInput.value = "";
        showToast(`Added ${boatNumber} (${boatType})`);
        await refresh();
        numberInput.focus();
    } catch (err) {
        showToast(err.message, true);
    }
}

const reassignModal = document.getElementById("reassign-modal");
const reassignModalDesc = document.getElementById("reassign-modal-desc");
const reassignTargetSelect = document.getElementById("reassign-target");
const reassignEmpty = document.getElementById("reassign-empty");
const reassignConfirmBtn = document.getElementById("reassign-confirm-btn");
let reassignFromBoat = null;

function availableReassignTargets(fromBoat) {
    return boats.filter(
        (b) =>
            b.status === "AVAILABLE" &&
            b.boatType === fromBoat.boatType &&
            b.boatNumber !== fromBoat.boatNumber
    );
}

function openReassignModal(boat) {
    reassignFromBoat = boat;
    const targets = availableReassignTargets(boat);
    reassignModalDesc.textContent = `${boat.customerName || "Customer"} is on ${boat.boatNumber}. Pick another ${boat.boatType}.`;

    reassignTargetSelect.innerHTML = "";
    for (const target of targets) {
        const option = document.createElement("option");
        option.value = target.boatNumber;
        option.textContent = target.boatNumber;
        reassignTargetSelect.appendChild(option);
    }

    const hasTargets = targets.length > 0;
    reassignTargetSelect.classList.toggle("hidden", !hasTargets);
    reassignEmpty.classList.toggle("hidden", hasTargets);
    reassignConfirmBtn.disabled = !hasTargets;

    reassignModal.classList.remove("hidden");
    if (hasTargets) {
        reassignTargetSelect.focus();
    }
}

function closeReassignModal() {
    reassignModal.classList.add("hidden");
    reassignFromBoat = null;
}

async function confirmReassign() {
    if (!reassignFromBoat) {
        return;
    }
    const targetBoatNumber = reassignTargetSelect.value;
    if (!targetBoatNumber) {
        showToast("Choose a boat", true);
        return;
    }
    reassignConfirmBtn.disabled = true;
    try {
        await post(`/api/boats/${encodeURIComponent(reassignFromBoat.boatNumber)}/reassign`, {
            targetBoatNumber,
        });
        showToast(`Moved ${reassignFromBoat.customerName} from ${reassignFromBoat.boatNumber} to ${targetBoatNumber}`);
        closeReassignModal();
        await refresh();
    } catch (err) {
        showToast(err.message, true);
    } finally {
        reassignConfirmBtn.disabled = false;
    }
}

document.getElementById("reassign-cancel-btn").addEventListener("click", closeReassignModal);
reassignConfirmBtn.addEventListener("click", confirmReassign);
reassignModal.querySelectorAll("[data-close-reassign]").forEach((el) => {
    el.addEventListener("click", closeReassignModal);
});

function startPolling() {
    refresh();
    pollTimer = setInterval(refresh, POLL_MS);
}


const exportBtn = document.getElementById("export-excel-btn");
const exportResult = document.getElementById("export-result");
const addBoatBtn = document.getElementById("add-boat-btn");
addBoatBtn?.addEventListener("click", addBoatToFleet);
document.getElementById("add-boat-number")?.addEventListener("input", syncFleetBoatTypeFromNumber);

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
