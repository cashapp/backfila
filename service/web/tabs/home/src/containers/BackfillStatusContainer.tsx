import * as React from "react"
import {connect} from "react-redux"
import {IDispatchProps, IState, mapDispatchToProps, mapStateToProps} from "../ducks"
import {Button, H1, H3, HTMLTable} from "@blueprintjs/core"
import {simpleSelect} from "@misk/simpleredux"
import {Link} from "react-router-dom"

function StartStopButton(props: any) {
  const state = props.state
  if (state == "RUNNING") {
    return <Button>stop</Button>
  } else if (state == "PAUSED") {
    return <Button>start</Button>
  }
  return <Button disabled={true}>start</Button>
}

class BackfillStatusContainer extends React.Component<
  IState & IDispatchProps,
  IState
> {
  private id: string = (this.props as any).match.params.id
  private backfillStatusTag: string = `${this.id}::BackfillRuns`

  componentDidMount() {
    this.props.simpleNetworkGet(
      this.backfillStatusTag,
      `/backfills/${this.id}/status`
    )
  }

  render() {
    let status = simpleSelect(
      this.props.simpleNetwork,
      this.backfillStatusTag,
      "data"
    )
    if (!status) {
      return <div>loading</div>
    } else {
      console.log(status)
      return (
        <div>
          <H1>
            Backfill #{this.id}: {status.name} in{" "}
            <Link to={`/app/home/services/${status.service_name}`}>
              {status.service_name}
            </Link>
          </H1>
          State: {status.state}
          <div>
            <StartStopButton state={status.state} />
          </div>
          <H3>Instances</H3>
          <HTMLTable bordered={true} striped={true}>
            <thead>
              <tr>
                <th>Name</th>
                <th>State</th>
                <th>Progress</th>
              </tr>
            </thead>
            <tbody>
              {status.instances.map((instance: any) => (
                <tr>
                  <td>{instance.name}</td>
                  <td>{instance.state}</td>
                  <td>{instance.pkey_cursor}</td>
                </tr>
              ))}
            </tbody>
          </HTMLTable>
        </div>
      )
    }
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(BackfillStatusContainer)
