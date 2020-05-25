import { Classes, HTMLTable } from "@blueprintjs/core"
import * as React from "react"
import { Link } from "react-router-dom"
import { BackfillProgressBar } from "../components"
import TimeAgo from "react-timeago"

export interface ITableProps {
  backfillRuns: any
}

export const BackfillRunsTable = (props: ITableProps) => {
  /**
   * Destructure props for easier usage: data instead of props.data
   */
  const { backfillRuns } = props
  /**
   * Have a nice failure mode while your data is loading or doesn't load
   */
  if (!backfillRuns) {
    const FakeCell = <p className={Classes.SKELETON}>lorem ipsum 1234 5678</p>
    return (
      <div>
        <HTMLTable bordered={true} striped={true}>
          <thead>
            <tr>
              <th>{FakeCell}</th>
              <th>{FakeCell}</th>
              <th>{FakeCell}</th>
              <th>{FakeCell}</th>
              <th>{FakeCell}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{FakeCell}</td>
              <td>{FakeCell}</td>
              <td>{FakeCell}</td>
              <td>{FakeCell}</td>
              <td>{FakeCell}</td>
            </tr>
          </tbody>
        </HTMLTable>
      </div>
    )
  } else {
    /**
     * Data is loaded and ready to be rendered
     */
    if (backfillRuns.length == 0) {
      return <div>No backfills.</div>
    }
    return (
      <div>
        <HTMLTable bordered={true} striped={true} style={{ width: "100%" }}>
          <thead>
            <tr>
              <th style={{ width: "10px" }}>ID</th>
              <th>Name</th>
              <th>State</th>
              <th>Dry Run</th>
              <th>Progress</th>
              <th>Created by</th>
              <th>Created at</th>
              <th>Last active at</th>
            </tr>
          </thead>
          <tbody>
            {backfillRuns.map((run: any) => (
              <tr>
                <td>
                  <Link to={`/app/backfills/${run.id}`}>#{run.id}</Link>
                </td>
                <td>
                  <Link to={`/app/backfills/${run.id}`}>{run.name}</Link>
                </td>
                <td>{run.state}</td>
                <td>{run.dry_run ? "Dry run" : "Wet run"}</td>
                <td style={{ verticalAlign: "middle", width: "200px" }}>
                  <BackfillProgressBar
                    precomputing_done={run.precomputing_done}
                    backfilled_matching_record_count={
                      run.backfilled_matching_record_count
                    }
                    computed_matching_record_count={
                      run.computed_matching_record_count
                    }
                    state={run.state}
                  />
                </td>
                <td>{run.created_by_user}</td>
                <td>
                  <TimeAgo date={run.created_at} />
                </td>
                <td>
                  <TimeAgo date={run.last_active_at} />
                </td>
              </tr>
            ))}
          </tbody>
        </HTMLTable>
      </div>
    )
  }
}

export default BackfillRunsTable
