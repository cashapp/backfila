import {Classes, H1, HTMLTable} from "@blueprintjs/core"
import {ErrorCalloutComponent} from "@misk/core"
import * as React from "react"
import {Link} from "react-router-dom"
import {RESERVED_FLAVOR} from "../utilities";

export interface IServiceLinkProps {
  flavor: string | null
  service: string
}

function FlavorLink(props: IServiceLinkProps) {
  const flavorName = props.flavor || RESERVED_FLAVOR
  return (
    <Link to={`/app/services/${props.service}/flavors/${flavorName}`}>
      {flavorName}
    </Link>
  );
}

export interface IFlavorTableProps {
  data: any
  service: string
}

export const FlavorsListComponent = (props: IFlavorTableProps) => {
  const {data, service} = props
  if (data) {
    /**
     * Data is loaded and ready to be rendered
     */
    return (
      <div>
        <H1>{service} flavors</H1>
        <HTMLTable bordered={true} striped={true}>
          <tbody>
          {data.map((flavor: any) => (
            <tr>
              <td>
                <FlavorLink
                  service={service}
                  flavor={flavor.name}
                />
              </td>
              <td>{flavor.running_backfills} running</td>
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
        <H1>Flavors</H1>
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
        <ErrorCalloutComponent error={data.error}/>
      </div>
    )
  }
}

export default FlavorsListComponent
