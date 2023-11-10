import { Link } from "react-router-dom"
import * as React from "react"
import { RESERVED_VARIANT } from "../utilities"
import { H2 } from "@blueprintjs/core"

export interface IServiceLevelProps {
  serviceName: string
  variants: string[]
}

export function ServiceLink(props: IServiceLevelProps) {
  if (props.variants.length > 1) {
    return (
      <Link to={`/app/services/${props.serviceName}/variants/`}>
        {props.serviceName}
      </Link>
    )
  }
  return (
    <Link to={`/app/services/${props.serviceName}/`}>{props.serviceName}</Link>
  )
}

export interface IVariantLevelProps {
  serviceName: string
  variant: string
}

export function VariantLink(props: IVariantLevelProps) {
  if (props.variant != RESERVED_VARIANT) {
    return (
      <Link
        to={`/app/services/${props.serviceName}/variants/${props.variant}/`}
      >
        {props.serviceName} ({props.variant})
      </Link>
    )
  }
  return (
    <Link to={`/app/services/${props.serviceName}/`}>{props.serviceName}</Link>
  )
}

export function ServiceHeader(props: IVariantLevelProps) {
  if (props.variant != RESERVED_VARIANT) {
    return (
      <H2>
        {props.serviceName} ({props.variant})
        <Link to={`/app/services/${props.serviceName}/variants`}>
          Other variants
        </Link>
      </H2>
    )
  }
  return <H2> {props.serviceName} </H2>
}
