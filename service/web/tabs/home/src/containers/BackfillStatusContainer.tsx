import * as React from "react"
import {connect} from "react-redux"
import {IDispatchProps, IState, mapDispatchToProps, mapStateToProps} from "../ducks"
import {Button, Classes, H2, H3, HTMLTable, Tooltip} from "@blueprintjs/core"
import {simpleSelect} from "@misk/simpleredux"
import {Link} from "react-router-dom"
import {BackfillProgressBar} from "../components"

// import TimeAgo from "react-timeago"

function StartStopButton(props: any) {
  // props.simpleNetworkPost
  const state = props.state
  if (state == "RUNNING") {
    return <Button>stop</Button>
  } else if (state == "PAUSED") {
    return <Button>start</Button>
  }
  return <Button disabled={true}>start</Button>
}

export interface IComputedCountProps {
  precomputing_done: boolean
  computed_matching_record_count: number
}

function ComputedCount(props: IComputedCountProps) {
  if (!props.precomputing_done) {
    return (
      <Tooltip
        className={Classes.TOOLTIP_INDICATOR}
        content="Total record count is still being computed"
      >
        <span>at&nbsp;least&nbsp;{props.computed_matching_record_count}</span>
      </Tooltip>
    )
  }
  return <span>{props.computed_matching_record_count}</span>
}

class BackfillStatusContainer extends React.Component<
  IState & IDispatchProps,
  IState
> {
  private id: string = (this.props as any).match.params.id
  private backfillStatusTag: string = `${this.id}::BackfillRuns`
  private status: any
  private interval: any

  componentDidMount() {
    this.props.simpleNetworkGet(
      this.backfillStatusTag,
      `/backfills/${this.id}/status`
    )
    this.interval = setInterval(() => {
      this.props.simpleNetworkGet(
        this.backfillStatusTag,
        `/backfills/${this.id}/status`
      )
    }, 5000)
  }

  render() {
    let result = simpleSelect(
      this.props.simpleNetwork,
      this.backfillStatusTag,
      "data"
    )
    if (result) {
      this.status = result
      if (result.state == "COMPLETE") {
        clearInterval(this.interval)
      }
    }
    if (!this.status) {
      return <div>loading</div>
    } else {
      let status = this.status
      let all_precomputing_done = status.instances.every(
        (instance: any) => instance.precomputing_done
      )
      let total_backfilled_matching_record_count = status.instances.reduce(
        (sum: number, instance: any) =>
          sum + instance.backfilled_matching_record_count,
        0
      )
      let total_computed_matching_record_count = status.instances.reduce(
        (sum: number, instance: any) =>
          sum + instance.computed_matching_record_count,
        0
      )
      return (
        <div>
          <H2>
            Backfill #{this.id}: {status.name} in{" "}
            <Link to={`/app/home/services/${status.service_name}`}>
              {status.service_name}
            </Link>
          </H2>
          <div>
            <HTMLTable>
              <thead></thead>
              <tbody>
                <tr>
                  <td>State</td>
                  <td>{status.state}</td>
                </tr>
                <tr>
                  <td>Dry run</td>
                  <td>{status.dry_run ? "dry run" : "wet run"}</td>
                </tr>
                <tr>
                  <td>Threads per instance</td>
                  {/*todo make editable*/}
                  <td>{status.num_threads}</td>
                </tr>
                <tr>
                  <td>Scan size</td>
                  {/*todo make editable*/}
                  <td>{status.scan_size}</td>
                </tr>
                <tr>
                  <td>Batch size</td>
                  {/*todo make editable*/}
                  <td>{status.batch_size}</td>
                </tr>
                <tr>
                  <td>Created</td>
                  <td>
                    {/*<TimeAgo date={status.created_at} />*/}
                    {status.created_at} by {status.created_by_user}
                  </td>
                </tr>
                <tr>
                  <td colSpan={2} style={{ textAlign: "center" }}>
                    Custom parameters
                  </td>
                </tr>
                {Object.entries(status.parameters).map(
                  ([name, value]: [string, string]) => (
                    <tr>
                      <td>{name}</td>
                      <td>
                        <code>{value}</code>
                      </td>
                    </tr>
                  )
                )}
              </tbody>
            </HTMLTable>
          </div>
          <div>
            <StartStopButton state={status.state} />
          </div>
          <div>
            Total backfilled records: {total_backfilled_matching_record_count}
          </div>
          <div>
            Total records to run:{" "}
            <ComputedCount
              precomputing_done={all_precomputing_done}
              computed_matching_record_count={
                total_computed_matching_record_count
              }
            />
          </div>
          <div>Overall Rate</div>
          <div style={{ width: 600 }}>
            <BackfillProgressBar
              precomputing_done={all_precomputing_done}
              backfilled_matching_record_count={
                total_backfilled_matching_record_count
              }
              computed_matching_record_count={
                total_computed_matching_record_count
              }
              state={status.state}
            />
          </div>
          <H3>Instances</H3>
          <HTMLTable bordered={true} striped={true}>
            <thead>
              <tr>
                <th>Name</th>
                <th>State</th>
                <th>Cursor</th>
                <th>Range</th>
                <th>#&nbsp;Completed</th>
                <th>#&nbsp;Total</th>
                <th>%&nbsp;Done</th>
                <th>Progress</th>
                <th>Rate</th>
                <th>ETA</th>
              </tr>
            </thead>
            <tbody>
              {status.instances.map((instance: any) => (
                <tr>
                  <td>{instance.name}</td>
                  <td>{instance.state}</td>
                  <td>{instance.pkey_cursor}</td>
                  <td>
                    {instance.pkey_start}&nbsp;to&nbsp;{instance.pkey_end}
                  </td>
                  <td style={{ textAlign: "right" }}>
                    {instance.backfilled_matching_record_count}
                  </td>
                  <td style={{ textAlign: "right" }}>
                    <ComputedCount
                      precomputing_done={instance.precomputing_done}
                      computed_matching_record_count={
                        instance.computed_matching_record_count
                      }
                    />
                  </td>
                  <td style={{ textAlign: "right" }}>
                    {instance.precomputing_done &&
                    instance.computed_matching_record_count > 0
                      ? (
                          (100 * instance.backfilled_matching_record_count) /
                          instance.computed_matching_record_count
                        ).toFixed(2) + " %"
                      : "? %"}
                  </td>
                  <td style={{ verticalAlign: "middle", width: "300px" }}>
                    <BackfillProgressBar
                      precomputing_done={instance.precomputing_done}
                      backfilled_matching_record_count={
                        instance.backfilled_matching_record_count
                      }
                      computed_matching_record_count={
                        instance.computed_matching_record_count
                      }
                      state={instance.state}
                    />
                  </td>
                  <td>TODO</td>
                  <td>TODO</td>
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
