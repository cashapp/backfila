import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js";
window.Stimulus = Application.start();

Stimulus.register("auto-reload", class extends Controller {
  static targets = ["frame"]

  connect() {
    console.log("Auto-reload connected...");
    this.startReloading()
  }

  disconnect() {
    console.log("Clearing auto-reload interval...");
    if (this.interval) clearInterval(this.interval)
  }

  startReloading() {
    this.interval = setInterval(() => {
      console.log("auto-reload triggered...");
      const pathname = window.location.pathname
      const url = new URL(pathname, window.location.origin)
      url.searchParams.set('frame', 'true')

      Turbo.visit(url.toString(), { frame: this.frameTarget.id })
    }, 10000)
  }
});