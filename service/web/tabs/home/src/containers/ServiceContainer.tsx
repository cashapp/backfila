import * as React from "react"
import {connect} from "react-redux"
import {IDispatchProps, IState, mapDispatchToProps, mapStateToProps} from "../ducks"
import {H1} from "@blueprintjs/core"
import {BackfillRunsTable} from "../components"
import {simpleSelect} from "@misk/simpleredux"

class ServiceContainer extends React.Component<
  IState & IDispatchProps,
  IState
> {
  private service: string = (this.props as any).match.params.service
  private backfillRunsTag: string = `${this.service}::BackfillRuns`

  componentDidMount() {
    this.props.simpleNetworkGet(
      this.backfillRunsTag,
      `/services/${this.service}/backfill-runs`
    )
  }

  render() {
    let result = simpleSelect(
      this.props.simpleNetwork,
      this.backfillRunsTag,
      "data"
    )
    if (!this.service || !result) {
      return (
        <div>
          <H1>Service: {this.service}</H1>
          loading
        </div>
      )
    }
    return (
      <div>
        <H1>Service: {this.service}</H1>
        <h4>Running Backfills</h4>
        <BackfillRunsTable backfillRuns={result.running_backfills} />
        <h4>Paused Backfills</h4>
        <BackfillRunsTable backfillRuns={result.paused_backfills} />
      </div>
    )
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(ServiceContainer)
