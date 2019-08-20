import { Classes, HTMLTable } from "@blueprintjs/core"
import * as React from "react"
import { Link } from "react-router-dom"

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
    return (
      <div>
        <ul>
          {backfillRuns.map((run: any) => (
            <li>
              <Link to={`/app/home/backfills/${run.id}`}>
                #{run.id} {run.name} {run.state}
              </Link>
            </li>
          ))}
        </ul>
      </div>
    )
  }
}

export default BackfillRunsTable
