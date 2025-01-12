import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js";
window.Stimulus = Application.start();

// Hard reload
Stimulus.register("auto-reload", class extends Controller {
  connect() {
    console.log("Auto-reload connected...");
    this.reloadInterval = setInterval(() => {
      console.log("Reloading page...");
      window.location.reload();
    }, 5000); // 5000 milliseconds = 1 second
  }

  disconnect() {
    console.log("Clearing auto-reload interval...");
    clearInterval(this.reloadInterval);
  }
});


// TODO fix soft-reload
//Stimulus.register("auto-reload", class extends Controller {
//  static targets = ["frame"];
//
//  connect() {
//    console.log("Auto-reload connected...");
//    this.reloadInterval = setInterval(() => {
//      console.log("Reloading Turbo Frame...");
//      this.reloadFrame();
//    }, 5000); // 5000 milliseconds = 5 seconds
//  }
//
//  disconnect() {
//    console.log("Clearing auto-reload interval...");
//    clearInterval(this.reloadInterval);
//  }
//
//  reloadFrame() {
//    this.frameTarget.src = this.frameTarget.src;
//  }
//});