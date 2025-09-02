// https://github.com/afcapel/stimulus-autocomplete
//import { Application, Controller } from "https://unpkg.com/@hotwired/stimulus/dist/stimulus.js"
import { Application, Controller } from "/static/cache/stimulus/3.1.0/stimulus.min.js" // provided by Misk
window.Stimulus = Application.start()

/*
 * Usage
 * =====
 *
 * <div data-controller="autocomplete" data-autocomplete-url-value="/birds/search" role="combobox">
 *   <input type="text" data-autocomplete-target="input"/>
 *   <input type="hidden" name="bird_id" data-autocomplete-target="hidden"/>
 *   <ul class="list-group" data-autocomplete-target="results"></ul>
 * </div>
 *
 * The server is expected to send back snippets as such:
 *
 * <li class="list-group-item" role="option" data-autocomplete-value="1">Blackbird</li>
 * <li class="list-group-item" role="option" data-autocomplete-value="2">Bluebird</li>
 * <li class="list-group-item" role="option" data-autocomplete-value="3">Mockingbird</li>
 *
 */
class AutocompleteController extends Controller {
  static targets = ["input", "results"]
  static values = { 
    url: String, 
    minLength: { type: Number, default: 2 },
    delay: { type: Number, default: 300 }
  }

  connect() {
    this.inputTarget.addEventListener('input', this.debounce(this.onInput.bind(this), this.delayValue));
    this.inputTarget.addEventListener('blur', this.onBlur.bind(this));
    this.resultsTarget.addEventListener('click', this.onClick.bind(this));
    this.resultsTarget.addEventListener('mousedown', this.onMousedown.bind(this));
    
    // Set autocomplete attributes
    this.inputTarget.setAttribute('autocomplete', 'off');
    this.inputTarget.setAttribute('spellcheck', 'false');
    
    this.mouseDown = false;
  }

  onInput(event) {
    const query = event.target.value.trim();
    
    if (query.length >= this.minLengthValue) {
      this.fetchResults(query);
    } else {
      this.hideResults();
    }
  }

  onBlur() {
    if (!this.mouseDown) {
      this.hideResults();
    }
  }

  onMousedown() {
    this.mouseDown = true;
    setTimeout(() => { this.mouseDown = false; }, 100);
  }

  onClick(event) {
    const option = event.target.closest('[role="option"]');
    if (option) {
      this.selectOption(option);
    }
  }

  selectOption(option) {
    const value = option.getAttribute('data-autocomplete-value') || option.textContent.trim();
    this.inputTarget.value = value;
    this.hideResults();
    this.inputTarget.focus();
  }

  async fetchResults(query) {
    try {
      const url = `${this.urlValue}?q=${encodeURIComponent(query)}`;
      const response = await fetch(url, {
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
      });
      const html = await response.text();
      
      this.resultsTarget.innerHTML = html;
      
      if (this.resultsTarget.children.length > 0) {
        this.showResults();
      } else {
        this.hideResults();
      }
    } catch (error) {
      this.hideResults();
    }
  }

  showResults() {
    this.resultsTarget.style.display = 'block';
  }

  hideResults() {
    this.resultsTarget.style.display = 'none';
  }

  debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }
}

Stimulus.register("autocomplete", AutocompleteController)

