import { simpleSelectorGet } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import { ServicesListComponent } from "src/components"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "src/ducks"
import { LayoutContainer } from "."

class TabContainer extends React.Component<IState & IDispatchProps, IState> {
  private tableTag = "services"
  private tableUrl = "/services"

  componentDidMount() {
    this.props.simpleNetworkGet(this.tableTag, this.tableUrl)
  }

  render() {
    return (
      <LayoutContainer>
        <ServicesListComponent
          data={simpleSelectorGet(
            this.props.simpleNetwork,
            [this.tableTag, "data", "services"],
            []
          )}
          url={this.tableUrl}
          tag={this.tableTag}
        />
      </LayoutContainer>
    )
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
