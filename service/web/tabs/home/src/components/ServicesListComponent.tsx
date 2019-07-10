import {Classes, H1} from "@blueprintjs/core"
import {ErrorCalloutComponent} from "@misk/core"
import * as React from "react"
import {Link} from "react-router-dom"

export interface ITableProps {
  data: any
  url?: string
  tag?: string
}

export const ServicesListComponent = (props: ITableProps) => {
  /**
   * Destructure props for easier usage: data instead of props.data
   */
  const { data } = props
  /**
   * Have a nice failure mode while your data is loading or doesn't load
   */
  if (!data.services || data.services === null) {
    const FakeLi = <li className={Classes.SKELETON}>lorem ipsum 1234 5678</li>
    return (
      <div>
        <H1>Services</H1>
        <ul>
          {FakeLi}
          {FakeLi}
          {FakeLi}
          {FakeLi}
          {FakeLi}
        </ul>
        <ErrorCalloutComponent error={data.error} />
      </div>
    )
  } else {
    /**
     * Data is loaded and ready to be rendered
     */
    const tableData = data.services
    return (
      <div>
        <H1>Services</H1>
        <ul>
          {tableData.map((service: string) => (
            <li>
              <Link to={`/app/home/services/${service}`}>{service}</Link>
            </li>
          ))}
        </ul>
      </div>
    )
  }
}

export default ServicesListComponent
