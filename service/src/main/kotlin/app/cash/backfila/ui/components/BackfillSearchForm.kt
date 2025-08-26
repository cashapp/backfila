package app.cash.backfila.ui.components

import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.script
import kotlinx.html.unsafe

fun TagConsumer<*>.BackfillSearchForm(
  backfillName: String? = null,
  createdByUser: String? = null,
  serviceName: String,
  variantName: String,
) {
  div("px-4 sm:px-6 lg:px-8 py-4 bg-gray-50 border-b border-gray-200") {
    form(classes = "flex flex-wrap gap-4 items-end") {
      method = kotlinx.html.FormMethod.get
      attributes["data-turbo-frame"] = "_top"

      // Backfill Name Search with datalist (native autocomplete)
      div("flex-1 min-w-0") {
        label("block text-sm font-medium text-gray-700 mb-1") {
          htmlFor = "backfill_name"
          +"Backfill Name"
        }
        input(classes = "block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm") {
          type = InputType.text
          attributes["id"] = "backfill_name"
          name = "backfill_name"
          placeholder = "Type to search backfills..."
          value = backfillName ?: ""
          attributes["list"] = "backfill-names"
          attributes["autocomplete"] = "off"
        }
        // Native HTML5 datalist for autocomplete (using unsafe HTML since datalist isn't in kotlinx.html)
        unsafe {
          +"""<datalist id="backfill-names"></datalist>"""
        }
      }

      // Created By User Search
      div("flex-1 min-w-0") {
        label("block text-sm font-medium text-gray-700 mb-1") {
          htmlFor = "created_by_user"
          +"Created by"
        }
        input(classes = "block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm") {
          type = InputType.text
          attributes["id"] = "created_by_user"
          name = "created_by_user"
          placeholder = "test.user"
          value = createdByUser ?: ""
        }
      }

      // Search and Clear buttons
      div("flex gap-2") {
        button(classes = "inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500") {
          type = ButtonType.submit
          +"FILTER"
        }

        button(classes = "inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500") {
          type = ButtonType.button
          attributes["id"] = "clear_filters_btn"
          +"CLEAR FILTERS"
        }
      }
    }

    // JavaScript to populate datalist and handle clear filters
    script {
      unsafe {
        +"""
        function initBackfillForm() {
          const datalist = document.getElementById('backfill-names');
          const clearFiltersBtn = document.getElementById('clear_filters_btn');
          
          if (!datalist) return;
          
          // Fetch and populate backfill names
          const url = '/services/$serviceName/variants/$variantName/backfill-names';
          fetch(url)
            .then(response => response.json())
            .then(data => {
              datalist.innerHTML = '';
              if (data.backfill_names) {
                data.backfill_names.forEach(name => {
                  const option = document.createElement('option');
                  option.value = name;
                  datalist.appendChild(option);
                });
              }
            })
            .catch(err => console.error('Error loading backfill names:', err));
          
          // Clear filters button handler
          if (clearFiltersBtn && !clearFiltersBtn.hasAttribute('data-initialized')) {
            clearFiltersBtn.setAttribute('data-initialized', 'true');
            clearFiltersBtn.addEventListener('click', function() {
              let baseUrl = '/services/$serviceName';
              if ('$variantName' !== 'default') {
                baseUrl += '/$variantName';
              }
              window.location.href = baseUrl;
            });
          }
        }
        
        // Run on initial page load
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', function() {
            initBackfillForm();
            setupAutoReloadWatcher();
          });
        } else {
          initBackfillForm();
          setupAutoReloadWatcher();
        }
        
        function setupAutoReloadWatcher() {
          // Watch for when the datalist gets replaced/removed by AutoReload
          const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
              if (mutation.type === 'childList') {
                // Check if nodes were added that contain our datalist
                mutation.addedNodes.forEach(function(node) {
                  if (node.nodeType === Node.ELEMENT_NODE) {
                    if (node.id === 'backfill-names' || 
                        (node.querySelector && node.querySelector('#backfill-names'))) {
                      // Datalist was re-added, initialize it
                      setTimeout(initBackfillForm, 10);
                    }
                  }
                });
              }
            });
          });
          
          // Start observing the entire document for changes
          observer.observe(document.body, {
            childList: true,
            subtree: true
          });
        }
        """.trimIndent()
      }
    }
  }
}
