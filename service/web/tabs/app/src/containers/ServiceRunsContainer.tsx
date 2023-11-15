import * as React from "react"
import { connect } from "react-redux"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"
import { H3, Spinner } from "@blueprintjs/core"
import { BackfillRunsTable, ServiceHeader } from "../components"
import { simpleSelectorGet } from "@misk/simpleredux"
import { Link } from "react-router-dom"
import { LayoutContainer } from "."
import { RESERVED_VARIANT } from "../utilities"

class ServiceRunsContainer extends React.Component<
  IState & IDispatchProps,
  IState
> {
  private service: string = (this.props as any).match.params.service
  private variant: string =
    (this.props as any).match.params.variant ?? RESERVED_VARIANT
  private backfillRunsTag: string = `${this.service}::${this.variant}::BackfillRuns`

  componentDidUpdate(prevProps: any) {
    if (
      (this.props as any).match.params.offset !==
      (prevProps as any).match.params.offset
    ) {
      this.sendRequest()
    }
  }

  componentDidMount() {
    this.sendRequest()
  }

  sendRequest() {
    const offset = (this.props as any).match.params.offset
    this.props.simpleNetworkGet(
      this.backfillRunsTag,
      `/services/${this.service}/variants/${this.variant}/backfill-runs?pagination_token=${offset}`
    )
  }

  render() {
    let result = simpleSelectorGet(this.props.simpleNetwork, [
      this.backfillRunsTag,
      "data"
    ])
    if (!this.service || !result) {
      return (
        <LayoutContainer>
          <ServiceHeader serviceName={this.service} variant={this.variant} />
          <Spinner />
        </LayoutContainer>
      )
    }
    if (this.variant != RESERVED_VARIANT) {
      return (
          <LayoutContainer>
            <ServiceHeader serviceName={this.service} variant={this.variant}/>
            <H3>Paused Backfills</H3>
            <BackfillRunsTable backfillRuns={result.paused_backfills}/>
            {result.next_pagination_token && (
                <div style={{paddingBottom: "100px"}}>
                  <Link
                      to={`/app/services/${this.service}/variants/${this.variant}/runs/${result.next_pagination_token}`}
                  >
                    more
                  </Link>
                </div>
            )}
          </LayoutContainer>
      )
    }
    return (
        <LayoutContainer>
          <ServiceHeader serviceName={this.service} variant={this.variant}/>
          <H3>Paused Backfills</H3>
          <BackfillRunsTable backfillRuns={result.paused_backfills}/>
          {result.next_pagination_token && (
              <div style={{paddingBottom: "100px"}}>
                <Link
                    to={`/app/services/${this.service}/runs/${result.next_pagination_token}`}
                >
                  more
                </Link>
              </div>
          )}
        </LayoutContainer>
    )
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(ServiceRunsContainer)
