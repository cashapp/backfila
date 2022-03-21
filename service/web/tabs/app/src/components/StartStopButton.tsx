import * as React from "react"
import Axios from "axios"
import { Button, Intent, Toaster } from "@blueprintjs/core"
import { connect } from "react-redux"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"

export interface IStartStopButtonProps {
  id: string
  state: string
  onUpdate?: () => void
}

interface IStartStopButtonState {
  loading: boolean
}

class StartStopButton extends React.Component<
  IState & IDispatchProps & IStartStopButtonProps,
  IStartStopButtonState
> {
  private id: string = this.props.id

  public state: IStartStopButtonState = {
    loading: false
  }

  startstop(startorstop: String) {
    const url = `/backfills/${this.id}/${startorstop}`
    this.setState({ loading: true })
    Axios.post(url, {})
      .then(response => {
        if (this.props.onUpdate) {
          this.props.onUpdate()
        }
      })
      .catch(error => {
        Toaster.create().show({
          intent: Intent.DANGER,
          message: `Error: ${error.response.data}`
        })
      })
      .finally(() => this.setState({ loading: false }))
  }

  render() {
    const state = this.props.state
    if (state == "RUNNING") {
      return (
        <Button
          onClick={() => this.startstop("stop")}
          intent={Intent.DANGER}
          loading={this.state.loading}
          small={true}
          text={"Stop"}
        />
      )
    } else if (state == "PAUSED") {
      return (
        <Button
          onClick={() => this.startstop("start")}
          intent={Intent.SUCCESS}
          loading={this.state.loading}
          small={true}
          text={"Start"}
        />
      )
    }
    return null
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(StartStopButton)
