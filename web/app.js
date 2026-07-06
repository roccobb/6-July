const WORDS_URL = "app/src/main/assets/words.json";
const FAVORITES_KEY = "valkotassu.favoriteWords";
const TABS = ["today", "favorites", "about"];
const SWIPE_MIN_DISTANCE = 56;
const SWIPE_MAX_VERTICAL_DRIFT = 70;
const WORD_TITLE_MAX_SIZE = 54;
const WORD_TITLE_MIN_SIZE = 18;
const BIRTHDAY_NAME = "Amaya";
const BIRTHDAY_MESSAGE_MS = 3600;
let birthdayMessageTimer = null;

const state = {
  words: [],
  dailyWord: null,
  favorites: new Set(),
  selectedTab: tabFromHash(),
  tabDirection: 0,
  swipeStart: null,
};

const elements = {
  appShell: document.querySelector(".app-shell"),
  appHeader: document.querySelector(".app-header"),
  dateLabel: document.querySelector("#dateLabel"),
  dailyCard: document.querySelector("#dailyCard"),
  favoritesList: document.querySelector("#favoritesList"),
  tabButtons: [...document.querySelectorAll(".tab-button")],
  panels: {
    today: document.querySelector("#todayPanel"),
    favorites: document.querySelector("#favoritesPanel"),
    about: document.querySelector("#aboutPanel"),
  },
};

init();

async function init() {
  elements.dateLabel.textContent = formatDate(new Date());
  state.favorites = loadFavorites();
  bindTabs();
  bindSwipeNavigation();
  setupBirthdaySurprise(new Date());
  registerServiceWorker();

  try {
    const response = await fetch(WORDS_URL);
    if (!response.ok) {
      throw new Error(`Could not load dictionary (${response.status})`);
    }
    state.words = await response.json();
    state.dailyWord = chooseDailyWord(state.words, todayKey());
    render();
  } catch (error) {
    elements.dailyCard.className = "word-card loading-card";
    elements.dailyCard.innerHTML = `<p>${escapeHtml(error.message)}</p>`;
  }
}

function bindTabs() {
  elements.tabButtons.forEach((button) => {
    button.addEventListener("click", () => {
      selectTab(button.dataset.tab);
    });
  });
}

function bindSwipeNavigation() {
  if (!elements.appShell) {
    return;
  }

  elements.appShell.addEventListener("touchstart", (event) => {
    if (event.touches.length !== 1) {
      state.swipeStart = null;
      return;
    }

    const touch = event.touches[0];
    state.swipeStart = {
      x: touch.clientX,
      y: touch.clientY,
    };
  }, { passive: true });

  elements.appShell.addEventListener("touchend", (event) => {
    if (!state.swipeStart || event.changedTouches.length !== 1) {
      state.swipeStart = null;
      return;
    }

    const touch = event.changedTouches[0];
    const deltaX = touch.clientX - state.swipeStart.x;
    const deltaY = touch.clientY - state.swipeStart.y;
    state.swipeStart = null;

    if (Math.abs(deltaX) < SWIPE_MIN_DISTANCE || Math.abs(deltaY) > SWIPE_MAX_VERTICAL_DRIFT) {
      return;
    }

    selectAdjacentTab(deltaX < 0 ? 1 : -1);
  }, { passive: true });
}

function selectAdjacentTab(direction) {
  const currentIndex = TABS.indexOf(state.selectedTab);
  const nextTab = TABS[currentIndex + direction];

  if (nextTab) {
    selectTab(nextTab);
  }
}

function selectTab(tab) {
  if (!TABS.includes(tab) || tab === state.selectedTab) {
    return;
  }

  state.tabDirection = Math.sign(TABS.indexOf(tab) - TABS.indexOf(state.selectedTab));
  state.selectedTab = tab;
  history.replaceState(null, "", `#${state.selectedTab}`);
  renderTabs();
}

function render() {
  renderTabs();
  renderDailyWord();
  renderFavorites();
}

function renderTabs() {
  elements.tabButtons.forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === state.selectedTab);
  });
  Object.entries(elements.panels).forEach(([name, panel]) => {
    const isActive = name === state.selectedTab;
    panel.classList.remove("panel-from-left", "panel-from-right");
    panel.classList.toggle("active", isActive);

    if (isActive && state.tabDirection !== 0) {
      panel.classList.add(state.tabDirection > 0 ? "panel-from-right" : "panel-from-left");
    }
  });
}

function renderDailyWord() {
  const word = state.dailyWord;
  if (!word) {
    elements.dailyCard.className = "word-card loading-card";
    elements.dailyCard.innerHTML = "<p>No words available.</p>";
    return;
  }

  const isFavorite = state.favorites.has(favoriteKey(word));
  elements.dailyCard.className = "word-card";
  elements.dailyCard.innerHTML = `
    <div class="word-topline">
      <h2 class="word-title">${escapeHtml(word.word)}</h2>
      <div class="word-actions">
        <span class="pos-pill">${escapeHtml(word.partOfSpeech)}</span>
        <button class="icon-button ${isFavorite ? "active" : ""}" type="button" data-action="favorite" aria-label="${isFavorite ? "Remove favorite" : "Add favorite"}">
          ${starIconMarkup(isFavorite)}
        </button>
        <button class="icon-button" type="button" data-action="share" aria-label="Share word">${shareIconMarkup()}</button>
      </div>
    </div>
    ${word.ipa ? `<p class="ipa">${escapeHtml(word.ipa)}</p>` : ""}
    <div class="definitions">
      ${word.definitions.map((definition) => `<p class="definition">${escapeHtml(definition)}</p>`).join("")}
    </div>
    ${word.example ? `
      <div class="example">
        <p class="section-label">Example</p>
        <p class="example-text">${escapeHtml(word.example.text)}</p>
        <p class="example-translation">${escapeHtml(word.example.translation)}</p>
      </div>
    ` : ""}
  `;

  elements.dailyCard.querySelector('[data-action="favorite"]').addEventListener("click", () => {
    toggleFavorite(word);
  });
  elements.dailyCard.querySelector('[data-action="share"]').addEventListener("click", () => {
    shareWord(word);
  });
  requestAnimationFrame(fitDailyWordTitle);
}

function renderFavorites() {
  const favoriteWords = state.words.filter((word) => state.favorites.has(favoriteKey(word)));

  if (favoriteWords.length === 0) {
    elements.favoritesList.innerHTML = `
      <article class="empty-card">
        <h2>No favorite words yet</h2>
        <p>Tap the star on today's word to save it here.</p>
      </article>
    `;
    return;
  }

  elements.favoritesList.innerHTML = favoriteWords.map((word) => `
    <article class="favorite-card">
      <div>
        <h2>${escapeHtml(word.word)}</h2>
        <p class="part">${escapeHtml(word.partOfSpeech)}</p>
        <p>${escapeHtml(word.definitions[0] || "")}</p>
      </div>
      <button class="icon-button active" type="button" data-key="${escapeHtml(favoriteKey(word))}" aria-label="Remove favorite">${starIconMarkup(true)}</button>
    </article>
  `).join("");

  elements.favoritesList.querySelectorAll("[data-key]").forEach((button) => {
    button.addEventListener("click", () => {
      state.favorites.delete(button.dataset.key);
      saveFavorites(state.favorites);
      renderDailyWord();
      renderFavorites();
    });
  });
}

function toggleFavorite(word) {
  const key = favoriteKey(word);
  if (state.favorites.has(key)) {
    state.favorites.delete(key);
  } else {
    state.favorites.add(key);
  }
  saveFavorites(state.favorites);
  renderDailyWord();
  renderFavorites();
}

async function shareWord(word) {
  const text = shareText(word);
  if (navigator.share) {
    await navigator.share({
      title: `Finnish Word of the Day: ${word.word}`,
      text,
    });
    return;
  }
  await navigator.clipboard.writeText(text);
}

function chooseDailyWord(words, key) {
  if (!words.length) {
    return null;
  }
  const mixed = key * 1103515245 + 12345;
  return words[mixed % words.length];
}

function todayKey() {
  const now = new Date();
  const start = new Date(now.getFullYear(), 0, 0);
  const day = Math.floor((now - start) / 86400000);
  return now.getFullYear() * 1000 + day;
}

function formatDate(date) {
  return new Intl.DateTimeFormat("en", {
    month: "long",
    day: "numeric",
  }).format(date);
}

function favoriteKey(word) {
  return `${word.word}|${word.partOfSpeech}`;
}

function loadFavorites() {
  try {
    return new Set(JSON.parse(localStorage.getItem(FAVORITES_KEY) || "[]"));
  } catch {
    return new Set();
  }
}

function saveFavorites(favorites) {
  localStorage.setItem(FAVORITES_KEY, JSON.stringify([...favorites]));
}

function shareText(word) {
  const lines = [
    `Today's Finnish word: ${word.word}`,
    word.ipa,
    word.partOfSpeech,
    ...word.definitions.map((definition) => `- ${definition}`),
  ].filter(Boolean);

  if (word.example) {
    lines.push("", "Example:", word.example.text, word.example.translation);
  }

  return lines.join("\n");
}

function shareIconMarkup() {
  return `
    <svg class="share-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path class="share-icon-line" d="M8 12 L18 6 M8 12 L18 18"></path>
      <circle cx="8" cy="12" r="3.2"></circle>
      <circle cx="18" cy="6" r="3.2"></circle>
      <circle cx="18" cy="18" r="3.2"></circle>
    </svg>
  `;
}

function cakeIconMarkup() {
  return `
    <svg class="cake-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path class="cake-flame" d="M12 2.8c1 1 1.4 1.8 1.4 2.6a1.4 1.4 0 0 1-2.8 0c0-.8.4-1.6 1.4-2.6z"></path>
      <path class="cake-line" d="M12 7v3"></path>
      <path class="cake-fill" d="M6.2 11.2h11.6c1 0 1.8.8 1.8 1.8v2.2H4.4V13c0-1 .8-1.8 1.8-1.8z"></path>
      <path class="cake-line" d="M4.4 15.2h15.2v4H4.4z"></path>
      <path class="cake-line" d="M6.4 14c1.2 1 2.4 1 3.6 0s2.4-1 3.6 0 2.4 1 3.6 0"></path>
    </svg>
  `;
}

function starIconMarkup(isFavorite) {
  return `
    <svg class="favorite-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path
        d="M12 3.4l2.58 5.23 5.77.84-4.18 4.07.99 5.74L12 16.56l-5.16 2.72.99-5.74-4.18-4.07 5.77-.84L12 3.4z"
        ${isFavorite ? "" : "fill=\"none\""}
      ></path>
    </svg>
  `;
}

function setupBirthdaySurprise(date) {
  if (!isBirthdayDate(date)) {
    return;
  }

  const button = document.createElement("button");
  button.className = "birthday-button";
  button.type = "button";
  button.setAttribute("aria-label", `Birthday surprise for ${BIRTHDAY_NAME}`);
  button.innerHTML = cakeIconMarkup();
  button.addEventListener("click", showBirthdaySurprise);
  document.body.append(button);
}

function isBirthdayDate(date) {
  return date.getMonth() === 6 && date.getDate() === 6;
}

function showBirthdaySurprise() {
  let message = document.querySelector(".birthday-message");

  if (!message) {
    message = document.createElement("div");
    message.className = "birthday-message";
    message.setAttribute("role", "status");
    message.setAttribute("aria-live", "polite");
    document.body.append(message);
  }

  message.textContent = `Hyvää syntymäpäivää ${BIRTHDAY_NAME}!`;
  message.classList.remove("show");
  window.clearTimeout(birthdayMessageTimer);
  requestAnimationFrame(() => {
    message.classList.add("show");
  });
  birthdayMessageTimer = window.setTimeout(() => {
    message.classList.remove("show");
  }, BIRTHDAY_MESSAGE_MS);

  launchConfetti();
}

function launchConfetti() {
  const existing = document.querySelector(".confetti-layer");

  if (existing) {
    existing.remove();
  }

  const layer = document.createElement("div");
  layer.className = "confetti-layer";
  layer.setAttribute("aria-hidden", "true");
  document.body.append(layer);

  const colors = ["#17633f", "#f0b429", "#e05d5d", "#4f8fd8", "#d76cc4", "#f58f29"];

  for (let index = 0; index < 96; index += 1) {
    const piece = document.createElement("span");
    piece.className = "confetti-piece";
    piece.style.setProperty("--x", `${Math.random() * 100}vw`);
    piece.style.setProperty("--delay", `${Math.random() * 0.45}s`);
    piece.style.setProperty("--duration", `${2.5 + Math.random() * 1.7}s`);
    piece.style.setProperty("--spin", `${360 + Math.random() * 720}deg`);
    piece.style.setProperty("--drift", `${(Math.random() - 0.5) * 120}px`);
    piece.style.setProperty("--color", colors[index % colors.length]);
    layer.append(piece);
  }

  window.setTimeout(() => {
    layer.remove();
  }, 4700);
}

function fitDailyWordTitle() {
  const title = elements.dailyCard.querySelector(".word-title");

  if (!title) {
    return;
  }

  title.style.fontSize = `${WORD_TITLE_MAX_SIZE}px`;

  for (let size = WORD_TITLE_MAX_SIZE; size >= WORD_TITLE_MIN_SIZE; size -= 1) {
    title.style.fontSize = `${size}px`;

    if (title.scrollWidth <= title.clientWidth) {
      return;
    }
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function registerServiceWorker() {
  if ("serviceWorker" in navigator) {
    let refreshing = false;

    navigator.serviceWorker.addEventListener("controllerchange", () => {
      if (refreshing) {
        return;
      }

      refreshing = true;
      window.location.reload();
    });

    navigator.serviceWorker.register("service-worker.js").then((registration) => {
      registration.update();
    });
  }
}

function tabFromHash() {
  const tab = window.location.hash.replace("#", "");
  return TABS.includes(tab) ? tab : "today";
}

window.addEventListener("resize", fitDailyWordTitle);
