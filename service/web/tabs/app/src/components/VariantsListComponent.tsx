import { Classes, H1, HTMLTable } from "@blueprintjs/core"
import { ErrorCalloutComponent } from "@misk/core"
import * as React from "react"
import { Link } from "react-router-dom"

export interface IVariantTableProps {
  data: any
  service: string
}

export const VariantsListComponent = (props: IVariantTableProps) => {
  const { data, service } = props
  if (data) {
    /**
     * Data is loaded and ready to be rendered
     */
    return (
      <div>
        <H1>{service} variants</H1>
        <HTMLTable bordered={true} striped={true}>
          <tbody>
            {data.map((variant: any) => (
              <tr>
                <td>
                  <Link
                    to={`/app/services/${props.service}/variants/${variant.name}`}
                  >
                    {variant.name}
                  </Link>
                </td>
                <td>{variant.running_backfills} running</td>
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
        <H1>Variants</H1>
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

export default VariantsListComponent
