package app.cash.backfila.ui.components

import kotlinx.html.ButtonType
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.unsafe

fun TagConsumer<*>.ServiceInfoModal(
  serviceName: String,
  variantName: String,
) {
  div("fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50 hidden") {
    id = "service-info-modal"

    div("relative top-20 mx-auto p-5 border w-11/12 md:w-3/4 lg:w-1/2 shadow-lg rounded-md bg-white") {
      div("flex items-center justify-between pb-3 border-b border-gray-200") {
        span("text-lg font-semibold text-gray-900") {
          +"Service Information"
        }
        button(classes = "text-gray-400 hover:text-gray-600") {
          type = ButtonType.button
          id = "close-modal-btn"
          unsafe {
            +"""<svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>"""
          }
        }
      }

      div("mt-4") {
        div("space-y-4") {
          div {
            span("text-sm font-medium text-gray-700") { +"Service Name:" }
            div("mt-1 text-sm text-gray-900") {
              id = "modal-service-name"
              +serviceName
            }
          }

          div {
            span("text-sm font-medium text-gray-700") { +"Variant:" }
            div("mt-1 text-sm text-gray-900") {
              id = "modal-variant"
              +variantName
            }
          }

          div {
            span("text-sm font-medium text-gray-700") { +"Connector:" }
            div("mt-1 text-sm text-gray-900") {
              id = "modal-connector"
              +"Loading..."
            }
          }

          div {
            span("text-sm font-medium text-gray-700") { +"Connector Extra Data:" }
            div("mt-1 text-sm text-gray-900 font-mono bg-gray-50 p-2 rounded border max-h-32 overflow-y-auto") {
              id = "modal-connector-extra-data"
              +"Loading..."
            }
          }

          div {
            span("text-sm font-medium text-gray-700") { +"Slack Channel:" }
            div("mt-1 text-sm text-gray-900") {
              id = "modal-slack-channel"
              +"Loading..."
            }
          }

          div {
            span("text-sm font-medium text-gray-700") { +"Created At:" }
            div("mt-1 text-sm text-gray-900") {
              id = "modal-created-at"
              +"Loading..."
            }
          }

          div {
            span("text-sm font-medium text-gray-700") { +"Updated At:" }
            div("mt-1 text-sm text-gray-900") {
              id = "modal-updated-at"
              +"Loading..."
            }
          }

          div {
            span("text-sm font-medium text-gray-700") { +"Last Registered At:" }
            div("mt-1 text-sm text-gray-900") {
              id = "modal-last-registered-at"
              +"Loading..."
            }
          }
        }
      }

      div("flex justify-end pt-4 border-t border-gray-200 mt-6") {
        button(classes = "px-4 py-2 bg-gray-300 text-gray-700 rounded hover:bg-gray-400") {
          type = ButtonType.button
          id = "close-modal-footer-btn"
          +"Close"
        }
      }
    }
  }

  script {
    unsafe {
      +"""
      function initServiceInfoModal() {
        const modal = document.getElementById('service-info-modal');
        const closeBtn = document.getElementById('close-modal-btn');
        const closeFooterBtn = document.getElementById('close-modal-footer-btn');
        
        function closeModal() {
          modal.classList.add('hidden');
        }
        
        function openModal() {
          modal.classList.remove('hidden');
          loadServiceDetails();
        }
        
        function loadServiceDetails() {
          const url = '/services/$serviceName/variants/$variantName/details';
          fetch(url)
            .then(response => response.json())
            .then(data => {
              document.getElementById('modal-connector').textContent = data.connector || 'N/A';
              document.getElementById('modal-connector-extra-data').textContent = data.connector_extra_data || 'None';
              document.getElementById('modal-slack-channel').textContent = data.slack_channel || 'None';
              document.getElementById('modal-created-at').textContent = new Date(data.created_at).toLocaleString();
              document.getElementById('modal-updated-at').textContent = new Date(data.updated_at).toLocaleString();
              document.getElementById('modal-last-registered-at').textContent = 
                data.last_registered_at ? new Date(data.last_registered_at).toLocaleString() : 'Never';
            })
            .catch(err => {
              console.error('Error loading service details:', err);
              document.getElementById('modal-connector').textContent = 'Error loading data';
              document.getElementById('modal-connector-extra-data').textContent = 'Error loading data';
              document.getElementById('modal-slack-channel').textContent = 'Error loading data';
              document.getElementById('modal-created-at').textContent = 'Error loading data';
              document.getElementById('modal-updated-at').textContent = 'Error loading data';
              document.getElementById('modal-last-registered-at').textContent = 'Error loading data';
            });
        }
        
        // Event listeners
        if (closeBtn) closeBtn.addEventListener('click', closeModal);
        if (closeFooterBtn) closeFooterBtn.addEventListener('click', closeModal);
        
        // Close modal when clicking outside
        modal.addEventListener('click', function(e) {
          if (e.target === modal) {
            closeModal();
          }
        });
        
        // Expose function globally so the info button can call it
        window.openServiceInfoModal = openModal;
      }
      
      // Initialize when DOM is ready
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initServiceInfoModal);
      } else {
        initServiceInfoModal();
      }
      """.trimIndent()
    }
  }
}
