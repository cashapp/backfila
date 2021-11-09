import * as React from "react"
import { createRef } from "react"
import { connect } from "react-redux"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps,
} from "../ducks"
import {
  AnchorButton,
  Intent,
  Classes,
  Dialog,
  H2,
  H3,
  HTMLTable,
  Tooltip,
  Spinner,
} from "@blueprintjs/core"
import { simpleSelectorGet } from "@misk/simpleredux"
import { Link } from "react-router-dom"
import {
  BackfillProgressBar,
  StartStopButton,
  EditableField,
} from "../components"
import { LayoutContainer } from "../containers"

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
        <span>
          at&nbsp;least&nbsp;
          {props.computed_matching_record_count.toLocaleString()}
        </span>
      </Tooltip>
    )
  }
  return <span>{props.computed_matching_record_count.toLocaleString()}</span>
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
  event_data?: string
  event_title?: string
}

class BackfillStatusContainer extends React.Component<
  IState & IDispatchProps,
  IState & BackfillStatusState
> {
  private id: string = (this.props as any).match.params.id
  private backfillStatusTag: string = `${this.id}::BackfillRuns`
  private status: any
  private interval: any
  private dialogTextRef: any = createRef()

  componentDidMount() {
    this.requestStatus()
    this.interval = setInterval(() => {
      this.requestStatus()
    }, 5000)
    this.setState({ editing: {} })
  }

  componentWillUnmount() {
    clearInterval(this.interval)
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
      "data",
    ])
    if (result) {
      this.status = result
      if (result.state == "COMPLETE") {
        clearInterval(this.interval)
      }
    }
    if (!this.status || !this.state) {
      return (
        <LayoutContainer>
          <Spinner />
        </LayoutContainer>
      )
    } else {
      let status = this.status
      let all_precomputing_done = status.partitions.every(
        (partition: any) => partition.precomputing_done
      )
      let total_backfilled_matching_record_count = status.partitions.reduce(
        (sum: number, partition: any) =>
          sum + partition.backfilled_matching_record_count,
        0
      )
      let total_computed_matching_record_count = status.partitions.reduce(
        (sum: number, partition: any) =>
          sum + partition.computed_matching_record_count,
        0
      )
      return (
        <LayoutContainer>
          <H2>
            Backfill #{this.id}: {status.name} in{" "}
            <Link to={`/app/services/${status.service_name}`}>
              {status.service_name}
            </Link>
          </H2>
          <Link to={`/app/backfills/${this.id}/clone`}>
            <AnchorButton text={"Clone"} intent={Intent.PRIMARY} />
          </Link>
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
                  <td>Threads per partition</td>
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
                  <td colSpan={2}>
                    <a href={`/backfills/${this.id}/view-logs`}>View Logs</a>
                  </td>
                </tr>
                {status.parameters.length > 0 && (
                  <tr>
                    <td colSpan={2} style={{ textAlign: "center" }}>
                      Custom parameters
                    </td>
                  </tr>
                )}
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
            Total backfilled records:{" "}
            {total_backfilled_matching_record_count.toLocaleString()}
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
          <H3>Partitions</H3>
          <HTMLTable
            bordered={true}
            striped={true}
            style={{ width: "100%", fontFamily: "monospace" }}
          >
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
              {status.partitions.map((partition: any) => (
                <tr>
                  <td>{partition.name}</td>
                  <td>{partition.state}</td>
                  <td>{partition.pkey_cursor}</td>
                  <td>
                    {partition.pkey_start}&nbsp;to&nbsp;{partition.pkey_end}
                  </td>
                  <td style={{ textAlign: "right" }}>
                    {partition.backfilled_matching_record_count.toLocaleString()}
                  </td>
                  <td style={{ textAlign: "right" }}>
                    <ComputedCount
                      precomputing_done={partition.precomputing_done}
                      computed_matching_record_count={
                        partition.computed_matching_record_count
                      }
                    />
                  </td>
                  <td style={{ textAlign: "right" }}>
                    {partition.precomputing_done &&
                    partition.computed_matching_record_count > 0
                      ? (
                          (100 * partition.backfilled_matching_record_count) /
                          partition.computed_matching_record_count
                        ).toFixed(2) + " %"
                      : "? %"}
                  </td>
                  <td style={{ verticalAlign: "middle", width: "300px" }}>
                    <BackfillProgressBar
                      precomputing_done={partition.precomputing_done}
                      backfilled_matching_record_count={
                        partition.backfilled_matching_record_count
                      }
                      computed_matching_record_count={
                        partition.computed_matching_record_count
                      }
                      state={partition.state}
                    />
                  </td>
                  <td>
                    {partition.matching_records_per_minute && (
                      <span>
                        {partition.matching_records_per_minute.toLocaleString()}{" "}
                        #/m
                      </span>
                    )}
                  </td>
                  <td>
                    {partition.precomputing_done &&
                      partition.computed_matching_record_count > 0 &&
                      partition.state != "COMPLETE" &&
                      partition.matching_records_per_minute &&
                      partition.matching_records_per_minute > 0 && (
                        <span>
                          {prettyEta(
                            (partition.computed_matching_record_count -
                              partition.backfilled_matching_record_count) /
                              (partition.matching_records_per_minute / 60)
                          )}{" "}
                        </span>
                      )}
                  </td>
                </tr>
              ))}
            </tbody>
          </HTMLTable>

          <Dialog
            isOpen={!!this.state.event_data}
            title={this.state.event_title}
            onClose={() => this.setState({ event_data: null })}
            style={{ width: 1000 }}
          >
            <div style={{ width: "100%", height: 600 }}>
              <AnchorButton
                text={"Copy"}
                intent={Intent.PRIMARY}
                onClick={() => {
                  this.dialogTextRef.current.select()
                  document.execCommand("copy")
                }}
              />
              <textarea
                ref={this.dialogTextRef}
                style={{ width: "100%", height: 600 }}
              >
                {this.state.event_data}
              </textarea>
            </div>
          </Dialog>
          <H3>Event log (last 50)</H3>
          <HTMLTable
            bordered={true}
            striped={true}
            style={{ width: "100%", fontFamily: "monospace" }}
          >
            <thead>
              <tr>
                <th>Time</th>
                <th>User</th>
                <th>Partition</th>
                <th>Event</th>
                <th>More data</th>
              </tr>
            </thead>
            <tbody>
              {status.event_logs.map((event_log: any) => (
                <tr>
                  <td
                    style={{
                      paddingTop: 0,
                      paddingBottom: 0,
                      whiteSpace: "nowrap",
                    }}
                  >
                    {new Date(event_log.occurred_at)
                      .toLocaleString("en-CA", {
                        timeZoneName: "short",
                        year: "numeric",
                        month: "numeric",
                        day: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                        second: "2-digit"
                      })
                      .replace(",", "")
                      .replace(".m.", "m")}
                  </td>
                  <td style={{ paddingTop: 0, paddingBottom: 0 }}>
                    {event_log.user}
                  </td>
                  <td style={{ paddingTop: 0, paddingBottom: 0 }}>
                    {event_log.partition_name}
                  </td>
                  <td style={{ paddingTop: 0, paddingBottom: 0 }}>
                    {event_log.message}
                  </td>
                  <td style={{ paddingTop: 0, paddingBottom: 0 }}>
                    {!!event_log.extra_data && (
                      <div>
                        <a
                          onClick={event =>
                            this.setState({
                              event_data: event_log.extra_data,
                              event_title:
                                event_log.occurred_at + " " + event_log.message,
                            })
                          }
                        >
                          show&nbsp;more
                        </a>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </HTMLTable>
        </LayoutContainer>
      )
    }
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(BackfillStatusContainer)
