import * as React from "react"
import Axios from "axios"
import { Button, Intent, Toaster, Classes, Popover } from "@blueprintjs/core"

interface IStopAllBackillsProps {
  onUpdate?: () => void
}

interface IStopAllBackfillsState {
  loading: boolean
}

class StopAllRealButton extends React.Component<IStopAllBackillsProps, IStopAllBackfillsState> {
  public state: IStopAllBackfillsState = {
    loading: false
  }

  stopall() {
    const url = `/backfills/stop_all`
    this.setState({loading: true})
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
      .finally(() => this.setState({loading: false}))
  }

  render() {
    return(
      <Button
        onClick={() => this.stopall()}
        intent={Intent.DANGER}
        loading={this.state.loading}
        small={true}
        text={"Yes Stop All Running Backfills"}
      />
    )
  }
}

class StopAllButton extends React.Component {
  render() {
    return (
      <Popover
        interactionKind="click"
        popoverClassName={Classes.POPOVER_CONTENT_SIZING}
        position="right"
        content={
          <div>
            <p>Are you sure? This should only been done during large incidents.</p>
            <StopAllRealButton />
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
    );
  }
}

export default StopAllButton