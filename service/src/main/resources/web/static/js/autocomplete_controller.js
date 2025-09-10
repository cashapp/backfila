// https://github.com/afcapel/stimulus-autocomplete
//import { Application, Controller } from "https://unpkg.com/@hotwired/stimulus/dist/stimulus.js"
import { Application, Controller } from "/static/cache/stimulus/3.1.0/stimulus.min.js" // provided by Misk
window.Stimulus = Application.start()

class AutocompleteController extends Controller {
  static targets = ["input", "results"]
  static values = { url: String }

  connect() {
    this.selectedIndex = -1;
    this.inputTarget.addEventListener('input', this.onInput.bind(this));
    this.inputTarget.addEventListener('keydown', this.onKeydown.bind(this));
    this.inputTarget.addEventListener('blur', () => setTimeout(() => this.hideResults(), 100));
    this.resultsTarget.addEventListener('click', this.onClick.bind(this));
    this.inputTarget.setAttribute('autocomplete', 'off');
  }

  onInput(event) {
    const query = event.target.value.trim();
    this.selectedIndex = -1;
    query.length >= 2 ? this.fetchResults(query) : this.hideResults();
  }

  onKeydown(event) {
    const options = this.getOptions();
    if (options.length === 0) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.selectedIndex = Math.min(this.selectedIndex + 1, options.length - 1);
        this.updateSelection();
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.selectedIndex = Math.max(this.selectedIndex - 1, -1);
        this.updateSelection();
        break;
      case 'Enter':
        event.preventDefault();
        if (this.selectedIndex >= 0) {
          this.selectOption(options[this.selectedIndex]);
        }
        break;
      case 'Escape':
        this.hideResults();
        break;
    }
  }

  onClick(event) {
    const option = event.target.closest('[role="option"]');
    if (option) {
      this.selectOption(option);
    }
  }

  selectOption(option) {
    this.inputTarget.value = option.textContent.trim();
    this.hideResults();
  }

  getOptions() {
    return Array.from(this.resultsTarget.querySelectorAll('[role="option"]'));
  }

  updateSelection() {
    const options = this.getOptions();
    options.forEach((option, index) => {
      if (index === this.selectedIndex) {
        option.classList.add('bg-indigo-600', 'text-white');
        option.classList.remove('text-gray-900');
      } else {
        option.classList.remove('bg-indigo-600', 'text-white');
        option.classList.add('text-gray-900');
      }
    });
  }

  async fetchResults(query) {
    try {
      const response = await fetch(`${this.urlValue}?q=${encodeURIComponent(query)}`);
      const html = await response.text();
      this.resultsTarget.innerHTML = html;
      this.selectedIndex = -1;
      html.trim() ? this.showResults() : this.hideResults();
    } catch {
      this.hideResults();
    }
  }

  showResults() {
    this.resultsTarget.style.display = 'block';
  }

  hideResults() {
    this.resultsTarget.style.display = 'none';
  }
}

Stimulus.register("autocomplete", AutocompleteController)
