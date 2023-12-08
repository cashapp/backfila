// https://github.com/afcapel/stimulus-autocomplete
//import { Application, Controller } from "https://unpkg.com/@hotwired/stimulus/dist/stimulus.js"
import { Application, Controller } from "/static/cache/stimulus/3.1.0/stimulus.min.js" // provided by Misk
import { Autocomplete } from "../cache/stimulus-autocomplete/3.0.2/autocomplete.min.js"
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
Stimulus.register("autocomplete", Autocomplete)

