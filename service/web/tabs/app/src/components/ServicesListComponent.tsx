import { Classes, H1, HTMLTable } from "@blueprintjs/core"
import { ErrorCalloutComponent } from "@misk/core"
import { ServiceLink } from "../components"
import * as React from "react"

export interface ITableProps {
  data: any
  url?: string
  tag?: string
  onlyShowRunningBackfills: boolean
}

export const ServicesListComponent = (props: ITableProps) => {
  const { data } = props
  const serviceHasRunningBackfills = (service: any) =>
    service.running_backfills > 0
  const shouldShowService = (service: any) =>
    !props.onlyShowRunningBackfills || serviceHasRunningBackfills(service)
  if (data) {
    /**
     * Data is loaded and ready to be rendered
     */
    return (
      <div>
        <H1>Services</H1>
        <HTMLTable bordered={true} striped={true}>
          <tbody>
            {data.filter(shouldShowService).map((service: any) => (
              <tr>
                <td>
                  <ServiceLink
                    serviceName={service.name}
                    variants={service.variants}
                  />
                </td>
                <td>{service.running_backfills} running</td>
              </tr>
            ))}
          </tbody>
        </HTMLTable>
      </div>
    )
  } else {
    /**
     * Have a nice failure mode while your data is loading or doesn't load
     */
    const FakeCell = <p className={Classes.SKELETON}>lorem ipsum 1234 5678</p>
    return (
      <div>
        <H1>Services</H1>
        <HTMLTable bordered={true} striped={true}>
          <tbody>
            <tr>
              <td>{FakeCell}</td>
            </tr>
            <tr>
              <td>{FakeCell}</td>
            </tr>
            <tr>
              <td>{FakeCell}</td>
            </tr>
          </tbody>
        </HTMLTable>
        <ErrorCalloutComponent error={data.error} />
      </div>
    )
  }
}

export default ServicesListComponent
