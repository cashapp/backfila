import {simpleSelect} from "@misk/simpleredux"
import * as React from "react"
import {connect} from "react-redux"
import {ServicesListComponent} from "../components"
import {IDispatchProps, IState, mapDispatchToProps, mapStateToProps} from "../ducks"

class TabContainer extends React.Component<IState & IDispatchProps, IState> {
  private tableTag = "services"
  private tableUrl = "/services"

  componentDidMount() {
    this.props.simpleNetworkGet(this.tableTag, this.tableUrl)
  }

  render() {
    return (
      <div>
        <ServicesListComponent
          data={simpleSelect(this.props.simpleNetwork, this.tableTag)}
          url={this.tableUrl}
          tag={this.tableTag}
        />
      </div>
    )
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(TabContainer)
