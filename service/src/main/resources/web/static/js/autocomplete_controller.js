// https://github.com/afcapel/stimulus-autocomplete
//import { Application, Controller } from "https://unpkg.com/@hotwired/stimulus/dist/stimulus.js"
import { Application, Controller } from "/static/cache/stimulus/3.1.0/stimulus.min.js" // provided by Misk
window.Stimulus = Application.start()

class AutocompleteController extends Controller {
  static targets = ["input", "results"]
  static values = { url: String }

  connect() {
    this.inputTarget.addEventListener('input', this.onInput.bind(this));
    this.inputTarget.addEventListener('blur', () => setTimeout(() => this.hideResults(), 100));
    this.resultsTarget.addEventListener('click', this.onClick.bind(this));
    this.inputTarget.setAttribute('autocomplete', 'off');
  }

  onInput(event) {
    const query = event.target.value.trim();
    query.length >= 2 ? this.fetchResults(query) : this.hideResults();
  }

  onClick(event) {
    const option = event.target.closest('[role="option"]');
    if (option) {
      this.inputTarget.value = option.textContent.trim();
      this.hideResults();
    }
  }

  async fetchResults(query) {
    try {
      const response = await fetch(`${this.urlValue}?q=${encodeURIComponent(query)}`);
      const html = await response.text();
      this.resultsTarget.innerHTML = html;
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
