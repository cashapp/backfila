import {
  MiskNavbarContainer,
  miskDashboardMetadataUrl,
  miskServiceMetadataUrl
} from "@misk/core"
import * as React from "react"
import { HomeLinkLogo } from "src/components"
import { backfilaTheme } from "src/utilities"

export const LayoutContainer = (props: { children: any }) => (
  <MiskNavbarContainer
    dashboardMetadataUrl={miskDashboardMetadataUrl("backfilaapp")}
    children={props.children}
    homeName={<HomeLinkLogo />}
    homeUrl={"/app/"}
    menuShowButton={false}
    propsOverrideRemoteData={true}
    serviceMetadataUrl={miskServiceMetadataUrl}
    theme={backfilaTheme}
  />
)
