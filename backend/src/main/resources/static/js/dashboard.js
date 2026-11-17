document.addEventListener('DOMContentLoaded', () => {
    loadClients();
    loadAnalytics();
    loadTriggers();
    loadUsers();

    document.getElementById('create-client-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        await createClient();
    });

    document.getElementById('geofence-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        await addGeofence();
    });

    document.getElementById('beacon-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        await addBeacon();
    });

    document.getElementById('campaign-form').addEventListener('submit', (e) => {
        e.preventDefault();
        sendCampaign();
    });
});

async function loadClients() {
    try {
        const response = await fetch('/v1/license/all');
        const clients = await response.json();
        const tbody = document.querySelector('#client-table tbody');
        const select = document.querySelector('#campaign-target');
        const gfSelect = document.querySelector('#gf-client');
        const bSelect = document.querySelector('#beacon-client');

        tbody.innerHTML = '';
        select.innerHTML = '';
        if (gfSelect) gfSelect.innerHTML = '<option value="">Select Client</option>';
        if (bSelect) bSelect.innerHTML = '<option value="">Select Client</option>';

        clients.forEach(client => {
            // Update Table
            const row = `
                <tr>
                    <td><strong>${client.clientName}</strong></td>
                    <td><code>${client.apiKey}</code></td>
                    <td>
                        <span class="badge ${client.active ? 'bg-success' : 'bg-danger'}">
                            ${client.active ? 'ACTIVE' : 'REVOKED'}
                        </span>
                    </td>
                    <td>
                        <button class="btn btn-sm ${client.active ? 'btn-outline-danger' : 'btn-outline-success'}"
                                onclick="toggleLicense(${client.id})">
                            ${client.active ? 'Kill' : 'Restore'}
                        </button>
                        <button class="btn btn-sm btn-danger ms-1"
                                onclick="removeLicense(${client.id})">
                            Delete
                        </button>
                    </td>
                </tr>`;
            tbody.innerHTML += row;

            // Update Dropdowns
            const opt = `<option value="${client.apiKey}">${client.clientName}</option>`;
            select.innerHTML += opt;
            if (gfSelect) gfSelect.innerHTML += opt;
            if (bSelect) bSelect.innerHTML += opt;
        });
    } catch (err) {
        console.error('Failed to load clients', err);
    }
}

async function loadTriggers() {
    try {
        const response = await fetch('/api/config/triggers/all');
        const data = await response.json();
        const tbody = document.querySelector('#trigger-table tbody');
        if (!tbody) return;
        tbody.innerHTML = '';

        data.geofences.forEach(gf => {
            tbody.innerHTML += `
                <tr>
                    <td><span class="badge bg-primary">GEOFENCE</span></td>
                    <td>${gf.license.clientName}</td>
                    <td>${gf.name} (${gf.latitude}, ${gf.longitude}) R:${gf.radius}m</td>
                    <td><button class="btn btn-sm btn-link text-danger" onclick="removeTrigger('geofence', ${gf.id})">Remove</button></td>
                </tr>`;
        });

        data.beacons.forEach(b => {
            tbody.innerHTML += `
                <tr>
                    <td><span class="badge bg-secondary">BEACON</span></td>
                    <td>${b.license.clientName}</td>
                    <td>UUID: ${b.beaconUuid.substring(0,8)}... M:${b.major} m:${b.minor}</td>
                    <td><button class="btn btn-sm btn-link text-danger" onclick="removeTrigger('beacon', ${b.id})">Remove</button></td>
                </tr>`;
        });
    } catch (err) {
        console.error('Error loading triggers', err);
    }
}

async function addGeofence() {
    const payload = {
        apiKey: document.getElementById('gf-client').value,
        geofenceId: document.getElementById('gf-id').value,
        name: document.getElementById('gf-name').value,
        lat: document.getElementById('gf-lat').value,
        lon: document.getElementById('gf-lon').value,
        radius: document.getElementById('gf-radius').value
    };
    await fetch('/api/config/geofence/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    document.getElementById('geofence-form').reset();
    loadTriggers();
}

async function addBeacon() {
    const payload = {
        apiKey: document.getElementById('beacon-client').value,
        uuid: document.getElementById('beacon-uuid').value,
        major: document.getElementById('beacon-major').value,
        minor: document.getElementById('beacon-minor').value
    };
    await fetch('/api/config/beacon/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    document.getElementById('beacon-form').reset();
    loadTriggers();
}

async function removeTrigger(type, id) {
    await fetch(`/api/config/trigger/remove?type=${type}&id=${id}`, { method: 'DELETE' });
    loadTriggers();
}

async function toggleLicense(id) {
    await fetch(`/v1/license/toggle?id=${id}`, { method: 'POST' });
    loadClients();
}

async function removeLicense(id) {
    if (confirm('Are you sure you want to permanently delete this API key?')) {
        await fetch(`/v1/license/remove?id=${id}`, { method: 'DELETE' });
        loadClients();
    }
}

async function createClient() {
    const name = document.getElementById('new-client-name').value;
    const key = document.getElementById('new-client-key').value;

    try {
        const response = await fetch('/v1/license/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ clientName: name, apiKey: key })
        });

        if (response.ok) {
            document.getElementById('create-client-form').reset();
            loadClients();
        } else {
            alert('Failed to create client. Check if API Key already exists.');
        }
    } catch (err) {
        console.error('Error creating client', err);
    }
}

async function loadAnalytics() {
    try {
        const response = await fetch('/api/usage/summary');
        const data = await response.json();

        const ctx = document.getElementById('usageChart').getContext('2d');
        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.client),
                datasets: [{
                    label: 'Total Events Processed',
                    data: data.map(d => d.count),
                    backgroundColor: 'rgba(54, 162, 235, 0.6)',
                    borderColor: 'rgba(54, 162, 235, 1)',
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                scales: { y: { beginAtZero: true } }
            }
        });
    } catch (err) {
        console.error('Failed to load analytics', err);
    }
}

async function sendCampaign() {
    const apiKey = document.getElementById('campaign-target').value;
    const payload = {
        targetPackage: document.getElementById('target-package').value,
        name: document.getElementById('campaign-name').value,
        title: document.getElementById('push-title').value,
        body: document.getElementById('push-body').value,
        imageUrl: document.getElementById('push-image').value
    };

    try {
        const response = await fetch('/api/campaigns/send', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Monitoring-Api-Key': apiKey
            },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            alert('Campaign Sent Successfully!');
            document.getElementById('campaign-form').reset();
        } else {
            alert('Failed to send campaign.');
        }
    } catch (err) {
        console.error('Error sending campaign', err);
    }
}

async function loadUsers() {
    try {
        const response = await fetch('/api/users/all');
        const users = await response.json();
        const tbody = document.querySelector('#user-table tbody');
        if (!tbody) return;
        tbody.innerHTML = '';

        users.forEach(user => {
            const date = new Date(user.firstSeen).toLocaleString();
            tbody.innerHTML += `
                <tr>
                    <td>${user.name}</td>
                    <td><a href="mailto:${user.email}">${user.email}</a></td>
                    <td>${user.phone || 'N/A'}</td>
                    <td><code>${user.appId}</code></td>
                    <td><small class="text-muted">${date}</small></td>
                </tr>`;
        });
    } catch (err) {
        console.error('Error loading users', err);
    }
}
