import * as React from "react"
import Axios from "axios"
import {
  Button,
  Intent,
  Toaster,
  Classes,
  Popover,
  InputGroup
} from "@blueprintjs/core"

interface IStopAllRealBackillsProps {
  onUpdate?: () => void
  disabled: boolean
}

interface IStopAllRealBackfillsState {
  loading: boolean
}

const EMERGENCY_PROMISE =
  "I realize that by breaking glass that I am imposing a cost on the whole organization"

class StopAllRealButton extends React.Component<
  IStopAllRealBackillsProps,
  IStopAllRealBackfillsState
> {
  public state: IStopAllRealBackfillsState = {
    loading: false
  }

  stopall() {
    const url = `/backfills/stop_all`
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
    return (
      <Button
        onClick={() => this.stopall()}
        disabled={this.props.disabled}
        intent={Intent.DANGER}
        loading={this.state.loading}
        small={true}
        text={"Yes Stop All Running Backfills"}
      />
    )
  }
}

interface IStopAllBackillsProps {}

interface IStopAllBackfillsState {
  disabled: boolean
}

class StopAllButton extends React.Component<
  IStopAllBackillsProps,
  IStopAllBackfillsState
> {
  handleTextChange(newValue: String) {
    this.setState({
      disabled: newValue != EMERGENCY_PROMISE
    })
  }

  public state: IStopAllBackfillsState = {
    disabled: true
  }

  render() {
    return (
      <Popover
        interactionKind="click"
        popoverClassName={Classes.POPOVER_CONTENT_SIZING}
        position="right"
        content={
          <div>
            <p>
              Are you sure? This should only been done during large incidents.
            </p>
            <p>If there is an ongoing incident, please type:</p>
            <p>"{EMERGENCY_PROMISE}"</p>
            <p>(without quotes) in the text box below to enable.</p>
            <InputGroup
              onChange={(event: React.FormEvent<HTMLElement>) =>
                this.handleTextChange((event.target as HTMLInputElement).value)
              }
            />
            <StopAllRealButton disabled={this.state.disabled} />
          </div>
        }
        children={
          <Button
            intent={Intent.DANGER}
            small={true}
            text={"Stop All Running Backfills"}
          />
        }
      />
    )
  }
}

export default StopAllButton
