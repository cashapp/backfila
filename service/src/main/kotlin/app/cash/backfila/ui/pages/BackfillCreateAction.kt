package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.GetRegisteredBackfillsAction
import app.cash.backfila.ui.actions.ServiceAutocompleteAction
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea
import kotlinx.html.ul
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
class BackfillCreateAction @Inject constructor(
  private val serviceAutocompleteAction: ServiceAutocompleteAction,
  private val getBackfillStatusAction: GetBackfillStatusAction,
  private val getRegisteredBackfillsAction: GetRegisteredBackfillsAction,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam service: String,
    @PathParam variantOrBlank: String? = "",
    @QueryParam backfill: String? = "",
    @QueryParam backfillIdToClone: Long? = null,
  ): Response<ResponseBody> {
    val htmlResponseBody = dashboardPageLayout.newBuilder()
      .title("Create Backfill | Backfila")
      .buildHtmlResponseBody {
        PageTitle("Create Backfill")
        val variant = variantOrBlank.orEmpty().ifBlank { "default" }
        if (service.isNotBlank() && backfill.isNullOrBlank() && backfillIdToClone == null) {
          // If service + variant is set and valid, show registered backfills drop down

          val registeredBackfills = getRegisteredBackfillsAction.backfills(service, variant)

          p {
            +"Service: $service"
          }
          p {
            +"Variant: $variant"
          }
          div("py-10") {
            ul("grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3") {
              role = "list"

              registeredBackfills.backfills.map {
                a {
                  // TODO redirect to same page but with backfill filled in
                  href = PATH.replace("{service}", service).replace("{variantOrBlank}", variantOrBlank ?: "") + "?backfill=${it.name}"

                  // TODO make full width
                  this@ul.li("registration col-span-1 divide-y divide-gray-200 rounded-lg bg-white shadow") {
                    div("flex w-full items-center justify-between space-x-6 p-6") {
                      div("flex-1 truncate") {
                        div("flex items-center space-x-3") {
                          // Don't include default variant in label, only for unique variants
//                          val label = if (variant == "default") service else "$service/$variant"
                          h3("truncate text-sm font-medium text-gray-900") {
                            +it.name
                          }
//                          variant?.let { span("inline-flex shrink-0 items-center rounded-full bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20") { +it } }
                        }
                        //                    p("mt-1 truncate text-sm text-gray-500") { +"""Regional Paradigm Technician""" }
                      }
                    }
                  }
                  // Buttons
//                div {
//                  div("-mt-px flex divide-x divide-gray-200") {
//                    div("flex w-0 flex-1") {
//                      a(classes = "relative -mr-px inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-bl-lg border border-transparent py-4 text-sm font-semibold text-gray-900") {
//                        href = "mailto:janecooper@example.com"
// //                        svg("size-5 text-gray-400") {
// //                          viewbox = "0 0 20 20"
// //                          fill = "currentColor"
// //                          attributes["aria-hidden"] = "true"
// //                          attributes["data-slot"] = "icon"
// //                          path {
// //                            d =
// //                              "M3 4a2 2 0 0 0-2 2v1.161l8.441 4.221a1.25 1.25 0 0 0 1.118 0L19 7.162V6a2 2 0 0 0-2-2H3Z"
// //                          }
// //                          path {
// //                            d =
// //                              "m19 8.839-7.77 3.885a2.75 2.75 0 0 1-2.46 0L1 8.839V14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8.839Z"
// //                          }
// //                        }
//                        +"""Email"""
//                      }
//                    }
//                    div("-ml-px flex w-0 flex-1") {
//                      a(classes = "relative inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-br-lg border border-transparent py-4 text-sm font-semibold text-gray-900") {
//                        href = "tel:+1-202-555-0170"
// //                        svg("size-5 text-gray-400") {
// //                          viewbox = "0 0 20 20"
// //                          fill = "currentColor"
// //                          attributes["aria-hidden"] = "true"
// //                          attributes["data-slot"] = "icon"
// //                          path {
// //                            attributes["fill-rule"] = "evenodd"
// //                            d =
// //                              "M2 3.5A1.5 1.5 0 0 1 3.5 2h1.148a1.5 1.5 0 0 1 1.465 1.175l.716 3.223a1.5 1.5 0 0 1-1.052 1.767l-.933.267c-.41.117-.643.555-.48.95a11.542 11.542 0 0 0 6.254 6.254c.395.163.833-.07.95-.48l.267-.933a1.5 1.5 0 0 1 1.767-1.052l3.223.716A1.5 1.5 0 0 1 18 15.352V16.5a1.5 1.5 0 0 1-1.5 1.5H15c-1.149 0-2.263-.15-3.326-.43A13.022 13.022 0 0 1 2.43 8.326 13.019 13.019 0 0 1 2 5V3.5Z"
// //                            attributes["clip-rule"] = "evenodd"
// //                          }
// //                        }
//                        +"""Call"""
//                      }
//                    }
//                  }
//                }
                }
              }
            }
          }
        } else if (backfill.orEmpty().isNotBlank() || backfillIdToClone != null) {
          // If service + variant + backfill id to clone are valid, pre-fill form with backfill details

          p {
            +"Service: $service"
          }
          p {
            +"Variant: $variant"
          }
          p {
            +"Backfill: $backfill"
          }
          p {
            +"Backfill ID to clone: $backfillIdToClone"
          }

          // TODO add Header buttons / metrics

          div("mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8") {
            form {
              div("space-y-12") {
                div("border-b border-gray-900/10 pb-12") {
                  h2("text-base/7 font-semibold text-gray-900") { +"""Profile""" }
                  p("mt-1 text-sm/6 text-gray-600") { +"""This information will be displayed publicly so be careful what you share.""" }
                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-4") {
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = "username"
                        +"""Username"""
                      }
                      div("mt-2") {
                        div("flex items-center rounded-md bg-white pl-3 outline outline-1 -outline-offset-1 outline-gray-300 focus-within:outline focus-within:outline-2 focus-within:-outline-offset-2 focus-within:outline-indigo-600") {
                          div("shrink-0 select-none text-base text-gray-500 sm:text-sm/6") { +"""workcation.com/""" }
                          input(classes = "block min-w-0 grow py-1.5 pl-1 pr-3 text-base text-gray-900 placeholder:text-gray-400 focus:outline focus:outline-0 sm:text-sm/6") {
                            type = InputType.text
                            name = "username"
                            this@input.id = "username"
                            placeholder = "janesmith"
                          }
                        }
                      }
                    }
                    div("col-span-full") {
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = "about"
                        +"""About"""
                      }
                      div("mt-2") {
                        textArea(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                          name = "about"
                          this@textArea.id = "about"
                          rows = "3"
                        }
                      }
                      p("mt-3 text-sm/6 text-gray-600") { +"""Write a few sentences about yourself.""" }
                    }
                    div("col-span-full") {
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = "photo"
                        +"""Photo"""
                      }
                      div("mt-2 flex items-center gap-x-3") {
//                      svg("size-12 text-gray-300") {
//                        viewbox = "0 0 24 24"
//                        fill = "currentColor"
//                        attributes["aria-hidden"] = "true"
//                        attributes["data-slot"] = "icon"
//                        path {
//                          attributes["fill-rule"] = "evenodd"
//                          d =
//                            "M18.685 19.097A9.723 9.723 0 0 0 21.75 12c0-5.385-4.365-9.75-9.75-9.75S2.25 6.615 2.25 12a9.723 9.723 0 0 0 3.065 7.097A9.716 9.716 0 0 0 12 21.75a9.716 9.716 0 0 0 6.685-2.653Zm-12.54-1.285A7.486 7.486 0 0 1 12 15a7.486 7.486 0 0 1 5.855 2.812A8.224 8.224 0 0 1 12 20.25a8.224 8.224 0 0 1-5.855-2.438ZM15.75 9a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0Z"
//                          attributes["clip-rule"] = "evenodd"
//                        }
                      }
                      button(classes = "rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50") {
                        type = ButtonType.button
                        +"""Change"""
                      }
                    }
                  }
                  div("col-span-full") {
                    label("block text-sm/6 font-medium text-gray-900") {
                      htmlFor = "cover-photo"
                      +"""Cover photo"""
                    }
                    div("mt-2 flex justify-center rounded-lg border border-dashed border-gray-900/25 px-6 py-10") {
                      div("text-center") {
//                        svg("mx-auto size-12 text-gray-300") {
//                          viewbox = "0 0 24 24"
//                          fill = "currentColor"
//                          attributes["aria-hidden"] = "true"
//                          attributes["data-slot"] = "icon"
//                          path {
//                            attributes["fill-rule"] = "evenodd"
//                            d =
//                              "M1.5 6a2.25 2.25 0 0 1 2.25-2.25h16.5A2.25 2.25 0 0 1 22.5 6v12a2.25 2.25 0 0 1-2.25 2.25H3.75A2.25 2.25 0 0 1 1.5 18V6ZM3 16.06V18c0 .414.336.75.75.75h16.5A.75.75 0 0 0 21 18v-1.94l-2.69-2.689a1.5 1.5 0 0 0-2.12 0l-.88.879.97.97a.75.75 0 1 1-1.06 1.06l-5.16-5.159a1.5 1.5 0 0 0-2.12 0L3 16.061Zm10.125-7.81a1.125 1.125 0 1 1 2.25 0 1.125 1.125 0 0 1-2.25 0Z"
//                            attributes["clip-rule"] = "evenodd"
//                          }
                      }
                      div("mt-4 flex text-sm/6 text-gray-600") {
                        label("relative cursor-pointer rounded-md bg-white font-semibold text-indigo-600 focus-within:outline-none focus-within:ring-2 focus-within:ring-indigo-600 focus-within:ring-offset-2 hover:text-indigo-500") {
                          htmlFor = "file-upload"
                          span { +"""Upload a file""" }
                          input(classes = "sr-only") {
                            id = "file-upload"
                            name = "file-upload"
                            type = InputType.file
                          }
                        }
                        p("pl-1") { +"""or drag and drop""" }
                      }
                      p("text-xs/5 text-gray-600") { +"""PNG, JPG, GIF up to 10MB""" }
                    }
                  }
                }
              }
            }
            div("border-b border-gray-900/10 pb-12") {
              h2("text-base/7 font-semibold text-gray-900") { +"""Personal Information""" }
              p("mt-1 text-sm/6 text-gray-600") { +"""Use a permanent address where you can receive mail.""" }
              div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                div("sm:col-span-3") {
                  label("block text-sm/6 font-medium text-gray-900") {
                    htmlFor = "first-name"
                    +"""First name"""
                  }
                  div("mt-2") {
                    input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                      type = InputType.text
                      name = "first-name"
                      id = "first-name"
                      attributes["autocomplete"] = "given-name"
                    }
                  }
                }
                div("sm:col-span-3") {
                  label("block text-sm/6 font-medium text-gray-900") {
                    htmlFor = "last-name"
                    +"""Last name"""
                  }
                  div("mt-2") {
                    input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                      type = InputType.text
                      name = "last-name"
                      id = "last-name"
                      attributes["autocomplete"] = "family-name"
                    }
                  }
                }
                div("sm:col-span-4") {
                  label("block text-sm/6 font-medium text-gray-900") {
                    htmlFor = "email"
                    +"""Email address"""
                  }
                  div("mt-2") {
                    input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                      id = "email"
                      name = "email"
                      type = InputType.email
                      attributes["autocomplete"] = "email"
                    }
                  }
                }
                div("sm:col-span-3") {
                  label("block text-sm/6 font-medium text-gray-900") {
                    htmlFor = "country"
                    +"""Country"""
                  }
                  div("mt-2 grid grid-cols-1") {
                    select("col-start-1 row-start-1 w-full appearance-none rounded-md bg-white py-1.5 pl-3 pr-8 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                      id = "country"
                      name = "country"
                      attributes["autocomplete"] = "country-name"
                      option { +"""United States""" }
                      option { +"""Canada""" }
                      option { +"""Mexico""" }
                    }
//                      svg("pointer-events-none col-start-1 row-start-1 mr-2 size-5 self-center justify-self-end text-gray-500 sm:size-4") {
//                        viewbox = "0 0 16 16"
//                        fill = "currentColor"
//                        attributes["aria-hidden"] = "true"
//                        attributes["data-slot"] = "icon"
//                        path {
//                          attributes["fill-rule"] = "evenodd"
//                          d =
//                            "M4.22 6.22a.75.75 0 0 1 1.06 0L8 8.94l2.72-2.72a.75.75 0 1 1 1.06 1.06l-3.25 3.25a.75.75 0 0 1-1.06 0L4.22 7.28a.75.75 0 0 1 0-1.06Z"
//                          attributes["clip-rule"] = "evenodd"
//                        }
//                      }
                  }
                }
                div("col-span-full") {
                  label("block text-sm/6 font-medium text-gray-900") {
                    htmlFor = "street-address"
                    +"""Street address"""
                  }
                  div("mt-2") {
                    input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                      type = InputType.text
                      name = "street-address"
                      id = "street-address"
                      attributes["autocomplete"] = "street-address"
                    }
                  }
                }
                div("sm:col-span-2 sm:col-start-1") {
                  label("block text-sm/6 font-medium text-gray-900") {
                    htmlFor = "city"
                    +"""City"""
                  }
                  div("mt-2") {
                    input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                      type = InputType.text
                      name = "city"
                      id = "city"
                      attributes["autocomplete"] = "address-level2"
                    }
                  }
                }
                div("sm:col-span-2") {
                  label("block text-sm/6 font-medium text-gray-900") {
                    htmlFor = "region"
                    +"""State / Province"""
                  }
                  div("mt-2") {
                    input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                      type = InputType.text
                      name = "region"
                      id = "region"
                      attributes["autocomplete"] = "address-level1"
                    }
                  }
                }
                div("sm:col-span-2") {
                  label("block text-sm/6 font-medium text-gray-900") {
                    htmlFor = "postal-code"
                    +"""ZIP / Postal code"""
                  }
                  div("mt-2") {
                    input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                      type = InputType.text
                      name = "postal-code"
                      id = "postal-code"
                      attributes["autocomplete"] = "postal-code"
                    }
                  }
                }
              }
            }
            div("border-b border-gray-900/10 pb-12") {
              h2("text-base/7 font-semibold text-gray-900") { +"""Notifications""" }
              p("mt-1 text-sm/6 text-gray-600") { +"""We'll always let you know about important changes, but you pick what else you want to hear about.""" }
              div("mt-10 space-y-10") {
                div {
                  legend("text-sm/6 font-semibold text-gray-900") { +"""By email""" }
                  div("mt-6 space-y-6") {
                    div("flex gap-3") {
                      div("flex h-6 shrink-0 items-center") {
                        div("group grid size-4 grid-cols-1") {
                          input(classes = "col-start-1 row-start-1 appearance-none rounded border border-gray-300 bg-white checked:border-indigo-600 checked:bg-indigo-600 indeterminate:border-indigo-600 indeterminate:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:checked:bg-gray-100 forced-colors:appearance-auto") {
                            id = "comments"
                            attributes["aria-describedby"] = "comments-description"
                            name = "comments"
                            type = InputType.checkBox
                            checked = true
                          }
//                            svg("pointer-events-none col-start-1 row-start-1 size-3.5 self-center justify-self-center stroke-white group-has-[:disabled]:stroke-gray-950/25") {
//                              viewbox = "0 0 14 14"
//                              fill = "none"
//                              path(classes = "opacity-0 group-has-[:checked]:opacity-100") {
//                                d = "M3 8L6 11L11 3.5"
//                                attributes["stroke-width"] = "2"
//                                attributes["stroke-linecap"] = "round"
//                                attributes["stroke-linejoin"] = "round"
//                              }
//                              path(classes = "opacity-0 group-has-[:indeterminate]:opacity-100") {
//                                d = "M3 7H11"
//                                attributes["stroke-width"] = "2"
//                                attributes["stroke-linecap"] = "round"
//                                attributes["stroke-linejoin"] = "round"
//                              }
//                            }
                        }
                      }
                      div("text-sm/6") {
                        label("font-medium text-gray-900") {
                          htmlFor = "comments"
                          +"""Comments"""
                        }
                        p("text-gray-500") {
                          id = "comments-description"
                          +"""Get notified when someones posts a comment on a posting."""
                        }
                      }
                    }
                    div("flex gap-3") {
                      div("flex h-6 shrink-0 items-center") {
                        div("group grid size-4 grid-cols-1") {
                          input(classes = "col-start-1 row-start-1 appearance-none rounded border border-gray-300 bg-white checked:border-indigo-600 checked:bg-indigo-600 indeterminate:border-indigo-600 indeterminate:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:checked:bg-gray-100 forced-colors:appearance-auto") {
                            id = "candidates"
                            attributes["aria-describedby"] = "candidates-description"
                            name = "candidates"
                            type = InputType.checkBox
                          }
//                            svg("pointer-events-none col-start-1 row-start-1 size-3.5 self-center justify-self-center stroke-white group-has-[:disabled]:stroke-gray-950/25") {
//                              viewbox = "0 0 14 14"
//                              fill = "none"
//                              path(classes = "opacity-0 group-has-[:checked]:opacity-100") {
//                                d = "M3 8L6 11L11 3.5"
//                                attributes["stroke-width"] = "2"
//                                attributes["stroke-linecap"] = "round"
//                                attributes["stroke-linejoin"] = "round"
//                              }
//                              path(classes = "opacity-0 group-has-[:indeterminate]:opacity-100") {
//                                d = "M3 7H11"
//                                attributes["stroke-width"] = "2"
//                                attributes["stroke-linecap"] = "round"
//                                attributes["stroke-linejoin"] = "round"
//                              }
//                            }
                        }
                      }
                      div("text-sm/6") {
                        label("font-medium text-gray-900") {
                          htmlFor = "candidates"
                          +"""Candidates"""
                        }
                        p("text-gray-500") {
                          id = "candidates-description"
                          +"""Get notified when a candidate applies for a job."""
                        }
                      }
                    }
                    div("flex gap-3") {
                      div("flex h-6 shrink-0 items-center") {
                        div("group grid size-4 grid-cols-1") {
                          input(classes = "col-start-1 row-start-1 appearance-none rounded border border-gray-300 bg-white checked:border-indigo-600 checked:bg-indigo-600 indeterminate:border-indigo-600 indeterminate:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:checked:bg-gray-100 forced-colors:appearance-auto") {
                            id = "offers"
                            attributes["aria-describedby"] = "offers-description"
                            name = "offers"
                            type = InputType.checkBox
                          }
//                            svg("pointer-events-none col-start-1 row-start-1 size-3.5 self-center justify-self-center stroke-white group-has-[:disabled]:stroke-gray-950/25") {
//                              viewbox = "0 0 14 14"
//                              fill = "none"
//                              path(classes = "opacity-0 group-has-[:checked]:opacity-100") {
//                                d = "M3 8L6 11L11 3.5"
//                                attributes["stroke-width"] = "2"
//                                attributes["stroke-linecap"] = "round"
//                                attributes["stroke-linejoin"] = "round"
//                              }
//                              path(classes = "opacity-0 group-has-[:indeterminate]:opacity-100") {
//                                d = "M3 7H11"
//                                attributes["stroke-width"] = "2"
//                                attributes["stroke-linecap"] = "round"
//                                attributes["stroke-linejoin"] = "round"
//                              }
//                            }
                        }
                      }
                      div("text-sm/6") {
                        label("font-medium text-gray-900") {
                          htmlFor = "offers"
                          +"""Offers"""
                        }
                        p("text-gray-500") {
                          id = "offers-description"
                          +"""Get notified when a candidate accepts or rejects an offer."""
                        }
                      }
                    }
                  }
                }
                div {
                  legend("text-sm/6 font-semibold text-gray-900") { +"""Push notifications""" }
                  p("mt-1 text-sm/6 text-gray-600") { +"""These are delivered via SMS to your mobile phone.""" }
                  div("mt-6 space-y-6") {
                    div("flex items-center gap-x-3") {
                      input(classes = "relative size-4 appearance-none rounded-full border border-gray-300 bg-white before:absolute before:inset-1 before:rounded-full before:bg-white checked:border-indigo-600 checked:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:before:bg-gray-400 forced-colors:appearance-auto forced-colors:before:hidden [&:not(:checked)]:before:hidden") {
                        id = "push-everything"
                        name = "push-notifications"
                        type = InputType.radio
                        checked = true
                      }
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = "push-everything"
                        +"""Everything"""
                      }
                    }
                    div("flex items-center gap-x-3") {
                      input(classes = "relative size-4 appearance-none rounded-full border border-gray-300 bg-white before:absolute before:inset-1 before:rounded-full before:bg-white checked:border-indigo-600 checked:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:before:bg-gray-400 forced-colors:appearance-auto forced-colors:before:hidden [&:not(:checked)]:before:hidden") {
                        id = "push-email"
                        name = "push-notifications"
                        type = InputType.radio
                      }
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = "push-email"
                        +"""Same as email"""
                      }
                    }
                    div("flex items-center gap-x-3") {
                      input(classes = "relative size-4 appearance-none rounded-full border border-gray-300 bg-white before:absolute before:inset-1 before:rounded-full before:bg-white checked:border-indigo-600 checked:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:before:bg-gray-400 forced-colors:appearance-auto forced-colors:before:hidden [&:not(:checked)]:before:hidden") {
                        id = "push-nothing"
                        name = "push-notifications"
                        type = InputType.radio
                      }
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = "push-nothing"
                        +"""No push notifications"""
                      }
                    }
                  }
                }
              }
            }
          }
          div("mt-6 flex items-center justify-end gap-x-6") {
            button(classes = "text-sm/6 font-semibold text-gray-900") {
              type = ButtonType.button
              +"""Cancel"""
            }
            button(classes = "rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600") {
              type = ButtonType.submit
              +"""Save"""
            }
          }
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/backfills/create/{service}/{variantOrBlank}"
  }
}
