import { Classes, H1 } from "@blueprintjs/core"
import { ErrorCalloutComponent } from "@misk/core"
import * as React from "react"
import { Link } from "react-router-dom"

export interface ITableProps {
  data: any
  url?: string
  tag?: string
}

export const ServicesListComponent = (props: ITableProps) => {
  const { data } = props
  if (data) {
    /**
     * Data is loaded and ready to be rendered
     */
    return (
      <div>
        <H1>Services</H1>
        <ul>
          {data.map((service: string) => (
            <li>
              <Link to={`/app/services/${service}`}>{service}</Link>
            </li>
          ))}
        </ul>
      </div>
    )
  } else {
    /**
     * Have a nice failure mode while your data is loading or doesn't load
     */
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
  }
}

export default ServicesListComponent
