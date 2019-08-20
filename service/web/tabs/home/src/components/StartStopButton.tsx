import * as React from "react"
import { Button, Intent } from "@blueprintjs/core"
import { connect } from "react-redux"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"
import { onClickFnCall, simpleSelect } from "@misk/simpleredux"

export interface IStartStopButtonProps {
  id: string
  state: string
}

class StartStopButton extends React.Component<
  IState & IDispatchProps & IStartStopButtonProps,
  IState
> {
  private id: string = this.props.id
  private postTag: string = `${this.id}::StartStopButton`

  render() {
    const state = this.props.state
    if (state == "RUNNING") {
      return (
        <Button
          onClick={onClickFnCall(
            this.props.simpleNetworkPost,
            this.postTag,
            `/backfills/${this.id}/stop`
          )}
          intent={Intent.DANGER}
          loading={simpleSelect(
            this.props.simpleNetwork,
            this.postTag,
            "loading"
          )}
          small={true}
          text={"Stop"}
        />
      )
    } else if (state == "PAUSED") {
      return (
        <Button
          onClick={onClickFnCall(
            this.props.simpleNetworkPost,
            this.postTag,
            `/backfills/${this.id}/start`
          )}
          intent={Intent.SUCCESS}
          loading={simpleSelect(
            this.props.simpleNetwork,
            this.postTag,
            "loading"
          )}
          small={true}
          text={"Start"}
        />
      )
    }
    return null
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(StartStopButton)
