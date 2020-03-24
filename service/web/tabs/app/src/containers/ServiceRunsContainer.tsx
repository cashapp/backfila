import * as React from "react"
import { connect } from "react-redux"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"
import { H2, H3, Spinner } from "@blueprintjs/core"
import { BackfillRunsTable } from "../components"
import { simpleSelectorGet } from "@misk/simpleredux"
import { Link } from "react-router-dom"
import { LayoutContainer } from "."

class ServiceRunsContainer extends React.Component<
  IState & IDispatchProps,
  IState
> {
  private service: string = (this.props as any).match.params.service
  private backfillRunsTag: string = `${this.service}::BackfillRuns`

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
    let offset = (this.props as any).match.params.offset
    this.props.simpleNetworkGet(
      this.backfillRunsTag,
      `/services/${this.service}/backfill-runs?pagination_token=${offset}`
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
          <H2>{this.service}</H2>
          <Spinner />
        </LayoutContainer>
      )
    }
    return (
      <LayoutContainer>
        <H2>
          <Link to={`/app/services/${this.service}`}>{this.service}</Link>
        </H2>
        <H3>Paused Backfills</H3>
        <BackfillRunsTable backfillRuns={result.paused_backfills} />
        {result.next_pagination_token && (
          <div style={{ paddingBottom: "100px" }}>
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
