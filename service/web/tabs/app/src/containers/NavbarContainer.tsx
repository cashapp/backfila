import { Environment, Navbar, ResponsiveAppContainer } from "@misk/core"
import { simpleSelectorGet } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import { HomeLinkLogo } from "src/components"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "src/ducks"
import { backfilaTheme } from "src/utilities"

class NavbarContainer extends React.Component<IState & IDispatchProps, IState> {
  private serviceMetadataTag = "serviceMetadata"
  private serviceMetadataUrl = "/api/service/metadata"

  componentDidMount() {
    if (
      !simpleSelectorGet(this.props.simpleNetwork, [
        this.serviceMetadataTag,
        "data"
      ])
    ) {
      this.props.simpleNetworkGet(
        this.serviceMetadataTag,
        this.serviceMetadataUrl
      )
    }
  }

  render() {
    return (
      <div>
        <Navbar
          environment={simpleSelectorGet(
            this.props.simpleNetwork,
            [this.serviceMetadataTag, "data", "serviceMetadata", "environment"],
            Environment.PRODUCTION
          )}
          homeName={<HomeLinkLogo />}
          homeUrl={"/app/"}
          menuShowButton={false}
          theme={backfilaTheme}
        />
        <ResponsiveAppContainer>{this.props.children}</ResponsiveAppContainer>
      </div>
    )
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(NavbarContainer)
