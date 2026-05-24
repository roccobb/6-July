const WORDS_URL = "app/src/main/assets/words.json";
const FAVORITES_KEY = "valkotassu.favoriteWords";

const state = {
  words: [],
  dailyWord: null,
  favorites: new Set(),
  selectedTab: "today",
};

const elements = {
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
      state.selectedTab = button.dataset.tab;
      renderTabs();
    });
  });
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
    panel.classList.toggle("active", name === state.selectedTab);
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
          ${isFavorite ? "★" : "☆"}
        </button>
        <button class="icon-button" type="button" data-action="share" aria-label="Share word">↗</button>
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
      <button class="icon-button active" type="button" data-key="${escapeHtml(favoriteKey(word))}" aria-label="Remove favorite">★</button>
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
    navigator.serviceWorker.register("service-worker.js");
  }
}
