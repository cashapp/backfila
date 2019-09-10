import * as React from "react"
import { connect } from "react-redux"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"
import { Classes, H2, H3, HTMLTable, Tooltip, Spinner } from "@blueprintjs/core"
import { simpleSelectorGet } from "@misk/simpleredux"
import { Link } from "react-router-dom"
import {
  BackfillProgressBar,
  StartStopButton,
  EditableField
} from "../components"
import { NavbarContainer } from "../containers"

import TimeAgo from "react-timeago"

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

function prettyEta(durationSeconds: number) {
  let result = ""
  let temp = durationSeconds
  const years = Math.floor(temp / 31536000)
  if (years) {
    result += years + "y"
  }
  const days = Math.floor((temp %= 31536000) / 86400)
  if (days) {
    result += days + "d"
  }
  const hours = Math.floor((temp %= 86400) / 3600)
  if (hours) {
    result += hours + "h"
  }
  const minutes = Math.floor((temp %= 3600) / 60)
  if (minutes) {
    result += minutes + "m"
  }
  const seconds = Math.floor(temp % 60)
  if (seconds) {
    result += seconds + "s"
  }
  return result
}

interface BackfillStatusState {
  editing: any
}

class BackfillStatusContainer extends React.Component<
  IState & IDispatchProps,
  IState & BackfillStatusState
> {
  private id: string = (this.props as any).match.params.id
  private backfillStatusTag: string = `${this.id}::BackfillRuns`
  private status: any
  private interval: any

  componentDidMount() {
    this.requestStatus()
    this.interval = setInterval(() => {
      this.requestStatus()
    }, 5000)
    this.setState({ editing: {} })
  }

  requestStatus() {
    this.props.simpleNetworkGet(
      this.backfillStatusTag,
      `/backfills/${this.id}/status`
    )
  }

  render() {
    let result = simpleSelectorGet(this.props.simpleNetwork, [
      this.backfillStatusTag,
      "data"
    ])
    if (result) {
      this.status = result
      if (result.state == "COMPLETE") {
        clearInterval(this.interval)
      }
    }
    if (!this.status) {
      return (
        <NavbarContainer>
          <Spinner />
        </NavbarContainer>
      )
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
        <NavbarContainer>
          <H2>
            Backfill #{this.id}: {status.name} in{" "}
            <Link to={`/app/services/${status.service_name}`}>
              {status.service_name}
            </Link>
          </H2>
          <div>
            <HTMLTable>
              <thead></thead>
              <tbody>
                <tr>
                  <td>State</td>
                  <td>
                    {status.state}{" "}
                    <div style={{ float: "right" }}>
                      <StartStopButton
                        id={this.id}
                        state={status.state}
                        onUpdate={() => this.requestStatus()}
                      />
                    </div>
                  </td>
                </tr>
                <tr>
                  <td>Dry run</td>
                  <td>{status.dry_run ? "dry run" : "wet run"}</td>
                </tr>
                <tr>
                  <td>Threads per instance</td>
                  <td>
                    <EditableField
                      id={this.id}
                      numeric={true}
                      fieldName={"num_threads"}
                      value={status.num_threads}
                      minValue={1}
                      onUpdate={() => this.requestStatus()}
                    />
                  </td>
                </tr>
                <tr>
                  <td>
                    <Tooltip
                      className={Classes.TOOLTIP_INDICATOR}
                      content="How many records to scan when computing batches."
                    >
                      Scan Size
                    </Tooltip>
                  </td>
                  <td>
                    <EditableField
                      id={this.id}
                      numeric={true}
                      fieldName={"scan_size"}
                      value={status.scan_size}
                      minValue={1}
                      onUpdate={() => this.requestStatus()}
                    />
                  </td>
                </tr>
                <tr>
                  <td>
                    <Tooltip
                      className={Classes.TOOLTIP_INDICATOR}
                      content="How many *matching* records to send per call to RunBatch."
                    >
                      Batch Size
                    </Tooltip>
                  </td>
                  <td>
                    <EditableField
                      id={this.id}
                      numeric={true}
                      fieldName={"batch_size"}
                      value={status.batch_size}
                      minValue={1}
                      onUpdate={() => this.requestStatus()}
                    />
                  </td>
                </tr>
                <tr>
                  <td>Sleep between batches (ms)</td>
                  <td>
                    <EditableField
                      id={this.id}
                      numeric={true}
                      fieldName={"extra_sleep_ms"}
                      value={status.extra_sleep_ms}
                      minValue={0}
                      onUpdate={() => this.requestStatus()}
                    />
                  </td>
                </tr>
                <tr>
                  <td>
                    <Tooltip
                      className={Classes.TOOLTIP_INDICATOR}
                      content="Comma separated list of milliseconds to backoff on subsequent failures"
                    >
                      Backoff Schedule
                    </Tooltip>
                  </td>
                  <td>
                    <EditableField
                      id={this.id}
                      numeric={false}
                      placeholder={"5000,15000,30000"}
                      fieldName={"backoff_schedule"}
                      value={status.backoff_schedule}
                      onUpdate={() => this.requestStatus()}
                    />
                  </td>
                </tr>
                <tr>
                  <td>Created</td>
                  <td>
                    <TimeAgo date={status.created_at} /> by{" "}
                    {status.created_by_user}
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
                  <td>
                    {instance.matching_records_per_minute && (
                      <span>{instance.matching_records_per_minute} #/m</span>
                    )}
                  </td>
                  <td>
                    {instance.precomputing_done &&
                      instance.computed_matching_record_count > 0 &&
                      instance.state != "COMPLETE" &&
                      instance.matching_records_per_minute &&
                      instance.matching_records_per_minute > 0 && (
                        <span>
                          {prettyEta(
                            (instance.computed_matching_record_count -
                              instance.backfilled_matching_record_count) /
                              (instance.matching_records_per_minute / 60)
                          )}{" "}
                        </span>
                      )}
                  </td>
                </tr>
              ))}
            </tbody>
          </HTMLTable>
        </NavbarContainer>
      )
    }
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(BackfillStatusContainer)
