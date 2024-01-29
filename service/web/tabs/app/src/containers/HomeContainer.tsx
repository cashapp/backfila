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
import { Checkbox, FormGroup } from "@blueprintjs/core"
import { LayoutContainer } from "."
import { RouteComponentProps } from "react-router-dom"
import StopAllBackfillsButton from "../components/StopAllBackfillsButton"

interface TabContainerState extends RouteComponentProps {
  only_show_running_backfills: boolean
}

class TabContainer extends React.Component<
  IState & IDispatchProps & TabContainerState,
  IState & TabContainerState
> {
  private tableTag = "services"
  private tableUrl = "/services"

  componentDidMount() {
    this.setState({ only_show_running_backfills: false })
    this.props.simpleNetworkGet(this.tableTag, this.tableUrl)
  }

  render() {
    if (!this.state) {
      return <div>loading...</div>
    } else {
      return (
        <LayoutContainer>
          <FormGroup>
            <Checkbox
              checked={this.state.only_show_running_backfills}
              label={"Only Services with running Backfills"}
              disabled={false}
              onChange={() => {
                this.setState({
                  only_show_running_backfills: !this.state
                    .only_show_running_backfills
                })
              }}
            />
          </FormGroup>
          <ServicesListComponent
            data={simpleSelectorGet(
              this.props.simpleNetwork,
              [this.tableTag, "data", "services"],
              []
            )}
            url={this.tableUrl}
            tag={this.tableTag}
            onlyShowRunningBackfills={this.state.only_show_running_backfills}
          />
          <StopAllBackfillsButton />
        </LayoutContainer>
      )
    }
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
