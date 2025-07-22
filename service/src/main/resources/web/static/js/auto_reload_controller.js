import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js";
window.Stimulus = Application.start();

Stimulus.register("auto-reload", class extends Controller {
  static targets = ["frame"]

  connect() {
    this.startReloading()
  }

  disconnect() {
    if (this.interval) clearInterval(this.interval)
  }

  startReloading() {
    this.interval = setInterval(() => {
      // Get the clean base URL without any frame parameters
      const baseUrl = window.location.origin + window.location.pathname
      const currentParams = new URLSearchParams(window.location.search)
      
      // Remove frame parameter if it exists
      currentParams.delete('frame')
      
      // Build target URL
      const targetUrl = baseUrl + (currentParams.toString() ? '?' + currentParams.toString() : '')
      
      fetch(targetUrl)
        .then(response => response.text())
        .then(html => {
          const parser = new DOMParser()
          const doc = parser.parseFromString(html, 'text/html')
          const newFrameContent = doc.querySelector(`#${this.frameTarget.id}`)
          
          if (newFrameContent) {
            this.preserveToggleStatesForEvents(newFrameContent)
            
            // Clear the src attribute to prevent any URL references
            this.frameTarget.removeAttribute('src')
            // Update the content
            this.frameTarget.innerHTML = newFrameContent.innerHTML
          }
        })
        .catch(error => {
          console.error("Auto-reload error:", error)
        })
    }, 10000)
  }

  preserveToggleStatesForEvents(newContent) {
    // Only preserve toggle states for events frame
    if (!this.frameTarget.id.includes('-events')) {
      return
    }
    
    // Find all toggle elements in current DOM that might be expanded
    const currentToggles = this.frameTarget.querySelectorAll('[data-event-id]')
    
    currentToggles.forEach(currentToggle => {
      const eventId = currentToggle.getAttribute('data-event-id')
      
      // Check if this toggle is currently expanded by looking for the visible full content
      const fullDiv = currentToggle.querySelector('[data-toggle-target="toggleable"]:last-child')
      const isExpanded = fullDiv && !fullDiv.classList.contains('hidden')

      if (isExpanded) {
        // Find corresponding element in new content
        const newToggle = newContent.querySelector(`[data-event-id="${eventId}"]`)
        if (newToggle) {
          // Set the new content to expanded state
          const newShortDiv = newToggle.querySelector('[data-toggle-target="toggleable"]:first-child')
          const newFullDiv = newToggle.querySelector('[data-toggle-target="toggleable"]:last-child')
          
          if (newShortDiv && newFullDiv) {
            newShortDiv.classList.add('hidden')
            newFullDiv.classList.remove('hidden')
          }
        }
      }
    })
  }
});