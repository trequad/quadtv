const API = '/api/v1';
const state = {
  token: localStorage.getItem('quadtv_admin_token') || '',
  users: [],
  devices: [],
  providerSync: [],
  selectedProfileDeviceId: null,
};

const $ = (id) => document.getElementById(id);

function headers(json = true) {
  const result = {};
  if (json) result['Content-Type'] = 'application/json';
  if (state.token) result.Authorization = `Bearer ${state.token}`;
  return result;
}

async function api(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { ...headers(options.body !== undefined), ...(options.headers || {}) },
  });
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const payload = await response.json();
      detail = payload.detail || detail;
    } catch (_) {
      // keep HTTP detail
    }
    throw new Error(detail);
  }
  if (response.status === 204) return null;
  return response.json();
}

function showToast(message) {
  const toast = $('global-status');
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => { toast.hidden = true; }, 4200);
}

function setSignedIn(signedIn) {
  $('login-panel').hidden = signedIn;
  $('dashboard-panel').hidden = !signedIn;
  $('logout-button').hidden = !signedIn;
  $('session-state').textContent = signedIn ? 'Signed in' : 'Signed out';
  $('session-state').className = signedIn ? 'pill ok' : 'pill muted';
}

function daysUntil(value) {
  if (!value) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(`${value}T00:00:00`);
  return Math.round((target - today) / 86400000);
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
  }[char]));
}

function packageLabel(value) {
  const labels = {
    live_tv_only: 'Live TV Only',
    live_tv_vod: 'Live TV + VOD',
    live_tv_quaddemand: 'Live TV + QuadOnDemand',
    full_access: 'Full Access',
  };
  return labels[value] || value || 'Full Access';
}

function userStatus(user) {
  const days = daysUntil(user.expires_on);
  if (!user.active) return { label: 'Inactive', cls: 'danger' };
  if (days !== null && days < 0) return { label: 'Expired', cls: 'danger' };
  if (days !== null && days <= 14) return { label: `Expires in ${days}d`, cls: 'warn' };
  if (days !== null) return { label: `Expires ${user.expires_on}`, cls: 'ok' };
  return { label: 'No expiration', cls: 'muted' };
}

function renderMetrics() {
  const expired = state.users.filter((user) => userStatus(user).cls === 'danger').length;
  const expiring = state.users.filter((user) => userStatus(user).cls === 'warn').length;
  $('metric-users').textContent = state.users.length;
  $('metric-devices').textContent = state.devices.length;
  $('metric-expiring').textContent = expiring;
  $('metric-expired').textContent = expired;
}

function renderUsers() {
  const list = $('users-list');
  if (!state.users.length) {
    list.innerHTML = '<p class="item-meta">No users yet. Add one above.</p>';
  } else {
    list.innerHTML = state.users.map((user) => {
      const status = userStatus(user);
      return `
        <div class="item">
          <div class="item-head">
            <div>
              <div class="item-title">${escapeHtml(user.display_name)}</div>
              <div class="item-meta">${escapeHtml(user.email || 'No email')} · User #${user.id} · ${user.app_username ? `Login: ${escapeHtml(user.app_username)}` : 'No app login'} · Package: ${escapeHtml(packageLabel(user.access_package))}</div>
            </div>
            <span class="pill ${status.cls}">${escapeHtml(status.label)}</span>
          </div>
          <div class="item-section-label">Subscription</div>
          <form class="item-actions" data-user-subscription="${user.id}">
            <input type="date" name="expires_on" value="${escapeHtml(user.expires_on || '')}" />
            <select name="active">
              <option value="true" ${user.active ? 'selected' : ''}>Active</option>
              <option value="false" ${!user.active ? 'selected' : ''}>Inactive</option>
            </select>
            <button type="submit">Save</button>
          </form>
          <div class="item-section-label">Access package</div>
          <form class="item-actions" data-user-access="${user.id}">
            <select name="access_package">
              <option value="full_access" ${user.access_package === 'full_access' ? 'selected' : ''}>Full Access</option>
              <option value="live_tv_only" ${user.access_package === 'live_tv_only' ? 'selected' : ''}>Live TV Only</option>
              <option value="live_tv_vod" ${user.access_package === 'live_tv_vod' ? 'selected' : ''}>Live TV + VOD</option>
              <option value="live_tv_quaddemand" ${user.access_package === 'live_tv_quaddemand' ? 'selected' : ''}>Live TV + QuadOnDemand</option>
            </select>
            <button type="submit">Save</button>
          </form>
          <div class="item-meta">Live: ${user.can_access_live_tv ? 'yes' : 'no'} · VOD: ${user.can_access_vod ? 'yes' : 'no'} · QuadOnDemand: ${user.can_access_quaddemand ? 'yes' : 'no'} · Seerr: ${user.can_access_seerr ? 'yes' : 'no'}</div>
          <div class="item-section-label">App login credentials</div>
          <form class="item-actions" data-user-credentials="${user.id}">
            <input type="text" name="app_username" placeholder="Username" value="${escapeHtml(user.app_username || '')}" autocomplete="off" />
            <input type="password" name="app_pin" placeholder="PIN (leave blank to keep current)" autocomplete="new-password" />
            <input type="password" name="app_password" placeholder="Legacy password (leave blank to keep current)" autocomplete="new-password" />
            <button type="submit">Save</button>
          </form>
          <div class="item-actions">
            <button class="danger" data-delete-user="${user.id}" type="button">Delete user</button>
          </div>
        </div>`;
    }).join('');
  }
  document.querySelectorAll('[data-user-subscription]').forEach((form) => {
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const userId = form.dataset.userSubscription;
      const data = new FormData(form);
      await api(`/subscriptions/users/${userId}`, {
        method: 'PUT',
        body: JSON.stringify({
          expires_on: data.get('expires_on') || null,
          active: data.get('active') === 'true',
        }),
      });
      showToast('Subscription updated.');
      await refreshAll();
    });
  });
  document.querySelectorAll('[data-user-access]').forEach((form) => {
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const userId = form.dataset.userAccess;
      const data = new FormData(form);
      await api(`/users/${userId}`, {
        method: 'PATCH',
        body: JSON.stringify({ access_package: data.get('access_package') }),
      });
      showToast('Access package updated.');
      await refreshAll();
    });
  });
  document.querySelectorAll('[data-user-credentials]').forEach((form) => {
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const userId = form.dataset.userCredentials;
      const data = new FormData(form);
      const body = {};
      const username = data.get('app_username').trim();
      const password = data.get('app_password');
      const pin = data.get('app_pin');
      if (username) body.app_username = username;
      if (password) body.app_password = password;
      if (pin) body.app_pin = pin;
      if (!Object.keys(body).length) { showToast('Enter a username, PIN, or password to update.'); return; }
      await api(`/users/${userId}`, { method: 'PATCH', body: JSON.stringify(body) });
      form.querySelector('[name=app_password]').value = '';
      form.querySelector('[name=app_pin]').value = '';
      showToast('Login credentials updated.');
      await refreshAll();
    });
  });
  document.querySelectorAll('[data-delete-user]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (!confirm(`Delete user #${btn.dataset.deleteUser}? This cannot be undone.`)) return;
      await api(`/users/${btn.dataset.deleteUser}`, { method: 'DELETE' });
      showToast('User deleted.');
      await refreshAll();
    });
  });
  renderUserSelects();
}

function renderDevices() {
  const list = $('devices-list');
  if (!state.devices.length) {
    list.innerHTML = '<p class="item-meta">No devices have self-registered yet.</p>';
  } else {
    list.innerHTML = state.devices.map((device) => {
      const user = state.users.find((candidate) => candidate.id === device.user_id);
      return `
        <div class="item">
          <div class="item-head">
            <div>
              <div class="item-title">${escapeHtml(device.device_name)}</div>
              <div class="item-meta">${escapeHtml(device.device_identifier)} · App ${escapeHtml(device.app_version || 'unknown')}</div>
            </div>
            <span class="pill ${user ? 'ok' : 'muted'}">${user ? escapeHtml(user.display_name) : 'Unassigned'}</span>
          </div>
        </div>`;
    }).join('');
  }
  renderDeviceSelects();
}

function renderUserSelects() {
  const options = state.users.map((user) => `<option value="${user.id}">${escapeHtml(user.display_name)}</option>`).join('');
  $('assign-user-select').innerHTML = options || '<option value="">No users</option>';
  $('provider-sync-user-select').innerHTML = options || '<option value="">No users</option>';
}

function renderDeviceSelects() {
  const options = state.devices.map((device) => `<option value="${device.id}">${escapeHtml(device.device_name)} (#${device.id})</option>`).join('');
  $('assign-device-select').innerHTML = options || '<option value="">No devices</option>';
  $('profiles-device-select').innerHTML = options || '<option value="">No devices</option>';
}

function fillConfig(config) {
  $('config-live').value = config.live_tv_endpoint || '';
  $('config-xmltv').value = config.xmltv_endpoint || '';
  $('config-vod').value = config.vod_endpoint || '';
  $('config-jellyfin-url').value = config.jellyfin_base_url || '';
  $('config-jellyfin-key').value = config.jellyfin_api_key || '';
  $('config-max-profiles').value = config.max_profiles_per_device;
  $('config-live-limit').value = config.live_stream_limit_per_user;
  $('config-vod-limit').value = config.vod_stream_limit_per_user;
  $('config-jellyfin-limit').value = config.jellyfin_stream_limit_per_user;
  $('config-warning-days').value = (config.warning_threshold_days || []).join(',');
}

function renderAnnouncements(items) {
  $('announcements-list').innerHTML = items.length ? items.map((item) => `
    <div class="item">
      <div class="item-head">
        <div>
          <div class="item-title">${escapeHtml(item.title)}</div>
          <div class="item-meta">${escapeHtml(item.body)}</div>
        </div>
        <span class="pill ${item.active ? 'ok' : 'muted'}">${item.active ? 'Active' : 'Inactive'}</span>
      </div>
    </div>`).join('') : '<p class="item-meta">No announcements yet.</p>';
}

function renderNotifications(items) {
  $('notifications-list').innerHTML = items.length ? items.map((item) => `
    <div class="item">
      <div class="item-head">
        <div>
          <div class="item-title">${escapeHtml(item.title)}</div>
          <div class="item-meta">${escapeHtml(item.notification_type)} · ${escapeHtml(item.delivery_status)}</div>
        </div>
      </div>
    </div>`).join('') : '<p class="item-meta">No notification history yet.</p>';
}

function renderProviderSync() {
  const list = $('provider-sync-list');
  if (!state.users.length) {
    list.innerHTML = '<p class="item-meta">Add a user before linking provider credentials.</p>';
    return;
  }
  const linked = state.providerSync.filter((sync) => sync.provider_username);
  if (!linked.length) {
    list.innerHTML = '<p class="item-meta">No provider credentials linked yet.</p>';
    return;
  }
  list.innerHTML = linked.map((sync) => {
    const user = state.users.find((candidate) => candidate.id === sync.user_id);
    const summary = sync.results.map((result) => `${result.provider_type}: ${result.sync_status}`).join(' · ');
    return `
      <div class="item">
        <div class="item-head">
          <div>
            <div class="item-title">${escapeHtml(user ? user.display_name : `User #${sync.user_id}`)}</div>
            <div class="item-meta">${escapeHtml(sync.provider_username)} · ${escapeHtml(summary)}</div>
          </div>
          <span class="pill ok">Linked</span>
        </div>
      </div>`;
  }).join('');
}

async function loadProviderSyncStatuses() {
  const statuses = await Promise.all(state.users.map(async (user) => {
    try {
      return await api(`/provider-sync/users/${user.id}`);
    } catch (error) {
      return { user_id: user.id, provider_username: '', results: [] };
    }
  }));
  state.providerSync = statuses;
  renderProviderSync();
}

async function loadProfiles(deviceId) {
  if (!deviceId) return;
  state.selectedProfileDeviceId = deviceId;
  const response = await api(`/devices/${deviceId}/profiles`, { headers: {} });
  $('profiles-list').innerHTML = response.items.length ? response.items.map((profile) => `
    <div class="item">
      <div class="item-head">
        <div>
          <div class="item-title">${escapeHtml(profile.display_name)}</div>
          <div class="item-meta">Avatar: ${escapeHtml(profile.avatar)} · Profile #${profile.id}</div>
        </div>
        <span class="pill ${profile.parental_enabled ? 'warn' : 'muted'}">${profile.parental_enabled ? 'Parental on' : 'Parental off'}</span>
      </div>
    </div>`).join('') : '<p class="item-meta">No profiles for this device.</p>';
}

async function refreshAll() {
  const [users, devices, config, announcements, notifications] = await Promise.all([
    api('/users'),
    api('/devices'),
    api('/app/config', { headers: {} }),
    api('/announcements'),
    api('/notifications/history'),
  ]);
  state.users = users.items || [];
  state.devices = devices.items || [];
  renderMetrics();
  renderUsers();
  renderDevices();
  fillConfig(config);
  renderAnnouncements(announcements.items || []);
  renderNotifications(notifications.items || []);
  await loadProviderSyncStatuses();
  await loadReleases();
  if (state.selectedProfileDeviceId) await loadProfiles(state.selectedProfileDeviceId);
}

async function loadReleases() {
  try {
    const status = await api('/releases/current', { headers: {} });
    renderCurrentRelease(status);
  } catch (_) {}
}

function renderCurrentRelease(status) {
  const el = $('current-release-info');
  if (!status || !status.release) {
    el.innerHTML = '<p class="muted">No published release yet.</p>';
    return;
  }
  const r = status.release;
  el.innerHTML = `
    <div class="list-row">
      <strong>v${r.version_name}</strong> (code ${r.version_code})
      ${r.forced ? '<span class="pill danger">Force update</span>' : '<span class="pill ok">Optional</span>'}
      <div style="margin-top:4px;font-size:0.85em;color:#aaa">Min supported code: ${r.minimum_supported_version_code}</div>
      ${r.changelog ? `<div style="margin-top:6px;font-size:0.9em">${r.changelog}</div>` : ''}
      <div style="margin-top:6px;font-size:0.8em;word-break:break-all"><a href="${r.apk_url}" target="_blank">${r.apk_url}</a></div>
    </div>`;
}

function toIsoOrNull(localDateTime) {
  if (!localDateTime) return null;
  return new Date(localDateTime).toISOString();
}

function bindEvents() {
  $('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    $('login-message').textContent = 'Signing in...';
    try {
      const response = await api('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: $('login-username').value, password: $('login-password').value }),
      });
      state.token = response.access_token;
      localStorage.setItem('quadtv_admin_token', state.token);
      setSignedIn(true);
      await refreshAll();
      $('login-message').textContent = '';
      showToast('Signed in to QuadTV Admin.');
    } catch (error) {
      $('login-message').textContent = error.message;
    }
  });

  $('logout-button').addEventListener('click', () => {
    state.token = '';
    localStorage.removeItem('quadtv_admin_token');
    setSignedIn(false);
  });

  $('refresh-button').addEventListener('click', async () => {
    await refreshAll();
    showToast('Dashboard refreshed.');
  });

  $('create-user-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const appUsername = $('user-app-username').value.trim() || null;
    const appPin = $('user-app-pin').value || null;
    const appPassword = $('user-app-password').value || null;
    await api('/users', {
      method: 'POST',
      body: JSON.stringify({
        display_name: $('user-display-name').value,
        email: $('user-email').value || null,
        app_username: appUsername,
        app_pin: appPin,
        app_password: appPassword,
        access_package: $('user-access-package').value,
      }),
    });
    event.target.reset();
    showToast('User added.');
    await refreshAll();
  });

  $('assign-device-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const userId = $('assign-user-select').value;
    const deviceId = $('assign-device-select').value;
    await api(`/users/${userId}/devices/${deviceId}`, { method: 'POST', body: JSON.stringify({}) });
    showToast('Device linked to user.');
    await refreshAll();
  });

  $('provider-sync-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const userId = $('provider-sync-user-select').value;
    await api(`/provider-sync/users/${userId}/manual-import`, {
      method: 'POST',
      body: JSON.stringify({
        provider_type: $('provider-sync-type').value,
        provider_username: $('provider-sync-username').value,
        provider_password: $('provider-sync-password').value,
      }),
    });
    $('provider-sync-password').value = '';
    showToast('Provider credentials linked.');
    await refreshAll();
  });

  $('config-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    await api('/app/config', {
      method: 'PUT',
      body: JSON.stringify({
        live_tv_endpoint: $('config-live').value,
        xmltv_endpoint: $('config-xmltv').value,
        vod_endpoint: $('config-vod').value,
        jellyfin_base_url: $('config-jellyfin-url').value || null,
        jellyfin_api_key: $('config-jellyfin-key').value || null,
        max_profiles_per_device: Number($('config-max-profiles').value),
        warning_threshold_days: $('config-warning-days').value.split(',').map((value) => Number(value.trim())).filter((value) => !Number.isNaN(value)),
        live_stream_limit_per_user: Number($('config-live-limit').value),
        vod_stream_limit_per_user: Number($('config-vod-limit').value),
        jellyfin_stream_limit_per_user: Number($('config-jellyfin-limit').value),
      }),
    });
    showToast('Endpoint config saved.');
    await refreshAll();
  });

  $('announcement-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    await api('/announcements', {
      method: 'POST',
      body: JSON.stringify({
        title: $('announcement-title').value,
        body: $('announcement-body').value,
        image_url: $('announcement-image').value || null,
        publish_at: toIsoOrNull($('announcement-publish').value),
        expires_at: toIsoOrNull($('announcement-expires').value),
        active: true,
        push_notification: $('announcement-push').checked,
      }),
    });
    event.target.reset();
    showToast('Announcement published.');
    await refreshAll();
  });

  $('publish-release-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const statusEl = $('release-status');
    statusEl.textContent = 'Publishing…';
    try {
      await api('/releases', {
        method: 'POST',
        body: JSON.stringify({
          version_name: $('release-version-name').value,
          version_code: parseInt($('release-version-code').value, 10),
          apk_url: $('release-apk-url').value,
          minimum_supported_version_code: parseInt($('release-min-version-code').value, 10),
          changelog: $('release-changelog').value || 'No changelog provided.',
          forced: $('release-forced').checked,
          published: true,
          release_date: null,
        }),
      });
      event.target.reset();
      statusEl.textContent = 'Release published. Beta testers will be prompted on next app launch.';
      showToast('Release published.');
      await loadReleases();
    } catch (err) {
      statusEl.textContent = `Error: ${err.message}`;
    }
  });

  $('load-profiles-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    await loadProfiles($('profiles-device-select').value);
  });

  $('create-profile-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const deviceId = state.selectedProfileDeviceId || $('profiles-device-select').value;
    await api(`/devices/${deviceId}/profiles`, {
      method: 'POST',
      body: JSON.stringify({
        display_name: $('profile-display-name').value,
        avatar: $('profile-avatar').value,
        parental_pin: $('profile-pin').value || null,
      }),
    });
    event.target.reset();
    showToast('Profile added.');
    await loadProfiles(deviceId);
  });
}

window.addEventListener('DOMContentLoaded', async () => {
  bindEvents();
  setSignedIn(Boolean(state.token));
  if (state.token) {
    try {
      await refreshAll();
    } catch (error) {
      showToast(`Session needs login: ${error.message}`);
      state.token = '';
      localStorage.removeItem('quadtv_admin_token');
      setSignedIn(false);
    }
  }
});
