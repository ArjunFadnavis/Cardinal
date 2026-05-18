let waitlistState = { entries: [], boatTypes: [] };
let lastNotifiedIds = new Set();
let editingEntryId = null;

function initWips() {
    document.getElementById("wips-add-btn").addEventListener("click", saveWaitlistEntry);
    document.getElementById("wips-edit-cancel").addEventListener("click", cancelEdit);
    document.getElementById("wips-mode").addEventListener("change", onWipsModeChange);
    document.getElementById("wips-add-row").addEventListener("click", () => addWipsRow("wips-rows"));
    document.getElementById("wips-add-or-option").addEventListener("click", () => addWipsRow("wips-or-rows"));
    initAdvancedBuilder();
    onWipsModeChange();
}

function onWipsModeChange() {
    const mode = document.getElementById("wips-mode").value;
    document.querySelectorAll(".wips-mode-panel").forEach((p) => {
        p.classList.toggle("hidden", p.dataset.mode !== mode);
    });
    if (mode === "all" && document.querySelector("#wips-rows .wips-row") === null) {
        addWipsRow("wips-rows");
    }
    if (mode === "any" && document.querySelector("#wips-or-rows .wips-row") === null) {
        addWipsRow("wips-or-rows");
    }
}

function addWipsRow(containerId) {
    const container = document.getElementById(containerId);
    const row = document.createElement("div");
    row.className = "wips-row";
    row.innerHTML = `
        <select class="wips-boat-type"></select>
        <label class="wips-qty-label">Qty <input type="number" class="wips-qty" min="1" value="1"></label>
        <button type="button" class="btn btn-small btn-remove-row">Remove</button>
    `;
    fillBoatTypeSelect(row.querySelector(".wips-boat-type"));
    row.querySelector(".btn-remove-row").addEventListener("click", () => row.remove());
    container.appendChild(row);
}

function fillBoatTypeSelect(select) {
    const previous = select.value;
    select.innerHTML = "";
    for (const type of waitlistState.boatTypes) {
        const opt = document.createElement("option");
        opt.value = type;
        opt.textContent = type;
        select.appendChild(opt);
    }
    if (previous && waitlistState.boatTypes.includes(previous)) {
        select.value = previous;
    }
}

function refreshExcludeCheckboxes(container) {
    const checked = new Set(
        Array.from(container.querySelectorAll("input:checked")).map((cb) => cb.value)
    );
    container.innerHTML = "";
    for (const type of waitlistState.boatTypes) {
        const label = document.createElement("label");
        label.className = "wips-exclude-item";
        const input = document.createElement("input");
        input.type = "checkbox";
        input.value = type;
        if (checked.has(type)) input.checked = true;
        label.appendChild(input);
        label.appendChild(document.createTextNode(" " + type));
        container.appendChild(label);
    }
}

function refreshBoatTypeSelects() {
    document.querySelectorAll(".wips-boat-type").forEach(fillBoatTypeSelect);
    const excludeBox = document.getElementById("wips-exclude-types");
    if (excludeBox) refreshExcludeCheckboxes(excludeBox);
    document.querySelectorAll(".wips-rule-party-exclude").forEach(refreshExcludeCheckboxes);
}

function readRows(containerId) {
    return Array.from(document.querySelectorAll(`#${containerId} .wips-row`)).map((row) => ({
        boatType: row.querySelector(".wips-boat-type").value,
        quantity: parseInt(row.querySelector(".wips-qty").value, 10) || 1,
    }));
}

function buildRequirement() {
    const mode = document.getElementById("wips-mode").value;

    if (mode === "single") {
        return {
            type: "BOAT",
            boatType: document.getElementById("wips-single-type").value,
            quantity: parseInt(document.getElementById("wips-single-qty").value, 10) || 1,
        };
    }
    if (mode === "all") {
        const children = readRows("wips-rows").map((r) => ({
            type: "BOAT",
            boatType: r.boatType,
            quantity: r.quantity,
        }));
        return { type: "AND", children };
    }
    if (mode === "any") {
        const children = readRows("wips-or-rows").map((r) => ({
            type: "BOAT",
            boatType: r.boatType,
            quantity: r.quantity,
        }));
        return { type: "OR", children };
    }
    if (mode === "party") {
        const excludeTypes = Array.from(
            document.querySelectorAll("#wips-exclude-types input:checked")
        ).map((cb) => cb.value);
        return {
            type: "PARTY",
            partySize: parseInt(document.getElementById("wips-party-size").value, 10) || 1,
            excludeTypes,
        };
    }
    if (mode === "advanced") {
        const root = document.querySelector("#wips-advanced-root > .wips-rule-group");
        if (!root) throw new Error("Advanced rule builder not ready");
        return serializeRuleGroup(root);
    }
    throw new Error("Unknown waitlist mode");
}

function initAdvancedBuilder() {
    const rootEl = document.getElementById("wips-advanced-root");
    if (!rootEl || rootEl.querySelector(".wips-rule-group")) return;

    const group = createRuleGroup(true);
    const children = group.querySelector(".wips-rule-children");

    const orGroup = createRuleGroup(false);
    addRuleBoat(orGroup.querySelector(".wips-rule-children"), "Canoe (2 person)");
    addRuleBoat(orGroup.querySelector(".wips-rule-children"), "Kayak (1 person)");
    orGroup.querySelector(".wips-combinator").value = "OR";
    children.appendChild(orGroup);

    addRuleBoat(children, "Stand-up paddleboard (1 person)");

    rootEl.appendChild(group);

    rootEl.addEventListener("click", (e) => {
        const btn = e.target.closest("[data-rule-action]");
        if (!btn) return;
        const groupEl = btn.closest(".wips-rule-group");
        const childContainer = groupEl.querySelector(":scope > .wips-rule-children");
        const action = btn.dataset.ruleAction;
        if (action === "add-boat") addRuleBoat(childContainer);
        else if (action === "add-group") childContainer.appendChild(createRuleGroup(false));
        else if (action === "add-party") childContainer.appendChild(createRuleParty());
        else if (action === "remove") btn.closest(".wips-rule-item")?.remove();
    });
}

function createRuleGroup(isRoot) {
    const group = document.createElement("div");
    group.className = "wips-rule-group" + (isRoot ? " wips-rule-group-root" : " wips-rule-item");
    group.innerHTML = `
        <div class="wips-rule-group-header">
            <span class="wips-rule-label">${isRoot ? "Customer needs" : "Subgroup"}</span>
            <select class="wips-combinator">
                <option value="AND">All of these (AND)</option>
                <option value="OR">Any one of these (OR)</option>
            </select>
            ${isRoot ? "" : '<button type="button" class="btn btn-small btn-remove-row" data-rule-action="remove">Remove</button>'}
        </div>
        <div class="wips-rule-children"></div>
        <div class="wips-rule-group-actions">
            <button type="button" class="btn btn-small" data-rule-action="add-boat">+ Boat</button>
            <button type="button" class="btn btn-small" data-rule-action="add-group">+ Subgroup</button>
            <button type="button" class="btn btn-small" data-rule-action="add-party">+ Party seats</button>
        </div>
    `;
    return group;
}

function addRuleBoat(container, preferredType) {
    const item = document.createElement("div");
    item.className = "wips-rule-item wips-rule-boat";
    item.innerHTML = `
        <span class="wips-rule-kind">Boat</span>
        <select class="wips-boat-type"></select>
        <label class="wips-qty-label">Qty <input type="number" class="wips-qty" min="1" value="1"></label>
        <button type="button" class="btn btn-small btn-remove-row" data-rule-action="remove">Remove</button>
    `;
    const select = item.querySelector(".wips-boat-type");
    fillBoatTypeSelect(select);
    if (preferredType && waitlistState.boatTypes.includes(preferredType)) {
        select.value = preferredType;
    }
    container.appendChild(item);
}

function createRuleParty() {
    const item = document.createElement("div");
    item.className = "wips-rule-item wips-rule-party";
    item.innerHTML = `
        <span class="wips-rule-kind">Party</span>
        <label class="wips-qty-label">People <input type="number" class="wips-party-size-input" min="1" value="2"></label>
        <span class="wips-sub-label">Exclude types:</span>
        <div class="wips-rule-party-exclude wips-exclude-types"></div>
        <button type="button" class="btn btn-small btn-remove-row" data-rule-action="remove">Remove</button>
    `;
    refreshExcludeCheckboxes(item.querySelector(".wips-rule-party-exclude"));
    return item;
}

function serializeRuleGroup(groupEl) {
    const combinator = groupEl.querySelector(":scope > .wips-rule-group-header .wips-combinator").value;
    const children = [];
    for (const child of groupEl.querySelector(":scope > .wips-rule-children").children) {
        children.push(serializeRuleItem(child));
    }
    if (children.length === 0) {
        throw new Error("Add at least one rule to the custom combination");
    }
    return { type: combinator, children };
}

function serializeRuleItem(el) {
    if (el.classList.contains("wips-rule-boat")) {
        return {
            type: "BOAT",
            boatType: el.querySelector(".wips-boat-type").value,
            quantity: parseInt(el.querySelector(".wips-qty").value, 10) || 1,
        };
    }
    if (el.classList.contains("wips-rule-party")) {
        const excludeTypes = Array.from(el.querySelectorAll(".wips-rule-party-exclude input:checked")).map(
            (cb) => cb.value
        );
        return {
            type: "PARTY",
            partySize: parseInt(el.querySelector(".wips-party-size-input").value, 10) || 1,
            excludeTypes,
        };
    }
    if (el.classList.contains("wips-rule-group")) {
        return serializeRuleGroup(el);
    }
    throw new Error("Unknown rule type");
}

async function fetchWaitlist() {
    const res = await fetch("/api/waitlist");
    if (!res.ok) throw new Error("Could not load waitlist");
    return res.json();
}

function isBoatOnlyGroup(node) {
    return node.children && node.children.length > 0 && node.children.every((c) => c.type === "BOAT");
}

function applyRequirementToForm(requirement) {
    if (!requirement) return;

    if (requirement.type === "BOAT") {
        document.getElementById("wips-mode").value = "single";
        onWipsModeChange();
        document.getElementById("wips-single-type").value = requirement.boatType;
        document.getElementById("wips-single-qty").value = requirement.quantity;
        return;
    }
    if (requirement.type === "AND" && isBoatOnlyGroup(requirement)) {
        document.getElementById("wips-mode").value = "all";
        onWipsModeChange();
        const container = document.getElementById("wips-rows");
        container.innerHTML = "";
        for (const child of requirement.children) {
            addWipsRow("wips-rows");
            const row = container.lastElementChild;
            row.querySelector(".wips-boat-type").value = child.boatType;
            row.querySelector(".wips-qty").value = child.quantity;
        }
        return;
    }
    if (requirement.type === "OR" && isBoatOnlyGroup(requirement)) {
        document.getElementById("wips-mode").value = "any";
        onWipsModeChange();
        const container = document.getElementById("wips-or-rows");
        container.innerHTML = "";
        for (const child of requirement.children) {
            addWipsRow("wips-or-rows");
            const row = container.lastElementChild;
            row.querySelector(".wips-boat-type").value = child.boatType;
            row.querySelector(".wips-qty").value = child.quantity;
        }
        return;
    }
    if (requirement.type === "PARTY") {
        document.getElementById("wips-mode").value = "party";
        onWipsModeChange();
        document.getElementById("wips-party-size").value = requirement.partySize;
        refreshExcludeCheckboxes(document.getElementById("wips-exclude-types"));
        const excluded = new Set(requirement.excludeTypes || []);
        document.querySelectorAll("#wips-exclude-types input").forEach((cb) => {
            cb.checked = excluded.has(cb.value);
        });
        return;
    }

    document.getElementById("wips-mode").value = "advanced";
    onWipsModeChange();
    renderRequirementToAdvanced(requirement);
}

function renderRequirementToAdvanced(node) {
    const rootEl = document.getElementById("wips-advanced-root");
    rootEl.innerHTML = "";
    const rootGroup = createRuleGroup(true);
    if (node.type === "AND" || node.type === "OR") {
        rootGroup.querySelector(".wips-combinator").value = node.type;
        node.children.forEach((child) => appendRequirementNode(child, rootGroup.querySelector(".wips-rule-children")));
    } else {
        appendRequirementNode(node, rootGroup.querySelector(".wips-rule-children"));
    }
    rootEl.appendChild(rootGroup);
    refreshBoatTypeSelects();
}

function appendRequirementNode(node, container) {
    if (node.type === "BOAT") {
        addRuleBoat(container, node.boatType);
        const row = container.lastElementChild;
        row.querySelector(".wips-qty").value = node.quantity;
        return;
    }
    if (node.type === "PARTY") {
        const el = createRuleParty();
        el.querySelector(".wips-party-size-input").value = node.partySize;
        const excluded = new Set(node.excludeTypes || []);
        el.querySelectorAll(".wips-rule-party-exclude input").forEach((cb) => {
            cb.checked = excluded.has(cb.value);
        });
        container.appendChild(el);
        return;
    }
    if (node.type === "AND" || node.type === "OR") {
        const group = createRuleGroup(false);
        group.querySelector(".wips-combinator").value = node.type;
        node.children.forEach((child) => appendRequirementNode(child, group.querySelector(".wips-rule-children")));
        container.appendChild(group);
    }
}

function resetWaitlistForm() {
    document.getElementById("wips-customer-name").value = "";
    document.getElementById("wips-mode").value = "single";
    document.getElementById("wips-single-qty").value = "1";
    document.getElementById("wips-party-size").value = "2";
    document.getElementById("wips-rows").innerHTML = "";
    document.getElementById("wips-or-rows").innerHTML = "";
    document.getElementById("wips-advanced-root").innerHTML = "";
    initAdvancedBuilder();
    onWipsModeChange();
    refreshBoatTypeSelects();
}

function setEditMode(entry) {
    editingEntryId = entry.id;
    document.getElementById("wips-form-title").textContent = "Edit waitlist request";
    document.getElementById("wips-form-banner").textContent =
        `Editing ${entry.customerName} — save to update their place in line.`;
    document.getElementById("wips-form-banner").classList.remove("hidden");
    document.getElementById("wips-add-btn").textContent = "Save changes";
    document.getElementById("wips-edit-cancel").classList.remove("hidden");
    document.getElementById("wips-customer-name").value = entry.customerName;
    applyRequirementToForm(entry.requirement);
    document.getElementById("wips-customer-name").focus();
    document.getElementById("wips-form-title").closest(".panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function cancelEdit() {
    editingEntryId = null;
    document.getElementById("wips-form-title").textContent = "Add to waitlist";
    document.getElementById("wips-form-banner").classList.add("hidden");
    document.getElementById("wips-add-btn").textContent = "Add to WIPS";
    document.getElementById("wips-edit-cancel").classList.add("hidden");
    resetWaitlistForm();
}

function startEditEntry(entry) {
    if (entry.status !== "WAITING" && entry.status !== "NOTIFIED") {
        showToast("Only waiting or notified entries can be edited", true);
        return;
    }
    setEditMode(entry);
}

async function saveWaitlistEntry() {
    const name = document.getElementById("wips-customer-name").value.trim();
    if (!name) {
        showToast("Enter customer name for waitlist", true);
        return;
    }
    let requirement;
    try {
        requirement = buildRequirement();
    } catch (e) {
        showToast("Invalid requirement: " + e.message, true);
        return;
    }
    try {
        if (editingEntryId) {
            await putJson(`/api/waitlist/${editingEntryId}`, { customerName: name, requirement });
            showToast(`${name} — waitlist updated`);
            cancelEdit();
        } else {
            await post("/api/waitlist", { customerName: name, requirement });
            document.getElementById("wips-customer-name").value = "";
            resetWaitlistForm();
            showToast(`${name} added to WIPS`);
        }
        await refresh();
        await refreshWips();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function putJson(path, body) {
    const res = await fetch(apiUrl(path), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
    if (!res.ok) {
        const text = await res.text();
        let message = text;
        try {
            const json = JSON.parse(text);
            message = json.message || json.error || text;
        } catch (_) {
            /* use raw text */
        }
        throw new Error(message || `Request failed (${res.status})`);
    }
    return res.json();
}

async function requeueWaitlist(entry) {
    try {
        const result = await post(`/api/waitlist/${entry.id}/requeue`, {});
        showToast(`${result.customerName} moved to top of waitlist`);
        await refresh();
        await refreshWips();
        const updated = (waitlistState.entries || []).find((e) => e.id === entry.id);
        if (updated) {
            startEditEntry(updated);
        }
    } catch (err) {
        showToast(err.message, true);
    }
}

async function approveWaitlist(id) {
    try {
        const result = await post(`/api/waitlist/${id}/approve`);
        showToast(`${result.customerName} — boats held as waitlisted`);
        await refresh();
        await refreshWips();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function removeWaitlist(id) {
    try {
        await fetch(`/api/waitlist/${id}`, { method: "DELETE" });
        showToast("Removed from waitlist");
        await refresh();
        await refreshWips();
    } catch (err) {
        showToast(err.message, true);
    }
}

function renderWaitlist() {
    const list = document.getElementById("wips-list");
    const entries = waitlistState.entries || [];
    if (entries.length === 0) {
        list.innerHTML = '<p class="empty">No one on the waitlist.</p>';
        return;
    }

    list.innerHTML = "";
    for (const entry of entries) {
        const card = document.createElement("article");
        card.className = "wips-card" + (entry.status === "NOTIFIED" ? " wips-card-notified" : "");

        let boatsHtml = "";
        if (entry.proposedBoatNumbers && entry.proposedBoatNumbers.length > 0) {
            boatsHtml = `<p class="wips-boats">Boats: <strong>${entry.proposedBoatNumbers.map(escapeHtml).join(", ")}</strong></p>`;
        }

        let actions = "";
        if (entry.status === "NOTIFIED") {
            actions = `
                <button type="button" class="btn btn-assign" data-approve="${entry.id}">Approve (hold boats)</button>
                <button type="button" class="btn btn-small btn-requeue" data-requeue="${entry.id}">Requeue at top</button>
                <button type="button" class="btn btn-small btn-edit" data-edit="${entry.id}">Edit</button>
                <button type="button" class="btn btn-return" data-remove="${entry.id}">Remove (no-show)</button>
            `;
        } else if (entry.status === "WAITING") {
            actions = `
                <button type="button" class="btn btn-small btn-edit" data-edit="${entry.id}">Edit</button>
                <button type="button" class="btn btn-return" data-remove="${entry.id}">Remove</button>
            `;
        } else {
            actions = `<button type="button" class="btn btn-return" data-remove="${entry.id}">Remove</button>`;
        }

        card.innerHTML = `
            <div class="wips-card-head">
                <strong>${escapeHtml(entry.customerName)}</strong>
                <span class="status-badge ${entry.status === "NOTIFIED" ? "status-assigned" : "status-available"}">${entry.status}</span>
            </div>
            <p class="wips-req">${escapeHtml(entry.requirementSummary)}</p>
            ${boatsHtml}
            <div class="wips-actions">${actions}</div>
        `;

        const approveBtn = card.querySelector("[data-approve]");
        if (approveBtn) {
            approveBtn.addEventListener("click", () => approveWaitlist(entry.id));
        }
        const requeueBtn = card.querySelector("[data-requeue]");
        if (requeueBtn) {
            requeueBtn.addEventListener("click", () => requeueWaitlist(entry));
        }
        const editBtn = card.querySelector("[data-edit]");
        if (editBtn) {
            editBtn.addEventListener("click", () => startEditEntry(entry));
        }
        card.querySelector("[data-remove]").addEventListener("click", () => removeWaitlist(entry.id));
        list.appendChild(card);
    }
}

function checkWaitlistNotifications() {
    const notified = (waitlistState.entries || []).filter((e) => e.status === "NOTIFIED");
    for (const entry of notified) {
        if (!lastNotifiedIds.has(entry.id)) {
            const boats = (entry.proposedBoatNumbers || []).join(", ");
            showToast(`WIPS: ${entry.customerName} can go out — boats ${boats}`, false);
            lastNotifiedIds.add(entry.id);
        }
    }
    const currentIds = new Set(notified.map((e) => e.id));
    lastNotifiedIds.forEach((id) => {
        if (!currentIds.has(id)) lastNotifiedIds.delete(id);
    });
}

async function refreshWips() {
    try {
        waitlistState = await fetchWaitlist();
        refreshBoatTypeSelects();
        const singleSelect = document.getElementById("wips-single-type");
        if (singleSelect && waitlistState.boatTypes.length > 0) {
            fillBoatTypeSelect(singleSelect);
        }
        renderWaitlist();
        checkWaitlistNotifications();
    } catch (err) {
        if (typeof currentView !== "undefined" && currentView === "wips") {
            showToast(err.message, true);
        }
    }
}

document.addEventListener("DOMContentLoaded", initWips);
