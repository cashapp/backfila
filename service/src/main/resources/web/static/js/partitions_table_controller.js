import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js";
window.Stimulus = Application.start();

Stimulus.register("partitions-table", class extends Controller {
  static targets = ["filter", "tbody", "sortIndicator"];

  connect() {
    const params = new URLSearchParams(window.location.search);
    this.filter = (params.get("state") || "ALL").toUpperCase();
    this.sort = params.get("sort") || "none";

    if (this.hasFilterTarget) {
      this.filterTarget.value = this.filter;
    }
    this.applyAll();
  }

  onFilterChange(ev) {
    this.filter = ev.target.value;
    this.updateUrl();
    this.applyAll();
  }

  toggleSort() {
    const next = { "none": "desc", "desc": "asc", "asc": "none" };
    this.sort = next[this.sort] || "desc";
    this.updateUrl();
    this.applyAll();
  }

  updateUrl() {
    const params = new URLSearchParams(window.location.search);
    if (this.filter === "ALL") {
      params.delete("state");
    } else {
      params.set("state", this.filter);
    }
    if (this.sort === "none") {
      params.delete("sort");
    } else {
      params.set("sort", this.sort);
    }
    const qs = params.toString();
    const newUrl = window.location.pathname + (qs ? "?" + qs : "");
    history.replaceState(null, "", newUrl);
  }

  applyAll() {
    if (!this.hasTbodyTarget) return;
    const rows = Array.from(this.tbodyTarget.querySelectorAll("[data-partition-row]"));

    rows.forEach(row => {
      const state = row.getAttribute("data-partition-state");
      const matches = this.filter === "ALL" || state === this.filter;
      row.classList.toggle("hidden", !matches);
    });

    if (this.sort !== "none") {
      const dir = this.sort === "asc" ? 1 : -1;
      rows.sort((a, b) => {
        const aPct = parseFloat(a.getAttribute("data-progress-pct"));
        const bPct = parseFloat(b.getAttribute("data-progress-pct"));
        const aUnknown = isNaN(aPct) || aPct < 0;
        const bUnknown = isNaN(bPct) || bPct < 0;
        if (aUnknown && !bUnknown) return 1;
        if (!aUnknown && bUnknown) return -1;
        if (aUnknown && bUnknown) return 0;
        return (aPct - bPct) * dir;
      });
      rows.forEach(row => this.tbodyTarget.appendChild(row));
    }

    if (this.hasSortIndicatorTarget) {
      this.sortIndicatorTarget.textContent =
        this.sort === "asc" ? "▲" : this.sort === "desc" ? "▼" : "↕";
    }
  }
});
